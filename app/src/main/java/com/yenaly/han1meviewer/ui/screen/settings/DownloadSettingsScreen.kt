package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

data class DownloadSettingsUiState(
    val downloadPathSummary: String,
    val downloadCountLimit: Int,
    val downloadCountLimitSummary: String,
    val downloadSpeedLimitIndex: Int,
    val downloadSpeedLimitSummary: String,
    val downloadAutoGroup: Boolean = true,
    val defaultGrouped: Boolean = true,
    val defaultDownloadView: String = "LIST",
)

@Composable
fun DownloadSettingsScreen(
    state: DownloadSettingsUiState,
    maxDownloadCountLimit: Int,
    maxDownloadSpeedLimitIndex: Int,
    onOpenDownloadPath: () -> Unit,
    onRestoreDefaultPath: () -> Unit,
    onImportDownloadedFiles: () -> Unit,
    onRestoreLocalDownloads: () -> Unit,
    onCleanRedundantLocalFiles: () -> Unit,
    onClearInvalidLocalRecords: () -> Unit,
    onRepairLocalCovers: () -> Unit,
    onDownloadCountLimitChange: (Int) -> Unit,
    onDownloadSpeedLimitChange: (Int) -> Unit,
    onDownloadAutoGroupChange: (Boolean) -> Unit,
    onDefaultGroupedChange: (Boolean) -> Unit = {},
    onDefaultDownloadViewChange: (String) -> Unit = {},
) {
    var showDefaultViewDialog by rememberSaveable { mutableStateOf(false) }
    ChoiceDialog(
        title = "下载页默认视图类型",
        options = listOf("列表" to "LIST", "多列" to "GRID"),
        selectedValue = state.defaultDownloadView,
        visible = showDefaultViewDialog,
        onDismiss = { showDefaultViewDialog = false },
        onSelect = { onDefaultDownloadViewChange(it); showDefaultViewDialog = false },
    )
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SettingNavigationItem(
                title = stringResource(R.string.download_path),
                summary = state.downloadPathSummary,
                iconRes = R.drawable.baseline_path_24,
                onClick = onOpenDownloadPath,
            )
        }

        item {
            SettingSwitchItem(
                title = "默认是否展示分组",
                summary = "进入下载页时默认显示分组视图",
                checked = state.defaultGrouped,
                iconRes = R.drawable.ic_baseline_fold_24,
                onCheckedChange = onDefaultGroupedChange,
            )
        }

        item {
            SettingNavigationItem(
                title = stringResource(R.string.pref_export_downloads_title),
                summary = stringResource(R.string.pref_export_downloads_summary),
                iconRes = R.drawable.baseline_export_24,
                onClick = onImportDownloadedFiles,
            )
        }

        item {
            SettingNavigationItem(
                title = "恢复下载项",
                summary = "扫描本地下载文件并恢复到下载列表",
                iconRes = R.drawable.baseline_path_24,
                onClick = onRestoreLocalDownloads,
            )
        }

        item {
            SettingNavigationItem(
                title = "清除本地下载冗余",
                summary = "清除不属于下载列表的已识别下载文件，保留未知文件",
                iconRes = R.drawable.baseline_remove_circle_24,
                onClick = onCleanRedundantLocalFiles,
            )
        }

        item {
            SettingNavigationItem(
                title = "清空无效下载项",
                summary = "移除已完成列表中视频缺失或大小为 0 的记录",
                iconRes = R.drawable.baseline_remove_circle_24,
                onClick = onClearInvalidLocalRecords,
            )
        }

        item {
            SettingNavigationItem(
                title = "更新失效封面项",
                summary = "重新生成或补全本地下载封面",
                iconRes = R.drawable.baseline_path_24,
                onClick = onRepairLocalCovers,
            )
        }

        item {
            SettingNavigationItem(
                title = "下载页默认视图类型",
                summary = if (state.defaultDownloadView == "GRID") "多列" else "列表",
                iconRes = R.drawable.baseline_grid_24,
                onClick = { showDefaultViewDialog = true },
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_count_limit),
                summary = state.downloadCountLimitSummary,
                value = state.downloadCountLimit,
                valueRange = 0..maxDownloadCountLimit,
                iconRes = R.drawable.baseline_count_24,
                onValueChange = onDownloadCountLimitChange,
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_speed_limit),
                summary = state.downloadSpeedLimitSummary,
                value = state.downloadSpeedLimitIndex,
                valueRange = 0..maxDownloadSpeedLimitIndex,
                iconRes = R.drawable.baseline_speed2_24,
                onValueChange = onDownloadSpeedLimitChange,
            )
        }

        item {
            SettingSwitchItem(
                title = stringResource(R.string.download_auto_group),
                summary = stringResource(R.string.download_auto_group_summary),
                checked = state.downloadAutoGroup,
                iconRes = R.drawable.ic_baseline_fold_24,
                onCheckedChange = onDownloadAutoGroupChange,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadSettingsScreenPreview() {
    ComponentPreview {
        DownloadSettingsScreen(
            state = DownloadSettingsUiState(
                downloadPathSummary = "/storage/emulated/0/Android/data/.../files",
                downloadCountLimit = 2,
                downloadCountLimitSummary = "2",
                downloadSpeedLimitIndex = 0,
                downloadSpeedLimitSummary = "无限制",
                downloadAutoGroup = true,
            ),
            maxDownloadCountLimit = 10,
            maxDownloadSpeedLimitIndex = 5,
            onOpenDownloadPath = {},
            onRestoreDefaultPath = {},
            onImportDownloadedFiles = {},
            onRestoreLocalDownloads = {},
            onCleanRedundantLocalFiles = {},
            onClearInvalidLocalRecords = {},
            onRepairLocalCovers = {},
            onDownloadCountLimitChange = {},
            onDownloadSpeedLimitChange = {},
            onDownloadAutoGroupChange = {},
        )
    }
}
