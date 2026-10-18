package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R

/** Width includes the navigation and title area; each action keeps a 48 dp touch target. */
internal fun downloadDirectActionCount(widthDp: Int, actionCount: Int): Int {
    val slots = ((widthDp - 144).coerceAtLeast(0) / 48)
    return if (slots >= actionCount) actionCount else (slots - 1).coerceAtLeast(0)
}

private data class DownloadAction(
    val label: String,
    val icon: Int,
    val enabled: Boolean = true,
    val run: () -> Unit,
)

@Composable
internal fun DownloadToolbar(
    width: Dp,
    viewMode: DownloadedViewMode,
    grouped: Boolean,
    multiSelectMode: Boolean,
    genres: List<String>,
    onToggleView: () -> Unit,
    onSort: (DownloadSort) -> Unit,
    onGenre: (String?) -> Unit,
    onToggleGrouping: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onSearch: () -> Unit,
    onCreateGroup: () -> Unit,
    onReadDirectory: () -> Unit,
    onUpdateSources: () -> Unit,
    updateEnabled: Boolean,
    updating: Boolean,
) {
    var menu by remember { mutableStateOf<String?>(null) }
    val actions = listOf(
        DownloadAction(if (viewMode == DownloadedViewMode.LIST) "切换多列视图" else "切换列表视图",
            if (viewMode == DownloadedViewMode.LIST) R.drawable.baseline_grid_24 else R.drawable.baseline_format_list_bulleted_24, run = onToggleView),
        DownloadAction("排序", R.drawable.baseline_sort_24) { menu = "sort" },
        DownloadAction("类型分类", R.drawable.baseline_advanced_search_24) { menu = "genre" },
        DownloadAction(if (grouped) "取消分组展示" else "分组展示", R.drawable.ic_baseline_fold_24, run = onToggleGrouping),
        DownloadAction(if (multiSelectMode) "退出多选" else "多选操作", R.drawable.baseline_edit_24, run = onToggleMultiSelect),
        DownloadAction("搜索", R.drawable.ic_baseline_search_24, run = onSearch),
        DownloadAction("创建新分组", R.drawable.baseline_add_24, run = onCreateGroup),
        DownloadAction(if (updating) "更新中" else "一键更新", R.drawable.ic_baseline_update_24,
            enabled = updateEnabled, run = onUpdateSources),
        DownloadAction("读取下载目录", R.drawable.ic_baseline_download_24, run = onReadDirectory),
    )
    val directCount = downloadDirectActionCount(width.value.toInt(), actions.size)
    Box {
        Row {
            actions.take(directCount).forEach { action ->
                IconButton(onClick = action.run, enabled = action.enabled, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(action.icon), contentDescription = action.label)
                }
            }
            if (directCount < actions.size) {
                IconButton(onClick = { menu = "more" }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
            }
        }
        DropdownMenu(expanded = menu == "more", onDismissRequest = { menu = null }) {
            actions.drop(directCount).forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, enabled = action.enabled, onClick = {
                    menu = null
                    action.run()
                }, leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) })
            }
        }
        DropdownMenu(expanded = menu == "sort", onDismissRequest = { menu = null }) {
            DownloadSort.entries.forEach { option ->
                DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(option.labelRes)) },
                    onClick = { onSort(option); menu = null })
            }
        }
        DropdownMenu(expanded = menu == "genre", onDismissRequest = { menu = null }) {
            DropdownMenuItem(text = { Text("全部类型") }, onClick = { onGenre(null); menu = null })
            genres.forEach { genre ->
                DropdownMenuItem(text = { Text(genre) }, onClick = { onGenre(genre); menu = null })
            }
        }
    }
}
