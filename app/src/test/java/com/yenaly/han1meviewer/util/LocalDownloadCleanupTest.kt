package com.yenaly.han1meviewer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDownloadCleanupTest {
    private val metadata = LocalDownloadMetadata(
        title = "Example/title",
        videoUrls = mapOf("720P" to "https://example.test/video.mp4"),
    )

    @Test
    fun recognizesOnlyFilesDescribedByDownloadMetadata() {
        listOf("info.json", ".nomedia", "Example_title.png", "Example_title_720P.mp4",
            "repair_123e4567-e89b-12d3-a456-426614174000.png").forEach {
            assertTrue(it, LocalDownloadMaintenance.isDownloadFileName(it, metadata))
        }
        listOf("personal.mp4", "notes.json", "Example_title_notes.mp4", "Example_title_1080P.mp4",
            "repair_personal.png", "another.png").forEach {
            assertFalse(it, LocalDownloadMaintenance.isDownloadFileName(it, metadata))
        }
    }

    @Test
    fun unrecognizedMetadataNeverAuthorizesCleanup() {
        listOf("info.json", ".nomedia", "video.mp4").forEach {
            assertFalse(LocalDownloadMaintenance.isDownloadFileName(it, LocalDownloadMetadata()))
            assertFalse(LocalDownloadMaintenance.isDownloadFileName(it, metadata.copy(videoUrls = emptyMap())))
        }
    }
}
