package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.DatabaseRepo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Download status for any single-video cover, kept separate from its played marker. */
@Composable
fun DownloadedBadge(videoCode: String, modifier: Modifier = Modifier) {
    DownloadedBadge(isDownloaded = rememberDownloadedStatus(videoCode), modifier = modifier)
}

@Composable
internal fun DownloadedBadge(isDownloaded: Boolean, modifier: Modifier = Modifier) {
    if (isDownloaded) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_download_24),
            contentDescription = stringResource(R.string.downloaded),
            tint = Color.White,
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .padding(3.dp)
                .size(14.dp),
        )
    }
}

@Composable
internal fun rememberDownloadedStatus(videoCode: String): Boolean {
    if (LocalInspectionMode.current || videoCode.isBlank()) return false
    val status = remember(videoCode) {
        DatabaseRepo.HanimeDownload.downloadedVideoCodes
            .map { videoCode in it }
            .distinctUntilChanged()
    }
    val isDownloaded by status.collectAsStateWithLifecycle(initialValue = false)
    return isDownloaded
}
