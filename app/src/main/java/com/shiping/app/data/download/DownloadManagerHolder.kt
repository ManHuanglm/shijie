package com.shiping.app.data.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.shiping.app.ShipingApp
import com.shiping.app.di.AppContainer
import com.shiping.app.data.player.MultiConnectionDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * 全局单例，持有 ExoPlayer DownloadManager 与缓存数据源。
 * 支持动态限速、并发数配置。
 */
@UnstableApi
object DownloadManagerHolder {

    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var downloadCache: Cache? = null

    @Volatile
    private var cacheDataSourceFactory: DataSource.Factory? = null

    @Volatile
    private var rateLimitedFactory: RateLimitedDataSourceFactory? = null

    /** 播放线路独立限速工厂（下载限速同样作用于播放线路的单条连接） */
    @Volatile
    private var playbackRateLimitedFactory: RateLimitedDataSourceFactory? = null

    /** 播放线路多连接工厂（缓冲线路倍数） */
    @Volatile
    private var multiConnectionFactory: MultiConnectionDataSourceFactory? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsJob: Job? = null

    /** 获取 DownloadManager 单例 */
    fun getDownloadManager(): DownloadManager {
        return downloadManager ?: synchronized(this) {
            downloadManager ?: createDownloadManager().also { downloadManager = it }
        }
    }

    private fun createDownloadManager(): DownloadManager {
        val context = ShipingApp.context
        val dbProvider = StandaloneDatabaseProvider(context)
        databaseProvider = dbProvider

        val downloadContentDirectory = File(context.filesDir, DOWNLOAD_CONTENT_DIRECTORY)
        val cache = SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), dbProvider)
        downloadCache = cache

        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Shiping/1.0)")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        // 限速包装（默认不限速）：下载线路与播放线路独立限速
        rateLimitedFactory = RateLimitedDataSourceFactory(upstreamFactory, 0L)
        playbackRateLimitedFactory = RateLimitedDataSourceFactory(upstreamFactory, 0L)

        // 播放线路多连接并行缓冲（缓冲线路倍数，默认 1=单连接）
        multiConnectionFactory = MultiConnectionDataSourceFactory(playbackRateLimitedFactory!!)

        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(multiConnectionFactory!!)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val manager = DownloadManager(
            context,
            dbProvider,
            cache,
            rateLimitedFactory!!,
            Executors.newFixedThreadPool(3),
        ).apply {
            maxParallelDownloads = 2
        }

        // 监听下载设置变化，动态应用
        observeDownloadSettings(manager)

        return manager
    }

    /** 监听偏好设置变化，动态更新限速与并发数 */
    private fun observeDownloadSettings(manager: DownloadManager) {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            AppContainer.preferences.downloadSpeedLimitKbps.collectLatest { kbps ->
                val bytesPerSecond = kbps * 1024L
                rateLimitedFactory?.setBytesPerSecond(bytesPerSecond)
                // 播放线路每条连接的限速与下载限速保持一致
                playbackRateLimitedFactory?.setBytesPerSecond(bytesPerSecond)
            }
        }
        scope.launch {
            AppContainer.preferences.downloadMaxParallel.collectLatest { max ->
                manager.maxParallelDownloads = max
            }
        }
        scope.launch {
            AppContainer.preferences.bufferLineMultiplier.collectLatest { multiplier ->
                multiConnectionFactory?.setConnections(multiplier)
            }
        }
    }

    /** 获取带缓存的数据源工厂，用于已下载内容的本地播放 */
    fun getCacheDataSourceFactory(): DataSource.Factory {
        if (cacheDataSourceFactory == null) getDownloadManager()
        return cacheDataSourceFactory!!
    }
}
