package com.example.data.network

import android.util.Log
import com.example.data.model.SourceLanguage
import com.example.data.model.SubtitleSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "GeminiTranslationSvc"
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024

class GeminiTranslationService {

    private val subtitleSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguage" to Schema.string(
                description = "ISO 639-1 language code of the spoken audio, e.g. en, zh, th, vi, ja"
            ),
            "segments" to Schema.array(
                description = "Chronological speech segments transcribed and translated to natural Khmer",
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Segment start time in milliseconds"),
                        "endMs" to Schema.integer(description = "Segment end time in milliseconds"),
                        "sourceText" to Schema.string(description = "Original spoken transcript in source language"),
                        "khmerText" to Schema.string(description = "Natural, idiomatic Khmer translation in Khmer script")
                    )
                )
            )
        )
    )

    private val config = generationConfig {
        responseMimeType = "application/json"
        responseSchema = subtitleSchema
        temperature = 0.2f
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun transcribeAndTranslateToKhmer(
        audioFile: File,
        mimeType: String,
        sourceLanguage: SourceLanguage,
        durationMs: Long
    ): TranslationOutput {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw IllegalStateException("ឯកសារសំឡេងដែលបានទាញយកគឺទទេ ឬរកមិនឃើញ។")
        }
        if (audioFile.length() > MAX_INLINE_AUDIO_BYTES) {
            throw IllegalStateException(
                "ទំហំសំឡេងលើសពី 18 MB (${audioFile.length() / (1024 * 1024)} MB)។ សូមប្រើវីដេអូខ្លីជាងនេះ ឬប្រើម៉ាស៊ីនមេ HTTPS ផ្ទាល់ខ្លួន។"
            )
        }

        val audioBytes = audioFile.readBytes()
        val sourceHint = if (sourceLanguage == SourceLanguage.AUTO) {
            "Automatically detect the spoken language."
        } else {
            "The primary spoken language is ${sourceLanguage.englishName} (${sourceLanguage.code})."
        }

        val prompt = """
            You are an expert professional video subtitle translator and Khmer dubbing scriptwriter.
            Listen carefully to the speech in this audio track (total video duration: ${durationMs}ms).
            $sourceHint
            
            Instructions:
            1. Transcribe every spoken dialogue segment accurately in its original language (`sourceText`).
            2. Translate each segment into natural, fluent, spoken-style Khmer script (`khmerText`) suitable for voice-over dubbing and readable subtitles.
            3. Provide accurate chronological timestamps (`startMs` and `endMs` in milliseconds, between 0 and $durationMs). Ensure `endMs > startMs` and segments do not overlap.
            4. Ignore pure background music or non-speech sound effects when creating dialogue segments.
            5. If there is no intelligible speech in the audio, return an empty `segments` array.
        """.trimIndent()

        try {
            val response = withTimeout(90_000L) {
                model.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("AI មិនបានផ្ញើលទ្ធផលបកប្រែត្រឡប់មកវិញទេ។")

            val root = JSONObject(rawJson)
            val detected = root.optString("detectedLanguage", sourceLanguage.code)
                .ifBlank { sourceLanguage.code }
            val segmentsArray = root.optJSONArray("segments")
            val parsedSegments = mutableListOf<SubtitleSegment>()

            if (segmentsArray != null) {
                for (i in 0 until segmentsArray.length()) {
                    val item = segmentsArray.getJSONObject(i)
                    val start = item.optLong("startMs", 0L).coerceAtLeast(0L)
                    val rawEnd = item.optLong("endMs", start + 1500L)
                    val end = maxOf(rawEnd, start + 350L).coerceAtMost(maxOf(durationMs, start + 500L))
                    val srcText = item.optString("sourceText", "").trim()
                    val kmText = item.optString("khmerText", "").trim()
                    if (kmText.isNotBlank() || srcText.isNotBlank()) {
                        parsedSegments.add(
                            SubtitleSegment(
                                index = parsedSegments.size + 1,
                                startMs = start,
                                endMs = end,
                                sourceText = srcText,
                                khmerText = kmText.ifBlank { srcText }
                            )
                        )
                    }
                }
            }

            return TranslationOutput(
                detectedLanguageCode = detected,
                segments = parsedSegments.sortedBy { it.startMs }
                    .mapIndexed { idx, seg -> seg.copy(index = idx + 1) }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini translation timed out", e)
            throw IllegalStateException("ការបកប្រែ AI អស់ពេលកំណត់ 90 វិនាទី។ សូមពិនិត្យអ៊ីនធឺណិត ហើយសាកល្បងម្តងទៀត។", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini translation failed", e)
            throw e
        }
    }
}
