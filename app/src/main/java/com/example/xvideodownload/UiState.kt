package com.example.xvideodownload

import com.example.xvideodownload.core.ResolvedTweet
import com.example.xvideodownload.core.VideoVariant

sealed interface UiState {
    data object Idle : UiState
    data object Resolving : UiState

    /** 解析完成，等待用户选画质 / 选择下载哪几条。key 是 VideoItem.id。 */
    data class Ready(
        val tweet: ResolvedTweet,
        val selections: Map<String, VideoVariant>,
    ) : UiState

    data class Error(val title: String, val detail: String) : UiState
}
