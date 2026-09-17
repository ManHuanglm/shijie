package com.shiping.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.util.UnstableApi
import com.shiping.app.data.download.GalleryExporter
import com.shiping.app.data.download.VideoExportService
import com.shiping.app.data.local.DownloadDao
import com.shiping.app.data.model.DownloadEntity
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载仓库：视频导出为 MP4 并保存到系统相册（Movies/视界），同时在 Room 中记录任务状态。
 * - mp4 直链：直接下载写入相册
 * - m3u8 等：Media3 Transformer 转封装为 MP4 后写入相册（相册文件即离线播放源）
 */
@UnstableApi
class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** contentId -> 导出协程（用于暂停/删除时取消） */
    private val exportJobs = ConcurrentHashMap<String, Job>()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    init {
        startWifiWatchdog()
        refreshTotalBytes()
        // 进程上次在导出过程中被杀（含原生崩溃）时，队列中/下载中的任务会成为僵尸，
        // 启动时统一标记失败，用户可在缓存管理页重试
        scope.launch {
            downloadDao.resetStatuses(
                listOf(DownloadEntity.STATUS_QUEUED, DownloadEntity.STATUS_DOWNLOADING),
                DownloadEntity.STATUS_FAILED,
                "上次导出中断，请重试",
            )
        }
    }

    /** 判断当前是否连接 WiFi */
    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun observeDownloads(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    /** 添加单个下载任务 */
    suspend fun addDownload(
        url: String,
        title: String,
        vodPic: String = "",
        quality: String = "自动",
    ) {
        val contentId = url
        val existing = downloadDao.getById(contentId)
        if (existing != null) {
            // 已完成：忽略；其他状态（失败/暂停/中断）：重新开始导出，避免静默无反应
            if (existing.status == DownloadEntity.STATUS_COMPLETED) return
            if (exportJobs[contentId]?.isActive == true) return
            startExport(contentId, force = true)
            return
        }

        val entity = DownloadEntity(
            contentId = contentId,
            title = title,
            vodPic = vodPic,
            url = url,
            quality = quality,
            status = DownloadEntity.STATUS_QUEUED,
        )
        downloadDao.insert(entity)
        startExport(contentId)
    }

    /** 批量添加下载任务 */
    suspend fun addDownloads(
        episodes: List<Triple<String, String, String>>,
        quality: String = "自动",
    ) {
        episodes.forEach { (url, title, vodPic) ->
            addDownload(url, title, vodPic, quality)
        }
    }

    /** 暂停下载（取消导出协程，恢复后重新导出） */
    suspend fun pauseDownload(contentId: String) {
        exportJobs[contentId]?.cancel()
        exportJobs.remove(contentId)
        downloadDao.updateStatus(contentId, DownloadEntity.STATUS_PAUSED)
        stopServiceIfIdle()
    }

    /** 恢复下载 */
    suspend fun resumeDownload(contentId: String) {
        startExport(contentId, force = true)
    }

    /** 删除下载（同时删除相册中的 MP4） */
    suspend fun removeDownload(contentId: String) {
        exportJobs[contentId]?.cancel()
        exportJobs.remove(contentId)
        downloadDao.getById(contentId)?.let { entity ->
            if (entity.localPath.isNotBlank()) {
                GalleryExporter.deleteFromGallery(context, entity.localPath)
            }
        }
        downloadDao.delete(contentId)
        refreshTotalBytes()
        stopServiceIfIdle()
    }

    /** 批量删除 */
    suspend fun removeDownloads(contentIds: List<String>) {
        contentIds.forEach { removeDownload(it) }
    }

    /** 删除全部 */
    suspend fun removeAll() {
        downloadDao.getAll().forEach { entity ->
            exportJobs[entity.contentId]?.cancel()
            exportJobs.remove(entity.contentId)
            if (entity.localPath.isNotBlank()) {
                GalleryExporter.deleteFromGallery(context, entity.localPath)
            }
        }
        downloadDao.clearAll()
        refreshTotalBytes()
        stopServiceIfIdle()
    }

    /** 重试失败的下载 */
    suspend fun retryDownload(contentId: String) {
        startExport(contentId, force = true)
    }

    /** 判断某 URL 是否已下载完成 */
    suspend fun isDownloaded(url: String): Boolean =
        downloadDao.getById(url)?.status == DownloadEntity.STATUS_COMPLETED

    /**
     * 启动导出协程。
     * @param force 手动恢复/重试时忽略暂停状态
     */
    private suspend fun startExport(contentId: String, force: Boolean = false) {
        val entity = downloadDao.getById(contentId) ?: return
        if (exportJobs[contentId]?.isActive == true) return
        if (!force && entity.status == DownloadEntity.STATUS_PAUSED) return

        // 仅 WiFi 下载检查
        val wifiOnly = AppContainer.preferences.downloadWifiOnly.first()
        if (wifiOnly && !isWifiConnected()) {
            downloadDao.updateFailed(contentId, DownloadEntity.STATUS_PAUSED, "等待 WiFi 网络")
            return
        }

        downloadDao.updateFailed(contentId, DownloadEntity.STATUS_QUEUED, null)
        VideoExportService.start(context)

        val job = scope.launch {
            val target = downloadDao.getById(contentId) ?: return@launch
            downloadDao.updateProgress(contentId, DownloadEntity.STATUS_DOWNLOADING, 0, 0, 0, 0)
            runCatching {
                GalleryExporter.exportToGallery(
                    context = context,
                    url = target.url,
                    title = target.title,
                    onProgress = { percent ->
                        launch {
                            downloadDao.updateProgress(
                                contentId = contentId,
                                status = DownloadEntity.STATUS_DOWNLOADING,
                                progress = percent,
                                bytesDownloaded = 0,
                                totalBytes = 0,
                                speed = 0,
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                downloadDao.updateCompleted(
                    contentId = contentId,
                    status = DownloadEntity.STATUS_COMPLETED,
                    localPath = result.localPath,
                    bytes = result.sizeBytes,
                    completedAt = System.currentTimeMillis(),
                )
                refreshTotalBytes()
            }.onFailure { e ->
                com.shiping.app.util.AppLog.e("DownloadRepository", "导出失败: ${target.title}", e)
                if (isActive) {
                    downloadDao.updateFailed(
                        contentId,
                        DownloadEntity.STATUS_FAILED,
                        "${e.javaClass.simpleName}: ${e.message}".take(120),
                    )
                }
            }
        }
        exportJobs[contentId] = job
        job.invokeOnCompletion {
            exportJobs.remove(contentId)
            stopServiceIfIdle()
        }
    }

    /** 无活跃导出任务时停止前台服务 */
    private fun stopServiceIfIdle() {
        if (exportJobs.isEmpty()) VideoExportService.stop(context)
    }

    /**
     * WiFi 看门狗：开启「仅 WiFi 下载」且非 WiFi 时，取消进行中的导出（标记等待 WiFi）；
     * WiFi 恢复后自动续传等待中的任务。
     */
    private fun startWifiWatchdog() {
        scope.launch {
            while (true) {
                runCatching {
                    val wifiOnly = AppContainer.preferences.downloadWifiOnly.first()
                    val onWifi = isWifiConnected()
                    val tasks = downloadDao.getAll()
                    if (wifiOnly && !onWifi) {
                        tasks.filter {
                            it.status == DownloadEntity.STATUS_DOWNLOADING ||
                                it.status == DownloadEntity.STATUS_QUEUED
                        }.forEach { entity ->
                            exportJobs[entity.contentId]?.cancel()
                            exportJobs.remove(entity.contentId)
                            downloadDao.updateFailed(entity.contentId, DownloadEntity.STATUS_PAUSED, "等待 WiFi 网络")
                        }
                        stopServiceIfIdle()
                    } else {
                        // 自动续传「等待 WiFi」的任务
                        tasks.filter {
                            it.status == DownloadEntity.STATUS_PAUSED && it.errorMessage == "等待 WiFi 网络"
                        }.forEach { startExport(it.contentId) }
                    }
                }
                delay(Constants.DOWNLOAD_PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshTotalBytes() {
        scope.launch {
            _totalBytes.value = downloadDao.getTotalDownloadedBytes()
        }
    }
}
