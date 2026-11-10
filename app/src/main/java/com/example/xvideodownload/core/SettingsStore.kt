package com.example.xvideodownload.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置持久化（进程内单例）。
 * Service 和 UI 必须共享同一个实例，否则 UI 改了设置、后台服务读到的还是旧值。
 */
object SettingsStore {

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _settings = MutableStateFlow(Settings.default())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences("x_video_download", Context.MODE_PRIVATE)
        _settings.value = snapshot()
    }

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs?.edit {
            putString(KEY_BASE_URL, next.baseUrl.trim().trimEnd('/')
                .ifBlank { ResolverConfig.DEFAULT_BASE_URL })
            putString(KEY_FALLBACKS, next.fallbackUrls.joinToString("\n"))
            putInt(KEY_MAX_HEIGHT, next.maxHeight)
            putBoolean(KEY_WIFI_ONLY, next.wifiOnly)
            putBoolean(KEY_SKIP_PICKER, next.skipQualityPicker)
            putString(KEY_ARCHIVE_DIR, next.archiveDirUri.orEmpty())
            putBoolean(KEY_BY_POST_DATE, next.archiveByPostDate)
        }
        _settings.value = snapshot()
    }

    private fun snapshot(): Settings {
        val p = prefs ?: return Settings.default()
        return Settings(
            baseUrl = p.getString(KEY_BASE_URL, ResolverConfig.DEFAULT_BASE_URL)
                .orEmpty().ifBlank { ResolverConfig.DEFAULT_BASE_URL }.trimEnd('/'),
            fallbackUrls = p.getString(KEY_FALLBACKS, "").orEmpty()
                .split('\n', ',')
                .map { it.trim().trimEnd('/') }
                .filter { it.startsWith("http", ignoreCase = true) },
            maxHeight = p.getInt(KEY_MAX_HEIGHT, ResolverConfig.DEFAULT_MAX_HEIGHT),
            wifiOnly = p.getBoolean(KEY_WIFI_ONLY, false),
            skipQualityPicker = p.getBoolean(KEY_SKIP_PICKER, false),
            archiveDirUri = p.getString(KEY_ARCHIVE_DIR, "").orEmpty().ifBlank { null },
            archiveByPostDate = p.getBoolean(KEY_BY_POST_DATE, false),
        )
    }

    private const val KEY_BASE_URL = "resolver_base_url"
    private const val KEY_FALLBACKS = "resolver_fallbacks"
    private const val KEY_MAX_HEIGHT = "max_height"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SKIP_PICKER = "skip_quality_picker"
    private const val KEY_ARCHIVE_DIR = "archive_dir_uri"
    private const val KEY_BY_POST_DATE = "archive_by_post_date"
}

data class Settings(
    val baseUrl: String,
    val fallbackUrls: List<String>,
    val maxHeight: Int,
    val wifiOnly: Boolean,
    val skipQualityPicker: Boolean,
    /** SAF tree URI；null 时回退到 Download/X存档/。 */
    val archiveDirUri: String?,
    /** true = 按推文发布日期归档；false = 按保存当天。 */
    val archiveByPostDate: Boolean,
) {
    /** 主地址 + 备用地址，去重。 */
    val resolverChain: List<String>
        get() = (listOf(baseUrl) + fallbackUrls + ResolverConfig.BUILTIN_FALLBACKS)
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("http", ignoreCase = true) }
            .distinct()

    companion object {
        fun default() = Settings(
            baseUrl = ResolverConfig.DEFAULT_BASE_URL,
            fallbackUrls = emptyList(),
            maxHeight = ResolverConfig.DEFAULT_MAX_HEIGHT,
            wifiOnly = false,
            skipQualityPicker = false,
            archiveDirUri = null,
            archiveByPostDate = false,
        )
    }
}
