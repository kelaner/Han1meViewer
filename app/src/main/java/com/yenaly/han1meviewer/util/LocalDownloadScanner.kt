package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.HanimeResolution
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Read-only entry shared by the private-file and SAF adapters. No create-on-read helpers. */
internal interface LocalDownloadEntry {
    val name: String?
    val uri: String
    val isDirectory: Boolean
    val isFile: Boolean
    val length: Long
    fun listFiles(): List<LocalDownloadEntry>
    fun openInputStream(): InputStream?
}

private val numericVideoCode = Regex("[0-9]+")
private val videoExtensions = setOf("mp4", "mkv", "avi", "flv", "mov", "webm", "mpeg", "3gp", "3g2", "ogv", "ts")
private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
private val videoQuality = Regex("""_(\d+P)\.[^.]+$""", RegexOption.IGNORE_CASE)

/** Serial scan: one directory snapshot per item and at most one open stream at a time. */
internal suspend fun importLocalDownloadFolders(
    folders: List<LocalDownloadEntry>,
    existingDownloads: List<HanimeDownloadEntity>,
    insert: suspend (HanimeDownloadEntity) -> Unit,
    update: suspend (HanimeDownloadEntity) -> Unit,
    insertIfUnchanged: (suspend (restored: HanimeDownloadEntity) -> Boolean)? = null,
    updateIfUnchanged: (suspend (restored: HanimeDownloadEntity, previous: HanimeDownloadEntity) -> Boolean)? = null,
    onItemProgress: (suspend (completed: Int, total: Int, failed: Int, skipped: Int, currentName: String?) -> Unit)? = null,
    onProgress: ((processed: Int, total: Int, currentName: String?) -> Unit)? = null,
): SafFileManager.ImportResult {
    val existingByCode = existingDownloads.groupBy { it.videoCode }.toMutableMap()
    val failures = mutableListOf<SafFileManager.ImportFailure>()
    var successCount = 0
    var skippedCount = 0
    onItemProgress?.invoke(0, folders.size, 0, 0, null)
    for ((index, folder) in folders.withIndex()) {
        coroutineContext.ensureActive()
        var currentName: String? = null
        var cancelled = false
        try {
            if (!folder.isDirectory) {
                skippedCount++
                continue
            }
            val code = folder.name
            currentName = code
            if (code == null || !numericVideoCode.matches(code)) {
                failures += SafFileManager.ImportFailure(code ?: "?", SafFileManager.ImportFailureReason.INVALID_FOLDER_NAME)
                continue
            }
            val existing = existingByCode[code].orEmpty()
            // Queued, paused, failed and downloading rows all belong to the download manager.
            if (existing.any { it.state != DownloadState.Finished }) {
                skippedCount++
                continue
            }
            val files = folder.listFiles()
            val video = files.firstOrNull {
                it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in videoExtensions && it.length > 0L
            }
            if (video == null) {
                failures += SafFileManager.ImportFailure(code, SafFileManager.ImportFailureReason.MISSING_OR_EMPTY_VIDEO)
                continue
            }
            currentName = video.name ?: code
            val videoLength = video.length
            // Prove that a provider's non-zero cached size represents readable data.
            if (videoLength <= 0L || video.openInputStream()?.use { it.read() >= 0 } != true) {
                failures += SafFileManager.ImportFailure(code, SafFileManager.ImportFailureReason.MISSING_OR_EMPTY_VIDEO)
                continue
            }
            val info = files.firstOrNull { it.isFile && it.name == "info.json" }
            val metadata = info?.openInputStream()?.use { readLocalDownloadMetadata(it) }
            if (metadata == null) {
                failures += SafFileManager.ImportFailure(code, SafFileManager.ImportFailureReason.MISSING_INFO_JSON)
                continue
            }
            val cover = files.firstOrNull {
                it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in imageExtensions && it.length > 0L
            }
            val quality = videoQuality.find(video.name.orEmpty())?.groupValues?.get(1)?.uppercase()
                ?: HanimeResolution.RES_UNKNOWN
            val previous = existing.firstOrNull { it.quality.equals(quality, ignoreCase = true) }
                ?: existing.singleOrNull()?.takeIf {
                    quality == HanimeResolution.RES_UNKNOWN ||
                        it.quality.equals(HanimeResolution.RES_UNKNOWN, ignoreCase = true) || it.quality == "unknow"
                }
            coroutineContext.ensureActive()
            if (previous != null) {
                val restored = previous.copy(
                    videoUri = video.uri,
                    coverUri = cover?.uri ?: previous.coverUri,
                    length = videoLength,
                    downloadedLength = videoLength,
                    author = metadata.author,
                    genre = metadata.genre,
                    uploadTime = metadata.uploadTime,
                    metadataResolved = true,
                )
                val applied = if (updateIfUnchanged != null) {
                    updateIfUnchanged(restored, previous)
                } else {
                    if (restored != previous) update(restored)
                    true
                }
                if (!applied) {
                    skippedCount++
                    continue
                }
                existingByCode[code] = existing.map { if (it.id == previous.id) restored else it }
            } else {
                val restored = HanimeDownloadEntity(
                    title = metadata.title,
                    coverUrl = metadata.coverUrl,
                    coverUri = cover?.uri,
                    addDate = System.currentTimeMillis(),
                    videoCode = code,
                    videoUri = video.uri,
                    quality = quality,
                    videoUrl = metadata.videoUrls[quality].orEmpty(),
                    length = videoLength,
                    downloadedLength = videoLength,
                    state = DownloadState.Finished,
                    author = metadata.author,
                    genre = metadata.genre,
                    uploadTime = metadata.uploadTime,
                    metadataResolved = true,
                )
                val applied = if (insertIfUnchanged != null) {
                    insertIfUnchanged(restored)
                } else {
                    insert(restored)
                    true
                }
                if (!applied) {
                    skippedCount++
                    continue
                }
                existingByCode[code] = existing + restored
            }
            successCount++
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (_: Exception) {
            failures += SafFileManager.ImportFailure(currentName ?: "?", SafFileManager.ImportFailureReason.IMPORT_EXCEPTION)
        } finally {
            // A cancelled operation must not dispatch a final progress event as if it completed.
            if (!cancelled) {
                coroutineContext.ensureActive()
                onProgress?.invoke(index + 1, folders.size, currentName)
                onItemProgress?.invoke(index + 1, folders.size, failures.size, skippedCount, currentName)
            }
        }
    }
    return SafFileManager.ImportResult(successCount, failures, skippedCount)
}
