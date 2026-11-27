package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer

/**
 * Mix a dubbing audio track over a video.
 * Requires Media3 1.5.0+ (media3-transformer, media3-effect, media3-common).
 * Must be called from a thread with a Looper (e.g. the main thread).
 */
@OptIn(UnstableApi::class)
class AudioDubbingMixer(private val context: Context) {

    interface Callback {
        fun onSuccess(outputPath: String)
        fun onError(error: Throwable)
    }

    /** Scale audio volume (1.0 = unchanged, 0.2 = quiet). */
    private fun volumeEffects(volume: Float): Effects {
        val mixer = ChannelMixingAudioProcessor()
        for (channels in 1..2) {
            mixer.putChannelMixingMatrix(
                ChannelMixingMatrix.create(channels, 2).scaleBy(volume)
            )
        }
        return Effects(listOf<AudioProcessor>(mixer), emptyList())
    }

    fun mix(
        videoUri: Uri,
        dubAudioUri: Uri,
        outputPath: String,
        originalVolume: Float = 0.2f,
        dubVolume: Float = 1.0f,
        callback: Callback
    ) {
        // Video + original audio (lowered volume)
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setEffects(volumeEffects(originalVolume))
            .build()

        // Dubbing audio only
        val dubItem = EditedMediaItem.Builder(MediaItem.fromUri(dubAudioUri))
            .setRemoveVideo(true)
            .setEffects(volumeEffects(dubVolume))
            .build()

        // Use Builder, not List
        val videoSequence = EditedMediaItemSequence.Builder(videoItem).build()
        val dubSequence = EditedMediaItemSequence.Builder(dubItem).build()

        val composition = Composition.Builder(listOf(videoSequence, dubSequence)).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    callback.onSuccess(outputPath)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    callback.onError(exportException)
                }
            })
            .build()

        transformer.start(composition, outputPath)
    }
}
