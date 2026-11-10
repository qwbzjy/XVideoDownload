package com.example.xvideodownload.download

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.xvideodownload.core.SaveKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 一条成功下载的记录。 */
data class DownloadRecord(
    val id: String,
    val kind: SaveKind,
    val tweetId: String,
    val author: String,
    val fileName: String,
    val displayPath: String,
    val uri: String?,
    val bytes: Long,
    val resolution: String,
    val timestamp: Long,
)

/**
 * 下载历史：SharedPreferences + 手写 JSON（不引 Room，也不需要 KSP 插件），最多保留 100 条。
 * 进程内单例：Service 写入后 UI 立刻能看到，避免两边各持有一份过期快照。
 */
object HistoryRepository {

    @Volatile
    private var prefs: SharedPreferences? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences("x_video_history", Context.MODE_PRIVATE)
        _records.value = load()
    }

    @Synchronized
    fun add(record: DownloadRecord) {
        val next = (listOf(record) + _records.value).take(MAX_RECORDS)
        save(next)
        _records.value = next
    }

    @Synchronized
    fun remove(id: String) {
        val next = _records.value.filterNot { it.id == id }
        save(next)
        _records.value = next
    }

    @Synchronized
    fun clear() {
        save(emptyList())
        _records.value = emptyList()
    }

    private fun load(): List<DownloadRecord> = runCatching {
        json.parseToJsonElement(prefs?.getString(KEY, "[]").orEmpty())
            .jsonArray.mapNotNull { it.toRecordOrNull() }
    }.getOrDefault(emptyList())

    private fun save(list: List<DownloadRecord>) {
        val array = JsonArray(list.map { it.toJson() })
        prefs?.edit { putString(KEY, array.toString()) }
    }

    private fun DownloadRecord.toJson() = JsonObject(
        buildMap {
            put("id", JsonPrimitive(id))
            put("kind", JsonPrimitive(kind.name))
            put("tweetId", JsonPrimitive(tweetId))
            put("author", JsonPrimitive(author))
            put("fileName", JsonPrimitive(fileName))
            put("displayPath", JsonPrimitive(displayPath))
            put("uri", JsonPrimitive(uri))
            put("bytes", JsonPrimitive(bytes))
            put("resolution", JsonPrimitive(resolution))
            put("timestamp", JsonPrimitive(timestamp))
        },
    )

    private fun JsonElement.toRecordOrNull(): DownloadRecord? = runCatching {
        val o = jsonObject
        DownloadRecord(
            id = o.str("id") ?: return null,
            // 兼容 v0.2 及更早的记录（没有 kind 字段，或已废弃的 TEXT）
            kind = runCatching {
                when (val raw = o.str("kind") ?: "VIDEO") {
                    "TEXT" -> SaveKind.MARKDOWN
                    else -> SaveKind.valueOf(raw)
                }
            }.getOrDefault(SaveKind.VIDEO),
            tweetId = o.str("tweetId") ?: "",
            author = o.str("author") ?: "",
            fileName = o.str("fileName") ?: "",
            displayPath = o.str("displayPath") ?: "",
            uri = o.str("uri"),
            bytes = o["bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
            resolution = o.str("resolution") ?: "",
            timestamp = o["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        runCatching {
            this[key]?.jsonPrimitive?.takeIf { it !is JsonNull }?.content
        }.getOrNull()

    private const val KEY = "records"
    private const val MAX_RECORDS = 100
}
