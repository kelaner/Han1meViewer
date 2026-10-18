package com.yenaly.han1meviewer.ui.screen.home.download

import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import kotlinx.coroutines.CancellationException

internal fun downloadQualityRank(quality: String): Int? =
    Regex("^(\\d+)[pP]?$").matchEntire(quality.trim())?.groupValues?.get(1)?.toIntOrNull()

/** Uses the full library snapshot, independently of the current UI filters. */
internal suspend fun refreshLowQualityDownloads(
    records: List<HanimeDownloadEntity>,
    onProgress: (DownloadBatchRefreshState) -> Unit,
    refresh: suspend (HanimeDownloadEntity) -> DownloadSourceAvailability,
): DownloadBatchRefreshState {
    val eligible = records.filter {
        it.isDownloaded && downloadQualityRank(it.quality)?.let { rank -> rank in 1 until 1080 } == true
    }
    var state = DownloadBatchRefreshState(isRunning = true, total = eligible.size)
    onProgress(state)
    for (record in eligible) {
        state = state.copy(currentTitle = record.title)
        onProgress(state)
        try {
            val result = refresh(record)
            if (result.higherQualities.isNotEmpty()) {
                state = state.copy(higherQualityCount = state.higherQualityCount + 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = state.copy(failures = state.failures + "${record.title}：${e.localizedMessage ?: "更新失败"}")
        }
        state = state.copy(processed = state.processed + 1)
        onProgress(state)
    }
    return state.copy(isRunning = false, isComplete = true, currentTitle = null).also(onProgress)
}
