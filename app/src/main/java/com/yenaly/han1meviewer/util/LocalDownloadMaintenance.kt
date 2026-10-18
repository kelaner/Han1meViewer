package com.yenaly.han1meviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkManager
import com.yenaly.han1meviewer.APP_NAME
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.worker.HanimeDownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Maintenance never treats an inaccessible file as a missing one. */
object LocalDownloadMaintenance {
    private const val MAX_COVER_BYTES = 10 * 1024 * 1024
    private val numericVideoDirectory = Regex("\\d+")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "flv", "mov", "webm", "mpeg", "3gp", "3g2", "ogv", "ts")
    private val repairedCoverName = Regex("repair_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")

    /** Completed includes failed and skipped candidates; discovery may increase total. */
    data class Progress(
        val total: Int = 0,
        val completed: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val currentName: String? = null,
    ) {
        val success: Int get() = completed - failed - skipped
        fun summary(): String = "总数 $total，已完成 $completed（含失败、跳过），失败 $failed，跳过 $skipped"
    }

    private enum class Outcome { SUCCESS, FAILED, SKIPPED }

    private class Counter(private val onProgress: suspend (Progress) -> Unit) {
        var progress = Progress()
            private set

        suspend fun discover(count: Int) {
            progress = progress.copy(total = progress.total + count)
            onProgress(progress)
        }

        suspend fun finish(outcome: Outcome, name: String?) {
            coroutineContext.ensureActive()
            progress = progress.copy(
                completed = progress.completed + 1,
                failed = progress.failed + if (outcome == Outcome.FAILED) 1 else 0,
                skipped = progress.skipped + if (outcome == Outcome.SKIPPED) 1 else 0,
                currentName = name,
            )
            onProgress(progress)
        }
    }

    suspend fun restore(
        context: Context,
        onProgress: suspend (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        var progress = Progress()
        onProgress(progress)
        try {
            val result = SafFileManager.scanAndImportHanimeDownloads(
                context,
                DownloadDatabase.instance.hanimeDownloadDao,
                onItemProgress = { completed, total, failed, skipped, name ->
                    progress = Progress(total, completed, failed, skipped, name)
                    onProgress(progress)
                },
            )
            "恢复完成：${progress.summary()}，成功 ${result.successCount}"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "恢复中止：${progress.summary()}；无法读取下载目录或数据库，请检查目录权限"
        }
    }

    suspend fun cleanRedundant(
        context: Context,
        onProgress: suspend (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.instance.hanimeDownloadDao
        val counter = Counter(onProgress)
        counter.discover(0)
        try {
            // Workers create files before inserting their DB row. Their public tag has no video-code mapping.
            if (hasActiveWork(context)) return@withContext "清理已跳过：${counter.progress.summary()}；存在下载任务，请暂停任务后重试"
            val folders = children(context, downloadRoot(context))
            counter.discover(folders.size)
            for (folder in folders) {
                coroutineContext.ensureActive()
                var outcome = Outcome.SKIPPED
                var cancelled = false
                var currentName: String? = null
                try {
                    val code = folder.name
                    currentName = code
                    if (!folder.isDirectory || code == null || !numericVideoDirectory.matches(code)) continue
                    val files = children(context, folder)
                    // A numeric name alone is not evidence that a directory belongs to this app.
                    val info = files.firstOrNull { it.name == "info.json" && it.isFile }
                    val metadata = info?.let { doc ->
                        openInput(context, doc.uri)?.use { readLocalDownloadMetadata(it) }
                    }
                    if (metadata == null || metadata.title.isBlank() || metadata.videoUrls.isEmpty()) continue
                    // Resolve ordering before registering children, so discovery errors only fail the directory.
                    val orderedFiles = files.sortedBy { it.name == "info.json" }
                    // Count the directory itself and every direct child; retained items are skipped.
                    counter.discover(orderedFiles.size)
                    for (file in orderedFiles) {
                        coroutineContext.ensureActive()
                        var fileOutcome = Outcome.SKIPPED
                        var fileName: String? = null
                        try {
                            fileName = file.name
                            if (!file.isFile || !isDownloadFileName(fileName.orEmpty(), metadata)) continue
                            // Re-read before each deletion to protect newly queued tasks and every DB state.
                            val entities = dao.getAll()
                            val references = entities.flatMap { listOfNotNull(it.videoUri, it.coverUri) }
                            if (hasActiveWork(context) || entities.any { it.videoCode == code } ||
                                references.any { referencesFolder(folder.uri, it) || sameFile(file.uri, it) }) continue
                            fileOutcome = if (file.delete()) Outcome.SUCCESS else Outcome.FAILED
                        } catch (e: CancellationException) {
                            cancelled = true
                            throw e
                        } catch (_: Exception) {
                            fileOutcome = Outcome.FAILED
                        } finally {
                            if (!cancelled) counter.finish(fileOutcome, fileName)
                        }
                    }
                    // File.delete is non-recursive even if another writer adds a file after this check.
                    // SAF deletion can recurse, so empty SAF directories must remain.
                    if (folder.uri.scheme == "file" && children(context, folder).isEmpty()) {
                        val entities = dao.getAll()
                        if (!hasActiveWork(context) && entities.none { it.videoCode == code } &&
                            entities.none { referencesFolder(folder.uri, it.videoUri) || referencesFolder(folder.uri, it.coverUri.orEmpty()) }) {
                            outcome = if (File(requireNotNull(folder.uri.path)).delete()) Outcome.SUCCESS else Outcome.FAILED
                        }
                    }
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                } catch (_: Exception) {
                    outcome = Outcome.FAILED
                } finally {
                    if (!cancelled) counter.finish(outcome, currentName)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext "清理中止：${counter.progress.summary()}；无法读取下载目录或数据库"
        }
        "清理完成：${counter.progress.summary()}，删除 ${counter.progress.success}（未知内容及SAF目录保留）"
    }

    suspend fun clearInvalid(
        context: Context,
        onProgress: suspend (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.instance.hanimeDownloadDao
        val counter = Counter(onProgress)
        counter.discover(0)
        val folderCache = mutableMapOf<String, List<DocumentFile>?>()
        try {
            val roots = knownRoots(context)
            val entities = dao.getAll()
            counter.discover(entities.size)
            for (entity in entities) {
                coroutineContext.ensureActive()
                var outcome = Outcome.SKIPPED
                var cancelled = false
                try {
                    if (entity.state != DownloadState.Finished || hasActiveWork(context) ||
                        videoPresent(context, entity, roots, folderCache) != false) continue
                    val current = dao.find(entity.videoCode, entity.quality)
                    if (current != entity) continue
                    dao.delete(entity)
                    outcome = Outcome.SUCCESS
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                } catch (_: Exception) {
                    outcome = Outcome.FAILED
                } finally {
                    if (!cancelled) counter.finish(outcome, entity.title)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext "清除中止：${counter.progress.summary()}；无法读取下载目录或数据库"
        }
        "清除完成：${counter.progress.summary()}，删除 ${counter.progress.success}（权限/I/O未知的记录保留）"
    }

    suspend fun repairCovers(
        context: Context,
        onProgress: suspend (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.instance.hanimeDownloadDao
        val counter = Counter(onProgress)
        counter.discover(0)
        try {
            val entities = dao.getAll()
            counter.discover(entities.size)
            for (entity in entities) {
                coroutineContext.ensureActive()
                var outcome = Outcome.SKIPPED
                var cancelled = false
                try {
                    if (entity.state != DownloadState.Finished || coverReadable(context, entity.coverUri)) continue
                    val video = availableVideo(context, entity) ?: continue
                    val folder = parent(context, video) ?: continue
                    // Prefer an existing, decodable local source before using the network.
                    val files = children(context, folder)
                    val localCover = files.firstOrNull {
                        it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp") &&
                                coverReadable(context, it.uri.toString())
                    }
                    val uri = localCover?.uri ?: run {
                        val bytes = try {
                            val url = entity.coverUrl.ifBlank {
                                val info = files.firstOrNull { it.name == "info.json" && it.isFile }
                                info?.let { openInput(context, it.uri)?.use { input -> readLocalDownloadMetadata(input).coverUrl } }.orEmpty()
                            }
                            downloadImage(url)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        } ?: videoFrame(context, video) ?: run { outcome = Outcome.FAILED; continue }
                        writeCover(context, folder, bytes)
                    }
                    val current = dao.find(entity.videoCode, entity.quality)
                    if (current != entity) continue
                    outcome = if (dao.update(entity.copy(coverUri = uri.toString())) > 0) Outcome.SUCCESS else Outcome.FAILED
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                } catch (_: Exception) {
                    outcome = Outcome.FAILED
                } finally {
                    if (!cancelled) counter.finish(outcome, entity.title)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext "封面修复中止：${counter.progress.summary()}；无法读取下载目录或数据库"
        }
        "封面修复完成：${counter.progress.summary()}，修复 ${counter.progress.success}"
    }

    internal fun isDownloadFileName(name: String, metadata: LocalDownloadMetadata): Boolean {
        if (metadata.title.isBlank() || metadata.videoUrls.isEmpty()) return false
        return name == "info.json" || name == ".nomedia" || repairedCoverName.matches(name) ||
                setOf("png", "jpg", "jpeg", "webp").any { name == HFileManager.createVideoCoverName(metadata.title, it) } ||
                metadata.videoUrls.keys.any { quality ->
                    videoExtensions.any { name == HFileManager.createVideoName(metadata.title, quality, it) }
                }
    }

    private fun hasActiveWork(context: Context): Boolean = WorkManager.getInstance(context)
        .getWorkInfosByTag(HanimeDownloadWorker.TAG).get().any { !it.state.isFinished }

    private suspend fun downloadImage(url: String): ByteArray? {
        if (url.isBlank()) return null
        return ServiceCreator.downloadClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body
            if (body.contentLength() > MAX_COVER_BYTES) return@use null
            val bytes = body.byteStream().use { readLimited(it, MAX_COVER_BYTES) }
            bytes.takeIf(::decodableCover)
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > limit - output.size()) throw IOException("File exceeds maintenance limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodableCover(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) return false
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > 1024 || bounds.outHeight / options.inSampleSize > 1024) {
            options.inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return false
        bitmap.recycle()
        return true
    }

    private fun coverReadable(context: Context, uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return try {
            openInput(context, uri.toUri())?.use { decodableCover(readLimited(it, MAX_COVER_BYTES)) } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun videoFrame(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
            } else {
                // Older APIs cannot bound frame decoding: avoid allocating a full-resolution video frame.
                null
            } ?: return null
            try {
                ByteArrayOutputStream().use { output ->
                    if (!frame.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
                    output.toByteArray().takeIf { it.size <= MAX_COVER_BYTES && decodableCover(it) }
                }
            } finally {
                frame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    private fun writeCover(context: Context, folder: DocumentFile, bytes: ByteArray): Uri {
        // New names avoid overwriting a valid file and invalidate Coil's URI cache.
        val name = "repair_${UUID.randomUUID()}.png"
        val target = if (folder.uri.scheme == "file") {
            DocumentFile.fromFile(File(requireNotNull(folder.uri.path), name))
        } else {
            val uri = DocumentsContract.createDocument(context.contentResolver, folder.uri, "image/png", name)
                ?: throw IOException("Cannot create cover")
            DocumentFile.fromSingleUri(context, uri) ?: throw IOException("Invalid cover document")
        }
        try {
            if (target.uri.scheme == "file") {
                File(requireNotNull(target.uri.path)).outputStream().use { it.write(bytes); it.fd.sync() }
            } else {
                val output = context.contentResolver.openOutputStream(target.uri, "w") ?: throw IOException("Cannot open cover")
                output.use { it.write(bytes) }
            }
            if (!coverReadable(context, target.uri.toString())) throw IOException("Cover verification failed")
            return target.uri
        } catch (e: Exception) {
            runCatching { target.delete() }
            throw e
        }
    }

    /** true = non-empty, false = confirmed missing/empty, null = unknown. */
    private fun readableNonEmpty(context: Context, uri: Uri): Boolean? {
        return try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                    ?: return null
                cursor.use { if (!it.moveToFirst()) return false }
            } else if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: return null)
                var candidate = file
                while (!candidate.exists()) {
                    val directory = candidate.parentFile ?: return null
                    val names = directory.list()
                    if (names != null) return if (candidate.name in names) null else false
                    candidate = directory
                }
                if (!file.isFile) return null
            } else return null
            openInput(context, uri)?.use { it.read() >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun videoPresent(
        context: Context,
        entity: HanimeDownloadEntity,
        roots: List<List<DocumentFile>?>,
        cache: MutableMap<String, List<DocumentFile>?>,
    ): Boolean? {
        if (entity.videoUri.isNotBlank()) {
            val direct = readableNonEmpty(context, entity.videoUri.toUri())
            if (direct != false) return direct
        }
        var unknown = false
        for (folders in roots) {
            if (folders == null) { if (entity.videoUri.isBlank()) unknown = true; continue }
            val folder = folders.firstOrNull { it.name == entity.videoCode && it.isDirectory } ?: continue
            val key = folder.uri.toString()
            if (key !in cache) cache[key] = try { children(context, folder) } catch (_: Exception) { null }
            val files = cache[key]
            if (files == null) { unknown = true; continue }
            for (file in files) {
                if (!file.isFile || file.name?.substringAfterLast('.', "")?.lowercase() !in videoExtensions) continue
                when (readableNonEmpty(context, file.uri)) {
                    true -> return true
                    null -> unknown = true
                    false -> Unit
                }
            }
        }
        return if (unknown) null else false
    }

    /** Enumerate each known storage once; inaccessible locations remain unknown, never empty. */
    private fun knownRoots(context: Context): List<List<DocumentFile>?> {
        val roots = mutableListOf<List<DocumentFile>?>()
        val bases = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, APP_NAME) },
            if (!Preferences.isUsePrivateStorage && Preferences.safDownloadPath.isNullOrBlank()) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_NAME)
            } else null,
        ).distinctBy { it.absolutePath }
        for (base in bases) {
            val root = File(base, HFileManager.HANIME_DOWNLOAD_FOLDER)
            roots += try {
                val uri = root.toUri()
                if (readableNonEmpty(context, uri) == false) emptyList() else children(context, DocumentFile.fromFile(root))
            } catch (_: Exception) { null }
        }
        SafFileManager.getSavedUri()?.let { uri ->
            roots += try {
                val tree = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Invalid tree")
                val root = children(context, tree).firstOrNull { it.name == HFileManager.HANIME_DOWNLOAD_FOLDER && it.isDirectory }
                if (root == null) emptyList() else children(context, root)
            } catch (_: Exception) { null }
        }
        return roots
    }

    private fun availableVideo(context: Context, entity: HanimeDownloadEntity): Uri? {
        if (entity.videoUri.isNotBlank()) {
            val uri = entity.videoUri.toUri()
            if (readableNonEmpty(context, uri) == true) return uri
        }
        val folder = children(context, downloadRoot(context)).firstOrNull { it.name == entity.videoCode && it.isDirectory } ?: return null
        return children(context, folder).firstOrNull {
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in videoExtensions && readableNonEmpty(context, it.uri) == true
        }?.uri
    }

    private fun openInput(context: Context, uri: Uri): InputStream? = if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri)
    } else {
        File(requireNotNull(uri.path)).inputStream()
    }

    private fun downloadRoot(context: Context): DocumentFile {
        if (Preferences.isUsePrivateStorage || Preferences.safDownloadPath.isNullOrBlank()) {
            return DocumentFile.fromFile(File(HFileManager.getAppDownloadFolder(context), HFileManager.HANIME_DOWNLOAD_FOLDER))
        }
        val tree = DocumentFile.fromTreeUri(context, requireNotNull(SafFileManager.getSavedUri())) ?: throw IOException("Invalid tree")
        return children(context, tree).firstOrNull { it.name == HFileManager.HANIME_DOWNLOAD_FOLDER && it.isDirectory }
            ?: throw IOException("Download folder unavailable")
    }

    /** DocumentFile.listFiles swallows provider failures; query explicitly so an error cannot mean empty. */
    private fun children(context: Context, folder: DocumentFile): List<DocumentFile> {
        val uri = folder.uri
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            if (file.canonicalFile != file.absoluteFile) throw IOException("Symbolic download path")
            return (file.listFiles() ?: throw IOException("Cannot list download folder")).map { DocumentFile.fromFile(it) }
        }
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        val cursor = context.contentResolver.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?: throw IOException("Cannot query download folder")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val child = DocumentsContract.buildDocumentUriUsingTree(uri, it.getString(0))
                    val document = DocumentFile.fromSingleUri(context, child) ?: throw IOException("Invalid document")
                    if (document.name == null || (!document.isFile && !document.isDirectory)) throw IOException("Unreadable document")
                    add(document)
                }
            }
        }
    }

    private fun parent(context: Context, uri: Uri): DocumentFile? {
        if (uri.scheme == "file" || uri.scheme == null) {
            return File(uri.path ?: return null).parentFile?.takeIf { it.isDirectory }?.let(DocumentFile::fromFile)
        }
        if (!DocumentsContract.isTreeUri(uri)) return null
        val id = DocumentsContract.getDocumentId(uri)
        val parentId = id.substringBeforeLast('/', "")
        if (parentId.isEmpty()) return null
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
        // Keep the exact document ID; fromTreeUri would silently return the grant's root.
        return DocumentFile.fromSingleUri(context, parentUri)
    }

    private fun sameFile(uri: Uri, reference: String): Boolean = reference.isNotBlank() && try {
        val other = reference.toUri()
        if (uri.scheme == "content" && other.scheme == "content") {
            uri.authority == other.authority && DocumentsContract.getDocumentId(uri) == DocumentsContract.getDocumentId(other)
        } else File(requireNotNull(uri.path)).canonicalFile == File(requireNotNull(other.path)).canonicalFile
    } catch (_: Exception) {
        true // An unresolvable DB association must never authorize deletion.
    }

    private fun referencesFolder(folder: Uri, reference: String): Boolean {
        if (reference.isBlank()) return false
        return try {
            val uri = reference.toUri()
            if (folder.scheme == "content" && uri.scheme == "content") {
                val id = DocumentsContract.getDocumentId(folder)
                val target = DocumentsContract.getDocumentId(uri)
                folder.authority == uri.authority && (target == id || target.startsWith("$id/"))
            } else if (folder.scheme == "file" && (uri.scheme == "file" || uri.scheme == null)) {
                val path = File(requireNotNull(folder.path)).canonicalPath
                val target = File(requireNotNull(uri.path)).canonicalPath
                target == path || target.startsWith("$path${File.separator}")
            } else false
        } catch (_: Exception) {
            true
        }
    }
}
