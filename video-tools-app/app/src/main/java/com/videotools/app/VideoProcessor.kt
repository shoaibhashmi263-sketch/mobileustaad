package com.videotools.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

/**
 * Thin wrapper around Media3 Transformer for all re-encoding work:
 * compression (target bitrate / max height), conversion, audio extraction
 * and trimming (clipping). Runs asynchronously with progress callbacks.
 */
@OptIn(UnstableApi::class)
class VideoProcessor(private val context: Context) {

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    val isRunning: Boolean get() = transformer != null

    fun process(
        inputUri: Uri,
        outputFile: File,
        videoBitrate: Int? = null,
        audioBitrate: Int? = null,
        maxHeight: Int? = null,
        clipStartMs: Long? = null,
        clipEndMs: Long? = null,
        removeVideo: Boolean = false,
        onProgress: (Int) -> Unit,
        onDone: (Result<File>) -> Unit
    ) {
        cancelInternal()

        val mediaItemBuilder = MediaItem.Builder().setUri(inputUri)
        if (clipStartMs != null || clipEndMs != null) {
            val clip = MediaItem.ClippingConfiguration.Builder()
            if (clipStartMs != null) clip.setStartPositionMs(clipStartMs)
            if (clipEndMs != null) clip.setEndPositionMs(clipEndMs)
            mediaItemBuilder.setClippingConfiguration(clip.build())
        }

        val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
        if (maxHeight != null && !removeVideo) {
            editedBuilder.setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForHeight(maxHeight))
                )
            )
        }
        if (removeVideo) {
            editedBuilder.setRemoveVideo(true)
        }

        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
        if (videoBitrate != null && !removeVideo) {
            encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(videoBitrate).build()
            )
        }
        if (audioBitrate != null) {
            encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(audioBitrate).build()
            )
        }

        var finished = false
        val t = Transformer.Builder(context)
            .setEncoderFactory(encoderFactoryBuilder.build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    if (finished) return
                    finished = true
                    stopPolling()
                    transformer = null
                    onDone(Result.success(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    if (finished) return
                    finished = true
                    stopPolling()
                    transformer = null
                    onDone(Result.failure(exception))
                }
            })
            .build()

        transformer = t
        val progressHolder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val current = transformer ?: return
                val state = current.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_UNAVAILABLE) {
                    onProgress(-1) // indeterminate
                } else {
                    onProgress(progressHolder.progress.coerceIn(0, 100))
                }
                handler.postDelayed(this, 250)
            }
        }
        pollRunnable = runnable
        handler.post(runnable)

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        t.start(editedBuilder.build(), outputFile.absolutePath)
    }

    fun cancel() {
        cancelInternal()
    }

    private fun cancelInternal() {
        stopPolling()
        try {
            transformer?.cancel()
        } catch (_: Exception) {
            // already finished
        }
        transformer = null
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
    }
}
