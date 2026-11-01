package com.videotools.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

private enum class ConvertFormat { MP4, AUDIO }

@Composable
fun ConverterScreen(s: AppStrings) {
    val context = LocalContext.current
    val processor = remember { VideoProcessor(context) }
    DisposableEffect(Unit) { onDispose { processor.cancel() } }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<VideoInfo.Info?>(null) }
    var format by remember { mutableStateOf(ConvertFormat.MP4) }
    var resolution by remember { mutableStateOf<Int?>(null) } // null = original
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            info = VideoInfo.probe(context, uri)
            message = null
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { picker.launch("video/*") },
            enabled = !processing,
            modifier = Modifier.fillMaxWidth()
        ) { Text(s.pickVideo) }

        info?.let { VideoSummaryCard(s, it) }

        if (pickedUri != null) {
            listOf(
                ConvertFormat.MP4 to s.formatMp4,
                ConvertFormat.AUDIO to s.extractAudio
            ).forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = format == value, enabled = !processing) { format = value },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = format == value, onClick = { format = value }, enabled = !processing)
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (format == ConvertFormat.MP4) {
                Text(text = s.resolutionLabel, style = MaterialTheme.typography.titleSmall)
                val options: List<Pair<Int?, String>> = listOf(
                    null to s.resOriginal,
                    1080 to "1080p",
                    720 to "720p",
                    480 to "480p",
                    360 to "360p"
                )
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = resolution == value, enabled = !processing) { resolution = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = resolution == value, onClick = { resolution = value }, enabled = !processing)
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (processing) {
                ProgressBlock(s, progress, onCancel = {
                    processor.cancel()
                    processing = false
                    message = null
                })
            } else {
                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        val vid = info ?: return@Button
                        val audioOnly = format == ConvertFormat.AUDIO
                        val base = vid.displayName.substringBeforeLast('.', vid.displayName)
                        val stamp = System.currentTimeMillis()
                        val outFile: File
                        val maxHeight: Int?
                        if (audioOnly) {
                            outFile = File(context.cacheDir, "outputs/audio_$stamp.m4a")
                            maxHeight = null
                        } else {
                            outFile = File(context.cacheDir, "outputs/converted_$stamp.mp4")
                            maxHeight = resolution?.takeIf { vid.height > it }
                        }
                        processing = true
                        progress = 0
                        message = null
                        processor.process(
                            inputUri = uri,
                            outputFile = outFile,
                            audioBitrate = if (audioOnly) 192_000 else null,
                            maxHeight = maxHeight,
                            removeVideo = audioOnly,
                            onProgress = { progress = it },
                            onDone = { result ->
                                processing = false
                                result.onSuccess { file ->
                                    val saved = if (audioOnly) {
                                        SaveUtils.saveFileToGallery(
                                            context, file, "audio_$base.m4a", "audio/mp4"
                                        )
                                    } else {
                                        SaveUtils.saveFileToGallery(
                                            context, file, "converted_$base.mp4", "video/mp4"
                                        )
                                    }
                                    message = buildString {
                                        append(s.done).append("\n")
                                        append("${s.afterLabel}: ${formatBytes(file.length())}")
                                        if (saved) append("\n").append(s.savedToGallery)
                                    }
                                    isError = !saved
                                    file.delete()
                                }.onFailure {
                                    message = s.errorProcessing
                                    isError = true
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.convertAction) }
            }
        }

        message?.let {
            Spacer(Modifier.height(4.dp))
            MessageBlock(it, isError)
        }
    }
}
