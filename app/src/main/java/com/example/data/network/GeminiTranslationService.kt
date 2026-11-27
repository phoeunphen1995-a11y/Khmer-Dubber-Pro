package com.example.data.network

import android.media.MediaMetadataRetriever
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
        private val MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.5-flash"
        )
        // បំបែកការបកប្រែជាកំណាត់ៗ (៣ នាទីម្តង) ដើម្បីឲ្យវីដេអូវែង ៣០ នាទីបកប្រែបានគ្រប់ម៉ាត់ និងមិន Timeout
        private const val CHUNK_DURATION_MS = 180_000L
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
        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw IllegalStateException("ឯកសារសំឡេងទទេ មិនអាចបកប្រែបានទេ!")
        }

        // រករយៈពេលពិតប្រាកដនៃសំឡេង (គាំទ្រដល់ ៣០ នាទី+)
        val totalDurationMs = if (durationMs > 0L) durationMs else extractAudioDurationMs(audioFile)

        // ជំហានទី ១៖ Upload ឯកសារសំឡេងតែម្តងគត់តាម File API
        val uploadedFile = uploadAudioFile(audioFile, mimeType)
        val fileUri = uploadedFile.first
        val fileResourceName = uploadedFile.second

        // រង់ចាំរហូតដល់ Google Server ដំណើរការឯកសាររួចរាល់ (សំខាន់សម្រាប់វីដេអូ ៣០ នាទី)
        waitUntilFileActive(fileResourceName)

        // ជំហានទី ២៖ បកប្រែជាកំណាត់ៗ (៣ នាទីម្តង) រហូតដល់ចប់វីដេអូ
        val allSegments = ArrayList<SubtitleSegment>()
        var detectedLang = "auto"

        val effectiveTotalMs = if (totalDurationMs > 0L) totalDurationMs else CHUNK_DURATION_MS
        var chunkStartMs = 0L

        while (chunkStartMs < effectiveTotalMs) {
            val chunkEndMs = if (totalDurationMs > 0L) {
                minOf(chunkStartMs + CHUNK_DURATION_MS, totalDurationMs)
            } else {
                CHUNK_DURATION_MS
            }

            val startSec = chunkStartMs / 1000.0
            val endSec = chunkEndMs / 1000.0

            val prompt = if (totalDurationMs > CHUNK_DURATION_MS) {
                """
                Listen ONLY to the audio segment from ${startSec}s to ${endSec}s (in milliseconds: $chunkStartMs to $chunkEndMs).
                Transcribe and translate all spoken dialogue in this time range into natural, expressive Khmer.
                Keep exact timestamps in milliseconds ("startMs" and "endMs" must be between $chunkStartMs and $chunkEndMs).
                Set "speaker" to "MALE" or "FEMALE".
                Return ONLY valid JSON:
                {"detectedLanguageCode":"zh","segments":[{"startMs":$chunkStartMs,"endMs":${chunkStartMs + 2500},"speaker":"MALE","sourceText":"...","khmerText":"..."}]}
                """.trimIndent()
            } else {
                """
                Listen to the entire audio and translate all spoken dialogue into natural, expressive Khmer.
                Set "speaker" to "MALE" or "FEMALE".
                Return ONLY valid JSON:
                {"detectedLanguageCode":"zh","segments":[{"startMs":1000,"endMs":3500,"speaker":"MALE","sourceText":"...","khmerText":"..."}]}
                """.trimIndent()
            }

            val chunkResponse = requestTranslationForChunk(fileUri, mimeType, prompt)
            val parsedObj = JSONObject(chunkResponse)
            if (detectedLang == "auto") {
                detectedLang = parsedObj.optString("detectedLanguageCode", "auto")
            }

            val jsonArray = parsedObj.optJSONArray("segments") ?: JSONArray()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val speaker = item.optString("speaker", "FEMALE").uppercase()
                val khmerText = item.optString("khmerText", "").trim()
                if (khmerText.isEmpty()) continue

                val taggedText = if (speaker == "MALE") "[M]$khmerText" else "[F]$khmerText"
                var start = item.optLong("startMs", chunkStartMs)
                if (start < chunkStartMs && totalDurationMs > CHUNK_DURATION_MS) {
                    start += chunkStartMs
                }
                val end = maxOf(item.optLong("endMs", start + 1500L), start + 500L)

                allSegments.add(
                    SubtitleSegment(
                        index = allSegments.size + 1,
                        startMs = start,
                        endMs = end,
                        sourceText = item.optString("sourceText", ""),
                        khmerText = taggedText
                    )
                )
            }

            if (totalDurationMs <= 0L) break
            chunkStartMs += CHUNK_DURATION_MS
        }

        // លុបឯកសារបណ្តោះអាសន្នលើ Server វិញដើម្បីកុំឲ្យពេញទំហំ
        runCatching { deleteRemoteFile(fileResourceName) }

        return@withContext TranslationOutput(
            detectedLanguageCode = detectedLang,
            segments = allSegments
        )
    }

    private fun extractAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun requestTranslationForChunk(
        fileUri: String,
        mimeType: String,
        prompt: String
    ): String {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("file_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("file_uri", fileUri)
                            })
                        })
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
                put("maxOutputTokens", 65536)
            })
        }

        val payloadBytes = requestBody.toString().toByteArray(Charsets.UTF_8)
        var lastError = "Server ជាប់រវល់ សូមព្យាយាមម្តងទៀត"

        for (modelName in MODELS) {
            for (attempt in 1..3) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$GEMINI_API_KEY")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 60000
                        readTimeout = 600000 // ទុកពេល ១០ នាទីពេញសម្រាប់វីដេអូវែង
                    }

                    conn.outputStream.use { it.write(payloadBytes) }

                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        return JSONObject(responseText)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .replace("```json", "")
                            .replace("```", "")
                            .trim()
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        Log.w("GeminiTranslation", "$modelName ($responseCode): $err")
                        lastError = "Gemini API Error ($responseCode): $err"
                        if (responseCode == 404) break
                        delay(1500L * attempt)
                    }
                } catch (e: Exception) {
                    Log.w("GeminiTranslation", "$modelName exception: ${e.message}")
                    lastError = e.message ?: "ការតភ្ជាប់អ៊ីនធឺណិតរអាក់រអួល"
                    delay(1500L * attempt)
                } finally {
                    conn?.disconnect()
                }
            }
        }
        throw RuntimeException(lastError)
    }

    private fun uploadAudioFile(file: File, mimeType: String): Pair<String, String> {
        val uploadUrl = URL("https://generativelanguage.googleapis.com/upload/v1beta/files?uploadType=media&key=$GEMINI_API_KEY")
        val conn = (uploadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("X-Goog-Upload-Protocol", "raw")
            doOutput = true
            connectTimeout = 60000
            readTimeout = 600000 // ទុកពេល ១០ នាទីសម្រាប់ Upload ឯកសារ ៣០ នាទី
        }

        try {
            file.inputStream().use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Upload failed ($code)"
                throw RuntimeException("Upload Audio Error ($code): $err")
            }
            val fileObj = JSONObject(body).getJSONObject("file")
            return Pair(fileObj.getString("uri"), fileObj.getString("name"))
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun waitUntilFileActive(fileResourceName: String) {
        for (i in 1..30) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/$fileResourceName?key=$GEMINI_API_KEY")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
            }
            try {
                if (conn.responseCode in 200..299) {
                    val state = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .optString("state", "ACTIVE")
                    if (state == "ACTIVE") return
                    if (state == "FAILED") throw RuntimeException("Google មិនអាចអានឯកសារសំឡេងនេះបានទេ")
                }
            } finally {
                conn.disconnect()
            }
            delay(2000L)
        }
    }

    private fun deleteRemoteFile(fileResourceName: String) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/$fileResourceName?key=$GEMINI_API_KEY")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
