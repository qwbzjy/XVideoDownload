package com.example.xvideodownload.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.xvideodownload.core.SaveKind
import com.example.xvideodownload.core.ThumbnailLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatusCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ErrorCard(title: String, detail: String, onRetry: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("重试")
        }
    }
}

@Composable
fun Thumbnail(url: String?) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) { bitmap = ThumbnailLoader.load(url) }

    Box(
        modifier = Modifier
            .width(104.dp)
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("▶", style = MaterialTheme.typography.titleLarge)
        }
    }
}

fun openFile(context: Context, uriString: String?, kind: SaveKind) {
    val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
    val mime = when (kind) {
        SaveKind.VIDEO -> "video/mp4"
        SaveKind.PHOTO -> "image/*"
        SaveKind.MARKDOWN -> "text/markdown"
    }
    fun start(type: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        true
    }.getOrDefault(false)

    // 部分系统/编辑器只注册了 text/plain，text/markdown 打不开时回退
    if (!start(mime) && kind == SaveKind.MARKDOWN) start("text/plain")
}

fun kindLabel(kind: SaveKind): String = when (kind) {
    SaveKind.VIDEO -> "视频"
    SaveKind.PHOTO -> "图片"
    SaveKind.MARKDOWN -> "Markdown"
}

fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "--"
    bytes < 1024 * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}

fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
