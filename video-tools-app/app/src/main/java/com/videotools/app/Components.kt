package com.videotools.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VideoSummaryCard(s: AppStrings, info: VideoInfo.Info) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = info.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "${s.durationLabel}: ${formatDuration(info.durationMs)}")
            Text(text = "${s.sizeLabel}: ${formatBytes(info.sizeBytes)}")
            if (info.width > 0 && info.height > 0) {
                Text(text = "${info.width} × ${info.height}")
            }
        }
    }
}

@Composable
fun ProgressBlock(s: AppStrings, progress: Int, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = if (progress >= 0) "${s.processing} $progress%" else s.processing)
        Spacer(Modifier.height(8.dp))
        if (progress >= 0) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(s.cancel)
        }
    }
}

@Composable
fun MessageBlock(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium
    )
}
