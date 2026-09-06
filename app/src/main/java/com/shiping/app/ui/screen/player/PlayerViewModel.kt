package com.shiping.app.ui.screen.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class PlayerUiState(
    val title: String = "",
    val url: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val resolvedUrl: String = ""
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    @UnstableApi
    fun initPlayer(context: Context, url: String, title: String) {
        _uiState.update { it.copy(title = title, url = url, error = null) }
        releasePlayer()

        viewModelScope.launch {
            // 解析URL：非直链拼接解析器
            val resolvedUrl = resolvePlayUrl(url)
            _uiState.update { it.copy(resolvedUrl = resolvedUrl) }

            // 获取缓冲/预载参数
            val prefs = AppContainer.preferences
            val bufferMultiplier = prefs.bufferMultiplier.first()
            val preloadEnabled = prefs.preloadEnabled.first()
            val preloadCapacityMb = prefs.preloadCapacityMb.first()
            val preloadTimeS = prefs.preloadTimeS.first()

            runCatching {
                val loadControl = buildLoadControl(
                    bufferMultiplier, preloadEnabled, preloadCapacityMb, preloadTimeS
                )
                exoPlayer = ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build()
                    .apply {
                        addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                _uiState.update { it.copy(isPlaying = isPlaying) }
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                _uiState.update { state ->
                                    state.copy(
                                        isBuffering = playbackState == Player.STATE_BUFFERING,
                                        error = when (playbackState) {
                                            Player.STATE_IDLE -> state.error
                                            else -> null
                                        }
                                    )
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                _uiState.update {
                                    it.copy(error = "播放失败：${error.localizedMessage ?: "未知错误"}")
                                }
                            }
                        })
                        setMediaItem(MediaItem.fromUri(resolvedUrl))
                        prepare()
                        playWhenReady = true
                    }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(error = "播放器初始化失败：${throwable.message ?: "未知错误"}")
                }
            }
        }
    }

    /**
     * 解析播放地址
     * 直链(.m3u8/.mp4等)直接播放，非直链拼接解析器URL
     */
    private suspend fun resolvePlayUrl(url: String): String {
        if (isDirectStream(url)) return url
        // 非直链，拼接解析器
        val parserUrl = AppContainer.parseSourceRepository.currentParserUrl()
        return parserUrl + URLEncoder.encode(url, "UTF-8")
    }

    /**
     * 判断是否为直链
     */
    private fun isDirectStream(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return Constants.DIRECT_STREAM_SUFFIXES.any { path.endsWith(it) }
    }

    /**
     * 构建 LoadControl
     * @param bufferMultiplier 缓冲倍数 1-10
     * @param preloadEnabled 是否启用预载
     * @param preloadCapacityMb 预载容量 MB
     * @param preloadTimeS 预载时间 秒
     */
    @UnstableApi
    private fun buildLoadControl(
        bufferMultiplier: Int,
        preloadEnabled: Boolean,
        preloadCapacityMb: Int,
        preloadTimeS: Int
    ): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()

        if (preloadEnabled) {
            // 预载模式：使用预载参数
            val minBufferMs = preloadTimeS * 1000
            // 将MB近似转换为ms（1MB ≈ 1s视频）
            val maxBufferMs = (preloadCapacityMb * 1000).coerceAtLeast(minBufferMs)
            builder.setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
        } else {
            // 常规模式：使用缓冲倍数
            val baseBufferMs = 50_000 // 默认50秒
            val bufferMs = baseBufferMs * bufferMultiplier
            builder.setBufferDurationsMs(
                bufferMs,
                bufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * bufferMultiplier,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * bufferMultiplier
            )
        }

        return builder.build()
    }

    fun releasePlayer() {
        runCatching {
            exoPlayer?.release()
        }
        exoPlayer = null
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
