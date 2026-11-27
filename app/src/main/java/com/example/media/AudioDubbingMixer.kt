    private fun readWavAsMono44k(wavFile: File): ShortArray {
        val bytes = runCatching { wavFile.readBytes() }.getOrNull() ?: return ShortArray(0)
        if (bytes.size <= 44) return ShortArray(0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var offset = 12
        var dataOffset = 44
        var dataSize = bytes.size - 44

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            if (id == "fmt " && offset + 24 <= bytes.size) {
                channels = buf.getShort(offset + 10).toInt().coerceAtLeast(1)
                sampleRate = buf.getInt(offset + 12).coerceAtLeast(8000)
                bitsPerSample = buf.getShort(offset + 22).toInt().coerceAtLeast(8)
            } else if (id == "data") {
                dataOffset = offset + 8
                dataSize = minOf(chunkSize, bytes.size - dataOffset)
                break
            }
            offset += 8 + chunkSize + (chunkSize % 2)
        }

        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameCount = (dataSize / bytesPerSample) / channels
        if (frameCount <= 0) return ShortArray(0)
        val mono = ShortArray(frameCount)
        buf.position(dataOffset)
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) {
                val sample = if (bytesPerSample == 2) {
                    buf.short.toInt()
                } else {
                    ((buf.get().toInt() and 0xFF) - 128) shl 8
                }
                sum += sample
            }
            mono[i] = (sum / channels).toShort()
        }
        return resampleArray(mono, sampleRate, TARGET_SAMPLE_RATE)
    }
