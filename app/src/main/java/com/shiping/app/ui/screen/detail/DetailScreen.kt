package com.shiping.app.ui.screen.detail

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shiping.app.data.model.PlayEpisode
import com.shiping.app.data.model.PlaySource
import com.shiping.app.data.model.Vod
import com.shiping.app.ui.component.EmptyView
import com.shiping.app.ui.component.ErrorView
import com.shiping.app.ui.component.LoadingView
import com.shiping.app.ui.components.EpisodeSelectionDialog
import com.shiping.app.di.AppContainer
import com.shiping.app.ui.screen.player.PlayerGestures
import com.shiping.app.ui.screen.player.PlayerUiState
import com.shiping.app.ui.screen.player.PlayerViewModel
import com.shiping.app.util.Constants
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vodId: Int,
    sourceUrl: String = "",
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onFullscreen: (title: String, url: String) -> Unit,
    onSearchTitle: (String) -> Unit = {},
    onRecommendClick: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val player by playerViewModel.player.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val longPressSpeed by AppContainer.preferences.longPressSpeed.collectAsState(initial = Constants.LONG_PRESS_SPEED)
    val isFavorite by viewModel.observeFavorite(vodId, sourceUrl).collectAsState(initial = false)
    var showDownloadDialog by remember { mutableStateOf(false) }

    // 内嵌播放器控制栏可见性（自动隐藏）
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(vodId) { viewModel.loadDetail(vodId, sourceUrl) }

    // 进入详情页时，若共享播放器正在播的是另一部影片，先保存进度并重置，
    // 避免旧影片的画面/片名串到当前详情页
    LaunchedEffect(uiState.vod) {
        val vod = uiState.vod ?: return@LaunchedEffect
        playerViewModel.resetIfContentDiffers(vod.vodId, sourceUrl)
    }

    // 横屏时自动进入全屏播放页（竖屏下由用户通过全屏按钮或返回键控制）
    // 单向开关：进入过全屏后关闭，退出延迟复位，避免返回时因仍处横屏而再次进入
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            playerUiState.url.isNotBlank() &&
            playerViewModel.autoFullscreenArmed
        ) {
            playerViewModel.disarmAutoFullscreen()
            onFullscreen(playerUiState.title, playerUiState.url)
        }
    }

    // 控制栏自动隐藏：播放中 3 秒后隐藏
    LaunchedEffect(controlsVisible, playerUiState.isPlaying) {
        if (controlsVisible && playerUiState.isPlaying && playerUiState.error == null) {
            delay(Constants.CONTROL_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // 播放器为全局共享实例：离开详情页/退出全屏时由导航层统一释放并恢复亮度，
    // 此处无需处理（切换到全屏时播放器保持播放，实现无缝交接）

    Scaffold { padding ->
        when {
            uiState.isLoading -> LoadingView(Modifier.padding(padding))
            uiState.error != null -> ErrorView(
                message = uiState.error ?: "加载失败",
                onRetry = { viewModel.loadDetail(vodId, sourceUrl) },
                modifier = Modifier.padding(padding),
            )
            uiState.vod == null -> EmptyView(
                message = "视频不存在",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val vod = uiState.vod!!
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 上：播放器区域（左上角悬浮返回键，自适应画面比例）
                        PlayerArea(
                            vod = vod,
                            player = player,
                            playerUiState = playerUiState,
                            currentSource = uiState.currentSource,
                            episodes = playerUiState.episodes,
                            currentEpisodeIndex = playerUiState.episodeIndex,
                            controlsVisible = controlsVisible,
                            onBack = onBack,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onPlayFirst = { url, title, episodes, index ->
                            playerViewModel.initPlayer(
                                context = context,
                                url = url,
                                title = title,
                                episodes = episodes,
                                episodeIndex = index,
                                vodId = vod.vodId,
                                vodPic = vod.safePic,
                                sourceUrl = sourceUrl,
                            )
                        },
                        onFullscreen = {
                            // 全屏接管同一播放器实例：不暂停、不重新缓冲，画面无缝切换
                            if (playerUiState.url.isNotBlank()) {
                                onFullscreen(playerUiState.title, playerUiState.url)
                            }
                        },
                        onTogglePlay = {
                            // 播放结束时点击播放从头开始
                            if (!playerUiState.isPlaying && playerUiState.durationMs > 0 &&
                                playerUiState.currentPositionMs >= playerUiState.durationMs - 500
                            ) {
                                playerViewModel.seekTo(0)
                            }
                            playerViewModel.togglePlayPause()
                            controlsVisible = true
                        },
                        onSeek = { playerViewModel.seekTo(it) },
                        onSpeedSelect = { playerViewModel.setPlaybackSpeed(it) },
                        onSeekBy = { playerViewModel.seekBy(it) },
                        onBrightnessChange = { value ->
                            activity?.window?.let { w ->
                                val params = w.attributes
                                params.screenBrightness = value.coerceIn(0f, 1f)
                                w.attributes = params
                            }
                        },
                        onVolumeChange = { playerViewModel.setVolume(it) },
                        onLongPressStart = {
                            playerViewModel.startLongPressSpeed(longPressSpeed)
                        },
                        onLongPressEnd = {
                            playerViewModel.endLongPressSpeed()
                        },
                        onPrev = { playerViewModel.onPrevEpisode() },
                        onNext = { playerViewModel.onNextEpisode() },
                        onEpisodeSelect = { playerViewModel.selectEpisode(it) },
                    )

                    // 下：详情区域（可滚动）
                    DetailContent(
                        state = uiState,
                        currentEpisodeIndex = playerUiState.episodeIndex,
                        onSourceSelect = viewModel::selectSource,
                        onPlay = { title, url, episodes, index, playVodId, vodPic ->
                            playerViewModel.initPlayer(
                                context,
                                url,
                                title,
                                episodes,
                                index,
                                playVodId,
                                vodPic,
                                sourceUrl,
                            )
                        },
                        onDownload = { showDownloadDialog = true },
                        isFavorite = isFavorite,
                        onToggleFavorite = { viewModel.toggleFavorite(vodId, sourceUrl) },
                        onSearchTitle = onSearchTitle,
                        onRecommendClick = onRecommendClick,
                        modifier = Modifier.weight(1f),
                    )
                    }
                }
            }
        }
    }

    // 缓存下载对话框
    if (showDownloadDialog) {
        val currentSource = uiState.currentSource
        val episodes = currentSource?.episodes ?: emptyList()
        DownloadDialog(
            episodes = episodes.map { it.name to it.url },
            vodName = uiState.vod?.vodName ?: "",
            vodPic = uiState.vod?.safePic ?: "",
            onDismiss = { showDownloadDialog = false },
            onDownload = { showDownloadDialog = false },
        )
    }
}

