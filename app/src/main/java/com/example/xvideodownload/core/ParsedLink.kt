package com.example.xvideodownload.core

/** 用户粘贴的内容被识别后的两种形态。 */
sealed interface ParsedLink {
    data class Tweet(val id: String) : ParsedLink
    data class DirectVideo(val url: String) : ParsedLink
}
