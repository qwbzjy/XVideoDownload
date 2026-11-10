package com.example.xvideodownload.core

/** 保存目标的类型，决定 MediaStore 集合、目录和 MIME。 */
enum class SaveKind { VIDEO, PHOTO, MARKDOWN }

/** 同一个视频的一种清晰度。 */
data class VideoVariant(
    val url: String,
    val width: Int,
    val height: Int,
    val bitrate: Long,
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x$height" else "未知分辨率"

    /** 下拉/Chip 上显示的简短标签，例如 720p、1080p。 */
    val shortLabel: String
        get() = when {
            height <= 0 -> resolutionLabel
            height >= 2160 -> "4K"
            else -> "${height}p"
        }

    val bitrateLabel: String
        get() = if (bitrate > 0) "%.1f Mbps".format(java.util.Locale.US, bitrate / 1_000_000.0) else ""
}

/** 一条推文里的一个视频（含多个清晰度）。 */
data class VideoItem(
    val id: String,
    val thumbnailUrl: String?,
    val durationSec: Double,
    val variants: List<VideoVariant>,
) {
    val durationLabel: String
        get() = if (durationSec <= 0.0) "" else {
            val total = durationSec.toInt()
            "%d:%02d".format(total / 60, total % 60)
        }

    /** 按画质上限挑一档：优先不超过 maxHeight 的最高码率，否则取全局最高。 */
    fun best(maxHeight: Int = ResolverConfig.DEFAULT_MAX_HEIGHT): VideoVariant? {
        if (variants.isEmpty()) return null
        return variants.filter { it.height in 1..maxHeight }.maxByOrNull { it.bitrate }
            ?: variants.maxByOrNull { it.bitrate }
    }

    /** 供画质选择器展示：按分辨率从高到低，同分辨率取码率高的。 */
    fun selectableVariants(): List<VideoVariant> = variants
        .sortedWith(compareByDescending<VideoVariant> { it.height }.thenByDescending { it.bitrate })
        .distinctBy { it.resolutionLabel }
}

/** 一条推文里的一张图片（URL 通常已带 ?name=orig，即原图）。 */
data class PhotoItem(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
    val altText: String?,
) {
    val sizeLabel: String
        get() = if (width > 0 && height > 0) "${width}x$height" else "图片"
}

/**
 * 一次解析的结果：一条推文可以同时有正文、图片和视频。
 */
data class ResolvedTweet(
    val tweetId: String,
    val author: String,
    val text: String,
    val createdAt: String?,
    /** 推文发布日期 yyyy-MM-dd，用于「按发布日期归档」。 */
    val postDate: String?,
    val sourceUrl: String?,
    val items: List<VideoItem>,
    val photos: List<PhotoItem>,
) {
    val hasContent: Boolean
        get() = text.isNotBlank() || items.isNotEmpty() || photos.isNotEmpty()

    companion object {
        const val TEXT_KEY = "text"

        fun videoKey(id: String) = "v:$id"
        fun photoKey(id: String) = "p:$id"

        /** 用户直接粘贴了 mp4 直链时构造，跳过解析。 */
        fun direct(url: String) = ResolvedTweet(
            tweetId = "direct",
            author = "",
            text = "",
            createdAt = null,
            postDate = null,
            sourceUrl = null,
            items = listOf(
                VideoItem(
                    id = "direct",
                    thumbnailUrl = null,
                    durationSec = 0.0,
                    variants = listOf(VideoVariant(url, 0, 0, 0L)),
                ),
            ),
            photos = emptyList(),
        )
    }
}
