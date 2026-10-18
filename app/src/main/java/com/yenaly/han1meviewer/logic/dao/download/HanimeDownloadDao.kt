package com.yenaly.han1meviewer.logic.dao.download

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/08/18 018 23:07
 */
@Dao
abstract class HanimeDownloadDao {
    /**
     * 获取所有未下载完成的任务
     *
     * 名字起得不好
     */
    @Query("SELECT * FROM HanimeDownloadEntity WHERE state != ${DownloadState.Mask.FINISHED} ORDER BY id DESC")
    abstract fun loadAllDownloadingHanime(): Flow<MutableList<HanimeDownloadEntity>>

    @Query("SELECT * FROM HanimeDownloadEntity ORDER BY id ASC")
    abstract suspend fun getAll(): List<HanimeDownloadEntity>

    /**
     * 获取所有正在下载的任务，单次
     */
    //@Query("SELECT * FROM HanimeDownloadEntity WHERE isDownloading = 1 ORDER BY id DESC")
    @Query("SELECT * FROM HanimeDownloadEntity WHERE state != ${DownloadState.Mask.FINISHED} ORDER BY id DESC")
    abstract suspend fun loadAllDownloadingHanimeOnce(): MutableList<HanimeDownloadEntity>

    /**
     * 获取部分正在下载的任务，单次
     */
    //@Query("SELECT * FROM HanimeDownloadEntity WHERE isDownloading = 1 ORDER BY id DESC LIMIT :limit")
    @Query("SELECT * FROM HanimeDownloadEntity WHERE state != ${DownloadState.Mask.FINISHED} ORDER BY id DESC LIMIT :limit")
    abstract suspend fun loadDownloadingHanimeOnce(limit: Int): MutableList<HanimeDownloadEntity>

    @Query(
        "SELECT * FROM HanimeDownloadEntity WHERE state = ${DownloadState.Mask.FINISHED} ORDER BY " +
                "CASE WHEN :ascending THEN title END ASC, CASE WHEN NOT :ascending THEN title END DESC"
    )
    @Transaction
    abstract fun loadAllDownloadedHanimeByTitle(ascending: Boolean): Flow<MutableList<VideoWithCategories>>

    @Query(
        "SELECT * FROM HanimeDownloadEntity WHERE state = ${DownloadState.Mask.FINISHED} ORDER BY " +
                "CASE WHEN :ascending THEN id END ASC, CASE WHEN NOT :ascending THEN id END DESC"
    )
    @Transaction
    abstract fun loadAllDownloadedHanimeById(ascending: Boolean): Flow<MutableList<VideoWithCategories>>

    @Query("DELETE FROM HanimeDownloadEntity WHERE (`videoCode` = :videoCode AND `quality` = :quality)")
//    @Deprecated("查屁")
    abstract suspend fun delete(videoCode: String, quality: String)

    @Query("DELETE FROM HanimeDownloadEntity WHERE (`videoCode` = :videoCode)")
    abstract suspend fun delete(videoCode: String)

    //@Query("UPDATE HanimeDownloadEntity SET `isDownloading` = 0")
    @Query("UPDATE HanimeDownloadEntity SET `state` = ${DownloadState.Mask.PAUSED}")
    abstract suspend fun pauseAll()

    @Delete
    abstract suspend fun delete(entity: HanimeDownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: HanimeDownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<HanimeDownloadEntity>)

    @Query("DELETE FROM HanimeDownloadEntity")
    abstract suspend fun deleteAll()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(entity: HanimeDownloadEntity): Int

    @Query("SELECT COUNT(*) FROM HanimeDownloadEntity WHERE videoCode = :videoCode " +
            "AND (state != ${DownloadState.Mask.FINISHED} OR quality = :quality COLLATE NOCASE)")
    abstract suspend fun countImportConflicts(videoCode: String, quality: String): Int

    @Transaction
    open suspend fun insertRestoredIfUnchanged(entity: HanimeDownloadEntity): Boolean {
        if (countImportConflicts(entity.videoCode, entity.quality) > 0) return false
        insert(entity)
        return true
    }

    /** Restore local file fields only while the completed snapshot is still current. */
    @Query(
        "UPDATE HanimeDownloadEntity SET videoUri = :videoUri, coverUri = :coverUri, coverUrl = :coverUrl, title = :title, " +
                "length = :length, downloadedLength = :length, author = :author, genre = :genre, uploadTime = :uploadTime, metadataResolved = 1 " +
                "WHERE id = :id AND videoUri = :previousUri AND state = ${DownloadState.Mask.FINISHED} " +
                "AND author IS :previousAuthor AND genre IS :previousGenre AND metadataResolved = :previousResolved"
    )
    abstract suspend fun restoreCompletedFiles(
        id: Int, previousUri: String, previousAuthor: String?, previousGenre: String?, previousResolved: Boolean,
        videoUri: String, coverUri: String?, coverUrl: String, title: String, length: Long, author: String?, genre: String?, uploadTime: String?,
    ): Int

