package com.yenaly.han1meviewer.ui.screen.home.download

/** 本地 info.json 中用于已下载库浏览的轻量元数据。 */
data class DownloadedMetadata(
    val author: String? = null,
    val genre: String? = null,
) {
    val displayAuthor: String get() = author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val displayGenre: String get() = genre?.takeIf { it.isNotBlank() } ?: "未知类型"
}

enum class DownloadedViewMode {
    GROUPS,
    ALL,
}
