package com.yenaly.han1meviewer.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.model.DownloadHeaderNode
import com.yenaly.han1meviewer.logic.model.DownloadItemNode
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadEvent
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadSort
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedMetadata
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedViewMode
import com.yenaly.han1meviewer.ui.screen.home.download.downloadSortComparator
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadUiState
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedScreen
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadingScreen
import com.yenaly.han1meviewer.ui.screen.home.download.MoveGroupDialog
import com.yenaly.han1meviewer.ui.screen.home.download.toDisplayGroups
import com.yenaly.han1meviewer.ui.screen.home.download.toFlatNodeList
import com.yenaly.han1meviewer.ui.screen.home.download.toNodeList
import com.yenaly.han1meviewer.util.toSimplified
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * 下载页面 Screen 层。
 *
 * 接收数据流和 [DownloadEvent] 回调，管理 Tab、分组展开等 UI 编排状态。
 * 本地 UI 事件（Tab 切换、分组折叠）由 Screen 处理；业务事件透传给 Route。
 * Content 组件仅接收 UiState + Event 回调。
 *
 * @param downloadingFlow 下载中任务流
 * @param downloadedFlow 已下载视频流
 * @param downloadedGroupsFlow 分组列表流
 * @param collapseDownloadedGroup 默认折叠分组
 * @param onBack 返回回调
 * @param onLoadDownloaded 初始加载已下载列表（一次性调用）
 * @param onEvent 业务事件回调（透传给 Route 层处理）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(
    downloadingFlow: Flow<List<HanimeDownloadEntity>>,
    downloadedFlow: StateFlow<List<VideoWithCategories>>,
    downloadedGroupsFlow: StateFlow<List<DownloadGroupEntity>>,
    collapseDownloadedGroup: Boolean,
    onBack: () -> Unit,
    onLoadDownloaded: () -> Unit,
    onEvent: (DownloadEvent) -> Unit,
) {
    val downloadingItems by downloadingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedItems by downloadedFlow.collectAsStateWithLifecycle()
    val downloadedGroups by downloadedGroupsFlow.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var downloadedGroupExpandedState by rememberSaveable(stateSaver = intBooleanMapSaver()) {
        mutableStateOf(emptyMap())
    }
    val downloadedLazyListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val scope = rememberCoroutineScope()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var downloadedHeaderNodes by remember { mutableStateOf<List<DownloadHeaderNode>>(emptyList()) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedVideoIds by remember { mutableStateOf(setOf<Int>()) }
    var pendingBatchMove by remember { mutableStateOf(false) }
    var pendingBatchMoveConfirm by remember { mutableStateOf<Pair<List<VideoWithCategories>, Int>?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DownloadSort.DATE_DESC) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(DownloadedViewMode.GROUPS) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var metadata by remember { mutableStateOf<Map<Int, DownloadedMetadata>>(emptyMap()) }
    val context = LocalContext.current

    LaunchedEffect(downloadedItems.map { it.video.id to it.video.videoCode }) {
        val loaded = loadDownloadedMetadata(context, downloadedItems.map { it.video.videoCode })
        metadata = downloadedItems.associate { item ->
            item.video.id to (loaded[item.video.videoCode] ?: DownloadedMetadata())
        }
    }

    val displayGroups = downloadedGroups.toDisplayGroups()
    val unknownGenre = stringResource(R.string.download_unknown_genre)
    val availableGenres = remember(downloadedItems, metadata, unknownGenre) {
        downloadedItems.map { metadata[it.video.id]?.displayGenre ?: unknownGenre }.distinct().sorted()
    }

    val filteredDownloadedItems =
        remember(downloadedItems, searchQuery, sort, metadata, selectedGenre, unknownGenre) {
            val filtered = downloadedItems.filter { item ->
                val matchesGenre = selectedGenre == null ||
                    (metadata[item.video.id]?.displayGenre ?: unknownGenre) == selectedGenre
                if (!matchesGenre) return@filter false
                if (searchQuery.isBlank()) return@filter true
                val normalizedQuery = searchQuery.toSimplified()
                item.video.title.toSimplified().contains(normalizedQuery, ignoreCase = true) ||
                    item.video.videoCode.contains(searchQuery, ignoreCase = true)
            }
            filtered.sortedWith(downloadSortComparator(sort, metadata))
        }

    val downloadedNodes =
        remember(filteredDownloadedItems, displayGroups, collapseDownloadedGroup, downloadedHeaderNodes, downloadedGroupExpandedState, searchQuery, viewMode) {
            val groupIdToNameMap = displayGroups.associate { it.id to it.name }
            when {
                viewMode == DownloadedViewMode.ALL -> {
                    downloadedHeaderNodes = emptyList()
                    filteredDownloadedItems.map { DownloadItemNode(it, it.video.groupId) }
                }
                filteredDownloadedItems.isEmpty() -> {
                    downloadedHeaderNodes = emptyList()
                    emptyList()
                }

                searchQuery.isNotBlank() -> {
                    downloadedHeaderNodes = emptyList()
                    filteredDownloadedItems.map { DownloadItemNode(it, it.video.groupId) }
                }

                else -> {
                    val newHeaders =
                        filteredDownloadedItems.toNodeList(groupIdToNameMap, collapseDownloadedGroup)
                    downloadedHeaderNodes = newHeaders.map { newHeader ->
                        newHeader.copy(
                            isExpanded = downloadedGroupExpandedState[newHeader.groupId]
                                ?: downloadedHeaderNodes.firstOrNull { it.groupId == newHeader.groupId }?.isExpanded
                                ?: !collapseDownloadedGroup
                        )
                    }
                    downloadedHeaderNodes.toFlatNodeList()
                }
            }
        }

    LaunchedEffect(downloadedNodes, viewMode, selectedGenre, searchQuery) {
        val visibleIds = downloadedNodes.filterIsInstance<DownloadItemNode>().map { it.data.video.id }.toSet()
        selectedVideoIds = selectedVideoIds.intersect(visibleIds)
    }

    val uiState = DownloadUiState(
        downloadingItems = downloadingItems,
        downloadedNodes = downloadedNodes,
        displayGroups = displayGroups,
        currentPage = pagerState.currentPage,
        showCreateGroupDialog = showCreateGroupDialog,
        multiSelectMode = multiSelectMode,
        selectedVideoIds = selectedVideoIds,
        viewMode = viewMode,
        metadata = metadata,
        selectedGenre = selectedGenre,
    )

    val handleEvent: (DownloadEvent) -> Unit = { event ->
        when (event) {
            // 本地 UI 事件：Screen 自行处理
            is DownloadEvent.OnToggleGroup -> {
                downloadedHeaderNodes = downloadedHeaderNodes.map {
                    if (it.groupId == event.groupId) {
                        val expanded = !it.isExpanded
                        downloadedGroupExpandedState = downloadedGroupExpandedState + (it.groupId to expanded)
                        it.copy(isExpanded = expanded)
                    } else {
                        it
                    }
                }
            }
            is DownloadEvent.OnCreateGroupDialogChange -> showCreateGroupDialog = event.visible
            is DownloadEvent.OnPageChange -> {
                scope.launch { pagerState.animateScrollToPage(event.page) }
            }
            // 多选事件：Screen 自行处理
            is DownloadEvent.OnToggleMultiSelect -> {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) selectedVideoIds = emptySet()
            }
            is DownloadEvent.OnToggleVideoSelection -> {
                selectedVideoIds = if (event.videoId in selectedVideoIds) {
                    selectedVideoIds - event.videoId
                } else {
                    selectedVideoIds + event.videoId
                }
            }
            is DownloadEvent.OnSelectAllCurrentGroup -> {
                val groupVideos = downloadedNodes.filterIsInstance<DownloadItemNode>()
                    .filter { it.parentKey == event.groupId }
                selectedVideoIds = if (event.select) {
                    selectedVideoIds + groupVideos.map { it.data.video.id }.toSet()
                } else {
                    selectedVideoIds - groupVideos.map { it.data.video.id }.toSet()
                }
            }
            is DownloadEvent.OnBatchMoveRequest -> { pendingBatchMove = true }
            // 业务事件：透传给 Route
            else -> onEvent(event)
        }
    }

    LaunchedEffect(Unit) {
        onLoadDownloaded()
    }

    HanimeScaffold(
        title = stringResource(R.string.download),
        onBack = onBack,
        actions = {
            if (uiState.currentPage == 0) {
                FilledIconButton(
                    onClick = { handleEvent(DownloadEvent.OnResumeAll(uiState.downloadingItems)) },
                    enabled = uiState.downloadingItems.isNotEmpty()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_play_arrow_24),
                        contentDescription = stringResource(R.string.start_all),
                    )
                }
                FilledIconButton(
                    onClick = { handleEvent(DownloadEvent.OnPauseAll(uiState.downloadingItems)) },
                    enabled = uiState.downloadingItems.isNotEmpty()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_pause_24),
                        contentDescription = stringResource(R.string.pause_all),
                    )
                }
            } else {
                if (!uiState.multiSelectMode) {
                    FilledIconButton(
                        onClick = { showSearchBar = !showSearchBar }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_search_24),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    Box {
                        FilledIconButton(
                            onClick = { showSortMenu = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_sort_24),
                                contentDescription = stringResource(R.string.sort_option),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DownloadSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        sort = option
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        FilledIconButton(onClick = { viewMode = if (viewMode == DownloadedViewMode.GROUPS) DownloadedViewMode.ALL else DownloadedViewMode.GROUPS }) {
                            Icon(
                                painter = painterResource(
                                    if (viewMode == DownloadedViewMode.GROUPS) R.drawable.baseline_grid_24
                                    else R.drawable.baseline_format_list_bulleted_24
                                ),
                                contentDescription = stringResource(
                                    if (viewMode == DownloadedViewMode.GROUPS) R.string.download_view_all
                                    else R.string.download_view_groups
                                ),
                            )
                        }
                    }
                    Box {
                        FilledIconButton(onClick = { showGenreMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_advanced_search_24),
                                contentDescription = stringResource(R.string.download_genre_filter),
                            )
                        }
                        DropdownMenu(expanded = showGenreMenu, onDismissRequest = { showGenreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.download_all_genres)) }, onClick = { selectedGenre = null; showGenreMenu = false })
                            availableGenres.forEach { genre ->
                                DropdownMenuItem(text = { Text(genre) }, onClick = { selectedGenre = genre; showGenreMenu = false })
                            }
                        }
                    }
                    FilledIconButton(
                        onClick = { handleEvent(DownloadEvent.OnToggleMultiSelect) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_edit_24),
                            contentDescription = stringResource(R.string.edit),
                        )
                    }
                    FilledIconButton(
                        onClick = { handleEvent(DownloadEvent.OnCreateGroupDialogChange(true)) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_add_24),
                            contentDescription = stringResource(R.string.create_new_group),
                        )
                    }
                    FilledIconButton(
                        onClick = { handleEvent(DownloadEvent.OnImportDownloaded) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_download_24),
                            contentDescription = stringResource(R.string.read_download_dir_title),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.currentPage) {
                Tab(
                    selected = uiState.currentPage == 0,
                    onClick = { handleEvent(DownloadEvent.OnPageChange(0)) },
                    text = { Text(stringResource(R.string.downloading)) },
                )
                Tab(
                    selected = uiState.currentPage == 1,
                    onClick = { handleEvent(DownloadEvent.OnPageChange(1)) },
                    text = { Text(stringResource(R.string.downloaded)) },
                )
            }

            AnimatedVisibility(visible = showSearchBar && uiState.currentPage == 1) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_downloaded_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_search_24),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.close),
                                )
                            }
                        }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> DownloadingScreen(
                        uiState = uiState,
                        onEvent = handleEvent,
                    )

                    else -> DownloadedScreen(
                        uiState = uiState,
                        listState = downloadedLazyListState,
                        onEvent = handleEvent,
                    )
                }
            }
        }
    }

    if (pendingBatchMove) {
        val selectedVideos = downloadedNodes
            .filterIsInstance<DownloadItemNode>()
            .filter { it.data.video.id in selectedVideoIds }
            .map { it.data }
        if (selectedVideos.isNotEmpty()) {
            MoveGroupDialog(
                video = selectedVideos.first(),
                groups = displayGroups,
                onDismiss = { pendingBatchMove = false },
                onConfirm = { _, groupId ->
                    pendingBatchMoveConfirm = Pair(selectedVideos, groupId)
                    pendingBatchMove = false
                },
            )
        } else {
            pendingBatchMove = false
        }
    }

    pendingBatchMoveConfirm?.let { (videos, groupId) ->
        val groupName = displayGroups.find { it.id == groupId }?.name ?: "ID:$groupId"
        ConfirmDialog(
            visible = true,
            title = stringResource(R.string.move_group),
            message = stringResource(R.string.confirm_move_videos,videos.size,groupName),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                handleEvent(DownloadEvent.OnBatchMoveGroup(videos, groupId))
                pendingBatchMoveConfirm = null
                multiSelectMode = false
                selectedVideoIds = emptySet()
            },
            onDismiss = { pendingBatchMoveConfirm = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadScreenPreview() {
    ComponentPreview {
        DownloadScreen(
            downloadingFlow = flowOf(emptyList()),
            downloadedFlow = MutableStateFlow(emptyList()),
            downloadedGroupsFlow = MutableStateFlow(emptyList()),
            collapseDownloadedGroup = false,
            onBack = {},
            onLoadDownloaded = {},
            onEvent = {},
        )
    }
}

private fun intBooleanMapSaver(): Saver<Map<Int, Boolean>, ArrayList<String>> {
    return Saver(
        save = { state -> ArrayList(state.map { (key, value) -> "$key=$value" }) },
        restore = { saved ->
            saved.mapNotNull { item ->
                val separatorIndex = item.lastIndexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val key = item.substring(0, separatorIndex).toIntOrNull()
                    ?: return@mapNotNull null
                val value = item.substring(separatorIndex + 1).toBooleanStrictOrNull()
                    ?: return@mapNotNull null
                key to value
            }.toMap()
        },
    )
}
