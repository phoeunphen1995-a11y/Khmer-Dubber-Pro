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
        // ដាក់ម៉ូដែល flash-lite មុនគេដើម្បីឲ្យបកប្រែលឿនបំផុត
        private val ULTRA_FAST_MODELS = listOf(
            "gemini-3.8-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.5-flash"
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
        if (audioBytes.isEmpty()) {
            throw IllegalStateException("ឯកសារសំឡេងទទេ មិនអាចបកប្រែបានទេ!")
        }
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        // Prompt ខ្លីបំផុតដើម្បីឲ្យ AI បកប្រែចេញលឿនភ្លាមៗ
        val prompt = """
            Translate speech to natural Khmer. Tag gender ("MALE"/"FEMALE").
            JSON ONLY:
            {"detectedLanguageCode":"zh","segments":[{"startMs":1000,"endMs":3500,"speaker":"MALE","sourceText":"","khmerText":"..."}]}
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
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 0)
                })
            })
        }

        val payloadBytes = requestBody.toString().toByteArray(Charsets.UTF_8)
        var lastError = "Server ជាប់រវល់ សូមព្យាយាមម្តងទៀត"
        var responseText: String? = null

        outerLoop@ for (modelName in ULTRA_FAST_MODELS) {
            for (attempt in 1..2) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$GEMINI_API_KEY")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 35000
                    }

                    conn.outputStream.use { it.write(payloadBytes) }

                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        break@outerLoop
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        Log.w("GeminiTranslation", "$modelName ($responseCode): $err")
                        lastError = "Gemini API Error ($responseCode): $err"
                        // បើម៉ូដែលអត់ស្គាល់ (404) ឬជាប់រវល់ (503) ប្តូរទៅម៉ូដែលបន្ទាប់ភ្លាមៗ
                        if (responseCode == 404 || responseCode == 503) break
                        delay(300L)
                    }
                } catch (e: Exception) {
                    Log.w("GeminiTranslation", "$modelName exception: ${e.message}")
                    lastError = e.message ?: "ការតភ្ជាប់យឺត"
                    delay(300L)
                } finally {
                    conn?.disconnect()
                }
            }
        }

        if (responseText == null) {
            throw RuntimeException(lastError)
        }

        val rawText = JSONObject(responseText)
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

        val segments = ArrayList<SubtitleSegment>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val speaker = item.optString("speaker", "FEMALE").uppercase()
            val khmerText = item.optString("khmerText", "").trim()
            if (khmerText.isEmpty()) continue

            val taggedText = if (speaker == "MALE") "[M]$khmerText" else "[F]$khmerText"
            val start = item.optLong("startMs", 0L)
            val end = maxOf(item.optLong("endMs", start + 1500L), start + 500L)

            segments.add(
                SubtitleSegment(
                    index = segments.size + 1,
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
