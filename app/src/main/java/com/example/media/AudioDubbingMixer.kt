package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Effects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioDubbingMixer(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun mixAndRenderDubbedVideo(
        projectId: String,
        sourceVideoFile: File,
        extractedAudioFile: File,
        durationMs: Long,
        segments: List<SubtitleSegment>,
        ttsClips: Map<Int, File>,
        enableDubbing: Boolean,
        preserveBgm: Boolean,
        bgmDuckingLevel: Float
    ): File = withContext(Dispatchers.Main) {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputMp4 = File(exportsDir, "dubbed_${projectId}.mp4")
        if (outputMp4.exists()) outputMp4.delete()

        val sequences = ArrayList<EditedMediaItemSequence>()

        // ១. វីដេអូដើម + ភ្លេងដើម (បន្ថយសំឡេងភ្លេងដើមតាម bgmDuckingLevel បើ preserveBgm = true)
        val bgmVolume = when {
            !preserveBgm -> 0f
            enableDubbing -> bgmDuckingLevel.coerceIn(0.1f, 0.5f)
            else -> 1.0f
        }
        val bgmAudioProcessors = listOf<AudioProcessor>(createVolumeProcessor(bgmVolume))
        val mainVideoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceVideoFile)))
            .setRemoveAudio(!preserveBgm)
            .setEffects(Effects(bgmAudioProcessors, emptyList()))
            .build()
        sequences.add(EditedMediaItemSequence(listOf(mainVideoItem)))

        // ២. បញ្ចូលសំឡេងនិយាយខ្មែរ (TTS) គ្រប់ឃ្លាតាមម៉ោងកំណត់ (startMs)
        if (enableDubbing) {
            val validClips = segments.mapNotNull { seg ->
                val clipFile = ttsClips[seg.index]
                if (clipFile != null && clipFile.exists() && clipFile.length() > 44L) {
                    seg to clipFile
                } else null
            }.sortedBy { it.first.startMs }

            if (validClips.isNotEmpty()) {
                val ttsSequenceItems = ArrayList<EditedMediaItem>()
                var cursorMs = 0L
                val voiceBoost = listOf<AudioProcessor>(createVolumeProcessor(1.35f))

                for ((seg, clipFile) in validClips) {
                    val gapMs = (seg.startMs - cursorMs).coerceAtLeast(0L)
                    if (gapMs > 0L) {
                        // បន្ថែមភាពស្ងាត់ (Gap) រង់ចាំដល់ម៉ោងតួអង្គនិយាយ
                        val silentGap = EditedMediaItem.Builder(
                            MediaItem.Builder()
                                .setUri(Uri.fromFile(clipFile))
                                .setClippingConfiguration(
                                    MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(0L)
                                        .setEndPositionMs(10L)
                                        .build()
                                )
                                .build()
                        )
                            .setRemoveVideo(true)
                            .setEffects(Effects(listOf(createVolumeProcessor(0f)), emptyList()))
                            .build()
                        ttsSequenceItems.add(silentGap)
                    }

                    val voiceItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clipFile)))
                        .setRemoveVideo(true)
                        .setEffects(Effects(voiceBoost, emptyList()))
                        .build()
                    ttsSequenceItems.add(voiceItem)
                    cursorMs = seg.endMs
                }

                sequences.add(EditedMediaItemSequence(ttsSequenceItems))
            }
        }

        val composition = Composition.Builder(sequences).build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, result: ExportResult) {
                        if (cont.isActive) cont.resume(outputMp4)
                    }

                    override fun onError(
                        comp: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()

            cont.invokeOnCancellation { runCatching { transformer.cancel() } }
            transformer.start(composition, outputMp4.absolutePath)
        }
    }

    @OptIn(UnstableApi::class)
    private fun createVolumeProcessor(volume: Float): ChannelMixingAudioProcessor {
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix(1, 1, floatArrayOf(volume)))
            putChannelMixingMatrix(ChannelMixingMatrix(2, 2, floatArrayOf(volume, 0f, 0f, volume)))
            putChannelMixingMatrix(ChannelMixingMatrix(1, 2, floatArrayOf(volume, volume)))
            putChannelMixingMatrix(ChannelMixingMatrix(2, 1, floatArrayOf(volume * 0.5f, volume * 0.5f)))
        }
    }
}
