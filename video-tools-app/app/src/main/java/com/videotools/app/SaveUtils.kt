package com.videotools.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/**
 * Saves processed media into the public gallery under a "VideoTools" folder.
 * API 29+: MediaStore, no permission needed.
 * API 26-28: legacy public directories (caller must hold WRITE_EXTERNAL_STORAGE).
 */
object SaveUtils {

    fun saveFileToGallery(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String
    ): Boolean = saveStreamToGallery(context, displayName, mimeType) { file.inputStream() }

    fun saveStreamToGallery(
        context: Context,
        displayName: String,
        mimeType: String,
        openStream: () -> InputStream
    ): Boolean {
        return try {
            val isVideo = mimeType.startsWith("video")
            val isAudio = mimeType.startsWith("audio")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val baseDir = when {
                    isVideo -> Environment.DIRECTORY_MOVIES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_PICTURES
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/VideoTools")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(collection, values) ?: return false
                val written = resolver.openOutputStream(itemUri)?.use { out ->
                    openStream().use { input -> input.copyTo(out) }
                    true
                } ?: false
                if (!written) {
                    resolver.delete(itemUri, null, null)
                    return false
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                true
            } else {
                val baseDir = when {
                    isVideo -> Environment.DIRECTORY_MOVIES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_PICTURES
                }
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(baseDir),
                    "VideoTools"
                )
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, displayName)
                outFile.outputStream().use { out -> openStream().use { it.copyTo(out) } }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
