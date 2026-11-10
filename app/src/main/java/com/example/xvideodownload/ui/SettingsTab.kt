package com.example.xvideodownload.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xvideodownload.MainViewModel
import com.example.xvideodownload.core.ResolverConfig

@Composable
fun SettingsTab(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var fallbacks by remember(settings.fallbackUrls) {
        mutableStateOf(settings.fallbackUrls.joinToString("\n"))
    }
    var maxHeight by remember(settings.maxHeight) { mutableStateOf(settings.maxHeight) }
    var wifiOnly by remember(settings.wifiOnly) { mutableStateOf(settings.wifiOnly) }
    var skipPicker by remember(settings.skipQualityPicker) {
        mutableStateOf(settings.skipQualityPicker)
    }
    var byPostDate by remember(settings.archiveByPostDate) {
        mutableStateOf(settings.archiveByPostDate)
    }
    val context = LocalContext.current

    // SAF 目录选择：拿到 tree URI 后持久化读写权限，重启 App 仍可用
    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        vm.setArchiveDir(uri.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Text("存档目录", style = MaterialTheme.typography.titleMedium)
            Text(
                "Markdown 与所有图片、视频都保存在同一个目录，md 里用相对文件名引用图片。\n" +
                    "当前：${vm.archiveDirLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickDir.launch(null) },
                    modifier = Modifier.weight(1f),
                ) { Text("选择目录") }
                OutlinedButton(
                    onClick = { vm.setArchiveDir(null) },
                    modifier = Modifier.weight(1f),
                ) { Text("恢复默认") }
            }
            SwitchRow(
                title = "按推文发布日期归档",
                body = "关闭时用「保存当天」的日期命名 md；开启后用推文本身的发布日期。",
                checked = byPostDate,
                onCheckedChange = { byPostDate = it },
            )
        }

        SectionCard {
            Text("解析服务", style = MaterialTheme.typography.titleMedium)
            Text(
                "默认 api.fxtwitter.com。服务失效时会自动依次尝试下方备用地址，也可以填自建 FxEmbed 实例。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("主解析地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fallbacks,
                onValueChange = { fallbacks = it },
                label = { Text("备用地址（每行一个，可留空）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "接口需实现 /2/status/{id} 或 /i/status/{id}，返回格式与 FxTwitter 一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("默认画质上限", style = MaterialTheme.typography.titleMedium)
            Text(
                "解析后自动选中的最高分辨率；仍可在结果页手动改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QUALITY_OPTIONS.forEach { (label, height) ->
                    if (maxHeight == height) {
                        Button(onClick = { maxHeight = height }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { maxHeight = height },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
        }

        SectionCard {
            SwitchRow(
                title = "仅 WiFi 下载",
                body = "开启后，非 WiFi 网络下的任务会直接标记失败，避免消耗流量。",
                checked = wifiOnly,
                onCheckedChange = { wifiOnly = it },
            )
            SwitchRow(
                title = "一键保存（跳过选择页）",
                body = "解析成功后直接保存正文文字、全部图片和默认画质视频，回到原来的「粘贴即下载」体验。",
                checked = skipPicker,
                onCheckedChange = { skipPicker = it },
            )
        }

        Button(
            onClick = {
                vm.updateSettings {
                    it.copy(
                        baseUrl = baseUrl.trim(),
                        fallbackUrls = fallbacks
                            .split('\n', ',')
                            .map { line -> line.trim() }
                            .filter { line -> line.startsWith("http", ignoreCase = true) },
                        maxHeight = maxHeight,
                        wifiOnly = wifiOnly,
                        skipQualityPicker = skipPicker,
                        archiveByPostDate = byPostDate,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存设置") }

        OutlinedButton(
            onClick = {
                baseUrl = ResolverConfig.DEFAULT_BASE_URL
                fallbacks = ""
                maxHeight = ResolverConfig.DEFAULT_MAX_HEIGHT
                wifiOnly = false
                skipPicker = false
                byPostDate = false
                vm.updateSettings {
                    it.copy(
                        baseUrl = ResolverConfig.DEFAULT_BASE_URL,
                        fallbackUrls = emptyList(),
                        maxHeight = ResolverConfig.DEFAULT_MAX_HEIGHT,
                        wifiOnly = false,
                        skipQualityPicker = false,
                        archiveDirUri = null,
                        archiveByPostDate = false,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("恢复默认") }

        Text(
            "仅用于个人保存自己有权保存的公开内容；不处理需登录 / 私密 / 年龄限制的推文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val QUALITY_OPTIONS = listOf(
    "480p" to 480,
    "720p" to 720,
    "1080p" to 1080,
    "4K" to 4320,
)
