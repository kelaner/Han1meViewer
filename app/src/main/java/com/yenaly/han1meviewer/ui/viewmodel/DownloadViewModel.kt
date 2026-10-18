package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.await
import okhttp3.Request
import java.io.IOException
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.han1meviewer.worker.HanimeDownloadWorker
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadSourceAvailability
import com.yenaly.han1meviewer.util.LocalDownloadUpdates
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadSourceRefreshState
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.ui.screen.home.loadDownloadedMetadata
import com.yenaly.han1meviewer.ui.screen.home.download.DownloadedMetadata
import com.yenaly.yenaly_libs.base.YenalyViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * @project Han1meViewer
 * @author Yenaly Liew - 创建 (2022/08/02)
 * 初始版本
 * @author Misaka10032w - 更新 (2025/11/27)
 * 实现分组展示和展开/折叠功能
 * 实现分组移动、重命名等
 */
class DownloadViewModel(application: Application) : YenalyViewModel(application) {

    private val _downloaded = MutableStateFlow(mutableListOf<VideoWithCategories>())
    val downloaded = _downloaded.asStateFlow()
    private var downloadedJob: Job? = null
    private val metadataMutex = Mutex()
    private val _sourceRefresh = MutableStateFlow(DownloadSourceRefreshState())
    val sourceRefresh = _sourceRefresh.asStateFlow()
    private val sourceLocks = mutableMapOf<Int, Mutex>()
    private val sourceJobs = mutableMapOf<Int, Job>()
    private var observedLocalRevision = LocalDownloadUpdates.changes.value

