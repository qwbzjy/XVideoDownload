package com.example.xvideodownload

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xvideodownload.core.MarkdownEntry
import com.example.xvideodownload.core.ResolvedTweet
import com.example.xvideodownload.core.SaveKind
import com.example.xvideodownload.core.Settings
import com.example.xvideodownload.core.SettingsStore
import com.example.xvideodownload.core.TweetResolver
import com.example.xvideodownload.core.VideoVariant
import com.example.xvideodownload.download.ArchiveStorage
import com.example.xvideodownload.download.DownloadCenter
import com.example.xvideodownload.download.DownloadRecord
import com.example.xvideodownload.download.DownloadService
import com.example.xvideodownload.download.DownloadTask
import com.example.xvideodownload.download.HistoryRepository
import com.example.xvideodownload.download.TaskState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    init {
        SettingsStore.init(app)
        HistoryRepository.init(app)
    }

    private val resolver = TweetResolver()

    val settings: StateFlow<Settings> = SettingsStore.settings
    val history: StateFlow<List<DownloadRecord>> = HistoryRepository.records
    val tasks: StateFlow<List<DownloadTask>> = DownloadCenter.tasks

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** 从分享入口进来时自增，MainScreen 据此切回「下载」Tab。 */
    private val _focusDownloadTab = MutableStateFlow(0)
    val focusDownloadTab: StateFlow<Int> = _focusDownloadTab.asStateFlow()

    private var resolveJob: Job? = null

    fun onInputChange(value: String) {
        _input.value = value
    }

    /** 从 X 分享菜单过来的文本：自动填入并开始解析。 */
    fun onSharedText(text: String) {
        if (text.isBlank()) return
        _input.value = text.trim()
        _focusDownloadTab.value += 1
        resolve()
    }

    fun updateSettings(transform: (Settings) -> Settings) = SettingsStore.update(transform)
    fun setArchiveDir(uri: String?) = updateSettings { it.copy(archiveDirUri = uri) }
    fun archiveDirLabel(): String = ArchiveStorage(getApplication()).dirLabel()

    fun removeHistory(id: String) = HistoryRepository.remove(id)
    fun clearHistory() = HistoryRepository.clear()
    fun cancelTask(id: String) = DownloadCenter.cancel(id)
    fun cancelAll() = DownloadCenter.snapshot()
        .filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }
        .forEach { DownloadCenter.cancel(it.id) }

    fun clearFinishedTasks() = DownloadCenter.clearFinished()

    fun reset() {
        resolveJob?.cancel()
        _state.value = UiState.Idle
    }

    fun resolve() {
        val current = _state.value
        if (current is UiState.Resolving) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _state.value = UiState.Resolving
            when (val result = resolver.resolve(_input.value)) {
                is TweetResolver.Result.Failure -> {
                    _state.value = UiState.Error(result.title, result.detail)
                }

                is TweetResolver.Result.Success -> {
                    val tweet = result.tweet
                    if (!tweet.hasContent) {
                        _state.value = UiState.Error(
                            "没有可保存的内容",
                            "这条推文没有正文、图片或视频",
                        )
                        return@launch
                    }

                    val maxHeight = SettingsStore.settings.value.maxHeight
                    val selections = tweet.items.mapNotNull { item ->
                        item.best(maxHeight)?.let { item.id to it }
                    }.toMap()

                    if (SettingsStore.settings.value.skipQualityPicker) {
                        enqueue(buildTasks(tweet, selections))
                        _state.value = UiState.Idle
                    } else {
                        _state.value = UiState.Ready(tweet, selections)
                    }
                }
            }
        }
    }

    fun selectVariant(itemId: String, variant: VideoVariant) {
        val current = _state.value as? UiState.Ready ?: return
        _state.value = current.copy(
            selections = current.selections.toMutableMap().apply { put(itemId, variant) },
        )
    }

    /** 保存解析出来的全部内容（文字 + 图片 + 视频）。 */
    fun downloadAll() {
        val current = _state.value as? UiState.Ready ?: return
        enqueue(buildTasks(current.tweet, current.selections))
        _state.value = UiState.Idle
    }

    /** 只保存其中一项，key 见 ResolvedTweet.videoKey / photoKey / TEXT_KEY。 */
    fun downloadOne(key: String) {
        val current = _state.value as? UiState.Ready ?: return
        enqueue(buildTasks(current.tweet, current.selections).filter { it.key == key })
        _state.value = UiState.Idle
    }

    /** 把一条推文展开成一组待保存任务，顺序与 UI 一致：文字 → 图片 → 视频。 */
    private fun buildTasks(
        tweet: ResolvedTweet,
        selections: Map<String, VideoVariant>,
    ): List<DownloadTask> = buildList {
        val groupId = UUID.randomUUID().toString()
        val base = fileNameBase(tweet)
        val config = SettingsStore.settings.value
        val date = MarkdownEntry.archiveDate(
            tweet,
            config.archiveByPostDate,
            LocalDate.now().toString(),
        )

        // 媒体任务排在前面，Markdown 任务排最后，这样写引用时能拿到真实文件名
        tweet.photos.forEachIndexed { index, photo ->
            val ext = photoExtension(photo.url)
            val suffix = if (tweet.photos.size > 1) "_${index + 1}" else ""
            add(
                DownloadTask(
                    id = UUID.randomUUID().toString(),
                    key = ResolvedTweet.photoKey(photo.id),
                    groupId = groupId,
                    kind = SaveKind.PHOTO,
                    url = photo.url,
                    fileName = "${base}_img${suffix}_${photo.sizeLabel}.$ext",
                    label = "@${tweet.author} · 图片 ${index + 1} · ${photo.sizeLabel}",
                    tweetId = tweet.tweetId,
                    author = tweet.author,
                    resolution = photo.sizeLabel,
                    state = TaskState.QUEUED,
                ),
            )
        }

        tweet.items.forEachIndexed { index, item ->
            val variant = selections[item.id] ?: return@forEachIndexed
            val suffix = if (tweet.items.size > 1) "_${index + 1}" else ""
            add(
                DownloadTask(
                    id = UUID.randomUUID().toString(),
                    key = ResolvedTweet.videoKey(item.id),
                    groupId = groupId,
                    kind = SaveKind.VIDEO,
                    url = variant.url,
                    fileName = "${base}${suffix}_${variant.resolutionLabel}.mp4",
                    label = "@${tweet.author} · ${variant.shortLabel}",
                    tweetId = tweet.tweetId,
                    author = tweet.author,
                    resolution = variant.resolutionLabel,
                    state = TaskState.QUEUED,
                ),
            )
        }

        // 每条帖子都追加一段到当天（或发布日）的 Markdown
        add(
            DownloadTask(
                id = UUID.randomUUID().toString(),
                key = ResolvedTweet.TEXT_KEY,
                groupId = groupId,
                kind = SaveKind.MARKDOWN,
                url = "",
                markdownBody = MarkdownEntry.body(tweet),
                archiveDate = date,
                fileName = MarkdownEntry.fileName(date),
                label = "@${tweet.author} · Markdown $date",
                tweetId = tweet.tweetId,
                author = tweet.author,
                resolution = "MD",
                state = TaskState.QUEUED,
            ),
        )
    }

    private fun enqueue(newTasks: List<DownloadTask>) {
        if (newTasks.isEmpty()) return
        DownloadCenter.enqueue(newTasks)
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), DownloadService::class.java),
            )
        }
    }

    private fun fileNameBase(tweet: ResolvedTweet): String {
        val author = tweet.author
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(20)
            .ifBlank { "x" }
        return "X_${author}_${tweet.tweetId}"
    }

    private fun photoExtension(url: String): String =
        url.substringBefore("?").substringAfterLast('.', "jpg").lowercase()
            .let { if (it in setOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
}
