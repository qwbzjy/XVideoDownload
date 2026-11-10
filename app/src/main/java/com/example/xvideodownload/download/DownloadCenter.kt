package com.example.xvideodownload.download

import com.example.xvideodownload.core.SaveKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskState { QUEUED, RUNNING, DONE, FAILED }

data class DownloadTask(
    val id: String,
    /** 内容稳定标识，用于「只下载这一项」时过滤。 */
    val key: String,
    /** 同一条帖子的任务共享一个组，Markdown 任务据此收集媒体引用。 */
    val groupId: String,
    val kind: SaveKind,
    /** 视频/图片的直链；MARKDOWN 时为空串。 */
    val url: String,
    /** MARKDOWN 任务的文字片段（媒体引用在执行时补齐）。 */
    val markdownBody: String? = null,
    /** MARKDOWN 任务的归档日期 yyyy-MM-dd。 */
    val archiveDate: String? = null,
    val fileName: String,
    val label: String,
    val tweetId: String,
    val author: String,
    val resolution: String,
    val state: TaskState,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val bytes: Long = 0L,
    val displayPath: String? = null,
    val uri: String? = null,
    val error: String? = null,
)

/**
 * 进程内下载队列。Service 负责执行，UI 只观察这里的状态，避免 Binder/AIDL 的复杂度。
 * App 进程被系统回收时队列会丢失（历史记录已落盘，不受影响）。
 */
object DownloadCenter {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val cancelHandlers = HashMap<String, () -> Unit>()

    @Synchronized
    fun enqueue(tasks: List<DownloadTask>) {
        _tasks.value = _tasks.value + tasks
    }

    @Synchronized
    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    @Synchronized
    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }
    }

    @Synchronized
    fun registerCancel(id: String, handler: () -> Unit) {
        cancelHandlers[id] = handler
    }

    @Synchronized
    fun unregisterCancel(id: String) {
        cancelHandlers.remove(id)
    }

    @Synchronized
    fun cancel(id: String) {
        cancelHandlers[id]?.invoke()
        update(id) { it.copy(state = TaskState.FAILED, error = "已取消") }
    }

    @Synchronized
    fun isCancelled(id: String): Boolean =
        _tasks.value.firstOrNull { it.id == id }?.state == TaskState.FAILED

    fun snapshot(): List<DownloadTask> = _tasks.value
}
