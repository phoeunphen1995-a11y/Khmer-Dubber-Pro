package com.example.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioExtractor(private val context: Context) {

    fun hasAudioTrack(videoFile: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(videoFile.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            true // Allow Transformer to attempt decoding exotic containers
        } finally {
            runCatching { extractor.release() }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun extractCompactAudio(projectId: String, videoFile: File): File {
        val hasAudio = withContext(Dispatchers.IO) { hasAudioTrack(videoFile) }
        if (!hasAudio) {
            throw IllegalStateException("វីដេអូនេះមិនមានសំឡេងសម្រាប់បម្លែងជាអក្សរទេ។")
        }

        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val outputFile = File(audioDir, "${projectId}_extracted.m4a")
        if (outputFile.exists()) outputFile.delete()

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                if (outputFile.exists() && outputFile.length() > 0L) {
                                    continuation.resume(outputFile)
                                } else {
                                    continuation.resumeWithException(
                                        IllegalStateException("ការទាញយកសំឡេងបានបង្កើតឯកសារទទេ។")
                                    )
                                }
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        exportException.localizedMessage ?: "មិនអាចទាញយកសំឡេងពីវីដេអូបានទេ។",
                                        exportException
                                    )
                                )
                            }
                        }
                    })
                    .build()

                continuation.invokeOnCancellation {
                    runCatching { transformer.cancel() }
                }

                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
        }
    }
}
