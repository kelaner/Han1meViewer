package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.model.DownloadHeaderNode
import com.yenaly.han1meviewer.logic.model.DownloadItemNode
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeDownloadedGroups
import com.yenaly.han1meviewer.ui.preview.fakeDownloadedNodes
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

/**
 * 已下载 Tab 页面（Content 层）。
 *
 * 接收 [DownloadUiState] + [DownloadEvent] 回调，支持多选批量移动和删除。
 *
 * @param uiState 页面 UI 状态
 * @param onEvent 用户交互事件回调
 */
@Composable
fun DownloadedScreen(
    uiState: DownloadUiState,
    listState: LazyListState,
    onEvent: (DownloadEvent) -> Unit,
) {
    var pendingRename by remember { mutableStateOf<DownloadHeaderNode?>(null) }
    var pendingMoveVideo by remember { mutableStateOf<VideoWithCategories?>(null) }
    var pendingBatchDeleteVideos by remember { mutableStateOf<List<VideoWithCategories>?>(null) }
    var pendingSourceVideo by remember { mutableStateOf<VideoWithCategories?>(null) }

    CreateGroupDialog(
        visible = uiState.showCreateGroupDialog,
        groups = uiState.displayGroups,
        onDismiss = { onEvent(DownloadEvent.OnCreateGroupDialogChange(false)) },
        onConfirm = {
            onEvent(DownloadEvent.OnCreateGroup(it))
            onEvent(DownloadEvent.OnCreateGroupDialogChange(false))
        },
        onDeleteGroup = { onEvent(DownloadEvent.OnDeleteGroup(it)) },
    )

    GroupRenameDialog(
        header = pendingRename,
        groups = uiState.displayGroups,
        onDismiss = { pendingRename = null },
        onConfirm = { header, newName ->
            uiState.displayGroups.find { it.id == header.groupId }?.let { group ->
                onEvent(DownloadEvent.OnRenameGroup(group.id, newName))
            }
            pendingRename = null
        },
        onDelete = { header ->
            uiState.displayGroups.find { it.id == header.groupId }
                ?.let { onEvent(DownloadEvent.OnDeleteGroup(it)) }
            pendingRename = null
        },
    )

    if (pendingSourceVideo != null) SourceQualityDialog(
        title = pendingSourceVideo?.video?.title ?: "换源",
        currentQuality = pendingSourceVideo?.video?.quality.orEmpty(),
        qualities = pendingSourceVideo?.let { uiState.sourceRefresh.results[it.video.id]?.qualities.orEmpty() } ?: emptyList(),
        loading = pendingSourceVideo?.let { uiState.sourceRefresh.loadingVideoId == it.video.id } == true,
        onDismiss = { pendingSourceVideo = null },
        onSelect = { quality -> pendingSourceVideo?.let { onEvent(DownloadEvent.OnSelectSourceQuality(it, quality)) }; pendingSourceVideo = null },
    )

    MoveGroupDialog(
        video = pendingMoveVideo,
        groups = uiState.displayGroups,
        onDismiss = { pendingMoveVideo = null },
        onConfirm = { video, groupId ->
            onEvent(DownloadEvent.OnMoveVideoGroup(video, groupId))
            pendingMoveVideo = null
        },
    )

    val videoNodes = uiState.downloadedNodes.filterIsInstance<DownloadItemNode>()
        .distinctBy { it.data.video.id }
    val selectedVideos = videoNodes
        .filter { it.data.video.id in uiState.selectedVideoIds }
        .map { it.data }
    var batchBarHeight by remember { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) {
        if (uiState.multiSelectMode) batchBarHeight.toDp() + 8.dp else 8.dp
    }
    val onRenameGroup: (DownloadHeaderNode) -> Unit = { header ->
        if (header.groupId != DownloadGroupEntity.DEFAULT_GROUP_ID && !uiState.multiSelectMode) {
            pendingRename = header
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.downloadedNodes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyContent(
                        hint = stringResource(R.string.empty_content),
                        subHint = stringResource(R.string.downloaded),
                    )
                }
            }

            uiState.viewMode == DownloadedViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(rememberVideoGridColumns()),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = SpacingNormal, top = SpacingNormal,
                        end = SpacingNormal, bottom = bottomPadding + SpacingNormal,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    items(
                        items = uiState.downloadedNodes,
                        key = {
                            when (it) {
                                is DownloadHeaderNode -> "header-${it.groupId}"
                                is DownloadItemNode -> "item-${it.parentKey}-${it.data.video.id}"
                            }
                        },
                        span = { if (it is DownloadHeaderNode) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        contentType = { if (it is DownloadHeaderNode) "header" else "video" },
                    ) { node ->
                        when (node) {
                            is DownloadHeaderNode -> DownloadGroupHeader(
                                header = node,
                                onToggle = { onEvent(DownloadEvent.OnToggleGroup(node.groupId)) },
                                onRename = { onRenameGroup(node) },
                            )

                            is DownloadItemNode -> DownloadedGridCard(
                                item = node.data,
                                metadata = uiState.metadata[node.data.video.id],
                                onOpenVideo = { onEvent(DownloadEvent.OnOpenDownloadedVideo(node.data)) },
                                onLocalPlayback = { onEvent(DownloadEvent.OnLocalPlayback(node.data)) },
                                onExternalPlayback = { onEvent(DownloadEvent.OnExternalPlayback(node.data)) },
                                onDeleteVideo = { onEvent(DownloadEvent.OnDeleteDownloadedVideo(node.data)) },
                                onMoveGroup = { pendingMoveVideo = node.data },
                                enableLongPressMove = uiState.grouped,
                                isMultiSelect = uiState.multiSelectMode,
                                isSelected = node.data.video.id in uiState.selectedVideoIds,
                                onToggleSelect = { onEvent(DownloadEvent.OnToggleVideoSelection(node.data.video.id)) },
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.downloadedNodes, key = {
                        when (it) {
                            is DownloadHeaderNode -> "header-${it.groupId}"
                            is DownloadItemNode -> "item-${it.parentKey}-${it.data.video.id}"
                        }
                    }) { node ->
                        when (node) {
                            is DownloadHeaderNode -> DownloadGroupHeader(
                                header = node,
                                onToggle = { onEvent(DownloadEvent.OnToggleGroup(node.groupId)) },
                                onRename = { onRenameGroup(node) },
                            )

                            is DownloadItemNode -> DownloadedVideoCard(
                                item = node.data,
                                metadata = uiState.metadata[node.data.video.id],
                                onOpenVideo = { onEvent(DownloadEvent.OnOpenDownloadedVideo(node.data)) },
                                onLocalPlayback = { onEvent(DownloadEvent.OnLocalPlayback(node.data)) },
                                onExternalPlayback = { onEvent(DownloadEvent.OnExternalPlayback(node.data)) },
                                onDeleteVideo = { onEvent(DownloadEvent.OnDeleteDownloadedVideo(node.data)) },
                                onMoveGroup = { pendingMoveVideo = node.data },
                                onRefreshMetadata = { onEvent(DownloadEvent.OnRefreshMetadata(node.data)) },
                                 onRefreshSource = { onEvent(DownloadEvent.OnRefreshSource(node.data)) },
                                 onRequestSourceSwitch = {
                                      pendingSourceVideo = node.data
                                      onEvent(DownloadEvent.OnRequestSourceSwitch(node.data))
                                  },
                                 higherQualities = node.data.video.availableQualities.split(',').filter { quality ->
                                      val currentRank = Regex("\\d+").find(node.data.video.quality)?.value?.toIntOrNull() ?: 0
                                      val candidateRank = Regex("\\d+").find(quality)?.value?.toIntOrNull() ?: 0
                                      currentRank > 0 && candidateRank > currentRank
                                  },
                                  sourceRefreshedAt = node.data.video.sourceRefreshedAt,
                                 sourceRefreshing = uiState.sourceRefresh.loadingVideoId == node.data.video.id,
                                enableLongPressMove = uiState.grouped,
                                isMultiSelect = uiState.multiSelectMode,
                                isSelected = node.data.video.id in uiState.selectedVideoIds,
                                onToggleSelect = { onEvent(DownloadEvent.OnToggleVideoSelection(node.data.video.id)) },
                            )
                        }
                    }
                }
            }
        }

        if (uiState.multiSelectMode) {
            BatchActionBar(
                selectedCount = selectedVideos.size,
                totalCount = videoNodes.size,
                onToggleSelectAll = {
                    val allSelected = selectedVideos.size == videoNodes.size
                    videoNodes.filter { allSelected || it.data.video.id !in uiState.selectedVideoIds }
                        .forEach { onEvent(DownloadEvent.OnToggleVideoSelection(it.data.video.id)) }
                },
                onExitMultiSelect = { onEvent(DownloadEvent.OnToggleMultiSelect) },
                onDeleteSelected = {
                    if (selectedVideos.isNotEmpty()) pendingBatchDeleteVideos = selectedVideos
                },
                onMoveSelected = {
                    if (selectedVideos.isNotEmpty()) onEvent(DownloadEvent.OnBatchMoveRequest)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { batchBarHeight = it.height },
            )
        }
    }

    pendingBatchDeleteVideos?.let { videos ->
        ConfirmDialog(
            visible = true,
            title = stringResource(R.string.delete),
            message = stringResource(R.string.confirm_delete_videos, videos.size),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                onEvent(DownloadEvent.OnBatchDelete(videos))
                pendingBatchDeleteVideos = null
            },
            onDismiss = { pendingBatchDeleteVideos = null },
        )
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAllSelected = totalCount > 0 && selectedCount == totalCount
    val hasSelection = selectedCount > 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onExitMultiSelect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${selectedCount}/${totalCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onToggleSelectAll, enabled = totalCount > 0) {
                    Text(
                        text = if (isAllSelected) {
                            stringResource(R.string.deselect_all)
                        } else {
                            stringResource(R.string.select_all)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onMoveSelected,
                    enabled = hasSelection,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.move_group),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                TextButton(
                    onClick = onDeleteSelected,
                    enabled = hasSelection,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (hasSelection) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun DownloadedScreenPreview() {
    ComponentPreview {
        DownloadedScreen(
            uiState = DownloadUiState(
                downloadedNodes = fakeDownloadedNodes,
                displayGroups = fakeDownloadedGroups,
                multiSelectMode = true,
            ),
            listState = rememberLazyListState(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun DownloadedScreenEmptyPreview() {
    ComponentPreview {
        DownloadedScreen(
            uiState = DownloadUiState(
                downloadedNodes = emptyList(),
                displayGroups = emptyList(),
                multiSelectMode = true,
            ),
            listState = rememberLazyListState(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 900)
@Composable
private fun DownloadedGridMultiSelectPreview() {
    ComponentPreview {
        DownloadedScreen(
            uiState = DownloadUiState(
                downloadedNodes = fakeDownloadedNodes,
                displayGroups = fakeDownloadedGroups,
                viewMode = DownloadedViewMode.GRID,
                multiSelectMode = true,
                selectedVideoIds = fakeDownloadedNodes.filterIsInstance<DownloadItemNode>()
                    .take(1).map { it.data.video.id }.toSet(),
            ),
            listState = rememberLazyListState(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun BatchActionBarPreview() {
    ComponentPreview {
        BatchActionBar(
            selectedCount = 10,
            totalCount = 19,
            onToggleSelectAll = { },
            onExitMultiSelect = { },
            onDeleteSelected = { },
            onMoveSelected = { }
        )
    }
}
