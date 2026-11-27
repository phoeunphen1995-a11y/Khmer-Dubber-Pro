package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private const val TAG = "AudioDubbingMixer"
private const val TARGET_SAMPLE_RATE = 44100
private const val TARGET_CHANNELS = 1

class AudioDubbingMixer(private val context: Context) {

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
    ): File {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputMp4 = File(exportsDir, "dubbed_${projectId}.mp4")
        if (outputMp4.exists()) outputMp4.delete()

        val compatibleVideoFile = ensureMp4VideoTrack(projectId, sourceVideoFile)

        val rawBgmFile = File(context.cacheDir, "bgm_${projectId}.pcm")
        val pcmBufferFile = File(context.cacheDir, "mix_${projectId}.pcm")
        val mixedAacFile = File(context.cacheDir, "mix_${projectId}.m4a")
        try {
            withContext<Unit>(Dispatchers.IO) {
                buildMixedPcmFast(
                    extractedAudioFile = extractedAudioFile,
                    durationMs = durationMs,
                    segments = segments,
                    ttsClips = ttsClips,
                    enableDubbing = enableDubbing,
                    preserveBgm = preserveBgm,
                    bgmDuckingLevel = bgmDuckingLevel,
                    tempBgmFile = rawBgmFile,
                    outPcmFile = pcmBufferFile
                )
                encodePcmToAacM4a(pcmBufferFile, mixedAacFile)
                muxVideoAndAudio(compatibleVideoFile, mixedAacFile, outputMp4)
            }
            return outputMp4
        } finally {
            runCatching { rawBgmFile.delete() }
            runCatching { pcmBufferFile.delete() }
            runCatching { mixedAacFile.delete() }
            if (compatibleVideoFile != sourceVideoFile) {
                runCatching { compatibleVideoFile.delete() }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun ensureMp4VideoTrack(projectId: String, sourceVideoFile: File): File {
        val extractor = MediaExtractor()
        val needsTranscode: Boolean = withContext(Dispatchers.IO) {
            try {
                extractor.setDataSource(sourceVideoFile.absolutePath)
                var videoMime: String? = null
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoMime = mime
                        break
                    }
                }
                videoMime != MediaFormat.MIMETYPE_VIDEO_AVC && videoMime != MediaFormat.MIMETYPE_VIDEO_HEVC
            } catch (_: Exception) {
                true
            } finally {
                runCatching { extractor.release() }
            }
        }

        if (!needsTranscode) return sourceVideoFile

        val transcoded = File(context.cacheDir, "video_only_${projectId}.mp4")
        if (transcoded.exists()) transcoded.delete()

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceVideoFile)))
                    .setRemoveAudio(true)
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(transcoded)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                transformer.start(item, transcoded.absolutePath)
            }
        }
    }

    private fun buildMixedPcmFast(
        extractedAudioFile: File,
        durationMs: Long,
        segments: List<SubtitleSegment>,
        ttsClips: Map<Int, File>,
        enableDubbing: Boolean,
        preserveBgm: Boolean,
        bgmDuckingLevel: Float,
        tempBgmFile: File,
        outPcmFile: File
    ) {
        if (tempBgmFile.exists()) tempBgmFile.delete()
        if (outPcmFile.exists()) outPcmFile.delete()

        val decodedBgmSamples = if (preserveBgm && extractedAudioFile.exists()) {
            decodeAudioToMono44kFile(extractedAudioFile, tempBgmFile)
        } else {
            0L
        }

        val estimatedSamples = ((durationMs.coerceAtLeast(1000L) / 1000.0) * TARGET_SAMPLE_RATE).toLong()
        val totalSamples = maxOf(estimatedSamples, decodedBgmSamples)

        val sortedRanges = if (preserveBgm && enableDubbing) {
            segments.map { seg ->
                val s = ((seg.startMs * TARGET_SAMPLE_RATE) / 1000L).coerceAtLeast(0L)
                val e = ((seg.endMs * TARGET_SAMPLE_RATE) / 1000L).coerceAtLeast(s)
                s to e
            }.sortedBy { it.first }
        } else {
            emptyList()
        }

        val duckGain = bgmDuckingLevel.coerceIn(0.05f, 1.0f)
        val blockSamples = 16384
        val inBytes = ByteArray(blockSamples * 2)
        val outBytes = ByteArray(blockSamples * 2)
        val inBuf = ByteBuffer.wrap(inBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        val bgmRaf = if (decodedBgmSamples > 0L && tempBgmFile.exists()) RandomAccessFile(tempBgmFile, "r") else null
        var rangeIdx = 0

        try {
            BufferedOutputStream(FileOutputStream(outPcmFile), 65536).use { out ->
                var sampleIdx = 0L
                while (sampleIdx < totalSamples) {
                    val count = minOf(blockSamples.toLong(), totalSamples - sampleIdx).toInt()
                    val bytesToRead = count * 2
                    var bytesRead = 0
                    if (bgmRaf != null) {
                        val r = bgmRaf.read(inBytes, 0, bytesToRead)
                        if (r > 0) bytesRead = r
                    }

                    inBuf.clear()
                    outBuf.clear()
                    val availableInSamples = bytesRead / 2

                    for (i in 0 until count) {
                        val currentSample = sampleIdx + i
                        val orig = if (i < availableInSamples) inBuf.getShort(i * 2).toFloat() else 0f
                        if (!preserveBgm || orig == 0f) {
                            outBuf.putShort(0)
                        } else {
                            while (rangeIdx < sortedRanges.size && sortedRanges[rangeIdx].second < currentSample) {
                                rangeIdx++
                            }
                            val isSpeech = rangeIdx < sortedRanges.size &&
                                    currentSample >= sortedRanges[rangeIdx].first &&
                                    currentSample <= sortedRanges[rangeIdx].second
                            val gain = if (isSpeech) duckGain else 1.0f
                            val mixed = (orig * gain).roundToInt().coerceIn(-32768, 32767)
                            outBuf.putShort(mixed.toShort())
                        }
                    }
                    out.write(outBytes, 0, count * 2)
                    sampleIdx += count
                }
                out.flush()
            }
        } finally {
            runCatching { bgmRaf?.close() }
        }

        if (enableDubbing) {
            RandomAccessFile(outPcmFile, "rw").use { mixRaf ->
                val maxFileBytes = mixRaf.length()
                for (seg in segments) {
                    val wavFile = ttsClips[seg.index] ?: continue
                    if (!wavFile.exists()) continue
                    val ttsSamples = readWavAsMono44k(wavFile)
                    if (ttsSamples.isEmpty()) continue

                    val startSample = ((seg.startMs * TARGET_SAMPLE_RATE) / 1000L).coerceAtLeast(0L)
                    val startByteOffset = startSample * 2L
                    if (startByteOffset >= maxFileBytes) continue

                    val maxWritableSamples = ((maxFileBytes - startByteOffset) / 2L).toInt()
                    val samplesToMix = minOf(ttsSamples.size, maxWritableSamples)
                    if (samplesToMix <= 0) continue

                    val segBytes = ByteArray(samplesToMix * 2)
                    mixRaf.seek(startByteOffset)
                    val readCount = mixRaf.read(segBytes)
                    if (readCount <= 0) continue

                    val segBuf = ByteBuffer.wrap(segBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val actualSamples = readCount / 2
                    for (j in 0 until actualSamples) {
                        val existing = segBuf.getShort(j * 2).toInt()
                        val mixed = existing + (ttsSamples[j].toInt() * 1.15f).roundToInt()
                        segBuf.putShort(j * 2, mixed.coerceIn(-32768, 32767).toShort())
                    }

                    mixRaf.seek(startByteOffset)
                    mixRaf.write(segBytes, 0, actualSamples * 2)
                }
            }
        }
    }
