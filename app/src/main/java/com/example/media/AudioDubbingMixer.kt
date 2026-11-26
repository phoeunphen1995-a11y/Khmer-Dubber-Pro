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
import java.io.File
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
        val outputMp4 = File(exportsDir, "${projectId}_khmer_dubbed.mp4")
        if (outputMp4.exists()) outputMp4.delete()

        val compatibleVideoFile = ensureMp4VideoTrack(projectId, sourceVideoFile)

        val pcmBufferFile = File(context.cacheDir, "${projectId}_mix.pcm")
        val mixedAacFile = File(context.cacheDir, "${projectId}_mixed.m4a")
        try {
            withContext(Dispatchers.IO) {
                buildMixedPcmFile(
                    extractedAudioFile = extractedAudioFile,
                    durationMs = durationMs,
                    segments = segments,
                    ttsClips = ttsClips,
                    enableDubbing = enableDubbing,
                    preserveBgm = preserveBgm,
                    bgmDuckingLevel = bgmDuckingLevel,
                    outPcmFile = pcmBufferFile
                )
                encodePcmToAacM4a(pcmBufferFile, mixedAacFile)
                muxVideoAndAudio(compatibleVideoFile, mixedAacFile, outputMp4)
            }
            return outputMp4
        } finally {
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
        val needsTranscode = withContext(Dispatchers.IO) {
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

        val transcoded = File(context.cacheDir, "${projectId}_video_h264.mp4")
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

    private fun buildMixedPcmFile(
        extractedAudioFile: File,
        durationMs: Long,
        segments: List<SubtitleSegment>,
        ttsClips: Map<Int, File>,
        enableDubbing: Boolean,
        preserveBgm: Boolean,
        bgmDuckingLevel: Float,
        outPcmFile: File
    ) {
        val totalSamples = ((durationMs.coerceAtLeast(1000L) * TARGET_SAMPLE_RATE) / 1000L).toInt()
        val origPcm = decodeAudioToMono44k(extractedAudioFile, totalSamples)

        val speechRegions = segments.map { it.startMs to it.endMs }
        val mixSamples = ShortArray(maxOf(totalSamples, origPcm.size))

        // Step 1: Apply side-chain BGM & SFX ducking envelope
        val fadeSamples = (TARGET_SAMPLE_RATE * 0.06f).toInt()
        for (i in mixSamples.indices) {
            val timeMs = (i.toLong() * 1000L) / TARGET_SAMPLE_RATE
            val bgmGain = if (!preserveBgm) {
                0f
            } else {
                val active = speechRegions.any { (start, end) -> timeMs in start..end }
                if (active && enableDubbing) bgmDuckingLevel.coerceIn(0.05f, 0.85f) else 0.90f
            }
            val orig = if (i < origPcm.size) origPcm[i] else 0
            mixSamples[i] = (orig * bgmGain).roundToInt().coerceIn(-32768, 32767).toShort()
        }

        // Smooth edge pops around speech region transitions
        if (preserveBgm && enableDubbing && fadeSamples > 0) {
            for ((startMs, _) in speechRegions) {
                val center = ((startMs * TARGET_SAMPLE_RATE) / 1000L).toInt()
                val lo = (center - fadeSamples).coerceAtLeast(0)
                val hi = (center + fadeSamples).coerceAtMost(mixSamples.lastIndex)
                for (j in lo..hi) {
                    val t = (j - lo).toFloat() / maxOf(1, hi - lo)
                    val smooth = 0.85f + 0.15f * kotlin.math.abs(t - 0.5f) * 2f
                    mixSamples[j] = (mixSamples[j] * smooth).roundToInt().toShort()
                }
            }
        }

        // Step 2: Overlay Khmer TTS dubbing WAV clips at their exact startMs
        if (enableDubbing) {
            for (seg in segments) {
                val wavFile = ttsClips[seg.index] ?: continue
                val dubSamples = readWavAsMono44k(wavFile)
                if (dubSamples.isEmpty()) continue
                val startSample = ((seg.startMs * TARGET_SAMPLE_RATE) / 1000L).toInt()
                    .coerceIn(0, mixSamples.lastIndex)
                val maxWindowSamples = (((seg.endMs - seg.startMs).coerceAtLeast(1500L) + 600L) * TARGET_SAMPLE_RATE / 1000L).toInt()
                val copyCount = minOf(dubSamples.size, maxWindowSamples, mixSamples.size - startSample)
                for (j in 0 until copyCount) {
                    val idx = startSample + j
                    val combined = mixSamples[idx].toInt() + (dubSamples[j].toInt() * 1.05f).roundToInt()
                    mixSamples[idx] = combined.coerceIn(-32768, 32767).toShort()
                }
            }
        }

        // Write little-endian 16-bit PCM file
        if (outPcmFile.exists()) outPcmFile.delete()
        val byteBuf = ByteBuffer.allocate(mixSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in mixSamples) {
            byteBuf.putShort(s)
        }
        outPcmFile.writeBytes(byteBuf.array())
    }

    private fun decodeAudioToMono44k(audioFile: File, expectedSamples: Int): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val collected = ArrayList<Short>(expectedSamples)
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return ShortArray(expectedSamples)

            extractor.selectTrack(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            val srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortCount = bufferInfo.size / 2
                        val frameCount = shortCount / srcChannels
                        val tempFrame = ShortArray(frameCount)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        for (f in 0 until frameCount) {
                            var sum = 0
                            for (c in 0 until srcChannels) {
                                sum += outBuf.short.toInt()
                            }
                            tempFrame[f] = (sum / srcChannels).toShort()
                        }
                        appendResampled(tempFrame, srcRate, TARGET_SAMPLE_RATE, collected)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode fallback due to: ${e.localizedMessage}")
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
        return collected.toShortArray()
    }

    private fun readWavAsMono44k(wavFile: File): ShortArray {
        val bytes = runCatching { wavFile.readBytes() }.getOrNull() ?: return ShortArray(0)
        if (bytes.size <= 44) return ShortArray(0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buf.getInt(24).coerceAtLeast(8000)

        // Locate "data" chunk dynamically
        var offset = 12
        var dataOffset = 44
        var dataSize = bytes.size - 44
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            if (id == "data") {
                dataOffset = offset + 8
                dataSize = minOf(chunkSize, bytes.size - dataOffset)
                break
            }
            offset += 8 + chunkSize
        }

        val frameCount = (dataSize / 2) / channels
        if (frameCount <= 0) return ShortArray(0)
        val mono = ShortArray(frameCount)
        buf.position(dataOffset)
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) {
                sum += buf.short.toInt()
            }
            mono[i] = (sum / channels).toShort()
        }
        if (sampleRate == TARGET_SAMPLE_RATE) return mono
        val resampled = ArrayList<Short>((frameCount.toLong() * TARGET_SAMPLE_RATE / sampleRate).toInt() + 16)
        appendResampled(mono, sampleRate, TARGET_SAMPLE_RATE, resampled)
        return resampled.toShortArray()
    }

    private fun appendResampled(
        input: ShortArray,
        srcRate: Int,
        dstRate: Int,
        out: MutableList<Short>
    ) {
        if (input.isEmpty()) return
        if (srcRate == dstRate) {
            for (s in input) out.add(s)
            return
        }
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outCount = (input.size / ratio).toInt().coerceAtLeast(1)
        for (i in 0 until outCount) {
            val pos = i * ratio
            val idx0 = pos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = pos - idx0
            val sample = input[idx0] * (1.0 - frac) + input[idx1] * frac
            out.add(sample.roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun encodePcmToAacM4a(pcmFile: File, outM4aFile: File) {
        if (outM4aFile.exists()) outM4aFile.delete()
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            TARGET_SAMPLE_RATE,
            TARGET_CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        RandomAccessFile(pcmFile, "r").use { raf ->
            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                val bufferInfo = MediaCodec.BufferInfo()
                val chunk = ByteArray(4096)
                var totalBytesRead = 0L
                var inputDone = false
                var outputDone = false

                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inIdx)!!
                            inBuf.clear()
                            val read = raf.read(chunk, 0, minOf(chunk.size, inBuf.remaining()))
                            if (read <= 0) {
                                val ptsUs = (totalBytesRead / 2) * 1_000_000L / TARGET_SAMPLE_RATE
                                encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                inBuf.put(chunk, 0, read)
                                val ptsUs = (totalBytesRead / 2) * 1_000_000L / TARGET_SAMPLE_RATE
                                totalBytesRead += read
                                encoder.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                            }
                        }
                    }

                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                muxerStarted
                            ) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            } finally {
                runCatching { encoder.stop(); encoder.release() }
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioM4aFile: File, outputMp4: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var srcVideoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    srcVideoTrack = i
                    videoFormat = fmt
                    break
                }
            }

            audioExtractor.setDataSource(audioM4aFile.absolutePath)
            var srcAudioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    srcAudioTrack = i
                    audioFormat = fmt
                    break
                }
            }

            if (srcVideoTrack < 0 || videoFormat == null || srcAudioTrack < 0 || audioFormat == null) {
                throw IllegalStateException("Missing video or mixed audio track for MP4 export.")
            }

            videoExtractor.selectTrack(srcVideoTrack)
            audioExtractor.selectTrack(srcAudioTrack)

            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(outVideoTrack, buffer, info)
                videoExtractor.advance()
            }

            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = audioExtractor.sampleTime
                info.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(outAudioTrack, buffer, info)
                audioExtractor.advance()
            }
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}
