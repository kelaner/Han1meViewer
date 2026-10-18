package com.yenaly.han1meviewer.ui.screen.home

import com.yenaly.han1meviewer.ui.screen.home.download.downloadDirectActionCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadToolbarTest {
    @Test
    fun narrowScreensReserveNavigationTitleAndOverflowTouchTargets() {
        for (width in 240..1200) {
            val direct = downloadDirectActionCount(width, 9)
            val actionWidth = (direct + if (direct < 9) 1 else 0) * 48
            assertTrue("width=$width", direct in 0..9)
            assertTrue("width=$width", actionWidth <= width - 144)
        }
    }

    @Test
    fun moreSpaceRevealsActionsInPriorityOrder() {
        assertEquals(2, downloadDirectActionCount(320, 9))
        assertEquals(3, downloadDirectActionCount(360, 9))
        assertEquals(4, downloadDirectActionCount(400, 9))
        assertEquals(9, downloadDirectActionCount(576, 9))
        assertEquals(0, downloadDirectActionCount(180, 9))
    }
}
