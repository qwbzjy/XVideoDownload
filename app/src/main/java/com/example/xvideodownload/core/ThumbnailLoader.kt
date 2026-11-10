package com.example.xvideodownload.core

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 封面加载：不引 Coil（本机缓存缺 coil-network 模块），自己用 HttpURLConnection + LruCache。
 * 失败时返回 null，UI 显示占位块即可。
 */
object ThumbnailLoader {

    private val cache = LruCache<String, ImageBitmap>(32)

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        synchronized(cache) { cache.get(url) }?.let { return it }
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                if (conn.responseCode !in 200..299) return@withContext null
                val bytes = conn.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                val image = bitmap.asImageBitmap()
                synchronized(cache) { cache.put(url, image) }
                image
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
}
