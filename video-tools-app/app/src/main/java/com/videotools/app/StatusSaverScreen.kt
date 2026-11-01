package com.videotools.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private data class StatusItem(
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val lastModified: Long
)

/** Finds the ".Statuses" folder inside the user-granted tree (breadth-first, depth <= 4). */
private fun findStatusesRoot(tree: DocumentFile): DocumentFile? {
    if (tree.name == ".Statuses") return tree
    val queue = ArrayDeque<Pair<DocumentFile, Int>>()
    queue.add(tree to 0)
    while (!queue.isEmpty()) {
        val (dir, depth) = queue.removeFirst()
        if (depth >= 4) continue
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            emptyArray()
        }
        for (child in children) {
            if (child.isDirectory) {
                if (child.name == ".Statuses") return child
                queue.add(child to depth + 1)
            }
        }
    }
    return null
}

private fun listStatuses(root: DocumentFile): List<StatusItem> {
    val files = try {
        root.listFiles()
    } catch (_: Exception) {
        return emptyList()
    }
    return files.filter { it.isFile }.mapNotNull { doc ->
        val name = doc.name ?: return@mapNotNull null
        val lower = name.lowercase()
        var mime = doc.type
        val isVideo = when {
            mime?.startsWith("video") == true -> true
            mime?.startsWith("image") == true -> false
            lower.endsWith(".mp4") || lower.endsWith(".3gp") ||
                lower.endsWith(".mov") || lower.endsWith(".mkv") -> true
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") -> false
            else -> return@mapNotNull null
        }
        if (mime == null) mime = if (isVideo) "video/mp4" else "image/jpeg"
        StatusItem(doc.uri, name, mime, isVideo, doc.lastModified())
    }.sortedByDescending { it.lastModified }
}

@Composable
fun StatusSaverScreen(s: AppStrings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("vt_prefs", Context.MODE_PRIVATE) }

    var treeUriStr by remember { mutableStateOf(prefs.getString("status_tree_uri", null)) }
    var items by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<StatusItem?>(null) }

    fun scan(uriStr: String) {
        loading = true
        errorText = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                    if (tree == null) {
                        null
                    } else {
                        val root = findStatusesRoot(tree) ?: tree
                        listStatuses(root)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            loading = false
            if (result == null) {
                errorText = s.folderError
                items = emptyList()
            } else {
                items = result
            }
        }
    }

    fun doSave(item: StatusItem) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                SaveUtils.saveStreamToGallery(context, item.name, item.mime) {
                    context.contentResolver.openInputStream(item.uri)
                        ?: throw IOException("cannot open status")
                }
            }
            Toast.makeText(
                context,
                if (ok) s.statusSaved else s.saveFailed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val writePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pendingSave
        pendingSave = null
        if (granted && item != null) {
            doSave(item)
        } else {
            Toast.makeText(context, s.permissionNeeded, Toast.LENGTH_LONG).show()
        }
    }

    fun saveItem(item: StatusItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = item
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doSave(item)
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // still usable for this session
            }
            val str = uri.toString()
            prefs.edit().putString("status_tree_uri", str).apply()
            treeUriStr = str
        }
    }

    LaunchedEffect(treeUriStr) {
        treeUriStr?.let { scan(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (treeUriStr == null) {
            Text(text = s.statusExplain, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { treeLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(s.grantAccess) }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { treeLauncher.launch(null) }) { Text(s.changeFolder) }
                Button(onClick = { treeUriStr?.let { scan(it) } }, enabled = !loading) {
                    Text("↻")
                }
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(8.dp))
                    Text(s.processing)
                }
            }

            errorText?.let { MessageBlock(it, isError = true) }

            if (!loading && errorText == null) {
                if (items.isEmpty()) {
                    Text(text = s.noStatuses, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(text = s.tapToSaveHint, style = MaterialTheme.typography.bodyMedium)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { saveItem(item) }
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = if (item.isVideo) s.videoLabel else s.photoLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color(0x88000000))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
