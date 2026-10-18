package com.yenaly.han1meviewer.ui.screen.home

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedMetadata
import com.yenaly.han1meviewer.util.parseLocalDownloadMetadata
import com.yenaly.han1meviewer.util.readLocalDownloadMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Read-only metadata. Missing entries indicate failed reads; parsed absent artist fields remain null. */
suspend fun loadDownloadedMetadata(
    context: Context,
    videoCodes: List<String>,
    videoUris: Map<String, String> = emptyMap(),
): Map<String, DownloadedMetadata> = withContext(Dispatchers.IO) {
    val result = mutableMapOf<String, DownloadedMetadata>()
    val privateStorage = Preferences.isUsePrivateStorage
    val safFolders = if (!privateStorage) {
        try {
            Preferences.safDownloadPath?.toUri()?.let { DocumentFile.fromTreeUri(context, it) }
                ?.findFile(HFileManager.HANIME_DOWNLOAD_FOLDER)?.listFiles()?.associateBy { it.name }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    } else null
    for (code in videoCodes.distinct()) {
        ensureActive()
        if (code.isBlank() || code.contains('/') || code.contains('\\') || code == "..") continue
        val candidates = mutableListOf<android.net.Uri>()
        // Prefer the record's actual location, which can differ after changing the download path.
        videoUris[code]?.takeIf { it.isNotBlank() }?.let { value ->
            try {
                val uri = value.toUri()
                if (uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    if ('/' in id) candidates += DocumentsContract.buildDocumentUriUsingTree(
                        uri, id.substringBeforeLast('/') + "/info.json"
                    )
                } else if (uri.scheme == "file" || uri.scheme == null) {
                    File(uri.path ?: value).parentFile?.let { candidates += File(it, "info.json").toUri() }
                }
            } catch (_: Exception) { /* Fall back to the configured directory. */ }
        }
        try {
            if (privateStorage) {
                candidates += File(HFileManager.getDownloadVideoFolder(context, code), "info.json").toUri()
            } else {
                safFolders?.get(code)?.findFile("info.json")?.let { candidates += it.uri }
            }
        } catch (_: Exception) { /* A failed provider must not discard another usable location. */ }
        var metadata: DownloadedMetadata? = null
        for (uri in candidates.distinct()) {
            ensureActive()
            try {
                val parsed = context.contentResolver.openInputStream(uri)?.use { readLocalDownloadMetadata(it) }
                    ?: continue
                metadata = DownloadedMetadata(parsed.author, parsed.genre)
                break
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Isolate damaged, missing, or inaccessible metadata. */ }
        }
        // No entry means unreadable/missing; a present null-valued entry means a parsed unknown.
        metadata?.let { result[code] = it }
    }
    result
}

internal fun parseDownloadedMetadata(text: String): DownloadedMetadata {
    val parsed = parseLocalDownloadMetadata(text)
    return DownloadedMetadata(parsed.author, parsed.genre)
}
