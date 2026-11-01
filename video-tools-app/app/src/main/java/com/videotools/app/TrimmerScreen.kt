package com.videotools.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun TrimmerScreen(s: AppStrings) {
    val context = LocalContext.current
    val processor = remember { VideoProcessor(context) }
    DisposableEffect(Unit) { onDispose { processor.cancel() } }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<VideoInfo.Info?>(null) }
    var startText by remember { mutableStateOf("0:00") }
    var endText by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            val probed = VideoInfo.probe(context, uri)
            info = probed
            startText = "0:00"
            endText = formatDuration(probed.durationMs)
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
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text(s.startLabel) },
                enabled = !processing,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text(s.endLabel) },
                enabled = !processing,
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = s.timeHint)

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
                        val startMs = parseTimeToMs(startText)
                        val endMs = parseTimeToMs(endText)
                        if (startMs == null || endMs == null || endMs <= startMs ||
                            (vid.durationMs > 0 && endMs > vid.durationMs + 1000)
                        ) {
                            message = s.invalidRange
                            isError = true
                            return@Button
                        }
                        val base = vid.displayName.substringBeforeLast('.', vid.displayName)
                        val outFile = File(
                            context.cacheDir,
                            "outputs/trimmed_${System.currentTimeMillis()}.mp4"
                        )
                        processing = true
                        progress = 0
                        message = null
                        processor.process(
                            inputUri = uri,
                            outputFile = outFile,
                            clipStartMs = startMs,
                            clipEndMs = endMs,
                            onProgress = { progress = it },
                            onDone = { result ->
                                processing = false
                                result.onSuccess { file ->
                                    val saved = SaveUtils.saveFileToGallery(
                                        context, file, "trimmed_$base.mp4", "video/mp4"
                                    )
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
                ) { Text(s.trimAction) }
            }
        }

        message?.let {
            Spacer(Modifier.height(4.dp))
            MessageBlock(it, isError)
        }
    }
}
