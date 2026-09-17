package com.shiping.app.ui.screen.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    title: String,
    url: String,
    viewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.player.collectAsState()
    val sleepTimerRunning by viewModel.sleepTimer.isRunning.collectAsState()
    val longPressSpeed by AppContainer.preferences.longPressSpeed.collectAsState(initial = Constants.LONG_PRESS_SPEED)

    var controlsVisible by remember { mutableStateOf(true) }
    var lockVisible by remember { mutableStateOf(true) }
    var panelType by remember { mutableStateOf(PlayerPanelType.NONE) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // 进入播放器时横屏 + 隐藏状态栏
    DisposableEffect(activity) {
        // 关闭横屏自动全屏，避免返回详情页时因仍处横屏而立刻再次进入
        viewModel.disarmAutoFullscreen()
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            }
            val w = activity?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                val controller = WindowInsetsControllerCompat(w, w.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            // 延迟复位自动全屏开关（详情页可能在退出动画期间重建）
            viewModel.reArmAutoFullscreenDelayed()
            // 播放器为全局共享实例，不在此释放；由导航层统一管理生命周期
        }
    }

    // 独立进入全屏页（如下载通知跳转）时才初始化；
    // 从内嵌页进入时共享播放器已存在，直接接管画面，不重新缓冲
    LaunchedEffect(Unit) {
        if (viewModel.player.value == null) {
            viewModel.initPlayer(context, url, title)
        }
    }

    // 控制栏自动隐藏
    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(Constants.CONTROL_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // 锁屏键 5 秒自动隐藏，点击屏幕重新显示
    LaunchedEffect(lockVisible) {
        if (lockVisible) {
            delay(Constants.LOCK_AUTO_HIDE_MS)
            lockVisible = false
        }
    }

    // 亮度控制
    val setBrightness: (Float) -> Unit = setBrightness@ { value ->
        val window = activity?.window ?: return@setBrightness
        val layoutParams = window.attributes
        layoutParams.screenBrightness = value.coerceIn(0f, 1f)
        window.attributes = layoutParams
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 视频画面：接管全局共享播放器；离开全屏时解绑 surface，播放器仍在播放
        // 画幅：原始/填充/裁剪用 resizeMode，16:9/4:3 用比例约束实现信箱效果
        val surfaceModifier = when (uiState.aspectRatio) {
            VideoAspectRatio.ORIGINAL, VideoAspectRatio.FILL, VideoAspectRatio.CROP -> {
                Modifier.align(Alignment.Center).fillMaxSize()
            }
            VideoAspectRatio.R16_9 -> Modifier.align(Alignment.Center).aspectRatio(16f / 9f)
            VideoAspectRatio.R4_3 -> Modifier.align(Alignment.Center).aspectRatio(4f / 3f)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.resizeMode = when (uiState.aspectRatio) {
                    VideoAspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    VideoAspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { playerView -> playerView.player = null },
            modifier = surfaceModifier,
        )

        // 手势层（在视频上方、控制层下方）
        PlayerGestures(
            isPlaying = uiState.isPlaying,
            isLocked = uiState.isLocked,
            onTogglePlay = {
                viewModel.togglePlayPause()
                controlsVisible = true
            },
            onSeekBy = { offset -> viewModel.seekBy(offset) },
            onBrightnessChange = setBrightness,
            onVolumeChange = { viewModel.setVolume(it) },
            onLongPressStart = { viewModel.startLongPressSpeed(longPressSpeed) },
            onLongPressEnd = { viewModel.endLongPressSpeed() },
            onSingleTap = {
                controlsVisible = !controlsVisible
                lockVisible = true
            },
        )

        // 缓冲动画
        if (uiState.isBuffering && uiState.error == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // 控制层
        if (controlsVisible && !uiState.isLocked) {
            PlayerControls(
                state = uiState,
                onTogglePlay = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it) },
                onPrev = { viewModel.onPrevEpisode() },
                onNext = { viewModel.onNextEpisode() },
                onBack = onBack,
                onToggleMute = { viewModel.toggleMute() },
                onSpeedClick = { panelType = PlayerPanelType.SPEED },
                onQualityClick = { panelType = PlayerPanelType.QUALITY },
                onAudioClick = { panelType = PlayerPanelType.AUDIO },
                onSubtitleClick = { panelType = PlayerPanelType.SUBTITLE },
                onTimerClick = { panelType = PlayerPanelType.SLEEP_TIMER },
                onMorePanelClick = { panelType = PlayerPanelType.MORE },
                onInfoClick = { showInfoDialog = true },
                onToggleDecodeClick = { viewModel.toggleDecodeMode() },
                onRefreshClick = { viewModel.retry() },
                onReplayClick = { viewModel.replay() },
                onToggleRepeat = { viewModel.toggleRepeatMode() },
                onAspectClick = { viewModel.cycleAspectRatio() },
                onCoreClick = {
                    android.widget.Toast.makeText(context, "当前播放内核：EXO（Media3）", android.widget.Toast.LENGTH_SHORT).show()
                },
                onEpisodeListClick = { panelType = PlayerPanelType.EPISODES },
                hasEpisodes = uiState.episodes.isNotEmpty(),
            )
        }

        // 锁定/解锁按钮：中右区域，5 秒自动隐藏，点屏幕重新显示
        if (lockVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(onClick = { viewModel.toggleLock() }) {
                    Icon(
                        if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (uiState.isLocked) "解锁" else "锁屏",
                        tint = Color.White,
                    )
                }
            }
        }

        // 错误提示与重试
        uiState.error?.let { err ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = err,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.retry() }) {
                    Text("重试")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "播放地址可能需要专用解析器，可尝试切换其他播放源",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 睡眠定时提示
        if (sleepTimerRunning) {
            Text(
                text = "睡眠定时：${viewModel.sleepTimer.remainingText}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp),
            )
        }

        // 设置面板
        PlayerSettingsPanel(
            panelType = panelType,
            state = uiState,
            onDismiss = { panelType = PlayerPanelType.NONE },
            onSpeedSelected = {
                viewModel.setPlaybackSpeed(it)
                panelType = PlayerPanelType.NONE
            },
            onQualitySelected = {
                viewModel.selectQualityTrack(it)
                panelType = PlayerPanelType.NONE
            },
            onAudioSelected = {
                viewModel.selectAudioTrack(it)
                panelType = PlayerPanelType.NONE
            },
            onSubtitleSelected = {
                viewModel.selectSubtitleTrack(it)
                panelType = PlayerPanelType.NONE
            },
            onSubtitleToggled = { viewModel.toggleSubtitle() },
            onSleepTimerSelected = { minutes ->
                viewModel.sleepTimer.start(minutes) {
                    viewModel.pause()
                }
                panelType = PlayerPanelType.NONE
            },
            onSleepTimerCancel = {
                viewModel.sleepTimer.cancel()
                panelType = PlayerPanelType.NONE
            },
            onIntroSecondsChange = { seconds ->
                if (seconds > 0) viewModel.setIntroSkipSeconds(seconds) else viewModel.clearIntroSkip()
            },
            onOutroSecondsChange = { seconds ->
                if (seconds > 0) viewModel.setOutroSkipSeconds(seconds) else viewModel.clearOutroSkip()
            },
            onToggleDecode = {
                viewModel.toggleDecodeMode()
                panelType = PlayerPanelType.NONE
            },
            episodes = uiState.episodes,
            currentEpisodeIndex = uiState.episodeIndex,
            onEpisodeSelected = { index ->
                viewModel.selectEpisode(index)
                panelType = PlayerPanelType.NONE
            },
        )

        // 影片信息弹窗
        if (showInfoDialog) {
            MovieInfoDialog(state = uiState, onDismiss = { showInfoDialog = false })
        }
    }
}

