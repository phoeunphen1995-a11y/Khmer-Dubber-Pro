package com.example.data.network

import com.example.data.model.SubtitleSegment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

object SecureUrlValidator {
    fun isValidHttpsUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host ?: return false
        return scheme == "https" && host.isNotBlank() && uri.userInfo == null
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(isValidHttpsUrl(trimmed)) { "Only secure https:// endpoints are allowed." }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @param:Json(name = "status") val status: String = "ok",
    @param:Json(name = "version") val version: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendSegmentDto(
    @param:Json(name = "start_ms") val startMs: Long,
    @param:Json(name = "end_ms") val endMs: Long,
    @param:Json(name = "source_text") val sourceText: String,
    @param:Json(name = "khmer_text") val khmerText: String
)

@JsonClass(generateAdapter = true)
data class BackendTranslationResponse(
    @param:Json(name = "detected_language") val detectedLanguage: String? = null,
    @param:Json(name = "segments") val segments: List<BackendSegmentDto> = emptyList()
)

interface VideoTranslatorBackendApi {
    @GET("api/v1/health")
    suspend fun checkHealth(): HealthResponse

    @Multipart
    @POST("api/v1/translate-audio")
    suspend fun translateAudio(
        @Part audio: MultipartBody.Part,
        @Part("source_language") sourceLanguage: okhttp3.RequestBody,
        @Part("target_language") targetLanguage: okhttp3.RequestBody,
        @Part("voice_profile") voiceProfile: okhttp3.RequestBody
    ): BackendTranslationResponse
}

class HttpsOnlyAndRetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            throw IOException("Blocked insecure non-HTTPS request to ${request.url.host}")
        }

        var attempt = 0
        var lastException: IOException? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.code in listOf(502, 503, 504) && attempt < maxRetries) {
                    response.close()
                    attempt++
                    Thread.sleep(600L * attempt)
                    continue
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt >= maxRetries) throw e
                attempt++
                runCatching { Thread.sleep(600L * attempt) }
            }
        }
        throw lastException ?: IOException("HTTPS request failed after retries")
    }
}

class SecureBackendClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .addInterceptor(HttpsOnlyAndRetryInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun buildApi(baseUrl: String): VideoTranslatorBackendApi {
        val normalized = SecureUrlValidator.normalizeBaseUrl(baseUrl)
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VideoTranslatorBackendApi::class.java)
    }

    suspend fun verifyHealth(baseUrl: String): Result<HealthResponse> = runCatching {
        buildApi(baseUrl).checkHealth()
    }

    suspend fun translateAudioFile(
        baseUrl: String,
        audioFile: File,
        sourceLanguageCode: String,
        voiceProfileId: String
    ): TranslationOutput {
        val api = buildApi(baseUrl)
        val audioPart = MultipartBody.Part.createFormData(
            "audio",
            audioFile.name,
            audioFile.asRequestBody("audio/mp4".toMediaType())
        )
        val textType = "text/plain".toMediaType()
        val response = api.translateAudio(
            audio = audioPart,
            sourceLanguage = sourceLanguageCode.toRequestBody(textType),
            targetLanguage = "km".toRequestBody(textType),
            voiceProfile = voiceProfileId.toRequestBody(textType)
        )
        val segments = response.segments.mapIndexed { index, dto ->
            SubtitleSegment(
                index = index + 1,
                startMs = dto.startMs.coerceAtLeast(0L),
                endMs = maxOf(dto.endMs, dto.startMs + 400L),
                sourceText = dto.sourceText.trim(),
                khmerText = dto.khmerText.trim()
            )
        }
        return TranslationOutput(
            detectedLanguageCode = response.detectedLanguage ?: sourceLanguageCode,
            segments = segments
        )
    }
}

data class TranslationOutput(
    val detectedLanguageCode: String,
    val segments: List<SubtitleSegment>
)
