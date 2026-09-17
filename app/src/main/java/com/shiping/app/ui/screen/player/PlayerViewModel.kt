package com.shiping.app.ui.screen.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.shiping.app.data.download.DownloadManagerHolder
import com.shiping.app.data.player.AdFilterDataSourceFactory
import com.shiping.app.di.AppContainer
import com.shiping.app.util.AppLog
import com.shiping.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** 音轨/字幕/清晰度轨道信息 */
data class TrackInfo(
    val index: Int,
    val label: String,
    val isSelected: Boolean,
)

/** A-B 循环状态 */
enum class AbLoopState {
    OFF, A_SET, BOTH_SET
}

/** 画幅模式 */
enum class VideoAspectRatio(val label: String) {
    ORIGINAL("原始"),
    R16_9("16:9"),
    R4_3("4:3"),
    FILL("填充"),
    CROP("裁剪"),
    ;

    fun next(): VideoAspectRatio = entries[(ordinal + 1) % entries.size]
}

data class PlayerUiState(
    val title: String = "",
    val url: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val resolvedUrl: String = "",

    /** 当前播放位置（毫秒） */
    val currentPositionMs: Long = 0L,
    /** 总时长（毫秒） */
    val durationMs: Long = 0L,
    /** 已缓冲时长（毫秒） */
    val bufferedPositionMs: Long = 0L,

    /** 倍速 */
    val playbackSpeed: Float = Constants.DEFAULT_PLAYBACK_SPEED,
    /** 是否静音 */
    val isMuted: Boolean = false,
    /** 当前音量 0-1 */
    val volume: Float = 1f,

    /** A-B 循环状态 */
    val abLoopState: AbLoopState = AbLoopState.OFF,
    val aLoopPositionMs: Long = -1L,
    val bLoopPositionMs: Long = -1L,

    /** 字幕是否开启 */
    val subtitleEnabled: Boolean = true,

    /** 可用音轨 */
    val audioTracks: List<TrackInfo> = emptyList(),
    /** 可用字幕轨 */
    val subtitleTracks: List<TrackInfo> = emptyList(),
    /** 可用清晰度（视频轨） */
    val qualityTracks: List<TrackInfo> = emptyList(),

    /** 是否硬解（true=硬解，false=软解） */
    val isHardwareDecode: Boolean = true,

    /** 循环模式（Player.REPEAT_MODE_*） */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,

    /** 画幅模式 */
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,

    /** 是否锁屏（儿童锁/防误触） */
    val isLocked: Boolean = false,

    /** 片头跳过位置（片头结束点，-1 表示未启用；每集开头均生效） */
    val introSkipAtMs: Long = -1L,
    /** 片尾跳过提前量（距结尾多少毫秒时切下一集，-1 表示未启用；跨集通用） */
    val outroSkipMs: Long = -1L,

    /** 上一集/下一集是否可用 */
    val hasPrevEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,

    /** 当前剧集列表（名称 to 地址）与播放索引，内嵌/全屏共享 */
    val episodes: List<Pair<String, String>> = emptyList(),
    val episodeIndex: Int = 0,

    /** 视频分辨率（像素，0 表示未知） */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val initMutex = Mutex()

    private var memoryManager: PlaybackMemoryManager? = null
    val sleepTimer = SleepTimerManager(viewModelScope)

    /** 应用上下文（播放器全局共享，剧集切换/重试时复用，无需 UI 传 Context） */
    private var appContext: Context? = null

    /** 当前剧集列表与索引，用于上一集/下一集 */
    private var episodes: List<Pair<String, String>> = emptyList() // name to url
    private var currentEpisodeIndex: Int = 0

    /** 播放记录相关 */
    private var currentVodId: Int = 0
    private var currentVodPic: String = ""
    private var currentEpisodeName: String = ""
    /** 当前影片来源 API 源地址，用于播放历史按来源隔离 */
    private var currentSourceUrl: String = ""

    /** 直播模式：点播型短列表 ENDED 时自动重播/换线路 */
    private var isLiveMode: Boolean = false
    private var liveReplayCount: Int = 0

    /** 直播看门狗：长时间停滞（缓冲卡死）时回拉直播边缘 */
    private var liveStallSinceMs: Long = 0L
    private var liveLastPositionMs: Long = -1L
    private var liveLastKickMs: Long = 0L
    private var liveKickCount: Int = 0
    /** 回拉达上限后是否已通知换线路（避免重复发事件） */
    private var liveDeadNotified: Boolean = false
    /** 直播 onPlayerError 后回拉自救的次数（READY 恢复后清零） */
    private var liveErrorRetryCount: Int = 0

    /** 直播线路无效事件（重播后仍结束）：TV 页收集后自动换线路 */
    val liveEndedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 进度轮询 Job */
    private var positionPollingJob: Job? = null

    @UnstableApi
    fun initPlayer(
        context: Context,
        url: String,
        title: String,
        episodes: List<Pair<String, String>> = emptyList(),
        episodeIndex: Int = 0,
        vodId: Int = 0,
        vodPic: String = "",
        sourceUrl: String = "",
        isLive: Boolean = false,
    ) {
        appContext = context.applicationContext
        this.episodes = episodes
        this.currentEpisodeIndex = episodeIndex
        this.currentVodId = vodId
        this.currentVodPic = vodPic
        this.currentSourceUrl = sourceUrl
        this.currentEpisodeName = episodes.getOrNull(episodeIndex)?.first ?: title
        this.isLiveMode = isLive
        this.liveReplayCount = 0
        this.liveStallSinceMs = 0L
        this.liveLastPositionMs = -1L
        this.liveLastKickMs = 0L
        this.liveKickCount = 0
        this.liveDeadNotified = false
        this.liveErrorRetryCount = 0
        _uiState.update {
            it.copy(
                title = title,
                url = url,
                error = null,
                episodes = episodes,
                episodeIndex = episodeIndex,
                hasPrevEpisode = episodeIndex > 0,
                hasNextEpisode = episodes.isNotEmpty() && episodeIndex < episodes.size - 1,
                repeatMode = Player.REPEAT_MODE_OFF,
                introSkipAtMs = -1L,
                outroSkipMs = -1L,
            )
        }
        loadPlayer(url)
        loadSavedSkipSettings(vodId)
    }

    /**
     * 创建/重建播放器加载指定地址。
     * 全局同一时刻只存在一个 ExoPlayer：旧实例在互斥锁内先释放再创建。
     * 内嵌页与全屏页共享同一个 ViewModel，切换全屏时无需调用本方法。
     */
    @UnstableApi
    private fun loadPlayer(url: String) {
        val context = appContext ?: return
        viewModelScope.launch {
            initMutex.withLock {
                releasePlayerInternal()
                memoryManager = PlaybackMemoryManager(context)
                // 无来源的播放（如首页直接点入）：开播时立刻绑定当前点播源，
                // 避免保存历史前用户切换源导致记录绑到错误来源
                if (!isLiveMode && currentSourceUrl.isBlank()) {
                    currentSourceUrl = runCatching {
                        AppContainer.apiSourceRepository.currentApiUrlOnce()
                    }.getOrDefault("")
                }
                val resolvedUrl = resolvePlayUrl(url)
                _uiState.update { it.copy(resolvedUrl = resolvedUrl) }
                AppLog.i(
                    "Player",
                    "加载${if (isLiveMode) "直播" else "点播"}: ${_uiState.value.title.take(40)} -> $resolvedUrl",
                )

                val prefs = AppContainer.preferences
                val bufferMultiplier = prefs.bufferMultiplier.first()
                val preloadEnabled = prefs.preloadEnabled.first()
                val preloadCapacityMb = prefs.preloadCapacityMb.first()
                val preloadTimeS = prefs.preloadTimeS.first()
                val adFilterEnabled = prefs.adFilterEnabled.first()

                runCatching {
                    // 直播使用小缓冲低延迟专用 LoadControl；点播沿用用户缓存设置
                    val loadControl = if (isLiveMode) {
                        com.shiping.app.data.player.LiveHlsSupport.createLoadControl()
                    } else {
                        buildLoadControl(
                            bufferMultiplier, preloadEnabled, preloadCapacityMb, preloadTimeS,
                        )
                    }

                    // 软解/硬解渲染工厂
                    val renderersFactory = DefaultRenderersFactory(context).apply {
                        setEnableDecoderFallback(true)
                        setExtensionRendererMode(
                            if (_uiState.value.isHardwareDecode) {
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                            } else {
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            },
                        )
                    }

                    val trackSelector = DefaultTrackSelector(context).apply {
                        parameters = buildUponParameters()
                            .setSelectUndeterminedTextLanguage(true)
                            .build()
                    }

                    // 使用带缓存的数据源工厂：http(s) 走缓存链，content/file 本地 URI 由 DefaultDataSource 处理
                    // 开启去广告时包一层 m3u8 播放列表过滤数据源
                    val baseFactory = androidx.media3.datasource.DefaultDataSource.Factory(
                        context,
                        DownloadManagerHolder.getCacheDataSourceFactory(),
                    )
                    val dataSourceFactory = if (adFilterEnabled) {
                        AdFilterDataSourceFactory(baseFactory)
                    } else {
                        baseFactory
                    }
                    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                    val exoPlayer = ExoPlayer.Builder(context)
                        .setLoadControl(loadControl)
                        .setRenderersFactory(renderersFactory)
                        .setTrackSelector(trackSelector)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build()
                        .apply {
                            addListener(object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    _uiState.update { it.copy(isPlaying = isPlaying) }
                                }

                                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                    _uiState.update {
                                        it.copy(
                                            videoWidth = videoSize.width.takeIf { w -> w > 0 } ?: 0,
                                            videoHeight = videoSize.height.takeIf { h -> h > 0 } ?: 0,
                                        )
                                    }
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    val stateName = when (playbackState) {
                                        Player.STATE_IDLE -> "IDLE"
                                        Player.STATE_BUFFERING -> "BUFFERING"
                                        Player.STATE_READY -> "READY"
                                        Player.STATE_ENDED -> "ENDED"
                                        else -> "UNKNOWN($playbackState)"
                                    }
                                    val isLiveWindow = runCatching {
                                        isCurrentMediaItemLive
                                    }.getOrDefault(false)
                                    AppLog.d(
                                        "Player",
                                        "状态=$stateName live模式=$isLiveMode live窗口=$isLiveWindow " +
                                            "位置=$currentPosition 时长=$duration",
                                    )
                                    _uiState.update { state ->
                                        state.copy(
                                            isBuffering = playbackState == Player.STATE_BUFFERING,
                                            durationMs = duration.takeIf { it > 0 } ?: state.durationMs,
                                            error = if (playbackState == Player.STATE_IDLE) state.error else null,
                                        )
                                    }
                                    // 直播恢复 READY：清零错误自救计数（短暂停顿恢复后不再累计）
                                    if (isLiveMode && playbackState == Player.STATE_READY) {
                                        liveErrorRetryCount = 0
                                    }
                                    if (playbackState == Player.STATE_ENDED) {
                                        if (isLiveMode) {
                                            // 直播：部分源是未标记滚动的点播型短列表，播完即 ENDED。
                                            // 先自动重播一次；再次结束说明该线路无效，通知 UI 自动换线路
                                            if (liveReplayCount < 1) {
                                                liveReplayCount++
                                                AppLog.w("Player", "直播流 ENDED，尝试从头重播（第 $liveReplayCount 次）")
                                                seekToDefaultPosition()
                                                play()
                                            } else {
                                                AppLog.w("Player", "直播流重播后再次 ENDED，通知换线路")
                                                liveEndedEvent.tryEmit(Unit)
                                            }
                                        } else {
                                            // 点播：播放结束，清除记忆并自动播放下一集
                                            viewModelScope.launch {
                                                memoryManager?.clearRecord(resolvedUrl)
                                            }
                                            if (episodes.isNotEmpty() && currentEpisodeIndex < episodes.size - 1) {
                                                switchToEpisode(currentEpisodeIndex + 1)
                                            }
                                        }
                                    }
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    AppLog.e(
                                        "Player",
                                        "播放错误 code=${error.errorCodeName} " +
                                            "type=${error.javaClass.simpleName} msg=${error.message}",
                                        error,
                                    )
                                    // 直播网络/源错误：先回拉直播边缘自救（重新拉清单），
                                    // 连续超过次数仍失败再上抛错误，由 TV 页自动换线路
                                    val recoverable = error.errorCode ==
                                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                                        error.errorCode ==
                                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                        error.errorCode ==
                                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                                    if (isLiveMode && recoverable && liveErrorRetryCount < LIVE_MAX_ERROR_RETRIES) {
                                        liveErrorRetryCount++
                                        AppLog.w(
                                            "Player",
                                            "直播源错误，回拉直播边缘重新加载（自救第 $liveErrorRetryCount/$LIVE_MAX_ERROR_RETRIES 次）",
                                        )
                                        runCatching {
                                            seekToDefaultPosition()
                                            prepare()
                                            play()
                                        }.onFailure {
                                            AppLog.e("Player", "直播错误自救失败", it)
                                            _uiState.update { s ->
                                                s.copy(error = "播放失败：${error.localizedMessage ?: "未知错误"}")
                                            }
                                        }
                                        return
                                    }
                                    _uiState.update {
                                        it.copy(error = "播放失败：${error.localizedMessage ?: "未知错误"}")
                                    }
                                }

                                override fun onTracksChanged(tracks: Tracks) {
                                    updateTrackInfo(tracks)
                                }
                            })
                            // 直播流强制 live 行为：持续刷新播放列表，避免播完缓冲区后自动停止
                            val mediaItem = if (isLiveMode) {
                                MediaItem.Builder()
                                    .setUri(resolvedUrl)
                                    .setLiveConfiguration(
                                        MediaItem.LiveConfiguration.Builder()
                                            .setTargetOffsetMs(10_000L)
                                            .setMinOffsetMs(5_000L)
                                            .setMaxOffsetMs(30_000L)
                                            .build(),
                                    )
                                    .build()
                            } else {
                                MediaItem.fromUri(resolvedUrl)
                            }
                            if (isLiveMode && isLiveHlsSource(resolvedUrl)) {
                                // 直播 HLS：放宽播放列表卡死判定 + 切片熔断 + 浏览器 UA/短超时/chunkless 起播；
                                // ResolvingDataSource 自动处理无后缀跳转链接（如 B 站直播代理 301 到 m3u8）
                                val liveHlsSource = com.shiping.app.data.player.LiveHlsSupport
                                    .createHlsMediaSource(context, mediaItem)
                                setMediaSource(liveHlsSource)
                            } else {
                                setMediaItem(mediaItem)
                            }
                            prepare()
                            playWhenReady = true
                        }

                    // 续播：恢复上次进度
                    val record = memoryManager?.getRecord(resolvedUrl)
                    if (record != null && record.positionMs in 1 until record.durationMs) {
                        exoPlayer.seekTo(record.positionMs)
                    }

                    _player.value = exoPlayer
                    startPositionPolling()
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = "播放器初始化失败：${throwable.message ?: "未知错误"}")
                    }
                }
            }
        }
    }

    /** 启动进度轮询 */
    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = viewModelScope.launch {
            while (true) {
                val exoPlayer = _player.value
                if (exoPlayer != null) {
                    val position = exoPlayer.currentPosition
                    val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                    val buffered = exoPlayer.bufferedPosition
                    _uiState.update {
                        it.copy(
                            currentPositionMs = position,
                            durationMs = duration,
                            bufferedPositionMs = buffered,
                        )
                    }
                    // 检查片头/片尾自动跳过
                    checkAutoSkip(position, duration)
                    // 检查 A-B 循环
                    checkAbLoop(position)
                    // 直播：停滞看门狗（仅非点播逻辑）
                    if (isLiveMode) checkLiveWatchdog(exoPlayer)
                    // 定期保存播放记忆
                    if (position > 0 && duration > 0 && position % 5_000L < 1_000L) {
                        memoryManager?.savePosition(
                            _uiState.value.resolvedUrl, position, duration,
                        )
                    }
                    // 每 15 秒保存一次播放记录
                    if (position > 0 && duration > 0 && position % 15_000L < 1_000L) {
                        savePlayHistory(position, duration)
                    }
                }
                delay(500L)
            }
        }
    }

    /** 片头片尾自动跳过 */
    private fun checkAutoSkip(position: Long, duration: Long) {
        val state = _uiState.value
        // 片头跳过：仍在片头区间内时快进到设定点；不自动失效，每集开头都会触发
        if (state.introSkipAtMs > 0 && state.introSkipAtMs < duration &&
            position in 0 until state.introSkipAtMs
        ) {
            _player.value?.seekTo(state.introSkipAtMs)
        }
        // 片尾跳过：按距结尾的相对时间判断，跨集通用；触发后播至结尾自动连播下一集
        val outroSkipMs = state.outroSkipMs
        if (outroSkipMs > 0 && duration > 0 && position >= duration - outroSkipMs && position < duration) {
            _player.value?.seekTo(duration)
        }
    }

    /** A-B 循环检查 */
    private fun checkAbLoop(position: Long) {
        val state = _uiState.value
        if (state.abLoopState == AbLoopState.BOTH_SET &&
            state.bLoopPositionMs in 0 until state.aLoopPositionMs + 1
        ) {
            if (position >= state.bLoopPositionMs) {
                _player.value?.seekTo(state.aLoopPositionMs)
            }
        }
    }

    /** 更新音轨/字幕/清晰度信息 */
    private fun updateTrackInfo(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()
        val qualityTracks = mutableListOf<TrackInfo>()

        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.length == 0) return@forEachIndexed
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        val label = buildAudioLabel(format)
                        audioTracks.add(TrackInfo(groupIndex * 100 + i, label, isSelected))
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val label = format.label ?: "字幕 ${subtitleTracks.size + 1}"
                        subtitleTracks.add(TrackInfo(groupIndex * 100 + i, label, isSelected))
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        val label = buildQualityLabel(format)
                        if (!qualityTracks.any { it.label == label }) {
                            qualityTracks.add(TrackInfo(groupIndex * 100 + i, label, isSelected))
                        }
                    }
                }
            }
        }
        _uiState.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                qualityTracks = qualityTracks,
            )
        }
    }

    private fun buildAudioLabel(format: Format): String {
        val lang = format.language ?: "默认"
        val channels = format.channelCount ?: 2
        return "${lang} (${channels}ch)"
    }

    private fun buildQualityLabel(format: Format): String {
        val height = format.height ?: return "自动"
        return when {
            height >= 2160 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            height >= 480 -> "480P"
            height >= 360 -> "360P"
            else -> "${height}P"
        }
    }

    // region 播放控制

    fun togglePlayPause() {
        val exoPlayer = _player.value ?: return
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun play() {
        _player.value?.play()
    }

    fun pause() {
        _player.value?.pause()
    }

    /** 跳转到指定位置 */
    fun seekTo(positionMs: Long) {
        val exoPlayer = _player.value ?: return
        val target = positionMs.coerceIn(0L, exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exoPlayer.seekTo(target)
    }

    /** 相对跳转（毫秒，正快进负快退） */
    fun seekBy(offsetMs: Long) {
        val exoPlayer = _player.value ?: return
        seekTo(exoPlayer.currentPosition + offsetMs)
    }

    /** 设置倍速 */
    fun setPlaybackSpeed(speed: Float) {
        val exoPlayer = _player.value ?: return
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    /** 长按倍速前的用户倍速，null 表示未处于长按状态 */
    private var speedBeforeLongPress: Float? = null

    /** 长按开始：临时切换到长按倍速（不改变用户选择的倍速状态） */
    fun startLongPressSpeed(speed: Float) {
        val exoPlayer = _player.value ?: return
        if (speedBeforeLongPress == null) {
            speedBeforeLongPress = _uiState.value.playbackSpeed
        }
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    /** 长按结束：恢复长按前的倍速 */
    fun endLongPressSpeed() {
        val restoreSpeed = speedBeforeLongPress ?: return
        speedBeforeLongPress = null
        _player.value?.playbackParameters = PlaybackParameters(restoreSpeed)
    }

    /** 静音切换 */
    fun toggleMute() {
        val exoPlayer = _player.value ?: return
        val newMuted = !_uiState.value.isMuted
        exoPlayer.volume = if (newMuted) 0f else _uiState.value.volume
        _uiState.update { it.copy(isMuted = newMuted) }
    }

    /** 设置音量 0-1 */
    fun setVolume(volume: Float) {
        val exoPlayer = _player.value ?: return
        val v = volume.coerceIn(0f, 1f)
        exoPlayer.volume = v
        _uiState.update { it.copy(volume = v, isMuted = v == 0f) }
    }

    /** 选择音轨 */
    fun selectAudioTrack(trackIndex: Int) {
        selectTrack(C.TRACK_TYPE_AUDIO, trackIndex)
    }

    /** 选择字幕轨 */
    fun selectSubtitleTrack(trackIndex: Int) {
        selectTrack(C.TRACK_TYPE_TEXT, trackIndex)
    }

    /** 选择清晰度（视频轨） */
    fun selectQualityTrack(trackIndex: Int) {
        selectTrack(C.TRACK_TYPE_VIDEO, trackIndex)
    }

    private fun selectTrack(trackType: @C.TrackType Int, trackIndex: Int) {
        val exoPlayer = _player.value ?: return
        val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return
        val groupIndex = trackIndex / 100
        val trackInGroup = trackIndex % 100
        val groups = exoPlayer.currentTracks.groups
        if (groupIndex >= groups.size) return
        val group = groups[groupIndex]
        if (trackInGroup >= group.length) return

        runCatching {
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .setTrackTypeDisabled(trackType, false)
                .clearOverridesOfType(trackType)
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(
                        group.mediaTrackGroup,
                        listOf(trackInGroup),
                    ),
                )
                .build()
        }
    }

    /** 字幕开关 */
    fun toggleSubtitle() {
        val exoPlayer = _player.value ?: return
        val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return
        val newEnabled = !_uiState.value.subtitleEnabled
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !newEnabled)
            .build()
        _uiState.update { it.copy(subtitleEnabled = newEnabled) }
    }

    // endregion

    // region A-B 循环

    /** 设置 A 点 */
    fun setLoopA() {
        val position = _player.value?.currentPosition ?: return
        _uiState.update {
            it.copy(
                aLoopPositionMs = position,
                abLoopState = AbLoopState.A_SET,
                bLoopPositionMs = -1L,
            )
        }
    }

    /** 设置 B 点 */
    fun setLoopB() {
        val state = _uiState.value
        if (state.abLoopState != AbLoopState.A_SET) return
        val position = _player.value?.currentPosition ?: return
        if (position <= state.aLoopPositionMs) return
        _uiState.update {
            it.copy(bLoopPositionMs = position, abLoopState = AbLoopState.BOTH_SET)
        }
        _player.value?.seekTo(state.aLoopPositionMs)
    }

    /** 取消 A-B 循环 */
    fun clearAbLoop() {
        _uiState.update {
            it.copy(
                abLoopState = AbLoopState.OFF,
                aLoopPositionMs = -1L,
                bLoopPositionMs = -1L,
            )
        }
    }

    // endregion

    // region 逐帧

    /** 上一帧 */
    fun stepBackFrame() {
        val exoPlayer = _player.value ?: return
        exoPlayer.pause()
        seekTo(exoPlayer.currentPosition - Constants.FRAME_STEP_MS)
    }

    /** 下一帧 */
    fun stepForwardFrame() {
        val exoPlayer = _player.value ?: return
        exoPlayer.pause()
        seekTo(exoPlayer.currentPosition + Constants.FRAME_STEP_MS)
    }

    // endregion

    // region 片头片尾

    /** 设置片头跳转到 [skipAtMs] 位置 */
    fun setIntroSkip(skipAtMs: Long) {
        _uiState.update { it.copy(introSkipAtMs = skipAtMs) }
        persistSkipSettings()
    }

    /** 设置片头跳过秒数（自动跳过开头 [seconds] 秒） */
    fun setIntroSkipSeconds(seconds: Int) = setIntroSkip(seconds * 1000L)

    /** 清除片头跳过 */
    fun clearIntroSkip() {
        setIntroSkip(-1L)
    }

    /** 设置片尾跳过（距结尾 [beforeEndMs] 毫秒时自动切下一集，跨集通用） */
    fun setOutroSkip(beforeEndMs: Long) {
        _uiState.update { it.copy(outroSkipMs = beforeEndMs) }
        persistSkipSettings()
    }

    /** 设置片尾跳过秒数（距结束 [seconds] 秒时自动切下一集） */
    fun setOutroSkipSeconds(seconds: Int) = setOutroSkip(seconds * 1000L)

    /** 清除片尾跳过 */
    fun clearOutroSkip() {
        setOutroSkip(-1L)
    }

    /** 将当前片头片尾设置按影片持久化（0 表示未设置） */
    private fun persistSkipSettings() {
        val vodId = currentVodId
        if (vodId <= 0) return
        val introSeconds = (_uiState.value.introSkipAtMs / 1000L).toInt().coerceIn(0, 300)
        val outroSeconds = (_uiState.value.outroSkipMs / 1000L).toInt().coerceIn(0, 300)
        val prefs = AppContainer.preferences
        viewModelScope.launch {
            prefs.setIntroSkipSeconds(vodId, introSeconds)
            prefs.setOutroSkipSeconds(vodId, outroSeconds)
        }
    }

    /** 进入影片时恢复该影片已保存的片头片尾设置 */
    private fun loadSavedSkipSettings(vodId: Int) {
        if (vodId <= 0) return
        val prefs = AppContainer.preferences
        viewModelScope.launch {
            val introSeconds = prefs.introSkipSeconds(vodId).first()
            val outroSeconds = prefs.outroSkipSeconds(vodId).first()
            _uiState.update {
                it.copy(
                    introSkipAtMs = if (introSeconds > 0) introSeconds * 1000L else -1L,
                    outroSkipMs = if (outroSeconds > 0) outroSeconds * 1000L else -1L,
                )
            }
        }
    }

    // endregion

    // region 剧集切换

    fun onPrevEpisode(): Boolean {
        if (currentEpisodeIndex <= 0) return false
        switchToEpisode(currentEpisodeIndex - 1)
        return true
    }

    fun onNextEpisode(): Boolean {
        if (currentEpisodeIndex >= episodes.size - 1) return false
        switchToEpisode(currentEpisodeIndex + 1)
        return true
    }

    /** 直接选择指定剧集 */
    fun selectEpisode(index: Int) {
        if (index !in episodes.indices) return
        if (index == currentEpisodeIndex) return
        switchToEpisode(index)
    }

    /** 切换剧集：更新索引/状态并直接重建播放器（内嵌与全屏共享，无需 UI 中转） */
    @UnstableApi
    private fun switchToEpisode(index: Int) {
        currentEpisodeIndex = index
        val (name, url) = episodes[index]
        currentEpisodeName = name
        _uiState.update {
            it.copy(
                title = name,
                url = url,
                error = null,
                episodeIndex = index,
                hasPrevEpisode = index > 0,
                hasNextEpisode = index < episodes.size - 1,
            )
        }
        loadPlayer(url)
    }

    // endregion

    // region 锁屏

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    // endregion

    // region 软解硬解

    /** 切换软解/硬解（需要重新创建播放器） */
    fun toggleDecodeMode() {
        val context = appContext ?: return
        val newHardware = !_uiState.value.isHardwareDecode
        _uiState.update { it.copy(isHardwareDecode = newHardware) }
        // 重新创建播放器
        initPlayer(
            context,
            _uiState.value.url,
            _uiState.value.title,
            episodes,
            currentEpisodeIndex,
            currentVodId,
            currentVodPic,
            currentSourceUrl,
        )
    }

    // endregion

    // region 重播/循环/画幅

    /** 从头重播 */
    fun replay() {
        val exoPlayer = _player.value ?: return
        exoPlayer.seekTo(0L)
        exoPlayer.play()
    }

    /** 切换循环模式：关闭 -> 单集循环 -> 列表循环 -> 关闭 */
    fun toggleRepeatMode() {
        val exoPlayer = _player.value ?: return
        val next = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = next
        _uiState.update { it.copy(repeatMode = next) }
    }

    /** 轮切换画幅：原始 -> 16:9 -> 4:3 -> 填充 -> 裁剪 */
    fun cycleAspectRatio() {
        _uiState.update { it.copy(aspectRatio = it.aspectRatio.next()) }
    }

    // endregion

    // region 重试

    /** 播放失败后使用当前地址重新加载 */
    fun retry() {
        val context = appContext ?: return
        initPlayer(
            context,
            _uiState.value.url,
            _uiState.value.title,
            episodes,
            currentEpisodeIndex,
            currentVodId,
            currentVodPic,
            currentSourceUrl,
        )
    }

    // endregion

    // region 私有方法

    private suspend fun resolvePlayUrl(url: String): String {
        // 本地 URI（相册 content://、file://）直接播放，不经过远程解析代理
        if (url.startsWith("content://") || url.startsWith("file://") ||
            url.startsWith("asset://") || url.startsWith("android.resource://")
        ) {
            return url
        }
        // 直播频道一律直连：m3u 源中的无后缀跳转链接（如 B 站直播代理
        // https://live.ottiptv.cc/bilibili/房间号 会 301 到真实 m3u8）由播放器侧解析，
        // 不能走点播解析接口
        if (isLiveMode) return url
        if (isDirectStream(url)) return url
        val parserUrl = AppContainer.parseSourceRepository.currentParserUrl()
        return parserUrl + URLEncoder.encode(url, "UTF-8")
    }

    private fun isDirectStream(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return Constants.DIRECT_STREAM_SUFFIXES.any { path.endsWith(it) }
    }

    /** 判断是否为 HLS 地址（含经参数/代理携带 .m3u8 的情况） */
    private fun isHlsUrl(url: String): Boolean =
        url.substringBefore("#").lowercase().contains(".m3u8")

    /**
     * 判断直播地址是否应走 HLS 管线：
     * - 本身就是 .m3u8 直链；或
     * - 无后缀跳转链接（如 B 站直播代理 /bilibili/房间号），预解析重定向后终态为 m3u8
     *
     * 预解析结果在 LiveHlsSupport 内带 30 秒缓存，随后播放器真正请求时直接命中。
     */
    private suspend fun isLiveHlsSource(url: String): Boolean {
        if (isHlsUrl(url)) return true
        if (com.shiping.app.data.player.LiveHlsSupport.hasMediaSuffix(url)) return false
        val final = withContext(Dispatchers.IO) {
            runCatching {
                com.shiping.app.data.player.LiveHlsSupport.resolveFinalUrl(url)
            }.getOrNull()
        } ?: return false
        return isHlsUrl(final)
    }

    /**
     * 直播停滞看门狗：播放位置持续不增长（缓冲卡死，或 READY 但画面冻结）超过阈值时，
     * 主动 seek 回直播边缘并重新 prepare，让播放列表重新加载；多次无效则放弃（自动换线路）。
     */
    private fun checkLiveWatchdog(player: ExoPlayer) {
        // 用户主动暂停/结束/出错时不干预
        if (!player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            liveStallSinceMs = 0L
            liveLastPositionMs = player.currentPosition
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val pos = player.currentPosition
        // 仅以位置是否增长为准：死流可能停在 BUFFERING（isPlaying=false），
        // 也可能停在 READY 冻结（isPlaying=true），两者都算停滞
        val stalled = pos == liveLastPositionMs
        if (!stalled) {
            // 正常播放或位置在推进
            liveStallSinceMs = 0L
            liveLastPositionMs = pos
            return
        }
        if (liveStallSinceMs == 0L) liveStallSinceMs = now
        val stalledFor = now - liveStallSinceMs
        val sinceKick = now - liveLastKickMs
        if (stalledFor < LIVE_STALL_KICK_AFTER_MS || sinceKick < LIVE_KICK_INTERVAL_MS) return
        if (liveKickCount >= LIVE_MAX_KICKS) {
            // 多次回拉仍停滞：判定该线路为死流，通知 TV 页自动换线路（只发一次，换线后 initPlayer 重置）
            if (!liveDeadNotified) {
                liveDeadNotified = true
                liveStallSinceMs = now
                AppLog.w(
                    "Player",
                    "直播连续 $LIVE_MAX_KICKS 次回拉仍停滞，判定线路无效，通知自动换线路",
                )
                liveEndedEvent.tryEmit(Unit)
            }
            return
        }
        liveLastKickMs = now
        liveKickCount++
        liveStallSinceMs = now
        AppLog.w(
            "Player",
            "直播停滞 ${stalledFor}ms，回拉直播边缘重新缓冲（第 $liveKickCount/$LIVE_MAX_KICKS 次）",
        )
        runCatching {
            player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }.onFailure { AppLog.e("Player", "直播回拉失败", it) }
    }

    @UnstableApi
    private fun buildLoadControl(
        bufferMultiplier: Int,
        preloadEnabled: Boolean,
        preloadCapacityMb: Int,
        preloadTimeS: Int,
    ): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
        if (preloadEnabled) {
            val minBufferMs = preloadTimeS * 1000
            val maxBufferMs = (preloadCapacityMb * 1000).coerceAtLeast(minBufferMs)
            builder.setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
        } else {
            val bufferMs = Constants.DEFAULT_BUFFER_MS * bufferMultiplier
            builder.setBufferDurationsMs(
                bufferMs,
                bufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * bufferMultiplier,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * bufferMultiplier,
            )
        }
        return builder.build()
    }

    private fun releasePlayerInternal() {
        positionPollingJob?.cancel()
        positionPollingJob = null
        runCatching { _player.value?.release() }
        _player.value = null
        speedBeforeLongPress = null
        sleepTimer.cancel()
    }

    fun releasePlayer() {
        viewModelScope.launch {
            saveProgressAndReleaseInternal()
        }
    }

    // region 横屏自动全屏单向开关

    /**
     * 横屏自动进全屏的单向开关：进入全屏页时关闭，退出后延迟复位。
     * 详情页因导航已被销毁重建，旋转事件无法区分"新横屏"与"返回时仍处横屏"，
     * 用共享 ViewModel 的开关避免返回详情页后立刻再次进入全屏。
     */
    var autoFullscreenArmed = true
        private set

    private var reArmAutoFullscreenJob: Job? = null

    /** 关闭横屏自动全屏（进入全屏页时调用） */
    fun disarmAutoFullscreen() {
        reArmAutoFullscreenJob?.cancel()
        autoFullscreenArmed = false
    }

    /** 延迟复位横屏自动全屏（退出全屏页时调用） */
    fun reArmAutoFullscreenDelayed() {
        reArmAutoFullscreenJob?.cancel()
        reArmAutoFullscreenJob = viewModelScope.launch {
            delay(Constants.AUTO_FULLSCREEN_REARM_DELAY_MS)
            autoFullscreenArmed = true
        }
    }

    // endregion

    /**
     * 进入新详情页时检查：若与正在播放的内容不是同一影片（vodId + sourceUrl 不同），
     * 保存进度并释放旧播放器、清空 UI 状态，避免旧片名/旧画面串到新详情页。
     */
    fun resetIfContentDiffers(vodId: Int, sourceUrl: String) {
        val sameContent = currentVodId > 0 && currentVodId == vodId && currentSourceUrl == sourceUrl
        if (sameContent) return
        // 播放器本来就空闲，无需重置
        if (_player.value == null && _uiState.value.url.isEmpty()) return
        viewModelScope.launch {
            saveProgressAndReleaseInternal(clearState = true)
        }
    }

    /** 保存最终进度与播放记录后释放播放器；[clearState] 为 true 时同时清空 UI 状态 */
    private suspend fun saveProgressAndReleaseInternal(clearState: Boolean = false) {
        val exoPlayer = _player.value
        val url = _uiState.value.resolvedUrl
        if (exoPlayer != null && url.isNotEmpty()) {
            memoryManager?.savePosition(url, exoPlayer.currentPosition, exoPlayer.duration)
            savePlayHistory(exoPlayer.currentPosition, exoPlayer.duration)
        }
        initMutex.withLock { releasePlayerInternal() }
        if (clearState) {
            _uiState.value = PlayerUiState()
        }
    }

    /** 保存播放记录到数据库 */
    private suspend fun savePlayHistory(positionMs: Long, durationMs: Long) {
        if (currentVodId <= 0) return
        if (positionMs < Constants.PLAYBACK_MEMORY_MIN_MS) return
        if (durationMs > 0 && durationMs - positionMs < Constants.RESUME_THRESHOLD_MS) return
        // 来源缺失时（如首页直接播放未携带源）用实际使用的点播源补全，保证历史可回跳
        val sourceKey = currentSourceUrl.ifBlank {
            runCatching { AppContainer.apiSourceRepository.currentApiUrlOnce() }.getOrDefault("")
        }
        runCatching {
            AppContainer.playHistoryRepository.save(
                com.shiping.app.data.model.PlayHistoryEntity(
                    vodId = currentVodId,
                    sourceUrl = sourceKey,
                    title = _uiState.value.title,
                    vodPic = currentVodPic,
                    lastEpisodeName = currentEpisodeName,
                    lastPositionMs = positionMs,
                    durationMs = durationMs,
                    watchedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override fun onCleared() {
        releasePlayerInternal()
        super.onCleared()
    }

    // endregion

    companion object {
        /** 直播停滞多久后回拉直播边缘 */
        private const val LIVE_STALL_KICK_AFTER_MS = 15_000L
        /** 两次回拉的最小间隔 */
        private const val LIVE_KICK_INTERVAL_MS = 20_000L
        /** 单次播放中最多回拉次数，超过后判定死流并自动换线路 */
        private const val LIVE_MAX_KICKS = 2
        /** 直播源错误（IO）回拉自救的最大次数，超过后上抛错误触发换线路 */
        private const val LIVE_MAX_ERROR_RETRIES = 2
    }
}