/** 影片全部信息弹窗，播放地址支持复制 */
@Composable
private fun MovieInfoDialog(
    state: PlayerUiState,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("影片信息", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InfoRow("片名", state.title)
                val episodeName = state.episodes.getOrNull(state.episodeIndex)?.first
                InfoRow(
                    "剧集",
                    buildString {
                        append(episodeName ?: "无")
                        if (state.episodes.isNotEmpty()) {
                            append("（第 ${state.episodeIndex + 1}/${state.episodes.size} 集）")
                        }
                    },
                )
                InfoRow(
                    "分辨率",
                    if (state.videoWidth > 0 && state.videoHeight > 0) "${state.videoWidth}×${state.videoHeight}" else "未知",
                )
                InfoRow("时长", formatMs(state.durationMs))
                InfoRow(
                    "进度",
                    "${formatMs(state.currentPositionMs)}（已缓冲 ${formatMs(state.bufferedPositionMs)}）",
                )
                InfoRow("倍速", "${state.playbackSpeed}x")
                InfoRow("解码", if (state.isHardwareDecode) "硬件解码" else "软件解码")
                InfoCopyRow("播放地址（原始）", state.url) { copy(state.url) }
                InfoCopyRow("播放地址（实际）", state.resolvedUrl) { copy(state.resolvedUrl) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoCopyRow(label: String, value: String, onCopy: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value.ifBlank { "-" },
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 毫秒格式化为 h:mm:ss 或 mm:ss */
private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
