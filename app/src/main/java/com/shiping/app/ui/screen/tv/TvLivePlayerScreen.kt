package com.shiping.app.ui.screen.tv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.shiping.app.data.model.TvChannel
import com.shiping.app.ui.screen.player.PlayerViewModel
import com.shiping.app.util.Constants
import kotlinx.coroutines.delay

/**
 * 电视直播全屏播放页：横屏铺满 + 左侧分类/频道双列面板 + 自定义三层控制组件。
 * 复用全局共享播放器（进入不重新缓冲），复用 Activity 作用域 TvViewModel 的频道数据。
 */
@UnstableApi
@Composable
fun TvLivePlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    tvViewModel: TvViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by tvViewModel.uiState.collectAsState()
    val sources by tvViewModel.sources.collectAsState()
    val favorites by tvViewModel.favorites.collectAsState()
    val player by playerViewModel.player.collectAsState()
    val pState by playerViewModel.uiState.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(TvAspect.CROP) }
    var mirrored by remember { mutableStateOf(false) }

    // 弹窗状态
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var showCrossDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }

    // 横屏 + 隐藏系统栏；退出恢复
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val w = activity?.window
            if (w != null) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, true)
                val c = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
                c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 控制层 + 左侧列表面板自动隐藏
    LaunchedEffect(controlsVisible, pState.isPlaying, pState.isLocked) {
        if (controlsVisible && pState.isPlaying && !pState.isLocked) {
            delay(Constants.CONTROL_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val locked = pState.isLocked

    // 返回：锁定时先解锁，否则退出全屏
    BackHandler(enabled = true) {
        if (locked) playerViewModel.toggleLock() else onBack()
    }

    // 频道操作
    val playChannel: (TvChannel, Int) -> Unit = { ch, line ->
        tvViewModel.playLine(ch, line)
        playerViewModel.initPlayer(context, ch.urls[line], ch.name, isLive = true)
    }
    val currentList = tvChannelsOfGroup(uiState.groups, uiState.channels, favorites, uiState.selectedGroup)
        .ifEmpty { uiState.channels }
    val switchChannel: (Int) -> Unit = { offset ->
        val current = uiState.currentChannel
        val list = currentList
        if (current != null && list.isNotEmpty()) {
            val idx = list.indexOfFirst { it.favoriteKey == current.favoriteKey }
            val next = if (idx >= 0) list[(idx + offset).mod(list.size)] else list.first()
            playChannel(next, 0)
        }
    }
    // 换源：当前频道多线路循环
    val switchLine: () -> Unit = {
        val current = uiState.currentChannel
        if (current == null || current.urls.size <= 1) {
            Toast.makeText(context, "该频道暂无其他线路", Toast.LENGTH_SHORT).show()
        } else {
            val next = (uiState.currentLineIndex + 1).mod(current.urls.size)
            playChannel(current, next)
            Toast.makeText(context, "已切换线路 ${next + 1}/${current.urls.size}", Toast.LENGTH_SHORT).show()
        }
    }

    // 播放失败自动换线路
    LaunchedEffect(pState.error) {
        if (pState.error != null) {
            tvViewModel.advanceLineOnError()?.let { (ch, line) ->
                playerViewModel.initPlayer(context, ch.urls[line], ch.name, isLive = true)
            }
        }
    }
    // 直播线路播完即结束（点播型死源）：重播仍结束 → 自动换线路
    LaunchedEffect(Unit) {
        playerViewModel.liveEndedEvent.collect {
            val next = tvViewModel.advanceLineOnError()
            if (next == null) {
                Toast.makeText(context, "该频道所有线路均无法正常播放，请手动换台", Toast.LENGTH_SHORT).show()
            } else {
                val (ch, line) = next
                playerViewModel.initPlayer(context, ch.urls[line], ch.name, isLive = true)
            }
        }
    }
    // 自动换源提示
    LaunchedEffect(uiState.autoSwitchMessage) {
        uiState.autoSwitchMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            tvViewModel.consumeAutoSwitchMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (!locked) controlsVisible = !controlsVisible },
                    onDoubleTap = { if (!locked) controlsVisible = true },
                )
            },
    ) {
        // 播放画面：按画幅模式铺满/定比
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    when (aspect) {
                        TvAspect.R16_9 -> Modifier.aspectRatio(16f / 9f)
                        TvAspect.R4_3 -> Modifier.aspectRatio(4f / 3f)
                        else -> Modifier.fillMaxSize()
                    },
                )
                .graphicsLayer { scaleX = if (mirrored) -1f else 1f },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.resizeMode = when (aspect) {
                        TvAspect.RAW, TvAspect.R16_9, TvAspect.R4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        TvAspect.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        TvAspect.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 缓冲/错误
        if (pState.isBuffering && pState.error == null && uiState.currentChannel != null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        pState.error?.let { err ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("播放失败", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(err, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.currentChannel?.let { ch ->
                    TextButton(onClick = { playChannel(ch, uiState.currentLineIndex) }) {
                        Text("重试", color = Color.White)
                    }
                }
            }
        }

        if (locked) {
            // 锁定状态：仅解锁按钮
            IconButton(
                onClick = { playerViewModel.toggleLock() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = "解锁", tint = Color.White)
            }
        } else if (controlsVisible) {
            // ===== 左侧双列面板：分类 + 频道（随控制层自动隐藏）=====
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(vertical = 56.dp),
            ) {
                // 分类列（收藏置顶）
                val displayGroups = remember(uiState.groupNames, favorites) {
                    tvGroupNamesWithFavorites(uiState.groupNames, favorites)
                }
                LazyColumn(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(displayGroups, key = { it }) { group ->
                        val selected = group == uiState.selectedGroup
                        Text(
                            text = group,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    else Color.Black.copy(alpha = 0.35f),
                                )
                                .clickable { tvViewModel.selectGroup(group) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                // 频道列
                val groupChannels = tvChannelsOfGroup(uiState.groups, uiState.channels, favorites, uiState.selectedGroup)
                LazyColumn(
                    modifier = Modifier
                        .width(170.dp)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(groupChannels, key = { it.favoriteKey }) { ch ->
                        val playing = ch.favoriteKey == uiState.currentChannel?.favoriteKey
                        val isFav = ch.favoriteKey in favorites
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    else Color.Black.copy(alpha = 0.35f),
                                )
                                .clickable { playChannel(ch, 0) }
                                .padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ch.name,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { tvViewModel.toggleFavorite(ch) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (isFav) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ===== 控制组件 =====
            // 顶部：返回 + 频道名 | 更多信息
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }
                Text(
                    text = uiState.currentChannel?.name ?: "未选择频道",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("更多信息", color = Color.White, fontSize = 13.sp)
                }
            }

            // 中部：上一个 / 播放暂停 / 下一个（居中）
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { switchChannel(-1) }) {
                    Icon(Icons.Default.SkipPrevious, "上一个", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        if (pState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = { switchChannel(1) }) {
                    Icon(Icons.Default.SkipNext, "下一个", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            // 锁定按钮单独放在屏幕中间右侧，避免与右上角“更多信息”图标重叠
            IconButton(
                onClick = { playerViewModel.toggleLock() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            ) {
                Icon(Icons.Default.LockOpen, "锁定", tint = Color.White)
            }

            // 底部：左 配置/硬软解/画幅 | 右 音轨/视轨/反转/跨类/换源
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧
                TextButton(onClick = { showSourceDialog = true }) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("配置", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = { playerViewModel.toggleDecodeMode() }) {
                    Icon(Icons.Default.Memory, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        if (pState.isHardwareDecode) "硬解" else "软解",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = {
                    aspect = TvAspect.entries[(TvAspect.entries.indexOf(aspect) + 1).mod(TvAspect.entries.size)]
                }) {
                    Icon(Icons.Default.AspectRatio, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(aspect.label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = {
                    // 横屏 <-> 反向横屏
                    val cur = activity?.requestedOrientation
                    activity?.requestedOrientation = if (cur == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                }) {
                    Icon(Icons.Default.ScreenRotation, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("旋转", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                Spacer(modifier = Modifier.weight(1f))
                // 右侧
                TextButton(onClick = { showAudioDialog = true }) {
                    Icon(Icons.Default.Audiotrack, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("音轨", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = { showVideoDialog = true }) {
                    Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("视轨", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = { mirrored = !mirrored }) {
                    Icon(Icons.Default.Flip, null, tint = if (mirrored) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("反转", color = if (mirrored) MaterialTheme.colorScheme.primary else Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = { showCrossDialog = true }) {
                    Icon(Icons.Default.Tune, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("跨类", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = switchLine) {
                    Icon(Icons.Default.Cached, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("换源", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }

    // ===== 弹窗 =====

    // 更多信息：名称 / UA / 播放链接
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("频道信息", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    InfoRow("频道名称", uiState.currentChannel?.name ?: "-")
                    InfoRow("直播源", uiState.currentSource?.name ?: "-")
                    InfoRow(
                        "User-Agent",
                        "ExoPlayerLib/${androidx.media3.common.MediaLibraryInfo.VERSION}",
                    )
                    InfoRow("播放链接", uiState.currentChannel?.urls?.getOrNull(uiState.currentLineIndex) ?: "-")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("关闭") }
            },
        )
    }

    // 音轨选择
    if (showAudioDialog) {
        TrackSelectDialog(
            player = player,
            trackType = C.TRACK_TYPE_AUDIO,
            title = "音轨",
            onDismiss = { showAudioDialog = false },
        )
    }
    // 视轨选择
    if (showVideoDialog) {
        TrackSelectDialog(
            player = player,
            trackType = C.TRACK_TYPE_VIDEO,
            title = "视轨",
            onDismiss = { showVideoDialog = false },
        )
    }

    // 跨类：跳转到其他分组并播放其第一个频道
    if (showCrossDialog) {
        AlertDialog(
            onDismissRequest = { showCrossDialog = false },
            title = { Text("跨类跳转", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(uiState.groupNames) { group ->
                        Text(
                            text = group,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tvViewModel.selectGroup(group)
                                    uiState.groups[group]?.firstOrNull()?.let { playChannel(it, 0) }
                                    showCrossDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossDialog = false }) { Text("关闭") }
            },
        )
    }

    // 配置：切换直播源
    if (showSourceDialog) {
        SourceSwitchDialog(
            sources = sources,
            currentId = uiState.currentSource?.id,
            onDismiss = { showSourceDialog = false },
            onSelect = { source ->
                tvViewModel.loadSource(source)
                showSourceDialog = false
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 13.sp,
            maxLines = if (label == "播放链接") 3 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
