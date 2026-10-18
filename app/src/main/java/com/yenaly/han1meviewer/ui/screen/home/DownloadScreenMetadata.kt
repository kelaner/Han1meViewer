package com.yenaly.han1meviewer.ui.screen.home

import android.content.Context
import com.yenaly.han1meviewer.HCacheManager
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 在 IO 调度器中批量读取，限制并发避免大量本地视频拖慢页面。 */
suspend fun loadDownloadedMetadata(
    context: Context,
    videoCodes: List<String>,
): Map<String, DownloadedMetadata> = withContext(Dispatchers.IO) {
    coroutineScope {
        val results = mutableListOf<Pair<String, DownloadedMetadata>>()
        videoCodes.distinct().chunked(8).forEach { batch ->
            results += batch.map { code ->
                async {
                    code to runCatching {
                        HCacheManager.loadHanimeVideoInfo(context, code).first()
                    }.getOrNull().let { info ->
                        DownloadedMetadata(info?.artist?.name, info?.artist?.genre)
                    }
                }
            }.awaitAll()
        }
        results.toMap()
    }
}
