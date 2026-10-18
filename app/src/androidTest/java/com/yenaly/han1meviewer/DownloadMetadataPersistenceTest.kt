package com.yenaly.han1meviewer

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadMetadataPersistenceTest {
    @Test
    fun migrateLegacyDatabase_andPersistMetadata_withoutOverwritingProtectedTasks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "download-metadata-test-${UUID.randomUUID()}.db"
        var database: DownloadDatabase? = null
        try {
            createVersion5Database(context, name)
            val opened = openDatabase(context, name)
            database = opened
            val dao = opened.hanimeDownloadDao
            val legacy = requireNotNull(dao.find("legacy", "720P"))
            assertEquals(41, legacy.id)
            assertEquals(1, legacy.groupId)
            assertEquals(123L, legacy.addDate)
            assertEquals(DownloadState.Finished, legacy.state)
            assertEquals(9L, legacy.length)
            assertEquals(9L, legacy.downloadedLength)
            assertNull(legacy.author)
            assertNull(legacy.genre)
            assertFalse(legacy.metadataResolved)

            assertEquals(1, dao.saveMetadata(legacy.id, legacy.videoUri, legacy.coverUrl, legacy.title, "Legacy author", "Drama", null))
            val finished = legacy.copy(id = 42, videoCode = "new", videoUri = "file:///new.mp4",
                author = null, genre = null, metadataResolved = false)
            val unfinished = finished.copy(id = 43, videoCode = "paused", state = DownloadState.Paused,
                downloadedLength = 2L)
            dao.insert(finished)
            dao.insert(unfinished)
            assertEquals(0, dao.saveMetadata(unfinished.id, unfinished.videoUri, "wrong-cover", "wrong-title", "Wrong", "Wrong", "2026-01-01", force = true))
            assertEquals(0, dao.saveMetadata(finished.id, "file:///old-location.mp4", "wrong-cover", "wrong-title", "Wrong", "Wrong", "2026-01-01"))
            assertEquals(1, dao.saveMetadata(finished.id, finished.videoUri, finished.coverUrl, finished.title, "First", "Comedy", null))
            assertEquals(0, dao.saveMetadata(finished.id, finished.videoUri, "wrong-cover", "wrong-title", "Wrong", "Wrong", "2026-01-01"))
            assertEquals("First", dao.find("new", "720P")?.author)
            assertEquals(1, dao.saveMetadata(finished.id, finished.videoUri, finished.coverUrl, finished.title, "Updated", "Fantasy", "2026-01-02", force = true))
            assertEquals(1, dao.saveSourceMetadata(
                finished.id, finished.videoUri, finished.coverUrl, finished.coverUri, finished.title,
                "Updated", "Fantasy", "2026-01-02", "720P,1080P", 1234L,
            ))
            assertEquals(0, dao.commitSourceSwitch(
                finished.id, "file:///outdated.mp4", "1080P", "new-url", "file:///new-1080.mp4",
                20L,
            ))

            opened.close()
            database = null
            val reopened = openDatabase(context, name)
            database = reopened
            val saved = requireNotNull(reopened.hanimeDownloadDao.find("new", "720P"))
            assertEquals("Updated", saved.author)
            assertEquals("Fantasy", saved.genre)
            assertTrue(saved.metadataResolved)
            assertEquals("2026-01-02", saved.uploadTime)
            assertEquals("720P,1080P", saved.availableQualities)
            assertEquals(1234L, saved.sourceRefreshedAt)
            assertEquals(finished, saved.copy(author = null, genre = null, uploadTime = null,
                availableQualities = "", sourceRefreshedAt = null, metadataResolved = false))
            assertEquals(1, reopened.hanimeDownloadDao.commitSourceSwitch(
                saved.id, saved.videoUri, "1080P", "new-url", "file:///new-1080.mp4", 20L,
            ))
            val switched = requireNotNull(reopened.hanimeDownloadDao.find("new", "1080P"))
            assertEquals(saved.id, switched.id)
            assertEquals(saved.groupId, switched.groupId)
            assertEquals(saved.addDate, switched.addDate)
            assertEquals(DownloadState.Finished, switched.state)
            assertEquals(20L, switched.downloadedLength)
            assertEquals("", switched.availableQualities)
            assertNull(switched.sourceRefreshedAt)
            assertEquals(unfinished, reopened.hanimeDownloadDao.find("paused", "720P"))
            val savedLegacy = requireNotNull(reopened.hanimeDownloadDao.find("legacy", "720P"))
            assertEquals("Legacy author", savedLegacy.author)
            assertEquals("Drama", savedLegacy.genre)
            assertTrue(savedLegacy.metadataResolved)
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(context: Context, name: String): DownloadDatabase =
        Room.databaseBuilder(context, DownloadDatabase::class.java, name)
            .addMigrations(
                DownloadDatabase.Migration5To6,
                DownloadDatabase.Migration6To7,
                DownloadDatabase.Migration7To8,
            )
            .build()

    /** Fixed legacy schema fixture, independent of the current Room-generated implementation. */
    private fun createVersion5Database(context: Context, name: String) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("""CREATE TABLE download_groups (
                name TEXT NOT NULL, orderIndex INTEGER NOT NULL,
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)""")
            db.execSQL("INSERT INTO download_groups (id, name, orderIndex) VALUES (1, 'Default Group', 0)")
            db.execSQL("""CREATE TABLE HanimeDownloadEntity (
                groupId INTEGER NOT NULL, coverUrl TEXT NOT NULL, coverUri TEXT,
                title TEXT NOT NULL, addDate INTEGER NOT NULL, videoCode TEXT NOT NULL,
                videoUri TEXT NOT NULL, quality TEXT NOT NULL, videoUrl TEXT NOT NULL,
                length INTEGER NOT NULL, downloadedLength INTEGER NOT NULL, state INTEGER NOT NULL,
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                FOREIGN KEY(groupId) REFERENCES download_groups(id) ON UPDATE NO ACTION ON DELETE SET DEFAULT)""")
            db.execSQL("CREATE INDEX index_HanimeDownloadEntity_groupId ON HanimeDownloadEntity (groupId)")
            db.execSQL("""CREATE TABLE DownloadCategoryEntity (
                name TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)""")
            db.execSQL("""CREATE TABLE HanimeCategoryCrossRef (
                videoId INTEGER NOT NULL, categoryId INTEGER NOT NULL, PRIMARY KEY(videoId, categoryId))""")
            db.execSQL("CREATE INDEX index_HanimeCategoryCrossRef_categoryId ON HanimeCategoryCrossRef (categoryId)")
            db.execSQL("""INSERT INTO HanimeDownloadEntity (
                id, groupId, coverUrl, coverUri, title, addDate, videoCode,
                videoUri, quality, videoUrl, length, downloadedLength, state)
                VALUES (41, 1, 'cover', NULL, 'Legacy title', 123, 'legacy',
                'file:///legacy.mp4', '720P', 'url', 9, 9, 4)""")
            db.version = 5
        }
    }
}
