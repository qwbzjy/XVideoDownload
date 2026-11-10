package com.example.xvideodownload.core

/**
 * Markdown 存档内容生成（纯 JVM，便于单测）。
 *
 * 每天一个文件，帖子之间用 --- 分隔；图片用 ![](相对文件名) 内嵌，视频用链接引用，
 * 因为媒体文件与 md 同目录，相对路径即可生效。
 */
object MarkdownEntry {

    data class MediaRef(val kind: SaveKind, val fileName: String, val label: String)

    fun header(date: String): String = "# X 推文存档 · $date\n"

    /** 一个帖子的文字部分（不含媒体引用，媒体任务完成后再拼进去）。 */
    fun body(tweet: ResolvedTweet): String = buildString {
        appendLine("## @${tweet.author.ifBlank { "未知" }} · ${tweet.createdAt ?: "时间未知"}")
        appendLine()
        appendLine(tweet.text.trim())
        appendLine()
        appendLine("- 原链接：${tweet.sourceUrl ?: "https://x.com/i/status/${tweet.tweetId}"}")
        appendLine("- 推文 ID：${tweet.tweetId}")
    }.trimEnd()

    /** 在文字片段后追加媒体引用。 */
    fun withMedia(body: String, refs: List<MediaRef>): String {
        if (refs.isEmpty()) return body
        return buildString {
            append(body.trimEnd())
            appendLine()
            refs.forEach { ref ->
                appendLine()
                when (ref.kind) {
                    SaveKind.PHOTO -> appendLine("![${ref.label}](${ref.fileName})")
                    SaveKind.VIDEO -> appendLine("🎬 [${ref.label}](${ref.fileName})")
                    else -> Unit
                }
            }
        }.trimEnd()
    }

    /** 把新片段追加到已有文件：首次写入带标题，之后用 --- 分隔。 */
    fun append(existing: String, date: String, entry: String): String {
        val clean = entry.trim()
        return if (existing.isBlank()) {
            header(date) + "\n" + clean + "\n"
        } else {
            existing.trimEnd() + "\n\n---\n\n" + clean + "\n"
        }
    }

    /** 归档用日期：按推文发布日期或保存当天。 */
    fun archiveDate(tweet: ResolvedTweet, byPostDate: Boolean, today: String): String =
        if (byPostDate && !tweet.postDate.isNullOrBlank()) tweet.postDate!! else today

    /** 该日期对应的 md 文件名。 */
    fun fileName(date: String): String = "$date.md"
}
