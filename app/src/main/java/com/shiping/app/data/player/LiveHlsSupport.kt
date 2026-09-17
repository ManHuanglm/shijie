package com.shiping.app.data.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.shiping.app.util.AppLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 电视直播 HLS 容错组件。
 *
 * 国内 IPTV 源普遍存在切片生成停顿、播放列表数十秒不更新的情况，
 * Media3 默认配置（3.5 倍 targetDuration 无新切片即抛 PlaylistStuckException、
 * 重试 3 次）会直接终止播放，表现为"直播不能持续播放"。
 *
 * 这里集中提供：
 * - 放宽的播放列表卡死判定系数
 * - 直播专用小缓冲 LoadControl（仅保留 2 秒回看，及时丢弃过期分片）
 * - 切片加载熔断（同一 ts 连续失败 2 次即放弃，交由应用层回拉/换线）
 * - 直播 HLS 媒体源工厂（浏览器 UA、短超时、chunkless 快速起播）
 */
@UnstableApi
object LiveHlsSupport {

    /**
     * 播放列表"卡死"判定系数：默认 3.5（约 35 秒判死），放宽到 30（约 5 分钟），
     * 给切片更新偶发停顿的直播源足够恢复时间；真死流再由应用层看门狗/手动换台处理。
     */
    private const val STUCK_COEFFICIENT = 30.0

    /** 播放列表/清单加载的最小重试次数 */
    private const val PLAYLIST_MIN_RETRY_COUNT = 5

    /** 切片（ts）连续失败多少次后熔断放弃 */
    private const val SEGMENT_MAX_RETRY_COUNT = 2

    /** 伪装浏览器 UA：部分 IPTV 源对非浏览器 UA 拒绝服务或限速 */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/114.0.0.0 Mobile Safari/537.36"

    /** 无后缀跳转链接的重定向结果缓存（原始地址 -> (最终地址, 缓存时刻)） */
    private val redirectCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** 重定向结果缓存有效期：直播窗口定期刷新播放列表，短缓存既减请求又能轮换签名 */
    private const val REDIRECT_CACHE_TTL_MS = 30_000L

    /** 直链媒体后缀：命中即视为终态地址，不再解析重定向 */
    private val MEDIA_SUFFIXES = arrayOf(
        ".m3u8", ".ts", ".m4s", ".m4a", ".m4v", ".mp4", ".aac", ".mp3",
        ".flv", ".key", ".jpg", ".png",
    )

    /** 判断地址路径是否已带媒体后缀（终态直链） */
    fun hasMediaSuffix(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return MEDIA_SUFFIXES.any { path.endsWith(it) }
    }

