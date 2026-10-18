package com.yenaly.han1meviewer.ui.screen.home

import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadSort
import com.yenaly.han1meviewer.ui.screen.home.download.downloadSortComparator
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSortTest {
    @Test
    fun uploadSorting_usesUploadDateAndKeepsMissingDatesLastInBothDirections() {
        val items = listOf(
            item(1, null, Long.MAX_VALUE),
            item(2, "2024-01-02", 1L),
            item(3, "invalid", Long.MIN_VALUE),
            item(4, "2024-01-01", 100L),
        )
        assertEquals(listOf(4, 2, 1, 3), items.sortedWith(downloadSortComparator(DownloadSort.UPLOAD_ASC)).map { it.video.id })
        assertEquals(listOf(2, 4, 1, 3), items.sortedWith(downloadSortComparator(DownloadSort.UPLOAD_DESC)).map { it.video.id })
    }

    private fun item(id: Int, uploaded: String?, added: Long) = VideoWithCategories(
        HanimeDownloadEntity(
            coverUrl = "", coverUri = null, title = "video$id", addDate = added,
            videoCode = "$id", videoUri = "file:///$id.mp4", quality = "720P", videoUrl = "",
            length = 10L, downloadedLength = 10L, id = id, uploadTime = uploaded,
        ), emptyList(),
    )
}
