package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.preview.fakeVideosItem
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.model.DownloadHeaderNode
import com.yenaly.han1meviewer.logic.model.DownloadItemNode
import com.yenaly.han1meviewer.logic.state.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadLibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)

    private val videos = (1..2).map { id ->
        VideoWithCategories(
            video = HanimeDownloadEntity(
                id = id,
                groupId = 1,
                coverUrl = "",
                coverUri = null,
                title = "Regression video $id",
                addDate = 1_700_000_000_000L,
                videoCode = "test-$id",
                videoUri = "test-$id.mp4",
                quality = "720P",
                videoUrl = "",
                length = 1024L,
                downloadedLength = 1024L,
                state = DownloadState.Finished,
            ),
            categories = emptyList(),
        )
    }

    private fun libraryState(mode: DownloadedViewMode, grouped: Boolean) = DownloadUiState(
        viewMode = mode,
        grouped = grouped,
        multiSelectMode = true,
        displayGroups = listOf(DownloadGroupEntity(id = 1, name = "Regression group", orderIndex = 0)),
        downloadedNodes = buildList {
            if (grouped) add(DownloadHeaderNode(1, "Regression group", videos))
            addAll(videos.map { DownloadItemNode(it, 1) })
        },
    )

    @Test
    fun downloadedBadgeAppearsOnlyForDownloadedCardsAndKeepsWatchedBadge() {
        var downloaded by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                VideoCardItem(
                    videoItem = fakeVideosItem,
                    isDownloaded = downloaded,
                    isWatched = true,
                    onClickVideosItem = {},
                    onLongClickVideosItem = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText(label(R.string.downloaded)).assertDoesNotExist()
        compose.runOnIdle { downloaded = true }
        compose.onNodeWithText(label(R.string.downloaded)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.played)).assertIsDisplayed()
        compose.runOnIdle { downloaded = false }
        compose.onNodeWithText(label(R.string.downloaded)).assertDoesNotExist()
    }

    @Test
    fun batchUpdateShowsProgressThenSummaryAndCanBeDismissed() {
        var state by mutableStateOf(DownloadBatchRefreshState(
            isRunning = true, total = 4, processed = 2, currentTitle = "当前视频",
            higherQualityCount = 1,
        ))
        var dismissed = false
        compose.setContent {
            MaterialTheme { DownloadSourceUpdateDialog(state) { dismissed = true } }
        }
        compose.onNodeWithText("已处理 2/4（50%）").assertIsDisplayed()
        compose.onNodeWithText("当前视频").assertIsDisplayed()
        compose.onNodeWithText("确定").assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(isRunning = false, isComplete = true, processed = 4,
                failures = listOf("失败视频：连接失败"))
        }
        compose.onNodeWithText("已完成 4/4 项检查。").assertIsDisplayed()
        compose.onNodeWithText("发现更高清源：1 项").assertIsDisplayed()
        compose.onNodeWithText("暂无更高清源：2 项").assertIsDisplayed()
        compose.onNodeWithText("更新失败：1 项").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun selectionAndBatchActionsWorkAcrossLayoutsAndGrouping() {
        var state by mutableStateOf(libraryState(DownloadedViewMode.LIST, true))
        val unexpectedEvents = mutableListOf<DownloadEvent>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxSize()) {
                    DownloadedScreen(state, rememberLazyListState()) { event ->
                        when (event) {
                            is DownloadEvent.OnToggleVideoSelection -> state = state.copy(
                                selectedVideoIds = if (event.videoId in state.selectedVideoIds)
                                    state.selectedVideoIds - event.videoId
                                else state.selectedVideoIds + event.videoId,
                            )
                            DownloadEvent.OnToggleMultiSelect -> state = state.copy(multiSelectMode = false)
                            else -> unexpectedEvents += event
                        }
                    }
                }
            }
        }
        for (mode in DownloadedViewMode.entries) {
            for (grouped in listOf(true, false)) {
                compose.runOnIdle { state = libraryState(mode, grouped) }
                if (grouped) compose.onNodeWithText("Regression group").assertIsDisplayed()
                else compose.onNodeWithText("Regression group").assertDoesNotExist()
                compose.onNodeWithText("0/2").assertIsDisplayed()
                compose.onNodeWithText(label(R.string.move_group)).assertIsNotEnabled()
                compose.onAllNodes(isToggleable()).onFirst().assertIsOff().performClick()
                compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
                compose.onNodeWithText("1/2").assertIsDisplayed()

                // The card title remains a selection target, without opening details or playback.
                compose.onNodeWithText(videos.first().video.title).performClick()
                compose.onNodeWithText("0/2").assertIsDisplayed()
                compose.onNodeWithText(label(R.string.select_all)).performClick()
                compose.onNodeWithText("2/2").assertIsDisplayed()
                compose.onNodeWithText(label(R.string.move_group)).assertIsEnabled()
                compose.onNodeWithText(label(R.string.delete)).assertIsEnabled()
                compose.onNodeWithText(label(R.string.deselect_all)).performClick()
                compose.onNodeWithText("0/2").assertIsDisplayed()
                compose.onNodeWithContentDescription(label(R.string.close)).performClick()
                compose.onNodeWithContentDescription(label(R.string.close)).assertDoesNotExist()
                compose.runOnIdle {
                    assertTrue("Unexpected normal action in $mode / grouped=$grouped", unexpectedEvents.isEmpty())
                    assertTrue(state.selectedVideoIds.isEmpty())
                    assertTrue(!state.multiSelectMode)
                }
            }
        }
    }

    @Test
    fun emptyLibraryKeepsExitAvailableInBothLayouts() {
        var state by mutableStateOf(DownloadUiState(multiSelectMode = true))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxSize()) {
                    DownloadedScreen(state, rememberLazyListState()) { event ->
                        if (event == DownloadEvent.OnToggleMultiSelect) state = state.copy(multiSelectMode = false)
                    }
                }
            }
        }
        for (mode in DownloadedViewMode.entries) {
            compose.runOnIdle { state = DownloadUiState(viewMode = mode, multiSelectMode = true) }
            compose.onNodeWithText("0/0").assertIsDisplayed()
            compose.onNodeWithText(label(R.string.select_all)).assertIsNotEnabled()
            compose.onNodeWithText(label(R.string.move_group)).assertIsNotEnabled()
            compose.onNodeWithText(label(R.string.delete)).assertIsNotEnabled()
            compose.onNodeWithContentDescription(label(R.string.close)).assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue(!state.multiSelectMode) }
        }
    }

    @Test
    fun narrowToolbarKeepsUpdatePenultimateAndOverflowActionsUsable() {
        var mode by mutableStateOf(DownloadedViewMode.LIST)
        var grouped by mutableStateOf(true)
        var multiSelect by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    DownloadToolbar(
                        width = 320.dp,
                        viewMode = mode,
                        grouped = grouped,
                        multiSelectMode = multiSelect,
                        genres = listOf("Regression genre"),
                        onToggleView = { mode = if (mode == DownloadedViewMode.LIST) DownloadedViewMode.GRID else DownloadedViewMode.LIST },
                        onSort = { actions += "sort:$it" },
                        onGenre = { actions += "genre:$it" },
                        onToggleGrouping = { grouped = !grouped },
                        onToggleMultiSelect = { multiSelect = !multiSelect },
                        onSearch = { actions += "search" },
                        onCreateGroup = { actions += "create" },
                        onReadDirectory = { actions += "read" },
                        onUpdateSources = { actions += "update" },
                        updateEnabled = true,
                        updating = false,
                    )
                }
            }
        }
        compose.onNodeWithText("一键更新").assertIsDisplayed().performClick()
        assertTrue(
            compose.onNodeWithText("一键更新").fetchSemanticsNode().boundsInRoot.right <=
                compose.onNodeWithContentDescription("更多").fetchSemanticsNode().boundsInRoot.left
        )
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("切换多列视图").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("切换列表视图").assertIsDisplayed()
        compose.onNodeWithText("排序").assertIsDisplayed().performClick()
        val sort = DownloadSort.entries.first()
        compose.onNodeWithText(label(sort.labelRes)).performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        for (text in listOf("类型分类", "取消分组展示", "多选操作", "搜索", "创建新分组", "读取下载目录")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        compose.onNodeWithText("取消分组展示").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("分组展示").assertIsDisplayed()
        compose.onNodeWithText("多选操作").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("退出多选").assertIsDisplayed()
        compose.onNodeWithText("类型分类").performClick()
        compose.onNodeWithText("Regression genre").performClick()
        for ((text, action) in listOf("搜索" to "search", "创建新分组" to "create", "读取下载目录" to "read")) {
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText(text).performClick()
            compose.runOnIdle { assertTrue(actions.contains(action)) }
        }
        compose.runOnIdle {
            assertEquals(DownloadedViewMode.GRID, mode)
            assertTrue(!grouped)
            assertTrue(multiSelect)
            assertEquals(listOf("update", "sort:$sort", "genre:Regression genre", "search", "create", "read"), actions)
        }
    }
}