    init {
        viewModelScope.launch {
            LocalDownloadUpdates.changes.collect { revision ->
                if (revision != observedLocalRevision) {
                    observedLocalRevision = revision
                    try {
                        resolveDownloadedMetadata(force = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /** Only completed records are eligible. A missing artist is still a resolved unknown. */
    private suspend fun resolveDownloadedMetadata(force: Boolean = false) = withContext(Dispatchers.IO) {
        metadataMutex.withLock {
            val dao = DownloadDatabase.instance.hanimeDownloadDao
            val records = dao.getAll().filter { it.isDownloaded && !it.metadataResolved }
            if (records.isEmpty()) return@withLock
            // Different qualities may live under different download roots after moving storage.
            for ((location, locatedRecords) in records.groupBy { it.videoCode to it.videoUri }) {
                val (code, uri) = location
                val parsed = loadDownloadedMetadata(application, listOf(code), mapOf(code to uri))[code]
                for (record in locatedRecords) {
                    val value = parsed ?: DownloadedMetadata(
                        title = record.title,
                        coverUrl = record.coverUrl,
                        author = record.author,
                        genre = record.genre,
                        uploadTime = record.uploadTime,
                    )
                    dao.saveMetadata(
                        id = record.id,
                        videoUri = record.videoUri,
                        coverUrl = value.coverUrl?.takeIf { it.isNotBlank() } ?: record.coverUrl,
                        title = value.title?.takeIf { it.isNotBlank() } ?: record.title,
                        author = value.author ?: record.author,
                        genre = value.genre ?: record.genre,
                        uploadTime = value.uploadTime ?: record.uploadTime,
                        force = force,
                    )
                }
            }
        }
    }

    /**
     * 下载分组列表的状态流
     *
     * 观察数据库中的下载分组变化，用于驱动分组管理UI的更新。
     *
     * @return StateFlow 包含所有下载分组实体列表，初始为空列表
     */
    val downloadedGroups: StateFlow<List<DownloadGroupEntity>> =
        DatabaseRepo.HanimeDownload.getAllGroups()
            .flowOn(Dispatchers.IO)
            .catch { e ->
                e.printStackTrace()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun loadAllDownloadingHanime() =
        DatabaseRepo.HanimeDownload.loadAllDownloadingHanime()
            .catch { e -> e.printStackTrace() }
            .flowOn(Dispatchers.IO)

    fun loadAllDownloadedHanime(
        sortedBy: HanimeDownloadEntity.SortedBy = HanimeDownloadEntity.SortedBy.ID,
        ascending: Boolean = false,
    ) {
        downloadedJob?.cancel()
        downloadedJob = viewModelScope.launch {
            DatabaseRepo.HanimeDownload.loadAllDownloadedHanime(sortedBy, ascending)
                .catch { e -> e.printStackTrace() }
                .flowOn(Dispatchers.IO)
                .collect {
                    if (it.any { row -> !row.video.metadataResolved }) {
                        try {
                            resolveDownloadedMetadata()
                            // Room emits the updated snapshot; do not flash legacy unknown fields first.
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _downloaded.value = it
                        }
                    } else {
                        _downloaded.value = it
                    }
                }
        }
    }

    /**
     * 更新视频所属的分组
     *
     * 将指定视频移动到新的下载分组中
     *
     * @param videoCode 视频唯一标识码
     * @param selectedGroupId 目标分组的ID
     */
    fun refreshDownloadedMetadata(video: VideoWithCategories? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            metadataMutex.withLock {
                val dao = DownloadDatabase.instance.hanimeDownloadDao
                val records = dao.getAll().filter { it.isDownloaded && (video == null || it.id == video.video.id) }
                for (record in records) {
                    val parsed = loadDownloadedMetadata(application, listOf(record.videoCode), mapOf(record.videoCode to record.videoUri))[record.videoCode]
                    val value = parsed ?: DownloadedMetadata(
                        title = record.title,
                        coverUrl = record.coverUrl,
                        author = record.author,
                        genre = record.genre,
                        uploadTime = record.uploadTime,
                    )
                    dao.saveMetadata(
                        record.id, record.videoUri,
                        value.coverUrl?.takeIf { it.isNotBlank() } ?: record.coverUrl,
                        value.title?.takeIf { it.isNotBlank() } ?: record.title,
                        value.author, value.genre, value.uploadTime, force = true,
                    )
                }
            }
        }
    }

    /** Refresh origin metadata only after at least one downloadable source has been verified. */
    fun refreshSource(video: VideoWithCategories, persistMetadata: Boolean = true) {
        if (_sourceRefresh.value.loadingVideoId != null) return
        val entity = video.video
        _sourceRefresh.value = _sourceRefresh.value.copy(
            loadingVideoId = entity.id, error = null,
            results = _sourceRefresh.value.results - entity.id,
        )
        sourceJobs[entity.id] = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val source = fetchOrigin(entity.videoCode)
                    val qualities = source.videoUrls.filter { (_, link) ->
                        if (link.link.isBlank()) false else try {
                            probeSourceLength(link.link)
                            true
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { false }
                    }.keys.toList()
                    if (qualities.isEmpty()) throw IOException("源视频没有可下载画质")
                    val refreshedAt = System.currentTimeMillis()
                    if (persistMetadata) {
                        var validCover = false
                        if (source.coverUrl.isNotBlank()) {
                            try {
                                ServiceCreator.downloadClient.newBuilder()
                                    .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
                                    .newCall(Request.Builder().url(source.coverUrl).get().build()).await().use {
                                        validCover = it.isSuccessful && it.header("Content-Type").orEmpty().startsWith("image/", true)
                                    }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { /* Keep the previous valid cover. */ }
                        }
                        val count = DownloadDatabase.instance.hanimeDownloadDao.saveSourceMetadata(
                            id = entity.id, videoUri = entity.videoUri,
                            coverUrl = if (validCover) source.coverUrl else entity.coverUrl,
                            coverUri = if (validCover) null else entity.coverUri,
                            title = source.title.takeIf(String::isNotBlank) ?: entity.title,
                            author = source.artist?.name?.takeIf(String::isNotBlank),
                            genre = source.artist?.genre?.takeIf(String::isNotBlank),
                            uploadTime = source.uploadTime?.toString(),
                            qualities = qualities.joinToString(","), refreshedAt = refreshedAt,
                        )
                        if (count == 0) throw IOException("下载记录已变化，请刷新列表后重试")
                    }
                    DownloadSourceAvailability(
                        qualities = qualities,
                        higherQualities = qualities.filter {
                            val current = qualityRank(entity.quality)
                            val candidate = qualityRank(it)
                            current != null && candidate != null && candidate > current
                        },
                        refreshedAt = refreshedAt,
                    )
                }
                _sourceRefresh.value = _sourceRefresh.value.copy(
                    loadingVideoId = null, results = _sourceRefresh.value.results + (entity.id to result),
                )
            } catch (e: CancellationException) {
                _sourceRefresh.value = _sourceRefresh.value.copy(loadingVideoId = null)
                throw e
            } catch (e: Exception) {
                _sourceRefresh.value = _sourceRefresh.value.copy(loadingVideoId = null, error = e.localizedMessage ?: "源站信息更新失败")
            } finally { sourceJobs.remove(entity.id) }
        }
    }

    private suspend fun fetchOrigin(code: String): HanimeVideo {
        var info: HanimeVideo? = null
        NetworkRepo.getHanimeVideo(code).collect { state ->
            when (state) {
                is VideoLoadingState.Success -> info = state.info
                is VideoLoadingState.Error -> throw state.throwable
                VideoLoadingState.NoContent -> throw IOException("源站未返回视频信息")
                VideoLoadingState.Loading -> Unit
            }
        }
        return info ?: throw IOException("源站未返回视频信息")
    }

    private fun qualityRank(quality: String): Int? =
        Regex("(\\d+)").find(quality)?.groupValues?.get(1)?.toIntOrNull()

    fun switchSource(video: VideoWithCategories, quality: String) {
        if (_sourceRefresh.value.loadingVideoId != null) return
        val entity = video.video
        sourceJobs.remove(entity.id)?.cancel()
        _sourceRefresh.value = _sourceRefresh.value.copy(
            loadingVideoId = entity.id,
            error = null,
            results = _sourceRefresh.value.results - entity.id,
        )
        sourceJobs[entity.id] = viewModelScope.launch {
            sourceLocks.getOrPut(entity.id) { Mutex() }.withLock {
            try {
                val result = withContext(Dispatchers.IO) {
                    var latest: HanimeVideo? = null
                    NetworkRepo.getHanimeVideo(entity.videoCode).collect { state ->
                        when (state) {
                            is VideoLoadingState.Success -> latest = state.info
                            is VideoLoadingState.Error -> throw state.throwable
                            VideoLoadingState.NoContent -> throw IllegalStateException("源站未返回视频信息")
                            VideoLoadingState.Loading -> Unit
                        }
                    }
                    val source = latest ?: throw IllegalStateException("源站未返回视频信息")
                    val link = source.videoUrls[quality]
                        ?: throw IllegalArgumentException("该画质已不再可用")
                    if (quality == entity.quality || link.link.isBlank()) {
                        throw IllegalArgumentException("请选择其他有效画质")
                    }
                    val length = probeSourceLength(link.link)
                    val dao = DownloadDatabase.instance.hanimeDownloadDao
                    if (dao.countOtherQuality(entity.videoCode, quality, entity.id) > 0) {
                        throw IllegalStateException("目标画质已有独立下载记录，不能覆盖")
                    }
                    val rowCount = dao.findCompletedByVideoCode(entity.videoCode)
                        .count { it.id == entity.id && it.videoUri == entity.videoUri }
                    if (rowCount != 1) throw IllegalStateException("下载记录已变化，请刷新列表后重试")
                    HanimeDownloadWorker.Args(
                        quality = quality,
                        downloadUrl = link.link,
                        videoType = link.suffix,
                        hanimeName = source.title.takeIf { it.isNotBlank() } ?: entity.title,
                        videoCode = entity.videoCode,
                        coverUrl = source.coverUrl.takeIf { it.isNotBlank() } ?: entity.coverUrl,
                        uploadTime = source.uploadTime?.toString() ?: entity.uploadTime,
                        groupId = entity.groupId,
                        replacementId = entity.id,
                        previousQuality = entity.quality,
                        previousVideoUri = entity.videoUri,
                        sourceQualities = source.videoUrls.keys.joinToString(","),
                    ) to length
                }
                HanimeDownloadManagerV2.addSourceReplacement(result.first)
                _sourceRefresh.value = _sourceRefresh.value.copy(loadingVideoId = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sourceRefresh.value = _sourceRefresh.value.copy(
                    loadingVideoId = null,
                    error = e.localizedMessage ?: "换源失败，原视频保持不变",
                )
            }
            }
        }
    }

    private suspend fun probeSourceLength(url: String): Long {
        val probeClient = ServiceCreator.downloadClient.newBuilder()
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
        return probeClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("所选画质链接不可下载 (${response.code})")
            val mime = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
            if (!mime.startsWith("video/") && mime != "application/octet-stream") throw IOException("所选链接不是可下载视频 ($mime)")
            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: if (response.code == 200) response.header("Content-Length")?.toLongOrNull() else null
            total?.takeIf { it > 0 } ?: throw IOException("源站未提供有效视频长度")
        }
    }

    fun updateVideoGroup(videoCode: String, selectedGroupId: Int) {
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.updateVideoGroup(videoCode, selectedGroupId)
        }
    }

    /**
     * 创建新的下载分组
     *
     * 在数据库中创建一个新的空分组
     *
     * @param groupName 新分组的名称，不能为空或空白字符串
     */
    fun createNewGroup(groupName: String){
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.createNewGroup(groupName)
        }
    }

    /**
     * 更新下载分组的名称
     *
     * 修改指定分组的显示名称，如果分组不存在或操作失败会静默处理错误
     *
     * @param groupId 要更新的分组ID
     * @param newName 新的分组名称，不能为空或空白字符串
     *
     * @sample
     * // 将分组ID为1的名称改为"收藏视频"
     * updateGroupName(1, "收藏视频")
     */
    fun updateGroupName(groupId: Int, newName: String){
        viewModelScope.launch {
            try {
                val oldGroupName = DatabaseRepo.HanimeDownload.getGroupById(groupId)
                if (oldGroupName != null){
                    val updatedGroup = oldGroupName.copy(name = newName)
                    DatabaseRepo.HanimeDownload.updateGroup(updatedGroup)
                }
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }
    fun deleteGroup(group: DownloadGroupEntity){
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.deleteGroup(group)
        }
    }

    fun updateDownloadHanime(entity: HanimeDownloadEntity) {
        viewModelScope.launch {
            DatabaseRepo.HanimeDownload.update(entity)
        }
    }

    fun deleteDownloadHanimeBy(videoCode: String, quality: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseRepo.HanimeDownload.delete(videoCode, quality)
        }
    }
}