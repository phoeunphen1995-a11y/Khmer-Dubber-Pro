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
            withContext(Dispatchers.IO) {
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

    /**
     * លាយសំឡេងល្បឿនលឿនបំផុត (O(N) Linear Speed) និងមិនស៊ី RAM
     */
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

        // រៀបចំចន្លោះ Sample ដែលត្រូវបន្ថយសំឡេងភ្លេងដើម (Ducking) ជាមុន ដើម្បីកុំឲ្យ Loop យឺត
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

        // បញ្ចូលសំឡេងនិយាយខ្មែរ (TTS) ផ្ទាល់ទៅកាន់ទីតាំង Byte លើ Disk
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

    private fun decodeAudioToMono44kFile(audioFile: File, outRawPcmFile: File): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var totalWrittenSamples = 0L
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
            if (trackIndex < 0 || trackFormat == null) return 0L

            extractor.selectTrack(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return 0L
            val srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleCount = 0

            BufferedOutputStream(FileOutputStream(outRawPcmFile), 65536).use { out ->
                while (!outputDone && idleCount < 500) {
                    var didWork = false
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5_000L)
                        if (inIdx >= 0) {
                            didWork = true
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
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
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                    if (outIdx >= 0) {
                        didWork = true
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val shortCount = bufferInfo.size / 2
                            val frameCount = shortCount / srcChannels
                            val tempFrame = ShortArray(frameCount)
                            for (f in 0 until frameCount) {
                                var sum = 0
                                for (c in 0 until srcChannels) {
                                    sum += outBuf.short.toInt()
                                }
                                tempFrame[f] = (sum / srcChannels).toShort()
                            }
                            val resampled = resampleArray(tempFrame, srcRate, TARGET_SAMPLE_RATE)
                            if (resampled.isNotEmpty()) {
                                val byteBuf = ByteBuffer.allocate(resampled.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (s in resampled) byteBuf.putShort(s)
                                out.write(byteBuf.array())
                                totalWrittenSamples += resampled.size
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        didWork = true
                    }

                    idleCount = if (didWork) 0 else idleCount + 1
                }
                out.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode fallback: ${e.message}")
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
        return totalWrittenSamples
    }

    private fun readWavAsMono44k(wavFile: File): ShortArray {
        val bytes = runCatching { wavFile.readBytes() }.getOrNull() ?: return ShortArray(0)
        if (bytes.size <= 44) return ShortArray(0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buf.getInt(24).coerceAtLeast(8000)

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
        return resampleArray(mono, sampleRate, TARGET_SAMPLE_RATE)
    }

    private fun resampleArray(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outCount = (input.size / ratio).toInt()
        val out = ShortArray(outCount)
        for (i in 0 until outCount) {
            val pos = i * ratio
            val idx0 = pos.toInt().coerceIn(0, input.size - 1)
            val idx1 = (idx0 + 1).coerceIn(0, input.size - 1)
            val frac = pos - idx0
            val sample = input[idx0] * (1.0 - frac) + input[idx1] * frac
            out[i] = sample.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
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
                val chunk = ByteArray(8192)
                var totalBytesRead = 0L
                var inputDone = false
                var outputDone = false
                var idleCount = 0

                while (!outputDone && idleCount < 500) {
                    var didWork = false
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(5_000L)
                        if (inIdx >= 0) {
                            didWork = true
                            val inBuf = encoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val maxRead = minOf(chunk.size, inBuf.remaining())
                                val read = raf.read(chunk, 0, maxRead)
                                if (read < 0) {
                                    val ptsUs = (totalBytesRead / 2L) * 1_000_000L / TARGET_SAMPLE_RATE
                                    encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    inBuf.put(chunk, 0, read)
                                    val ptsUs = (totalBytesRead / 2L) * 1_000_000L / TARGET_SAMPLE_RATE
                                    totalBytesRead += read
                                    encoder.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                                }
                            }
                        }
                    }

                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 5_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            didWork = true
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            didWork = true
                            val outBuf = encoder.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                if (!muxerStarted) {
                                    trackIndex = muxer.addTrack(encoder.outputFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
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

                    idleCount = if (didWork) 0 else idleCount + 1
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
                throw IllegalStateException("រកមិនឃើញ Track វីដេអូ ឬសំឡេងសម្រាប់បញ្ចូលគ្នា")
            }

            videoExtractor.selectTrack(srcVideoTrack)
            audioExtractor.selectTrack(srcAudioTrack)

            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val maxInputSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxOf(videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 2 * 1024 * 1024)
            } else {
                2 * 1024 * 1024
            }
            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val info = MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
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
                buffer.clear()
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
