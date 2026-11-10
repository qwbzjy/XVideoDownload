package com.example.xvideodownload.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xvideodownload.MainViewModel
import com.example.xvideodownload.UiState
import com.example.xvideodownload.core.PhotoItem
import com.example.xvideodownload.core.ResolvedTweet
import com.example.xvideodownload.core.VideoItem
import com.example.xvideodownload.core.VideoVariant
import com.example.xvideodownload.download.DownloadTask
import com.example.xvideodownload.download.TaskState

@Composable
fun DownloadTab(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val input by vm.input.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = vm::onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://x.com/user/status/1234567890") },
            minLines = 3,
            maxLines = 5,
            enabled = state !is UiState.Resolving,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.onInputChange(readClipboard(context)) },
                enabled = state !is UiState.Resolving,
                modifier = Modifier.weight(1f),
            ) { Text("粘贴") }
            OutlinedButton(
                onClick = { vm.onInputChange("") },
                enabled = input.isNotEmpty() && state !is UiState.Resolving,
                modifier = Modifier.weight(1f),
            ) { Text("清空") }
        }

        Button(
            onClick = { vm.resolve() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = input.isNotBlank() && state !is UiState.Resolving,
        ) {
            Text(if (settings.skipQualityPicker) "解析并保存" else "解析内容")
        }

        when (val s = state) {
            UiState.Idle -> InfoCard(
                title = "使用方式",
                body = "1. 在 X 里复制推文链接，或从 X 分享菜单直接分享到本 App\n" +
                    "2. 点「粘贴」\n" +
                    "3. 点「解析内容」→「全部保存」：帖子正文追加到当天的 .md，图片内嵌引用，视频和图片与 md 同目录\n\n" +
                    "存档目录可在「设置」里指定；也可以直接粘贴 video.twimg.com 的 mp4 直链。",
            )

            UiState.Resolving -> StatusCard("正在解析推文…", "向解析服务查询正文、图片与视频")

            is UiState.Ready -> ReadyPanel(s, vm, context)

            is UiState.Error -> ErrorCard(s.title, s.detail, onRetry = { vm.resolve() })
        }

        if (tasks.isNotEmpty()) {
            TaskQueuePanel(tasks, vm, context)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "仅用于个人保存公开内容。保存由前台服务执行，切后台也不会中断。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyPanel(state: UiState.Ready, vm: MainViewModel, context: Context) {
    val tweet = state.tweet

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            if (tweet.author.isNotBlank()) {
                Text("@${tweet.author}", style = MaterialTheme.typography.titleMedium)
            }
            if (!tweet.createdAt.isNullOrBlank()) {
                Text(
                    tweet.createdAt.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val summary = buildList {
                if (tweet.text.isNotBlank()) add("文字")
                if (tweet.photos.isNotEmpty()) add("${tweet.photos.size} 张图片")
                if (tweet.items.isNotEmpty()) add("${tweet.items.size} 个视频")
            }.joinToString(" · ")
            Text(
                "解析到：$summary",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.downloadAll() }, modifier = Modifier.fillMaxWidth()) {
                Text("全部保存")
            }
        }

        if (tweet.text.isNotBlank()) {
            TextSection(tweet, vm, context)
        }

        if (tweet.photos.isNotEmpty()) {
            SectionCard {
                Text("图片（${tweet.photos.size}）", style = MaterialTheme.typography.titleMedium)
                tweet.photos.forEachIndexed { index, photo ->
                    PhotoOptionRow(
                        index = index + 1,
                        photo = photo,
                        onSave = { vm.downloadOne(ResolvedTweet.photoKey(photo.id)) },
                    )
                }
            }
        }

        if (tweet.items.isNotEmpty()) {
            SectionCard {
                Text(
                    "视频（${tweet.items.size}）· 可分别选择画质",
                    style = MaterialTheme.typography.titleMedium,
                )
                tweet.items.forEachIndexed { index, item ->
                    VideoOptionRow(
                        index = index + 1,
                        item = item,
                        selected = state.selections[item.id],
                        onSelect = { vm.selectVariant(item.id, it) },
                        onDownload = { vm.downloadOne(ResolvedTweet.videoKey(item.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSection(tweet: ResolvedTweet, vm: MainViewModel, context: Context) {
    SectionCard {
        Text("正文（追加到 Markdown）", style = MaterialTheme.typography.titleMedium)
        Text(
            tweet.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { copyText(context, tweet.text) },
                modifier = Modifier.weight(1f),
            ) { Text("复制文字") }
            Button(
                onClick = { vm.downloadOne(ResolvedTweet.TEXT_KEY) },
                modifier = Modifier.weight(1f),
            ) { Text("追加到 md") }
        }
        Text(
            "按日期追加到同一个 .md，帖子之间用 --- 分隔；点「全部保存」还会把图片内嵌进 md。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoOptionRow(index: Int, photo: PhotoItem, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Thumbnail(photo.url)
                Column(modifier = Modifier.weight(1f)) {
                    Text("图片 $index", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "原图 · ${photo.sizeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!photo.altText.isNullOrBlank()) {
                        Text(
                            photo.altText!!.take(80),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存图片")
            }
        }
    }
}

@Composable
private fun VideoOptionRow(
    index: Int,
    item: VideoItem,
    selected: VideoVariant?,
    onSelect: (VideoVariant) -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Thumbnail(item.thumbnailUrl)
                Column(modifier = Modifier.weight(1f)) {
                    Text("视频 $index", style = MaterialTheme.typography.titleSmall)
                    if (item.durationLabel.isNotEmpty()) {
                        Text("时长 ${item.durationLabel}", style = MaterialTheme.typography.bodySmall)
                    }
                    selected?.let {
                        Text(
                            "${it.resolutionLabel} · ${it.bitrateLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.selectableVariants().forEach { variant ->
                    val isSelected = selected?.url == variant.url
                    if (isSelected) {
                        Button(onClick = { onSelect(variant) }) { Text(variant.shortLabel) }
                    } else {
                        OutlinedButton(onClick = { onSelect(variant) }) {
                            Text(variant.shortLabel)
                        }
                    }
                }
            }

            OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("下载这条视频")
            }
        }
    }
}

@Composable
private fun TaskQueuePanel(tasks: List<DownloadTask>, vm: MainViewModel, context: Context) {
    val doneCount = tasks.count { it.state == TaskState.DONE }
    val activeCount = tasks.count { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "保存队列（$doneCount 完成 / ${tasks.size} 总数）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (activeCount == 0) {
                    TextButton(onClick = { vm.clearFinishedTasks() }) { Text("清除") }
                }
            }

            tasks.forEach { task -> TaskRow(task, vm, context) }

            if (activeCount > 0) {
                OutlinedButton(onClick = { vm.cancelAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("全部取消")
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: DownloadTask, vm: MainViewModel, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${kindLabel(task.kind)} · ${task.label}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            when (task.state) {
                TaskState.QUEUED -> Text("等待中", style = MaterialTheme.typography.labelSmall)
                TaskState.RUNNING -> Text("保存中", style = MaterialTheme.typography.labelSmall)
                TaskState.DONE -> Text("✅ 完成", style = MaterialTheme.typography.labelSmall)
                TaskState.FAILED -> Text("❌ 失败", style = MaterialTheme.typography.labelSmall)
            }
        }

        when (task.state) {
            TaskState.RUNNING -> {
                val fraction = if (task.total > 0)
                    (task.downloaded.toFloat() / task.total).coerceIn(0f, 1f) else 0f
                if (task.total > 0) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "${formatBytes(task.downloaded)}" +
                        if (task.total > 0) " / ${formatBytes(task.total)}" else "（总大小未知）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TaskState.DONE -> Text(
                "${task.displayPath.orEmpty()} · ${formatBytes(task.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TaskState.FAILED -> Text(
                task.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            TaskState.QUEUED -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (task.state == TaskState.QUEUED || task.state == TaskState.RUNNING) {
                TextButton(onClick = { vm.cancelTask(task.id) }) { Text("取消") }
            }
            if (task.state == TaskState.DONE && task.uri != null) {
                TextButton(onClick = { openFile(context, task.uri, task.kind) }) {
                    Text("打开")
                }
            }
        }
    }
}

private fun readClipboard(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return ""
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).text?.toString().orEmpty()
}

private fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    runCatching { manager.setPrimaryClip(ClipData.newPlainText("X 推文正文", text)) }
}