/** 内嵌播放器区域：未播放时显示封面，播放时显示精简控制栏 + 手势，画面比例自适应视频 */
@Composable
private fun PlayerArea(
    vod: Vod,
    player: ExoPlayer?,
    playerUiState: PlayerUiState,
    currentSource: PlaySource?,
    episodes: List<Pair<String, String>>,
    currentEpisodeIndex: Int,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onPlayFirst: (url: String, title: String, episodes: List<Pair<String, String>>, index: Int) -> Unit,
    onFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onSeekBy: (Long) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEpisodeSelect: (Int) -> Unit,
) {
    // 自适应视频画面比例（默认 16:9，随视频实际尺寸更新）
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }

    // 监听视频尺寸变化，自适应画面比例
    DisposableEffect(player) {
        val current = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio =
                        (videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                            .coerceIn(Constants.INLINE_PLAYER_MIN_ASPECT, Constants.INLINE_PLAYER_MAX_ASPECT)
                }
            }
        }
        current.addListener(listener)
        onDispose { current.removeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(videoAspectRatio)
            .background(Color.Black),
    ) {

        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { it.player = player },
                // 离开内嵌页（进入全屏）时解绑 surface；共享播放器继续播放
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            // 手势层（双击播放/暂停，单击切换控制栏，拖动 seek/亮度/音量，长按 3x）
            PlayerGestures(
                isPlaying = playerUiState.isPlaying,
                isLocked = false,
                onTogglePlay = onTogglePlay,
                onSeekBy = onSeekBy,
                onBrightnessChange = onBrightnessChange,
                onVolumeChange = onVolumeChange,
                onLongPressStart = onLongPressStart,
                onLongPressEnd = onLongPressEnd,
                onSingleTap = onToggleControls,
            )
            // 缓冲动画
            if (playerUiState.isBuffering && playerUiState.error == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            // 错误提示
            playerUiState.error?.let { err ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = err,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (controlsVisible) {
                // 底部：进度条（贴视频底边缘）+ 控制按钮
                InlineControlBar(
                    isPlaying = playerUiState.isPlaying,
                    playbackSpeed = playerUiState.playbackSpeed,
                    positionMs = playerUiState.currentPositionMs,
                    durationMs = playerUiState.durationMs,
                    bufferedMs = playerUiState.bufferedPositionMs,
                    hasPrevEpisode = playerUiState.hasPrevEpisode,
                    hasNextEpisode = playerUiState.hasNextEpisode,
                    episodes = episodes,
                    currentEpisodeIndex = currentEpisodeIndex,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    onSpeedSelect = onSpeedSelect,
                    onPrev = onPrev,
                    onNext = onNext,
                    onEpisodeSelect = onEpisodeSelect,
                    onFullscreen = onFullscreen,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        } else {
            // 未播放时显示封面
            AsyncImage(
                model = vod.safePic,
                contentDescription = vod.vodName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 播放按钮（播放首选源第一集）
            val firstEp = currentSource?.episodes?.firstOrNull()
            if (firstEp != null && currentSource != null) {
                val episodeList = currentSource.episodes.map { it.name to it.url }
                IconButton(
                    onClick = { onPlayFirst(firstEp.url, vod.vodName, episodeList, 0) },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        // 左上角悬浮返回按钮（<，背景全透明）
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 内嵌播放器底部控制栏：进度条（贴视频底边缘）+ 控制按钮 */
@Composable
private fun InlineControlBar(
    isPlaying: Boolean,
    playbackSpeed: Float,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    episodes: List<Pair<String, String>>,
    currentEpisodeIndex: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var episodeMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
            ),
        ),
    ) {
        // 进度条行（红线位置：贴视频底边缘）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(positionMs),
                color = Color.White,
                fontSize = 10.sp,
            )
            InlineSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            Text(
                text = formatTime(durationMs),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
            )
        }
        // 控制按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上一集
            if (hasPrevEpisode) {
                IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一集",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // 播放/暂停键
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            // 下一集
            if (hasNextEpisode) {
                IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一集",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // 选集（弹出 5x8 分页表格面板）
            if (episodes.isNotEmpty()) {
                TextButton(
                    onClick = { episodeMenuExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = "选集",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
                if (episodeMenuExpanded) {
                    EpisodeSelectionDialog(
                        episodeNames = episodes.map { it.first },
                        currentIndex = currentEpisodeIndex,
                        title = "选集",
                        onDismiss = { episodeMenuExpanded = false },
                        onSelected = { index ->
                            onEpisodeSelect(index)
                            episodeMenuExpanded = false
                        },
                    )
                }
            }
            // 倍速
            Box {
                TextButton(
                    onClick = { speedMenuExpanded = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false },
                ) {
                    Constants.INLINE_PLAYER_SPEEDS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            trailingIcon = {
                                if (speed == playbackSpeed) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                onSpeedSelect(speed)
                                speedMenuExpanded = false
                            },
                        )
                    }
                }
            }
            // 全屏
            IconButton(onClick = onFullscreen, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "全屏",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 内嵌播放器精简进度条：支持点击与拖动跳转，显示缓冲进度 */
@Composable
private fun InlineSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = (dragFraction
        ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f)
        .coerceIn(0f, 1f)
    val bufferedFraction = if (durationMs > 0) {
        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackHeight = 3.dp
    val thumbSize = 10.dp

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        onSeek((offset.x / size.width.toFloat() * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0) {
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        dragFraction?.let { f ->
                            if (durationMs > 0) onSeek((f * durationMs).toLong())
                        }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    if (durationMs > 0) {
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 底轨
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.25f)),
        )
        // 缓冲轨
        if (bufferedFraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }
        // 已播放轨
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White),
        )
        // 拖动圆点
        Box(
            Modifier
                .offset(x = maxWidth * fraction - thumbSize / 2)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 毫秒格式化为 mm:ss / h:mm:ss */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    currentEpisodeIndex: Int,
    onSourceSelect: (Int) -> Unit,
    onPlay: (title: String, url: String, episodes: List<Pair<String, String>>, index: Int, vodId: Int, vodPic: String) -> Unit,
    onDownload: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSearchTitle: (String) -> Unit,
    onRecommendClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vod = state.vod ?: return
    val currentSource = state.currentSource
    var showEpisodeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 基本信息
        Column(modifier = Modifier.padding(12.dp)) {
            Row() {
                Text(
                text = vod.vodName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { onSearchTitle(vod.vodName) },)

                if (vod.vodRemarks.isNotBlank()) {
                    Badge(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = vod.vodRemarks,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (vod.vodScore.isNotBlank() && vod.vodScore != "0.0") {
                    Badge(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = vod.vodScore,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                // 收藏
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(){
            InfoRow("类型", vod.typeName)
            InfoRow("年份", vod.vodYear)
            InfoRow("地区", vod.vodArea)
            InfoRow("语言", vod.vodLang)  
            }
        }
        // 播放源
        if (state.sources.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    text = "播放源",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sources.forEachIndexed { index, source ->
                        AssistChip(
                            onClick = { onSourceSelect(index) },
                            label = { Text(source.name) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (index == state.selectedSourceIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = if (index == state.selectedSourceIndex) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        )
                    }
                }
            }
        }

        // 剧集列表：横向滚动集数条，点击“选集”弹出 5x8 分页面板
        currentSource?.let { source ->
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${source.name} 选集",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "共 ${source.episodes.size} 集",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showEpisodeDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选集", fontSize = 12.sp)
                    }
                    // 下载/缓存（选集入口右侧）
                    IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "缓存",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    // 面板入口
                    item {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { showEpisodeDialog = true }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "选集",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    val episodeList = source.episodes.map { it.name to it.url }
                    itemsIndexed(source.episodes) { index, ep ->
                        EpisodeItem(
                            ep = ep,
                            selected = index == currentEpisodeIndex,
                        ) { onPlay(vod.vodName, ep.url, episodeList, index, vod.vodId, vod.safePic) }
                    }
                }
            }

            // 选集面板：正/倒序 + 每页 40 集 5x8 表格 + 分段翻页
            if (showEpisodeDialog) {
                EpisodeSelectionDialog(
                    episodeNames = source.episodes.map { it.name },
                    currentIndex = currentEpisodeIndex,
                    title = "${source.name} 选集",
                    onDismiss = { showEpisodeDialog = false },
                    onSelected = { index ->
                        val ep = source.episodes[index]
                        val episodeList = source.episodes.map { it.name to it.url }
                        onPlay(vod.vodName, ep.url, episodeList, index, vod.vodId, vod.safePic)
                        showEpisodeDialog = false
                    },
                )
            }
        } ?: run {
            if (state.sources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无播放地址",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
        }


        // 导演 / 主演
        if (vod.vodDirector.isNotBlank()) {
            InfoSection("导演", vod.vodDirector)
        }
        if (vod.vodActor.isNotBlank()) {
            InfoSection("主演", vod.vodActor)
        }

        // 简介
        if (vod.vodContent.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = vod.vodContent.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }

        // 同类型推荐
        if (state.recommendations.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "同类型推荐",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.recommendations, key = { _, item -> item.vodId }) { _, item ->
                        RecommendationPoster(
                            vod = item,
                            onClick = { onRecommendClick(item.vodId) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 同类型推荐海报卡片 */
@Composable
private fun RecommendationPoster(vod: Vod, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = vod.safePic,
            contentDescription = vod.vodName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = vod.vodName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoSection(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EpisodeItem(ep: PlayEpisode, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ep.name,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
