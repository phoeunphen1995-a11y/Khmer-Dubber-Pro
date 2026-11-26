package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedVideoMeta(
    val projectId: String,
    val title: String,
    val format: VideoFormat,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val localVideoFile: File,
    val thumbnailFile: File?
)

class VideoImportHelper(private val context: Context) {

    suspend fun importFromUri(uri: Uri): ImportedVideoMeta = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        var displayName = "video_${System.currentTimeMillis()}.mp4"
        var reportedSize = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    reportedSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val format = VideoFormat.fromFileNameOrMime(displayName, mimeType)
            ?: throw IllegalArgumentException(
                "មិនគាំទ្រទម្រង់វីដេអូនេះទេ។ សូមជ្រើសរើស MP4, MOV, MKV, WEBM ឬ AVI។"
            )

        val projectId = UUID.randomUUID().toString()
        val importsDir = File(context.filesDir, "imports").apply { mkdirs() }
        val localVideoFile = File(importsDir, "${projectId}.${format.extension.lowercase()}")

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(localVideoFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: throw IllegalStateException("មិនអាចអានឯកសារវីដេអូដែលបានជ្រើសរើសបានទេ។")

        if (!localVideoFile.exists() || localVideoFile.length() == 0L) {
            throw IllegalStateException("ឯកសារវីដេអូដែលបាននាំចូលគឺទទេ។")
        }

        val actualSize = if (localVideoFile.length() > 0L) localVideoFile.length() else reportedSize
        var durationMs = 0L
        var width = 0
        var height = 0
        var thumbFile: File? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(localVideoFile.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            val frameTimeUs = if (durationMs > 1000L) (durationMs * 150L) else 0L
            val bitmap = retriever.getFrameAtTime(
                frameTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (bitmap != null) {
                val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
                val outThumb = File(thumbsDir, "${projectId}.jpg")
                FileOutputStream(outThumb).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                bitmap.recycle()
                thumbFile = outThumb
            }
        } catch (_: Exception) {
            // Some exotic MKV/AVI containers may fail thumbnail extraction; continue gracefully.
        } finally {
            runCatching { retriever.release() }
        }

        val cleanTitle = displayName.substringBeforeLast('.').ifBlank { "វីដេអូគ្មានចំណងជើង" }
        ImportedVideoMeta(
            projectId = projectId,
            title = cleanTitle,
            format = format,
            durationMs = durationMs.coerceAtLeast(1_000L),
            width = width,
            height = height,
            fileSizeBytes = actualSize,
            localVideoFile = localVideoFile,
            thumbnailFile = thumbFile
        )
    }
}
