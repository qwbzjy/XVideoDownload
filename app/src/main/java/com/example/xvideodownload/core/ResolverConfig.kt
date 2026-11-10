package com.example.xvideodownload.core

/**
 * 解析服务配置。
 *
 * 默认使用 FxTwitter 的公开 API（无需密钥）：
 *   GET https://api.fxtwitter.com/2/status/{id}
 *   GET https://api.fxtwitter.com/i/status/{id}
 *
 * [TweetResolver] 会按「主地址 /2/status → 主地址 /i/status → 备用地址」顺序自动降级。
 * 备用地址可以在 App 设置里添加（需实现同样的接口，例如自建 FxEmbed 实例）。
 */
object ResolverConfig {
    const val DEFAULT_BASE_URL = "https://api.fxtwitter.com"

    /** 内置备用服务；实测可用性不稳定，默认只保留主服务，用户可在设置里自行添加。 */
    val BUILTIN_FALLBACKS: List<String> = emptyList()

    /** 默认画质上限：避免默认下载几百 MB 的 4K 版本。 */
    const val DEFAULT_MAX_HEIGHT = 1080

    /** 每个解析地址的超时。 */
    const val CONNECT_TIMEOUT_MS = 12_000
    const val READ_TIMEOUT_MS = 20_000
}
