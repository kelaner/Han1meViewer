package com.yenaly.han1meviewer.ui.screen.home

import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadBatchRefreshState
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadSourceAvailability
import com.yenaly.han1meviewer.ui.screen.home.download.refreshLowQualityDownloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DownloadSourceUpdatesTest {
    @Test
    fun onlyCompletedVideosStrictlyBelow1080pAreChecked() = runBlocking {
        val checked = mutableListOf<Int>()
        val records = listOf(
            video(1, "360p"), video(2, "720P"), video(3, " 480p "),
            video(4, "1080p"), video(5, "1440p"), video(6, "2160p"),
            video(7, "720p", DownloadState.Paused), video(8, "unknown"),
            video(9, "0p"), video(10, "1080"), video(11, "720p", DownloadState.Downloading),
        )
        val result = refreshLowQualityDownloads(records, {}) {
            checked += it.id
            DownloadSourceAvailability()
        }
        assertEquals(listOf(1, 2, 3), checked)
        assertEquals(3, result.total)
        assertEquals(3, result.processed)
        assertTrue(result.isComplete)
        assertFalse(result.isRunning)
    }

    @Test
    fun everyEligibleRecordIsProcessedDespiteFailuresAndProgressReachesTotal() = runBlocking {
        val states = mutableListOf<DownloadBatchRefreshState>()
        val checked = mutableListOf<Int>()
        val result = refreshLowQualityDownloads(
            (1..150).map { video(it, "720p") }, states::add,
        ) {
            checked += it.id
            if (it.id == 2) throw IOException("连接失败")
            DownloadSourceAvailability(higherQualities = if (it.id % 2 == 1) listOf("1080p") else emptyList())
        }
        assertEquals((1..150).toList(), checked)
        assertEquals(150, result.processed)
        assertEquals(75, result.higherQualityCount)
        assertEquals(listOf("视频2：连接失败"), result.failures)
        assertEquals((0..150).toList(), states.map { it.processed }.distinct())
        assertTrue(states.any { it.currentTitle == "视频150" && it.processed == 149 })
        assertTrue(states.last().isComplete)
        assertNull(states.last().currentTitle)
    }

    @Test
    fun emptyEligibleSetCompletesWithoutNetworkCalls() = runBlocking {
        val result = refreshLowQualityDownloads(listOf(video(1, "1080p")), {}) {
            fail("1080p must never be refreshed")
            DownloadSourceAvailability()
        }
        assertTrue(result.isComplete)
        assertEquals(0, result.total)
        assertEquals(0, result.processed)
    }

    @Test
    fun cancellationIsPropagatedInsteadOfReportedAsAnItemFailure() = runBlocking {
        var checked = 0
        try {
            refreshLowQualityDownloads(listOf(video(1, "720p"), video(2, "480p")), {}) {
                checked++
                throw CancellationException("取消")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, checked)
        }
    }

    private fun video(id: Int, quality: String, state: DownloadState = DownloadState.Finished) =
        HanimeDownloadEntity(
            coverUrl = "", coverUri = null, title = "视频$id", addDate = 0,
            videoCode = "$id", videoUri = "/$id.mp4", quality = quality,
            videoUrl = "", length = 100, downloadedLength = 100, state = state, id = id,
        )
}
