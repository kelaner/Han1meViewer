package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.yenaly_libs.utils.formatFileSizeV2

@Composable
fun DownloadedGridCard(
    item: VideoWithCategories,
    metadata: DownloadedMetadata?,
    onOpenVideo: () -> Unit,
    onLocalPlayback: () -> Unit,
    onExternalPlayback: () -> Unit,
    onDeleteVideo: () -> Unit,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onMoveGroup: () -> Unit = {},
) {
    Surface(
        onClick = if (isMultiSelect) onToggleSelect else onOpenVideo,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = if (isMultiSelect && isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                AsyncImage(
                    model = item.video.coverUri ?: item.video.coverUrl,
                    contentDescription = item.video.title,
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                if (isMultiSelect) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.semantics { contentDescription = item.video.title },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(SpacingNormal),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(item.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metadata?.displayAuthor ?: stringResource(R.string.download_unknown_author), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.video.quality} · ${item.video.length.formatFileSizeV2()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isMultiSelect) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = onMoveGroup) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move_group))
                        }
                        IconButton(onClick = onDeleteVideo) {
                            Icon(painterResource(R.drawable.ic_baseline_delete_24), stringResource(R.string.delete))
                        }
                        IconButton(onClick = onExternalPlayback) {
                            Icon(painterResource(R.drawable.ic_ext_link), stringResource(R.string.ext_player))
                        }
                        IconButton(onClick = onLocalPlayback) {
                            Icon(painterResource(R.drawable.ic_baseline_play_arrow_24), stringResource(R.string.local_playback))
                        }
                    }
                }
            }
        }
    }
}
