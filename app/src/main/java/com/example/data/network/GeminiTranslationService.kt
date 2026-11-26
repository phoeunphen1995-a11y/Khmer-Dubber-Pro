package com.example.data.network

import android.util.Base64
import android.util.Log
import com.example.data.model.SourceLanguage
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GeminiTranslationService {

    companion object {
        private val GEMINI_API_KEY = "AQ.Ab8RN6LvvD1XUBWkDltJoBww" + "-JXzDrvmWHuh9wSyYqCGv6AMwg"
        private val FALLBACK_MODELS = listOf(
            "gemini-3.8-flash",
            "gemini-3.8-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash-lite"
        )
    }

    suspend fun transcribeAndTranslateToKhmer(
        audioFile: File,
        mimeType: String = "audio/mp4",
        sourceLanguage: SourceLanguage = SourceLanguage.AUTO,
        durationMs: Long = 0L
    ): TranslationOutput = withContext(Dispatchers.IO) {
        if (GEMINI_API_KEY.isBlank()) {
            throw IllegalStateException("សូមដាក់ Gemini API Key ក្នុង GeminiTranslationService.kt ជាមុនសិន!")
        }

        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val prompt = """
            Listen to this audio carefully and translate all spoken dialogue into natural, expressive Khmer.
            Identify the speaker's gender for each line ("MALE" or "FEMALE") so we can dub with male and female voices.
            Return ONLY a valid JSON object with no markdown formatting, using this exact structure:
            {
              "detectedLanguageCode": "zh",
              "segments": [
                {"startMs": 1000, "endMs": 3500, "speaker": "MALE", "sourceText": "...", "khmerText": "..."},
                {"startMs": 3800, "endMs": 6200, "speaker": "FEMALE", "sourceText": "...", "khmerText": "..."}
              ]
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", base64Audio)
                            })
                        })
                    })
                })
            })
        }

        val payloadBytes = requestBody.toString().toByteArray(Charsets.UTF_8)
        var lastError = "Unknown error"
        var responseText: String? = null

        for (modelName in FALLBACK_MODELS) {
            for (attempt in 1..2) {
                try {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$GEMINI_API_KEY")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 60000
                        readTimeout = 120000
                    }

                    conn.outputStream.use { it.write(payloadBytes) }

                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        Log.w("GeminiTranslation", "Model $modelName (attempt $attempt) failed ($responseCode): $err")
                        lastError = "Gemini API Error ($responseCode): $err"
                        // បើម៉ូដែលអត់មាន (404) ប្តូរទៅម៉ូដែលបន្ទាប់ភ្លាម
                        if (responseCode == 404) break
                        // បើជាប់រវល់ (503 ឬ 429) រង់ចាំ 2 វិនាទីរួចសាកម្តងទៀត
                        delay(2000L)
                    }
                } catch (e: Exception) {
                    Log.w("GeminiTranslation", "Model $modelName exception: ${e.message}")
                    lastError = e.message ?: "Network error"
                    delay(1500L)
                }
            }
            if (responseText != null) break
        }

        if (responseText == null) {
            throw RuntimeException(lastError)
        }

        val jsonResponse = JSONObject(responseText)
        val rawText = jsonResponse
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val parsedObj = JSONObject(rawText)
        val detectedLang = parsedObj.optString("detectedLanguageCode", "auto")
        val jsonArray = parsedObj.getJSONArray("segments")

        val segments = mutableListOf<SubtitleSegment>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val speaker = item.optString("speaker", "FEMALE").uppercase()
            val khmerText = item.optString("khmerText", "")
            val taggedText = if (speaker == "MALE") "[M]$khmerText" else "[F]$khmerText"
            val start = item.optLong("startMs", 0L)
            val end = maxOf(item.optLong("endMs", start + 1500L), start + 500L)

            segments.add(
                SubtitleSegment(
                    index = i + 1,
                    startMs = start,
                    endMs = end,
                    sourceText = item.optString("sourceText", ""),
                    khmerText = taggedText
                )
            )
        }

        return@withContext TranslationOutput(
            detectedLanguageCode = detectedLang,
            segments = segments
        )
    }
}
