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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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

private enum class CompressPreset { WHATSAPP, SMALL, MEDIUM, CUSTOM }

@Composable
fun CompressorScreen(s: AppStrings) {
    val context = LocalContext.current
    val processor = remember { VideoProcessor(context) }
    DisposableEffect(Unit) { onDispose { processor.cancel() } }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<VideoInfo.Info?>(null) }
    var preset by remember { mutableStateOf(CompressPreset.WHATSAPP) }
    var customMb by remember { mutableStateOf(10f) }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            info = VideoInfo.probe(context, uri)
            message = null
            val sizeMb = (info?.sizeBytes ?: 0) / 1_000_000f
            if (sizeMb > 2f) customMb = (sizeMb * 0.5f).coerceIn(1f, sizeMb)
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
            val presets = listOf(
                CompressPreset.WHATSAPP to s.presetWhatsapp,
                CompressPreset.SMALL to s.presetSmall,
                CompressPreset.MEDIUM to s.presetMedium,
                CompressPreset.CUSTOM to s.presetCustom
            )
            presets.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = preset == value, enabled = !processing) { preset = value },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = preset == value, onClick = { preset = value }, enabled = !processing)
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (preset == CompressPreset.CUSTOM) {
                val vid = info
                val maxMb = (((vid?.sizeBytes ?: 0) / 1_000_000f).coerceAtLeast(2f))
                Text(text = "${s.targetSizeLabel}: ${String.format("%.1f", customMb)} MB")
                Slider(
                    value = customMb.coerceIn(1f, maxMb),
                    onValueChange = { customMb = it },
                    valueRange = 1f..maxMb,
                    enabled = !processing
                )
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
                        val durationSec = (vid.durationMs / 1000.0).coerceAtLeast(1.0)
                        val audioBitrate = 128_000
                        val targetBytes = when (preset) {
                            CompressPreset.WHATSAPP -> minOf(vid.sizeBytes, (15.5 * 1_000_000).toLong())
                            CompressPreset.SMALL -> (vid.sizeBytes * 0.35).toLong()
                            CompressPreset.MEDIUM -> (vid.sizeBytes * 0.6).toLong()
                            CompressPreset.CUSTOM -> (customMb * 1_000_000).toLong()
                        }.coerceAtLeast(300_000L)
                        val videoBitrate = ((targetBytes * 8 / durationSec) - audioBitrate)
                            .toInt().coerceIn(150_000, 16_000_000)
                        val desiredHeight = when (preset) {
                            CompressPreset.WHATSAPP -> 720
                            CompressPreset.SMALL -> 480
                            CompressPreset.MEDIUM -> 720
                            CompressPreset.CUSTOM -> 1080
                        }
                        val maxHeight = desiredHeight.takeIf { vid.height > it }
                        val outFile = File(
                            context.cacheDir,
                            "outputs/compressed_${System.currentTimeMillis()}.mp4"
                        )
                        processing = true
                        progress = 0
                        message = null
                        processor.process(
                            inputUri = uri,
                            outputFile = outFile,
                            videoBitrate = videoBitrate,
                            audioBitrate = audioBitrate,
                            maxHeight = maxHeight,
                            onProgress = { progress = it },
                            onDone = { result ->
                                processing = false
                                result.onSuccess { file ->
                                    val base = vid.displayName.substringBeforeLast('.', vid.displayName)
                                    val saved = SaveUtils.saveFileToGallery(
                                        context, file, "compressed_$base.mp4", "video/mp4"
                                    )
                                    message = buildString {
                                        append(s.done).append("\n")
                                        append("${s.beforeLabel}: ${formatBytes(vid.sizeBytes)}  →  ")
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
                ) { Text(s.compressAction) }
            }
        }

        message?.let {
            Spacer(Modifier.height(4.dp))
            MessageBlock(it, isError)
        }
        if (message != null && isError && !processing) {
            OutlinedButton(onClick = { message = null }, modifier = Modifier.fillMaxWidth()) {
                Text(s.cancel)
            }
        }
    }
}
