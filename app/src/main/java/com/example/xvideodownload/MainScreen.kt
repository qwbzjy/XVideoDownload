package com.example.xvideodownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xvideodownload.ui.DownloadTab
import com.example.xvideodownload.ui.HistoryTab
import com.example.xvideodownload.ui.SettingsTab

@Composable
fun MainScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("下载", "历史", "设置")
    val focus by vm.focusDownloadTab.collectAsState()

    LaunchedEffect(focus) {
        if (focus > 0) tab = 0
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "X 内容下载",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    if (tab == index) {
                        Button(onClick = { tab = index }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { tab = index }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> DownloadTab(vm)
                    1 -> HistoryTab(vm)
                    2 -> SettingsTab(vm)
                }
            }
        }
    }
}
