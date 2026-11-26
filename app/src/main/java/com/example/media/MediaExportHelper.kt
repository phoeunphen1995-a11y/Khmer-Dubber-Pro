package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaExportHelper(private val context: Context) {

    fun writeSrtToAppStorage(projectId: String, srtContent: String): File {
        val srtDir = File(context.filesDir, "exports").apply { mkdirs() }
        val srtFile = File(srtDir, "${projectId}_khmer.srt")
        srtFile.writeText(srtContent, Charsets.UTF_8)
        return srtFile
    }

    suspend fun saveVideoToDeviceMovies(sourceMp4: File, title: String): Uri =
        withContext(Dispatchers.IO) {
            val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "khmer_video" }
            val fileName = "${safeName}_khmer_${System.currentTimeMillis() % 10000}.mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/KhmerTranslator"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create MediaStore entry for video export.")

            resolver.openOutputStream(itemUri)?.use { out ->
                sourceMp4.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Unable to write video to device storage.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        }

    suspend fun saveSrtToDownloads(srtFile: File, title: String): Uri =
        withContext(Dispatchers.IO) {
            val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "khmer_subtitles" }
            val fileName = "${safeName}_khmer.srt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/KhmerTranslator"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Downloads entry for SRT.")

                resolver.openOutputStream(itemUri)?.use { out ->
                    srtFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Unable to write SRT file.")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                itemUri
            } else {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    srtFile
                )
            }
        }

    fun buildShareIntent(file: File, mimeType: String, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }
}
