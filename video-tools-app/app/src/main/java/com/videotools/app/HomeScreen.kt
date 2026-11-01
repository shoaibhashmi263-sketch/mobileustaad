package com.videotools.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes

private data class ToolEntry(
    val screen: Screen,
    @DrawableRes val icon: Int,
    val title: String,
    val desc: String
)

@Composable
fun HomeScreen(s: AppStrings, onOpen: (Screen) -> Unit) {
    val tools = listOf(
        ToolEntry(Screen.COMPRESSOR, R.drawable.ic_tool_compress, s.compressor, s.compressorDesc),
        ToolEntry(Screen.CONVERTER, R.drawable.ic_tool_convert, s.converter, s.converterDesc),
        ToolEntry(Screen.TRIMMER, R.drawable.ic_tool_trim, s.trimmer, s.trimmerDesc),
        ToolEntry(Screen.STATUS, R.drawable.ic_tool_save, s.statusSaver, s.statusSaverDesc)
    )
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = s.tagline, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
        }
        items(tools) { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(tool.screen) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(tool.icon),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = tool.desc, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
