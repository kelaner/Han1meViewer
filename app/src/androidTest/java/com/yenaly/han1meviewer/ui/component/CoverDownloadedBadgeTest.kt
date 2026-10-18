package com.yenaly.han1meviewer.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Before
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.CheckInRecordEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.CreatorUploadingItem
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.ui.preview.fakeHeroItems
import com.yenaly.han1meviewer.ui.preview.fakeHomePageVideos
import com.yenaly.han1meviewer.ui.preview.fakeNewHanimeInfo
import com.yenaly.han1meviewer.ui.screen.home.creatorcenter.CreatorUploadingCard
import com.yenaly.han1meviewer.ui.screen.home.dailycheckin.ExistingRecordItem
import com.yenaly.han1meviewer.ui.screen.home.dailycheckin.WatchHistoryItem
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.BannerCarousel
import com.yenaly.han1meviewer.ui.screen.home.homepage.component.HeroQueue
import com.yenaly.han1meviewer.ui.screen.home.preview.PreviewInfoCard
import com.yenaly.han1meviewer.ui.screen.home.preview.PreviewTourRow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class CoverDownloadedBadgeTest(private val cover: Cover) {
    enum class Cover { Banner, HeroQueue, PreviewTour, PreviewInfo, Uploading, CheckInRecord, CheckInHistory }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun covers(): List<Array<Any>> = Cover.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun useApplicationTheme() {
        compose.runOnUiThread { compose.activity.setTheme(R.style.Theme_Hanime) }
    }

    @Test
    fun coverObservesFinishedRecordInsertionAndRemoval() {
        val code = "cover-badge-${System.nanoTime()}"
        val description = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.downloaded)
        val download = HanimeDownloadEntity(
            id = 0,
            groupId = 1,
            coverUrl = "",
            coverUri = null,
            title = "Cover regression",
            addDate = 1_700_000_000_000L,
            videoCode = code,
            videoUri = "$code.mp4",
            quality = "720P",
            videoUrl = "",
            length = 1024L,
            downloadedLength = 1024L,
            state = DownloadState.Finished,
        )
        fun waitForBadge(expected: Boolean) {
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasContentDescription(description), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() == expected
            }
        }
        runBlocking { DatabaseRepo.HanimeDownload.insertDefaultGroup() }
        try {
            compose.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) { ActualCover(code) }
                }
            }
            waitForBadge(false)
            runBlocking { DatabaseRepo.HanimeDownload.insert(download) }
            waitForBadge(true)
            compose.onNodeWithContentDescription(description, useUnmergedTree = true)
                .assertIsDisplayed()
            if (cover == Cover.Banner) captureRepresentativeCover()
            runBlocking { DatabaseRepo.HanimeDownload.delete(code) }
            waitForBadge(false)
        } finally {
            runBlocking { DatabaseRepo.HanimeDownload.delete(code) }
        }
    }

    private fun captureRepresentativeCover() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, "cover-downloaded-badge-banner.png").outputStream().use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Composable
    private fun ActualCover(code: String) {
        val video = fakeHomePageVideos.first().copy(videoCode = code, coverUrl = "")
        val history = WatchHistoryEntity(
            coverUrl = "", title = video.title, releaseDate = 0, watchDate = 0, videoCode = code,
        )
        when (cover) {
            Cover.Banner -> BannerCarousel(
                items = listOf(fakeHeroItems.first().copy(videoCode = code, imageUrl = "")),
                onItemClick = {},
                modifier = Modifier.width(320.dp),
            )
            Cover.HeroQueue -> HeroQueue(
                items = listOf(fakeHeroItems.first().copy(videoCode = code, imageUrl = "")),
                currentIndex = 0,
                onItemClick = {},
                modifier = Modifier.width(320.dp).height(100.dp),
            )
            Cover.PreviewTour -> PreviewTourRow(
                latestHanime = listOf(video), selectedIndex = 0, onSelect = {},
            )
            Cover.PreviewInfo -> PreviewInfoCard(
                previewInfo = fakeNewHanimeInfo.first().copy(
                    videoCode = code, coverUrl = "", relatedPicsUrl = emptyList(),
                ),
                onOpenVideo = {},
                onOpenImage = { _, _ -> },
            )
            Cover.Uploading -> CreatorUploadingCard(
                item = CreatorUploadingItem(
                    title = video.title, coverUrl = "", videoCode = code,
                    remoteVideoUrl = "", reviewStatus = "排隊",
                ),
                onClick = {},
            )
            Cover.CheckInRecord -> ExistingRecordItem(
                index = 1,
                record = CheckInRecordEntity(
                    date = "2026-01-01", time = "12:00", type = "",
                    sideDishes = "${video.title}\u001E$code", feeling = "",
                ),
                coverUrlMap = mapOf(code to ""),
                onNavigateToVideo = {},
                onDelete = {},
            )
            Cover.CheckInHistory -> WatchHistoryItem(
                watch = history, onNavigateToVideo = {}, onClick = {},
            )
        }
    }
}