    /**
     * 解析无后缀直播链接的重定向链。
     *
     * 典型场景：哔哩直播 m3u 源中的 https://live.ottiptv.cc/bilibili/房间号
     * 会 301 跳转到 bilivideo.com 上带签名的真实 m3u8。
     * 结果缓存 [REDIRECT_CACHE_TTL_MS]；已是媒体直链或解析失败时原样返回。
     *
     * 注意：该方法会发起网络请求，必须在 IO 线程调用。
     */
    fun resolveFinalUrl(url: String): String {
        if (hasMediaSuffix(url)) return url
        val now = android.os.SystemClock.elapsedRealtime()
        redirectCache[url]?.let { (final, cachedAt) ->
            if (now - cachedAt < REDIRECT_CACHE_TTL_MS) return final
        }
        var current = url
        runCatching {
            for (hop in 0 until 5) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    // 该跳转服务对 HEAD 返回 404，必须 GET
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                try {
                    val code = conn.responseCode
                    if (code !in 300..399) break
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) break
                    // 处理相对 Location
                    current = URL(URL(current), location).toString()
                } finally {
                    runCatching { conn.inputStream?.close() }
                    runCatching { conn.errorStream?.close() }
                    conn.disconnect()
                }
            }
        }.onFailure {
            AppLog.w("LiveHls", "解析直播重定向失败，使用原地址: ${it.message}")
        }
        redirectCache[url] = current to now
        AppLog.i("LiveHls", "直播跳转解析: $url -> $current")
        return current
    }

    /** 自定义播放列表跟踪器工厂：仅放宽 stuck 判定，其余行为同默认 */
    val playlistTrackerFactory = HlsPlaylistTracker.Factory { dataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory ->
        DefaultHlsPlaylistTracker(
            dataSourceFactory,
            loadErrorHandlingPolicy,
            playlistParserFactory,
            STUCK_COEFFICIENT,
        )
    }

    /**
     * 直播专用加载错误策略：
     * - 播放列表多重试几次（源清单偶发拉取失败可恢复）
     * - ts 切片连续失败 2 次即熔断（不再无限重试过期分片，快速交给应用层换线）
     */
    val loadErrorHandlingPolicy: LoadErrorHandlingPolicy =
        object : DefaultLoadErrorHandlingPolicy(PLAYLIST_MIN_RETRY_COUNT) {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val dataType = loadErrorInfo.mediaLoadData.dataType
                // 切片熔断：同一分片连续失败达上限，放弃本次加载（异常上抛给播放器）
                if (dataType == C.DATA_TYPE_MEDIA &&
                    loadErrorInfo.errorCount > SEGMENT_MAX_RETRY_COUNT
                ) {
                    AppLog.w(
                        "LiveHls",
                        "切片连续 ${loadErrorInfo.errorCount + 1} 次加载失败，熔断放弃: " +
                            loadErrorInfo.exception.message,
                    )
                    return C.TIME_UNSET
                }
                val delay = super.getRetryDelayMsFor(loadErrorInfo)
                if (delay == C.TIME_UNSET) {
                    // 默认策略对 ParserException 等不重试：解析错误说明源真坏了，保持不重试
                    AppLog.w(
                        "LiveHls",
                        "不可重试的加载错误: ${loadErrorInfo.exception.javaClass.simpleName} " +
                            "${loadErrorInfo.exception.message}",
                    )
                    return C.TIME_UNSET
                }
                // 至少 500ms 退避，避免服务器压力
                return delay.coerceAtLeast(500L)
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int =
                if (dataType == C.DATA_TYPE_MEDIA) SEGMENT_MAX_RETRY_COUNT + 1 else PLAYLIST_MIN_RETRY_COUNT
        }

    /**
     * 直播专用 LoadControl：小缓冲低延迟，backbuffer 仅 2 秒且不保留，
     * 让播放器尽快丢弃过期分片，避免反复读取旧窗口内容。
     */
    fun createLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 3_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setBackBuffer(/* backBufferDurationMs = */ 2_000, /* retainBackBufferFromKeyframe = */ false)
            .build()

    /**
     * 创建直播 HLS 媒体源。
     *
     * 直播流量不走点播缓存（直播切片无复用价值），使用独立 HTTP 工厂：
     * 浏览器 UA、6 秒连接/8 秒读取短超时（快速失败换线）、允许跨协议重定向；
     * chunkless preparation 跳过首个分片探测，起播更快。
     *
     * 注意：直播**不启用点播去广告过滤器**——直播播放列表是只有几个切片的滚动窗口，
     * 删除任意切片都会打断解码参考链导致花屏（VOD 去广告仍在点播链路生效）。
     *
     * 数据源外包一层 [ResolvingDataSource]：无后缀跳转链接（如 B 站直播代理）
     * 在请求前被改写为重定向后的真实 m3u8；已是直链的地址原样放行。
     */
    fun createHlsMediaSource(
        context: Context,
        mediaItem: MediaItem,
    ): HlsMediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSource.Factory(context, httpFactory)
        // 跳转链接改写：仅对无后缀的地址解析重定向（结果带缓存），直链零开销放行
        val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory) { dataSpec ->
            val original = dataSpec.uri.toString()
            val final = resolveFinalUrl(original)
            if (final == original) {
                dataSpec
            } else {
                dataSpec.buildUpon().setUri(final).build()
            }
        }
        return HlsMediaSource.Factory(resolvingFactory)
            .setAllowChunklessPreparation(true)
            .setPlaylistTrackerFactory(playlistTrackerFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(mediaItem)
    }
}
