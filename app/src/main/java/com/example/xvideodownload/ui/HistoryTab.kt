package com.example.xvideodownload.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun HistoryTab(vm: MainViewModel) {
    val records by vm.history.collectAsState()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "下载历史（最多 100 条）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) { Text("清空") }
            }
        }

        if (records.isEmpty()) {
            InfoCard("还没有记录", "下载成功的视频会出现在这里，可以直接打开或删除记录。")
        } else {
            records.forEach { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            record.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "[${kindLabel(record.kind)}] ${record.resolution} · ${formatBytes(record.bytes)} · ${formatTimestamp(record.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            record.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (record.uri != null) {
                                OutlinedButton(
                                    onClick = { openFile(context, record.uri, record.kind) },
                                ) { Text("打开") }
                            }
                            TextButton(onClick = { vm.removeHistory(record.id) }) {
                                Text("删除记录")
                            }
                        }
                    }
                }
            }
        }

        Text(
            "删除记录不会删除相册里的视频文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