    /** Update display metadata only; never overwrite download progress or resurrect deleted tasks. */
    @Query(
        "UPDATE HanimeDownloadEntity SET coverUrl = :coverUrl, title = :title, author = :author, genre = :genre, uploadTime = :uploadTime, metadataResolved = 1 " +
                "WHERE id = :id AND videoUri = :videoUri AND state = ${DownloadState.Mask.FINISHED} " +
                "AND (:force OR metadataResolved = 0)"
    )
    abstract suspend fun saveMetadata(
        id: Int, videoUri: String, coverUrl: String, title: String, author: String?, genre: String?, uploadTime: String?, force: Boolean = false,
    ): Int

    @Query("SELECT * FROM HanimeDownloadEntity WHERE (`videoCode` = :videoCode AND `quality` = :quality) LIMIT 1")
    abstract suspend fun find(videoCode: String, quality: String): HanimeDownloadEntity?

    @Query("SELECT * FROM HanimeDownloadEntity WHERE videoCode = :videoCode AND state = ${DownloadState.Mask.FINISHED}")
    abstract suspend fun findCompletedByVideoCode(videoCode: String): List<HanimeDownloadEntity>

    @Query("SELECT * FROM HanimeDownloadEntity WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Int): HanimeDownloadEntity?

    @Query("SELECT COUNT(*) FROM HanimeDownloadEntity WHERE videoCode = :videoCode AND quality = :quality COLLATE NOCASE AND id != :excludedId")
    abstract suspend fun countOtherQuality(videoCode: String, quality: String, excludedId: Int): Int

    /** Persist origin metadata and availability without changing download progress. */
    @Query(
        "UPDATE HanimeDownloadEntity SET coverUrl = :coverUrl, coverUri = :coverUri, title = :title, " +
                "author = COALESCE(:author, author), genre = COALESCE(:genre, genre), uploadTime = COALESCE(:uploadTime, uploadTime), metadataResolved = 1, " +
                "availableQualities = :qualities, sourceRefreshedAt = :refreshedAt " +
                "WHERE id = :id AND videoUri = :videoUri AND state = ${DownloadState.Mask.FINISHED}"
    )
    abstract suspend fun saveSourceMetadata(
        id: Int,
        videoUri: String,
        coverUrl: String,
        coverUri: String?,
        title: String,
        author: String?,
        genre: String?,
        uploadTime: String?,
        qualities: String,
        refreshedAt: Long,
    ): Int

    @Query("UPDATE HanimeDownloadEntity SET videoUri = :restoredUri WHERE id = :id AND videoUri = :previousUri AND state = ${DownloadState.Mask.FINISHED}")
    abstract suspend fun restoreSourceLocation(id: Int, previousUri: String, restoredUri: String): Int

    /** Commit the new source only after the replacement media has been written successfully. */
    @Query(
        "UPDATE HanimeDownloadEntity SET quality = :quality, videoUrl = :videoUrl, videoUri = :videoUri, " +
                "length = :length, downloadedLength = :length, state = ${DownloadState.Mask.FINISHED}, " +
                "availableQualities = '', sourceRefreshedAt = NULL " +
                "WHERE id = :id AND videoUri = :previousUri AND state = ${DownloadState.Mask.FINISHED}"
    )
    abstract suspend fun commitSourceSwitch(
        id: Int, previousUri: String, quality: String, videoUrl: String, videoUri: String,
        length: Long,
    ): Int

    @Query("SELECT * FROM HanimeDownloadEntity WHERE (`videoCode` = :videoCode) LIMIT 1")
    abstract suspend fun find(videoCode: String): HanimeDownloadEntity?

    @Query("SELECT COUNT(*) FROM HanimeDownloadEntity WHERE (`videoCode` = :videoCode)")
//    @Deprecated("查屁")
    abstract suspend fun countBy(videoCode: String): Int
    // 更新已下载的某视频的分组
    @Query("UPDATE HanimeDownloadEntity SET groupId = :newGroupId WHERE videoCode = :videoCode")
    abstract suspend fun updateVideoGroup(videoCode: String, newGroupId: Int)

    /**
     * 查询这些影片中已归入非默认分组的分组 id，用于下载时推荐同系列已用的分组。
     * 同一系列散落在多个分组时，取影片数量最多的那个。
     *
     * [videoCodes] 不可为空，调用方需自行过滤。
     */
    @Query(
        "SELECT groupId FROM HanimeDownloadEntity WHERE videoCode IN (:videoCodes) " +
                "AND groupId != ${DownloadGroupEntity.DEFAULT_GROUP_ID} " +
                "GROUP BY groupId ORDER BY COUNT(*) DESC LIMIT 1"
    )
    abstract suspend fun findGroupIdOfSeries(videoCodes: List<String>): Int?

}
