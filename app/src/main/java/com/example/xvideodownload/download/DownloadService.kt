package com.example.xvideodownload.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.xvideodownload.core.MarkdownEntry
import com.example.xvideodownload.core.SaveKind
import com.example.xvideodownload.core.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 前台下载服务：顺序执行 [DownloadCenter] 里的队列，进度写到通知栏，完成后落历史记录。
 * 切后台/锁屏也能继续下载。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var currentJob: Job? = null

    private lateinit var notifier: DownloadNotifier
    private lateinit var downloader: MediaDownloader

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        HistoryRepository.init(this)
        notifier = DownloadNotifier(this).also { it.createChannel() }
        downloader = MediaDownloader(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = DownloadCenter.snapshot().size
        ServiceCompat.startForeground(
            this,
            DownloadNotifier.NOTIFICATION_ID,
            notifier.build(0, count, "", 0),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        startWorker()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                val next = DownloadCenter.snapshot().firstOrNull { it.state == TaskState.QUEUED }
                if (next == null) {
                    delay(600)
                    if (DownloadCenter.snapshot().none {
                            it.state == TaskState.QUEUED || it.state == TaskState.RUNNING
                        }
                    ) break
                    continue
                }
                DownloadCenter.update(next.id) { it.copy(state = TaskState.RUNNING) }
                updateNotification(next.label, 0L, -1L)

                val job = scope.launch { runTask(next) }
                currentJob = job
                DownloadCenter.registerCancel(next.id) { job.cancel() }
                job.join()
                DownloadCenter.unregisterCancel(next.id)
                currentJob = null
                updateNotification()
            }
            finish()
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        if (SettingsStore.settings.value.wifiOnly && !isWifiConnected()) {
            DownloadCenter.update(task.id) {
                it.copy(state = TaskState.FAILED, error = "已开启「仅 WiFi 下载」，当前不是 WiFi")
            }
            return
        }

        try {
            val onProgress: suspend (Long, Long) -> Unit = { downloaded, total ->
                if (DownloadCenter.isCancelled(task.id)) throw CancellationException("已取消")
                DownloadCenter.update(task.id) { it.copy(downloaded = downloaded, total = total) }
                updateNotification(task.label, downloaded, total)
            }
            val result = when (task.kind) {
                SaveKind.MARKDOWN -> {
                    // 队列是顺序执行的，Markdown 任务排在同组媒体任务之后，
                    // 这里拿到的都是已写入磁盘的真实文件名（含系统自动加的后缀）。
                    val body = MarkdownEntry.withMedia(
                        task.markdownBody.orEmpty(),
                        collectMediaRefs(task.groupId),
                    )
                    downloader.appendMarkdown(
                        task.fileName,
                        task.archiveDate.orEmpty(),
                        body,
                        onProgress,
                    )
                }

                else -> downloader.download(task.url, task.fileName, task.kind, onProgress)
            }
            when (result) {
                is MediaDownloader.Result.Success -> {
                    DownloadCenter.update(task.id) {
                        it.copy(
                            state = TaskState.DONE,
                            bytes = result.bytes,
                            displayPath = result.displayPath,
                            uri = result.uri?.toString(),
                        )
                    }
                    HistoryRepository.add(
                        DownloadRecord(
                            id = UUID.randomUUID().toString(),
                            kind = task.kind,
                            tweetId = task.tweetId,
                            author = task.author,
                            fileName = result.displayPath.substringAfterLast('/'),
                            displayPath = result.displayPath,
                            uri = result.uri?.toString(),
                            bytes = result.bytes,
                            resolution = task.resolution,
                            timestamp = System.currentTimeMillis(),
                        ),
                    )
                }

                is MediaDownloader.Result.Failure -> DownloadCenter.update(task.id) {
                    it.copy(state = TaskState.FAILED, error = result.message)
                }
            }
        } catch (e: CancellationException) {
            DownloadCenter.update(task.id) {
                it.copy(state = TaskState.FAILED, error = "已取消")
            }
        }
    }

    private fun collectMediaRefs(groupId: String): List<MarkdownEntry.MediaRef> {
        var photoIndex = 0
        var videoIndex = 0
        return buildList {
            DownloadCenter.snapshot()
                .filter {
                    it.groupId == groupId && it.state == TaskState.DONE &&
                        (it.kind == SaveKind.PHOTO || it.kind == SaveKind.VIDEO)
                }
                .forEach { finished ->
                    val name = finished.displayPath?.substringAfterLast('/')
                        ?: return@forEach
                    when (finished.kind) {
                        SaveKind.PHOTO -> add(
                            MarkdownEntry.MediaRef(
                                SaveKind.PHOTO,
                                name,
                                "图片 ${++photoIndex} · ${finished.resolution}",
                            ),
                        )

                        SaveKind.VIDEO -> add(
                            MarkdownEntry.MediaRef(
                                SaveKind.VIDEO,
                                name,
                                "视频 ${++videoIndex} · ${finished.resolution}",
                            ),
                        )

                        else -> Unit
                    }
                }
        }
    }

    private fun updateNotification(label: String = "", downloaded: Long = 0L, total: Long = -1L) {
        val list = DownloadCenter.snapshot()
        val done = list.count { it.state == TaskState.DONE || it.state == TaskState.FAILED }
        val percent = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else -1
        notifier.notify(
            DownloadNotifier.NOTIFICATION_ID,
            notifier.build(done, list.size, label, percent),
        )
    }

    private fun finish() {
        val list = DownloadCenter.snapshot()
        val done = list.count { it.state == TaskState.DONE }
        notifier.notify(
            DownloadNotifier.NOTIFICATION_ID,
            notifier.build(list.size, list.size, "$done 个成功", 100),
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
