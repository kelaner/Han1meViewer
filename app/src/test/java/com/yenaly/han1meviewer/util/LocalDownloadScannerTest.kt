package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class LocalDownloadScannerTest {
    @Test
    fun scanLargeLibrary_isolatesBadItemsAndBoundsOpenResources() = runBlocking {
        val resources = Resources()
        val folders = (1..350).map { code ->
            folder(code.toString(), resources, if (code % 50 == 0) "{" else info)
        }
        val imported = mutableListOf<HanimeDownloadEntity>()
        val progress = mutableListOf<Int>()
        val result = importLocalDownloadFolders(
            folders, (50..350 step 50).map { entity(it.toString()) },
            { imported += it }, { fail("Corrupt metadata must preserve the existing row") },
        ) { processed, total, _ ->
            assertEquals(350, total)
            progress += processed
        }

        assertEquals(343, result.successCount)
        assertEquals(7, result.failureCount)
        assertEquals(343, imported.size)
        assertTrue(imported.all { it.isDownloaded && it.length == it.downloadedLength && it.length > 0 })
        assertTrue(imported.all { it.author == "author" && it.genre == "genre" && it.metadataResolved })
        assertTrue(folders.all { it.listCount == 1 })
        assertEquals((1..350).toList(), progress)
        assertEquals(1, resources.maxOpen)
        assertEquals(0, resources.open)
    }

    @Test
    fun missingAndEmptyVideos_preserveExistingRecordsAndDoNotImport() = runBlocking {
        val resources = Resources()
        val empty = folder("1", resources).apply {
            children = children.filterNot { it.name.endsWith("mp4") }
        }
        val zero = folder("2", resources).apply {
            children = children.map { if (it.name.endsWith("mp4")) Entry("video_720P.mp4", resources, byteArrayOf()) else it }
        }
        val result = importLocalDownloadFolders(
            listOf(empty, zero), listOf(entity("1")),
            { fail("Missing or empty video must not be imported") },
            { fail("Existing valid row must survive a missing local candidate") },
        )
        assertEquals(0, result.successCount)
        assertEquals(2, result.failureCount)
        assertTrue(result.failures.all { it.reason == SafFileManager.ImportFailureReason.MISSING_OR_EMPTY_VIDEO })
    }

    @Test
    fun duplicateTasks_skipEveryUnfinishedStateAndPreserveFinishedIdentity() = runBlocking {
        val resources = Resources()
        val states = DownloadState.entries.filter { it != DownloadState.Finished }
        val existing = states.mapIndexed { index, state -> entity((index + 1).toString(), state) }
        val folders = existing.map { folder(it.videoCode, resources) } + folder("99", resources)
        val previous = entity("99").copy(id = 42, groupId = 9, addDate = 123L)
        val updated = mutableListOf<HanimeDownloadEntity>()
        val result = importLocalDownloadFolders(folders, existing + previous,
            { fail("Duplicate tasks must not insert new rows") }, { updated += it })

        assertEquals(states.size, result.skippedCount)
        assertTrue(folders.take(states.size).all { it.listCount == 0 })
        assertEquals(1, result.successCount)
        assertEquals(1, updated.size)
        assertEquals(42, updated.single().id)
        assertEquals(9, updated.single().groupId)
        assertEquals(123L, updated.single().addDate)
        assertEquals(DownloadState.Finished, updated.single().state)
        assertEquals("author", updated.single().author)
        assertEquals("genre", updated.single().genre)
        assertTrue(updated.single().metadataResolved)
    }

    @Test
    fun cancellation_propagatesAndClosesTheStream() = runBlocking {
        val resources = Resources()
        val cancelled = folder("1", resources).apply {
            children.first { it.name == "info.json" }.failure = CancellationException("cancel metadata read")
        }
        val completed = mutableListOf<Int>()
        try {
            importLocalDownloadFolders(listOf(cancelled, folder("2", resources)), emptyList(),
                { fail("Cancellation must stop importing") }, { fail("Unexpected update") },
                onItemProgress = { count, _, _, _, _ -> completed += count })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, resources.open)
            assertEquals(listOf(0), completed)
        }
    }

    @Test
    fun itemProgress_countsSuccessFailureAndSkippedAfterEachCandidate() = runBlocking {
        val resources = Resources()
        val progress = mutableListOf<List<Int>>()
        val imported = mutableListOf<HanimeDownloadEntity>()
        val result = importLocalDownloadFolders(
            listOf(folder("1", resources, "{}"), folder("2", resources, "{"), folder("3", resources)),
            listOf(entity("3", DownloadState.Paused)),
            { imported += it }, { fail("No completed existing row to update") },
            onItemProgress = { completed, total, failed, skipped, _ ->
                progress += listOf(completed, total, failed, skipped)
            },
        )
        assertEquals(listOf(
            listOf(0, 3, 0, 0), listOf(1, 3, 0, 0),
            listOf(2, 3, 1, 0), listOf(3, 3, 1, 1),
        ), progress)
        assertEquals(1, result.successCount)
        assertNull(imported.single().author)
        assertNull(imported.single().genre)
        assertTrue(imported.single().metadataResolved)
    }

    @Test
    fun concurrentDatabaseChanges_skipConflictingInsertsAndUpdates() = runBlocking {
        val resources = Resources()
        val progress = mutableListOf<List<Int>>()
        val result = importLocalDownloadFolders(
            listOf(folder("1", resources), folder("2", resources)), listOf(entity("1")),
            { fail("Conflicting insert must not use the unguarded fallback") },
            { fail("Conflicting update must not use the unguarded fallback") },
            insertIfUnchanged = { false }, updateIfUnchanged = { _, _ -> false },
            onItemProgress = { completed, _, failed, skipped, _ ->
                progress += listOf(completed, failed, skipped)
            },
        )
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(2, result.skippedCount)
        assertEquals(listOf(listOf(0, 0, 0), listOf(1, 0, 1), listOf(2, 0, 2)), progress)
    }

    @Test
    fun metadata_readsActualArtistStructureAndRejectsOversizedInput() = runBlocking {
        val metadata = parseLocalDownloadMetadata("""{"artist":{"name":" Author ","genre":" Drama "},"videoUrls":{"720P":{"link":"https://example.test/video"}}}""")
        assertEquals("Author", metadata.author)
        assertEquals("Drama", metadata.genre)
        assertEquals("https://example.test/video", metadata.videoUrls["720P"])
        assertNull(parseLocalDownloadMetadata("""{"artist":"legacy"}""").author)
        assertNull(parseLocalDownloadMetadata("""{"artist":{"name":12,"genre":[]}}""").author)
        for (invalidRoot in listOf("[]", "null", "123", "\"text\"")) {
            try {
                parseLocalDownloadMetadata(invalidRoot)
                fail("Non-object metadata must be rejected")
            } catch (_: IOException) {
                // Invalid document shapes must not clear previously saved artist fields.
            }
        }
        try {
            readLocalDownloadMetadata(ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1) { ' '.code.toByte() }))
            fail("Metadata must have a bounded size")
        } catch (_: IOException) {
            // A damaged metadata file must not consume unbounded heap.
        }
    }

    private class Resources {
        var open = 0
        var maxOpen = 0
    }

    private class Entry(
        override val name: String,
        private val resources: Resources,
        private val bytes: ByteArray? = null,
        var children: List<Entry> = emptyList(),
    ) : LocalDownloadEntry {
        override val uri = "file:///test/$name"
        override val isDirectory get() = bytes == null
        override val isFile get() = bytes != null
        override val length get() = bytes?.size?.toLong() ?: 0L
        var listCount = 0
        var failure: Exception? = null
        override fun listFiles(): List<LocalDownloadEntry> {
            listCount++
            return children
        }
        override fun openInputStream(): InputStream {
            resources.open++
            resources.maxOpen = maxOf(resources.open, resources.maxOpen)
            return object : ByteArrayInputStream(bytes ?: byteArrayOf()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    failure?.let { throw it }
                    return super.read(b, off, len)
                }
                override fun close() {
                    resources.open--
                    super.close()
                }
            }
        }
    }

    private fun folder(code: String, resources: Resources, metadata: String = info) = Entry(
        code, resources, children = listOf(
            Entry("info.json", resources, metadata.toByteArray()),
            Entry("video_720P.mp4", resources, byteArrayOf(1, 2, 3)),
        ),
    )

    private fun entity(code: String, state: DownloadState = DownloadState.Finished) = HanimeDownloadEntity(
        coverUrl = "", coverUri = null, title = "existing", addDate = 1L,
        videoCode = code, videoUri = "file:///existing/video.mp4", quality = "720P",
        videoUrl = "", length = 3L, downloadedLength = 3L, state = state,
    )

    private companion object {
        const val info = """{"title":"video","coverUrl":"https://example.test/cover","artist":{"name":"author","genre":"genre"},"videoUrls":{"720P":{"link":"https://example.test/video"}}}"""
    }
}
