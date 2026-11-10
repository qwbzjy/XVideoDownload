package com.example.xvideodownload.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.xvideodownload.R

/** 前台服务通知：显示批量下载的整体进度。 */
class DownloadNotifier(private val context: Context) {

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "X 内容保存进度" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun build(done: Int, total: Int, currentLabel: String, percent: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(if (done >= total) "下载完成" else "正在下载视频")
            .setContentText(
                if (done >= total) "$total 个视频已保存到相册" else "$done/$total · $currentLabel",
            )
            .setProgress(100, percent, percent < 0)
            .setOngoing(done < total)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL_ID = "x_video_download"
        const val NOTIFICATION_ID = 1001
    }
}
