package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadSourceUpdateDialog(state: DownloadBatchRefreshState, onDismiss: () -> Unit) {
    if (!state.isRunning && !state.isComplete) return
    AlertDialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        title = { Text(if (state.isRunning) "一键更新" else "更新结果") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isRunning) {
                    Text("正在检查低于 1080p 的已下载视频，检测是否有更高清的视频源。")
                    state.currentTitle?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (state.total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在读取下载记录…")
                    } else {
                        LinearProgressIndicator(
                            progress = { state.processed.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("已处理 ${state.processed}/${state.total}（${state.processed * 100 / state.total}%）")
                        Text("发现更高清源 ${state.higherQualityCount} 项，失败 ${state.failures.size} 项")
                    }
                } else {
                    when {
                        state.error != null -> Text("更新未完成：${state.error}")
                        state.total == 0 -> Text("没有需要更新的视频：仅检查已下载且分辨率低于 1080p 的视频，1080p 及以上不参与。")
                        else -> {
                            Text("已完成 ${state.processed}/${state.total} 项检查。")
                            Text("发现更高清源：${state.higherQualityCount} 项")
                            Text("暂无更高清源：${state.processed - state.higherQualityCount - state.failures.size} 项")
                            Text("更新失败：${state.failures.size} 项")
                            Text("已保存可用画质。可在对应视频的画质切换入口选择更高清源。")
                        }
                    }
                    state.failures.take(30).forEach { Text(it) }
                    if (state.failures.size > 30) Text("仅展示前 30 项失败详情，其余已计入总数。")
                }
            }
        },
        confirmButton = {
            if (!state.isRunning) TextButton(onClick = onDismiss) { Text("确定") }
        },
    )
}
