package com.example.xvideodownload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[MainViewModel::class.java]

        setContent {
            XVideoTheme {
                MainScreen(vm)
            }
        }

        requestNotificationPermission()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** 从 X 的「分享 → X 内容下载」直接进入。 */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) vm.onSharedText(text)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    private companion object {
        const val REQ_NOTIFICATIONS = 100
    }
}
