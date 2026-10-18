package com.yenaly.han1meviewer.worker

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yenaly.han1meviewer.logic.dao.download.HanimeDownloadDao
import com.yenaly.han1meviewer.util.SafFileManager
import java.io.File
import java.io.IOException
import java.util.Properties

/** A small durable journal lets a retried Worker recover a moved MP4 before starting again. */
internal class SourceReplacementFiles(private val context: Context, id: Int) {
    private val journal = File(context.filesDir, "source-replacement-$id.properties")
    private val values = Properties()

    private fun save() {
        val temporary = File(journal.path + ".tmp")
        temporary.outputStream().use {
            values.store(it, null)
            it.fd.sync()
        }
        if (!temporary.renameTo(journal)) throw IOException("Unable to persist source replacement recovery data")
    }

    private fun exists(uri: String): Boolean = if (uri.toUri().scheme == "file") {
        File(uri.toUri().path!!).exists()
    } else DocumentFile.fromSingleUri(context, uri.toUri())?.exists() == true

    suspend fun recover(dao: HanimeDownloadDao, recordId: Int): Boolean {
        if (!journal.exists()) return false
        journal.inputStream().use(values::load)
        val installed = values.getProperty("newUri")
        val row = dao.findById(recordId)
        if (installed != null && row?.videoUri == installed) {
            complete()
            return true
        }
        rollback(dao, recordId)
        return false
    }

    fun backup(originalUri: String) {
        val original = originalUri.toUri()
        val name = if (original.scheme == "file") File(original.path!!).name
            else DocumentFile.fromSingleUri(context, original)?.name ?: throw IOException("Old video is unavailable")
        val backupName = "$name.backup-source-${journal.nameWithoutExtension}"
        values.clear()
        values.setProperty("originalUri", originalUri)
        values.setProperty("originalName", name)
        values.setProperty("backupName", backupName)
        if (original.scheme == "file") {
            val file = File(original.path!!)
            val backup = File(file.parentFile, backupName)
            values.setProperty("backupUri", backup.toUri().toString())
            save()
            if (!file.exists() || backup.exists() || !file.renameTo(backup)) throw IOException("Unable to back up old MP4")
        } else {
            val document = DocumentFile.fromSingleUri(context, original) ?: throw IOException("Old video is unavailable")
            save()
            if (!document.renameTo(backupName)) throw IOException("Unable to back up old MP4")
            values.setProperty("backupUri", document.uri.toString())
            save()
        }
    }

    fun planNew(fileUri: Uri?, parentUri: Uri?, fileName: String) {
        fileUri?.let { values.setProperty("newUri", it.toString()) }
        parentUri?.let { values.setProperty("newParentUri", it.toString()) }
        values.setProperty("newName", fileName)
        save()
    }

    private fun plannedNewUri(): Uri? {
        val parent = values.getProperty("newParentUri")?.toUri() ?: return null
        val parentId = DocumentsContract.getDocumentId(parent)
        return context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == values.getProperty("newName")) {
                    return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
                }
            }
            null
        }
    }

    fun trackNew(uri: Uri) {
        values.setProperty("newUri", uri.toString())
        save()
    }

    private fun locateBackup(): Uri? {
        values.getProperty("backupUri")?.takeIf(::exists)?.let { return it.toUri() }
        // Some SAF providers change document IDs on rename. Recover the planned name after interruption.
        val original = values.getProperty("originalUri")?.toUri() ?: return null
        if (original.scheme != "content") return null
        val documentId = DocumentsContract.getDocumentId(original)
        if ('/' !in documentId) return null
        return context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(original, documentId.substringBeforeLast('/')),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == values.getProperty("backupName")) {
                        return DocumentsContract.buildDocumentUriUsingTree(original, cursor.getString(0))
                    }
                }
                null
            }
    }

    suspend fun rollback(dao: HanimeDownloadDao, recordId: Int) {
        (values.getProperty("newUri")?.toUri() ?: plannedNewUri())?.let {
            SafFileManager.deleteDownloadUri(context, it.toString())
        }
        val original = values.getProperty("originalUri") ?: return
        val backup = locateBackup()
        if (backup != null) {
            val restored = if (backup.scheme == "file") {
                val file = File(backup.path!!)
                val target = File(original.toUri().path!!)
                if (target.exists() || !file.renameTo(target)) throw IOException("Unable to restore old MP4; backup retained")
                target.toUri()
            } else {
                val document = DocumentFile.fromSingleUri(context, backup) ?: throw IOException("Backup is unavailable")
                if (!document.renameTo(values.getProperty("originalName"))) throw IOException("Unable to restore old MP4; backup retained")
                document.uri
            }
            dao.restoreSourceLocation(recordId, original, restored.toString())
        } else if (!exists(original)) {
            throw IOException("Old MP4 backup is unavailable")
        }
        journal.delete()
        values.clear()
    }

    fun complete() {
        locateBackup()?.let { SafFileManager.deleteDownloadUri(context, it.toString()) }
        journal.delete()
        values.clear()
    }
}
