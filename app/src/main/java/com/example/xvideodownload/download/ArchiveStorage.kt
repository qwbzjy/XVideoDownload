package com.example.xvideodownload.download

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.xvideodownload.core.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 统一存档出口：md / 图片 / 视频都写进同一个目录。
 *
 * 优先级：
 * 1. 用户在设置里通过 SAF 选的目录（tree URI，权限已持久化）
 * 2. Android 10+：MediaStore.Downloads，RELATIVE_PATH = Download/X存档
 * 3. Android 9 及以下：公共 Download/X存档 目录（WRITE_EXTERNAL_STORAGE 已声明到 maxSdk 28）
 *
 * 这样 md 里用相对文件名引用图片才成立。
 */
class ArchiveStorage(private val context: Context) {

    data class Target(
        val uri: Uri?,
        val file: File?,
        val displayName: String,
        val displayPath: String,
        val mime: String,
        val isMediaStore: Boolean,
    )

    private val resolver get() = context.contentResolver

    private val treeDir: DocumentFile?
        get() = SettingsStore.settings.value.archiveDirUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** 当前存档目录的可读名称（设置页展示用）。 */
    fun dirLabel(): String {
        val dir = treeDir
        if (dir != null) return dir.name ?: "已选目录"
        return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
    }

    /** 新建文件；返回系统实际使用的文件名（可能带 " (1)" 或 provider 追加的扩展名）。 */
    fun create(name: String, mime: String): Target? {
        val dir = treeDir
        if (dir != null) return createSaf(dir, name, mime)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStore(name, mime)
        } else {
            createLegacy(name, mime)
        }
    }

    /** 查找已有文件（md 追加时用）；若命中早期构建留下的 .txt，自动迁移成 .md。 */
    fun find(name: String): Target? {
        val safDir = treeDir
        val raw: Target? = if (safDir != null) {
            findSaf(safDir, name)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStore(name)
        } else {
            findLegacy(name)
        }
        if (raw == null) return null
        return migrateToMarkdown(raw, name)
    }

    /**
     * 早期版本用 text/plain 写 md，被 MediaProvider 改名成 xxx.md.txt。
     * 这里把内容搬到真正的 xxx.md 并删掉旧文件，保证之后永远追加到 .md。
     */
    private fun migrateToMarkdown(target: Target, requested: String): Target {
        if (target.displayName.equals(requested, ignoreCase = true)) return target
        if (!target.displayName.endsWith(".txt", ignoreCase = true)) return target

        val content = readText(target)
        val fresh = create(requested, MARKDOWN_MIME) ?: return target
        val out = open(fresh) ?: run {
            discard(fresh)
            return target
        }
        return try {
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            publish(fresh)
            discard(target)
            fresh
        } catch (e: Exception) {
            discard(fresh)
            target
        }
    }

    fun open(target: Target, append: Boolean = false): OutputStream? = when {
        target.file != null -> runCatching { FileOutputStream(target.file, append) }.getOrNull()
        target.uri != null -> runCatching {
            resolver.openOutputStream(target.uri, if (append) "wa" else "w")
        }.getOrNull()
        else -> null
    }

    fun readText(target: Target): String = runCatching {
        val stream: InputStream? = when {
            target.file != null -> if (target.file.exists()) target.file.inputStream() else null
            target.uri != null -> resolver.openInputStream(target.uri)
            else -> null
        }
        stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }.getOrDefault("")

    /**
     * MediaStore 新插入的行默认 IS_PENDING=1，写完要发布。
     * 返回**发布后的真实显示路径**：重名时系统会在这里才最终确定加 " (1)" 后缀的名字，
     * md 里的相对引用必须用它，否则会指向不存在的文件。
     */
    fun publish(target: Target): String {
        if (target.isMediaStore && target.uri != null) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            runCatching { resolver.update(target.uri, values, null, null) }
        }
        target.file?.let {
            runCatching {
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(it.absolutePath), arrayOf(target.mime), null,
                )
            }
        }
        val finalName = when {
            target.isMediaStore && target.uri != null -> queryDisplayName(target.uri)
            target.file != null -> target.file.name
            target.uri != null -> runCatching {
                DocumentFile.fromSingleUri(context, target.uri)?.name
            }.getOrNull()
            else -> null
        } ?: target.displayName
        return if (target.file != null) target.file.absolutePath
        else "${parentLabel()}/$finalName"
    }

    private fun parentLabel(): String = treeDir?.let { it.name ?: "存档目录" } ?: RELATIVE_PATH

    fun discard(target: Target) {
        when {
            target.isMediaStore && target.uri != null ->
                runCatching { resolver.delete(target.uri, null, null) }
            target.file != null -> runCatching { target.file.delete() }
            target.uri != null -> runCatching { DocumentFile.fromSingleUri(context, target.uri)?.delete() }
        }
    }

    // ---- SAF ----

    private fun createSaf(dir: DocumentFile, name: String, mime: String): Target? {
        val doc = runCatching { dir.createFile(mime, name) }.getOrNull() ?: return null
        val real = doc.name ?: name
        return Target(
            uri = doc.uri,
            file = null,
            displayName = real,
            displayPath = "${dir.name ?: "存档目录"}/$real",
            mime = mime,
            isMediaStore = false,
        )
    }

    private fun findSaf(dir: DocumentFile, name: String): Target? {
        val chosen = pickArchiveName(
            runCatching { dir.listFiles().toList() }.getOrDefault(emptyList())
                .filter { it.isFile }.mapNotNull { it.name },
            name,
        ) ?: return null
        val exact = runCatching { dir.findFile(chosen) }.getOrNull() ?: return null
        val real = exact.name ?: name
        return Target(
            uri = exact.uri,
            file = null,
            displayName = real,
            displayPath = "${dir.name ?: "存档目录"}/$real",
            mime = MARKDOWN_MIME,
            isMediaStore = false,
        )
    }

    // ---- MediaStore (API 29+) ----

    @SuppressLint("NewApi")
    private fun createMediaStore(name: String, mime: String): Target? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        val real = queryDisplayName(uri) ?: name
        return Target(uri, null, real, "$RELATIVE_PATH/$real", mime, true)
    }

    @SuppressLint("NewApi")
    private fun findMediaStore(name: String): Target? {
        val rows = mutableListOf<Pair<Long, String>>()
        runCatching {
            // MediaProvider 会把 relative_path 规范化成带尾斜杠（Download/X存档/），
            // 用等值匹配永远查不到，必须用前缀匹配。
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$RELATIVE_PATH%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rows += cursor.getLong(0) to cursor.getString(1)
                }
            }
        }
        val chosen = pickArchiveName(rows.map { it.second }, name) ?: return null
        val match = rows.firstOrNull { it.second == chosen } ?: return null
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, match.first,
        )
        return Target(
            uri, null, match.second, "$RELATIVE_PATH/${match.second}", MARKDOWN_MIME, true,
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    // ---- 旧版公共目录 (API <= 28) ----

    private fun legacyDir(): File? {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(root, FOLDER)
        return if (dir.exists() || dir.mkdirs()) dir else null
    }

    private fun createLegacy(name: String, mime: String): Target? {
        val dir = legacyDir() ?: return null
        var file = File(dir, name)
        if (file.exists()) {
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "")
            var i = 1
            while (file.exists()) {
                file = File(dir, "$base ($i).$ext")
                i++
            }
        }
        return Target(null, file, file.name, file.absolutePath, mime, false)
    }

    private fun findLegacy(name: String): Target? {
        val dir = legacyDir() ?: return null
        val exact = File(dir, name)
        if (exact.exists()) {
            return Target(null, exact, exact.name, exact.absolutePath, MARKDOWN_MIME, false)
        }
        val candidates = runCatching { dir.listFiles()?.toList() }.getOrNull().orEmpty()
        val chosen = pickArchiveName(
            candidates.filter { it.isFile }.map { it.name }, name,
        ) ?: return null
        val candidate = candidates.firstOrNull { it.name == chosen } ?: return null
        return Target(null, candidate, candidate.name, candidate.absolutePath, MARKDOWN_MIME, false)
    }

    companion object {
        const val FOLDER = "X存档"
        const val MARKDOWN_MIME = "text/markdown"
        val RELATIVE_PATH: String = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
    }
}

/**
 * 从已有文件名里挑出当天 md 应该继续追加的那个。
 *
 * MediaProvider / 部分 SAF provider 会按 MIME 给 displayName 追加扩展名
 * （`2026-11-10.md` → `2026-11-10.md.txt`），或给重名文件加 ` (1)`。
 * 精确匹配优先；其次优先选 .md 结尾的文件；只有完全没有 .md 时
 * 才回退到 .txt 遗留文件（调用方会把它迁移成 .md）。
 */
fun pickArchiveName(candidates: List<String>, requested: String): String? {
    val exact = candidates.firstOrNull { it == requested }
    if (exact != null) return exact
    val base = requested.substringBeforeLast('.')
    return candidates
        .filter { it.startsWith(requested) || it.startsWith("$base.") }
        .sortedWith(
            compareBy<String> { if (it.endsWith(".md", ignoreCase = true)) 0 else 1 }
                .thenBy { it.length }
                .thenBy { it },
        )
        .firstOrNull()
}
