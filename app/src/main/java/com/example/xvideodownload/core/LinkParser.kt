package com.example.xvideodownload.core

import java.net.URI

/**
 * 从用户粘贴的一整段文本里提取推文 ID，或直接给出的 mp4 直链。
 * 分享出来的文本经常是「看这个 https://x.com/... 太好笑了」这种形式，所以不能假设整段就是 URL。
 */
object LinkParser {

    sealed interface Result {
        data class Success(val link: ParsedLink) : Result
        data class Error(val message: String) : Result

        /** 文本里只有短链（t.co 等），需要先跟随跳转拿到最终地址。 */
        data class NeedExpand(val url: String) : Result
    }

    private val urlRegex = Regex(
        """https?://[^\s<>"'，。；！？、）】》]+""",
        RegexOption.IGNORE_CASE,
    )

    // ID 长度放宽到 1~25：2006 年的老推文 ID 可能只有几位（如 jack/status/20）
    private val statusRegex = Regex(
        """/(?:i/)?(?:status|statuses)/(\d{1,25})""",
        RegexOption.IGNORE_CASE,
    )

    private val xHostRegex = Regex(
        """^(?:www\.|mobile\.)?(?:x\.com|twitter\.com|fxtwitter\.com|fixupx\.com|vxtwitter\.com|fixvx\.com)$""",
        RegexOption.IGNORE_CASE,
    )

    private val bareStatusRegex = Regex(
        """(?:www\.|mobile\.)?(?:x\.com|twitter\.com|fxtwitter\.com|fixupx\.com|vxtwitter\.com)/[^\s，。；！？、）】》]+""",
        RegexOption.IGNORE_CASE,
    )

    private val shortHostRegex = Regex(
        """^(?:www\.)?(?:t\.co|bit\.ly|x\.co)$""",
        RegexOption.IGNORE_CASE,
    )

    /** 分享文本里 URL 后面常跟着标点，URL 本身不会以这些字符结尾。 */
    private val trailingJunk = Regex("""[.,;:!?'")\]}>\-]+$""")

    fun parse(rawInput: String): Result {
        val text = rawInput.trim()
        if (text.isEmpty()) return Result.Error("请先粘贴 X 的视频链接")

        val candidates = LinkedHashSet<String>()
        urlRegex.findAll(text).forEach { candidates += clean(it.value) }
        if (candidates.isEmpty()) {
            bareStatusRegex.find(text)?.let { candidates += clean("https://" + it.value) }
        }
        if (candidates.isEmpty()) {
            return Result.Error("没有找到链接，请粘贴完整的 X 推文链接")
        }

        // 1) X / fxtwitter 等域名下的推文链接
        for (candidate in candidates) {
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            if (!xHostRegex.matches(host)) continue
            statusRegex.find(uri.path.orEmpty())?.let {
                return Result.Success(ParsedLink.Tweet(it.groupValues[1]))
            }
        }

        // 2) 直接粘贴的 video.twimg.com mp4 直链，跳过解析直接下载
        for (candidate in candidates) {
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            if (host.endsWith("twimg.com", ignoreCase = true) &&
                uri.path.orEmpty().endsWith(".mp4", ignoreCase = true)
            ) {
                return Result.Success(ParsedLink.DirectVideo(candidate))
            }
        }

        // 3) 短链，交给上层跟随跳转后再解析一次
        for (candidate in candidates) {
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            if (shortHostRegex.matches(uri.host.orEmpty())) {
                return Result.NeedExpand(candidate)
            }
        }

        return Result.Error("无法识别为 X 视频链接，需要类似 x.com/用户/status/数字 的链接")
    }

    private fun clean(url: String): String = trailingJunk.replace(url, "")
}
