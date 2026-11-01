package com.videotools.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

/** Reads basic facts about a picked video: duration, size, dimensions, name. */
object VideoInfo {

    data class Info(
        val durationMs: Long,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val displayName: String
    )

    fun probe(context: Context, uri: Uri): Info {
        var durationMs = 0L
        var width = 0
        var height = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width; width = height; height = tmp
            }
        } catch (_: Exception) {
            // leave zeros; screens handle unknown duration gracefully
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        var size = 0L
        var name = "video"
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx) ?: name
                    }
                }
            }
        } catch (_: Exception) {
            // ignore; size stays 0 (unknown)
        }

        return Info(durationMs, size, width, height, name)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "?"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "?"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}

/** Parses "mm:ss" (or plain seconds) into milliseconds; null if invalid. */
fun parseTimeToMs(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    return try {
        when (parts.size) {
            1 -> (parts[0].toDouble() * 1000).toLong()
            2 -> {
                val m = parts[0].toLong()
                val s = parts[1].toDouble()
                if (s >= 60) null else ((m * 60) * 1000 + s * 1000).toLong()
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
