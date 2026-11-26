package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "KhmerVoiceSynthesizer"

data class TtsSynthesisResult(
    val ttsAvailable: Boolean,
    val clipFilesBySegmentIndex: Map<Int, File>
)

class KhmerVoiceSynthesizer(private val context: Context) {

    private val khmerLocale = Locale.forLanguageTag("km-KH")

    suspend fun synthesizeSegments(
        projectId: String,
        segments: List<SubtitleSegment>,
        voiceProfile: DubbingVoiceProfile,
        onSegmentProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): TtsSynthesisResult = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) {
            return@withContext TtsSynthesisResult(ttsAvailable = false, clipFilesBySegmentIndex = emptyMap())
        }

        val tts = initTts() ?: return@withContext TtsSynthesisResult(
            ttsAvailable = false,
            clipFilesBySegmentIndex = emptyMap()
        )

        try {
            val availability = tts.isLanguageAvailable(khmerLocale)
            val khmerSupported = availability >= TextToSpeech.LANG_AVAILABLE
            if (!khmerSupported) {
                Log.w(TAG, "Khmer TTS voice is not installed on this device (code=$availability)")
                return@withContext TtsSynthesisResult(
                    ttsAvailable = false,
                    clipFilesBySegmentIndex = emptyMap()
                )
            }

            tts.language = khmerLocale
            tts.setPitch(voiceProfile.pitch)
            tts.setSpeechRate(voiceProfile.speechRate)

            val ttsDir = File(context.cacheDir, "tts/$projectId").apply { mkdirs() }
            val clips = mutableMapOf<Int, File>()

            segments.forEachIndexed { idx, segment ->
                val rawText = segment.khmerText.trim()
                val isMale = rawText.startsWith("[M]", ignoreCase = true)
                val isFemale = rawText.startsWith("[F]", ignoreCase = true)
                val text = rawText
                    .removePrefix("[M]")
                    .removePrefix("[m]")
                    .removePrefix("[F]")
                    .removePrefix("[f]")
                    .trim()

                tts.setPitch(
                    when {
                        isMale -> 0.75f
                        isFemale -> 1.15f
                        else -> voiceProfile.pitch
                    }
                )

                if (text.isNotEmpty()) {
                    val outWav = File(ttsDir, "seg_${segment.index}.wav")
                    if (outWav.exists()) outWav.delete()
                    val utteranceId = "utt_${projectId}_${segment.index}"
                    val ok = synthesizeToFile(tts, text, outWav, utteranceId)
                    if (ok && outWav.exists() && outWav.length() > 44L) {
                        clips[segment.index] = outWav
                    }
                }
                onSegmentProgress(idx + 1, segments.size)
            }

            TtsSynthesisResult(
                ttsAvailable = clips.isNotEmpty(),
                clipFilesBySegmentIndex = clips
            )
        } finally {
            runCatching {
                tts.stop()
                tts.shutdown()
            }
        }
    }

    private suspend fun initTts(): TextToSpeech? = withTimeout(10_000L) {
        suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                if (cont.isActive) {
                    if (status == TextToSpeech.SUCCESS) {
                        cont.resume(engine)
                    } else {
                        runCatching { engine?.shutdown() }
                        cont.resume(null)
                    }
                }
            }
            cont.invokeOnCancellation {
                runCatching { engine?.shutdown() }
            }
        }
    }

    private suspend fun synthesizeToFile(
        tts: TextToSpeech,
        text: String,
        outFile: File,
        utteranceId: String
    ): Boolean = withTimeout(30_000L) {
        suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(false)
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(false)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = tts.synthesizeToFile(text, params, outFile, utteranceId)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(false)
            }
        }
    }
}
