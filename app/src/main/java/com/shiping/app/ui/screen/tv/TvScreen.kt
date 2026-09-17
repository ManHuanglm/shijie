package com.shiping.app.ui.screen.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shiping.app.data.model.TvChannel
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.ui.screen.player.PlayerViewModel
import com.shiping.app.util.Constants
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun TvScreen(
    playerViewModel: PlayerViewModel,
    onFullscreen: (title: String, url: String) -> Unit,
    viewModel: TvViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val player by playerViewModel.player.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    // 控件自动隐藏：播放中 3 秒后隐藏
    LaunchedEffect(controlsVisible, playerUiState.isPlaying) {
        if (controlsVisible && playerUiState.isPlaying) {
            delay(Constants.CONTROL_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var showCrossDialog by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(TvAspect.RAW) }
    var mirrored by remember { mutableStateOf(false) }

    val playChannel: (TvChannel, Int) -> Unit = { ch, line ->
        viewModel.playLine(ch, line)
        playerViewModel.initPlayer(context, ch.urls[line], ch.name, isLive = true)
    }

    // 播放失败自动换线路
    LaunchedEffect(playerUiState.error) {
        if (playerUiState.error != null) {
            viewModel.advanceLineOnError()?.let { (ch, line) ->
                playerViewModel.initPlayer(context, ch.urls[line], ch.name, isLive = true)
            }
        }
    }
    // 直播线路播完即结束（点播型死源）：重播仍结束 → 自动换线路
    LaunchedEffect(Unit) {
        playerViewModel.liveEndedEvent.collect {
            val next = viewModel.advanceLineOnError()
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
            viewModel.consumeAutoSwitchMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== 播放器（16:9 容器）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = if (mirrored) -1f else 1f },
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            keepScreenOn = true
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

            // 手势：单击切换控件，双击全屏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { onFullscreen("", "") },
                        )
                    },
            )

            val hasChannel = uiState.currentChannel != null
            if (hasChannel && playerUiState.isBuffering && playerUiState.error == null) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // 中间播放/暂停（精简控件）
            if (hasChannel && controlsVisible && playerUiState.error == null) {
                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp),
                ) {
                    Icon(
                        if (playerUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // 右上角：更多 + 全屏
            if (hasChannel && controlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showMoreDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.White)
                    }
                    IconButton(onClick = { onFullscreen("", "") }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "全屏", tint = Color.White)
                    }
                }
            }

            // 播放失败提示（自动换线路失败后展示）
            if (playerUiState.error != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("播放失败", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        playerUiState.error ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                    uiState.currentChannel?.let { ch ->
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            playChannel(ch, uiState.currentLineIndex)
                        }) {
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
        }

        // ===== 播放器下方：标题 + 切换源 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = uiState.currentChannel?.name ?: "电视直播",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            uiState.currentChannel?.let { ch ->
                if (ch.urls.size > 1) {
                    Text(
                        text = "线路${uiState.currentLineIndex + 1}/${ch.urls.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            TextButton(onClick = { showSourceDialog = true }) {
                Text(
                    text = uiState.currentSource?.name ?: "切换源",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "切换直播源",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ===== 分组 + 频道列表 =====
        when {
            uiState.loading && uiState.channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.channels.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        uiState.error ?: "加载失败",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.retry() }) { Text("重试") }
                }
            }
            uiState.channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该直播源暂无频道", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                // 分组标签（收藏置顶 + 横向滚动）
                val displayGroups = remember(uiState.groupNames, favorites) {
                    tvGroupNamesWithFavorites(uiState.groupNames, favorites)
                }
                val groupListState = rememberLazyListState()
                LazyRow(
                    state = groupListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayGroups) { group ->
                        val selected = group == uiState.selectedGroup
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { viewModel.selectGroup(group) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = group,
                                fontSize = 13.sp,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // 频道列表（支持收藏虚拟分组）
                val channels = tvChannelsOfGroup(uiState.groups, uiState.channels, favorites, uiState.selectedGroup)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(channels, key = { it.favoriteKey }) { channel ->
                        ChannelItem(
                            channel = channel,
                            playing = channel.favoriteKey == uiState.currentChannel?.favoriteKey,
                            isFavorite = channel.favoriteKey in favorites,
                            lineIndex = uiState.currentLineIndex.takeIf {
                                channel.favoriteKey == uiState.currentChannel?.favoriteKey
                            } ?: 0,
                            onPlay = { playChannel(channel, 0) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel) },
                            onSwitchLine = { playChannel(channel, (it + 1).mod(channel.urls.size)) },
                        )
                    }
                }
            }
        }
    }

    // 直播源切换弹窗
    if (showSourceDialog) {
        SourceSwitchDialog(
            sources = sources,
            currentId = uiState.currentSource?.id,
            onDismiss = { showSourceDialog = false },
            onSelect = { source ->
                viewModel.loadSource(source)
                showSourceDialog = false
            },
        )
    }

    // 更多面板（与全屏播放器相同的功能）
    if (showMoreDialog) {
        TvMorePanelDialog(
            playerViewModel = playerViewModel,
            isHardwareDecode = playerUiState.isHardwareDecode,
            aspect = aspect,
            mirrored = mirrored,
            currentChannel = uiState.currentChannel,
            lineIndex = uiState.currentLineIndex,
            onDismiss = { showMoreDialog = false },
            onAspectChange = { aspect = it },
            onMirrorToggle = { mirrored = !mirrored },
            onShowTracks = { type ->
                showMoreDialog = false
                if (type == androidx.media3.common.C.TRACK_TYPE_AUDIO) showAudioDialog = true
                else showVideoDialog = true
            },
            onSwitchLine = {
                uiState.currentChannel?.let { ch ->
                    if (ch.urls.size > 1) {
                        playChannel(ch, (uiState.currentLineIndex + 1).mod(ch.urls.size))
                    } else {
                        Toast.makeText(context, "该频道暂无其他线路", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCrossGroup = {
                showMoreDialog = false
                showCrossDialog = true
            },
        )
    }
    if (showAudioDialog) {
        TrackSelectDialog(
            player = player,
            trackType = androidx.media3.common.C.TRACK_TYPE_AUDIO,
            title = "音轨",
            onDismiss = { showAudioDialog = false },
        )
    }
    if (showVideoDialog) {
        TrackSelectDialog(
            player = player,
            trackType = androidx.media3.common.C.TRACK_TYPE_VIDEO,
            title = "视轨",
            onDismiss = { showVideoDialog = false },
        )
    }
    // 跨类跳转
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
                                    viewModel.selectGroup(group)
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
}

@Composable
private fun ChannelItem(
    channel: TvChannel,
    playing: Boolean,
    isFavorite: Boolean,
    lineIndex: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSwitchLine: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.logo.isNotBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                fontSize = 14.sp,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.urls.size > 1) {
                Text(
                    text = "${channel.urls.size} 条线路",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (playing && channel.urls.size > 1) {
            // 多线路频道：点击「换」字按钮切换线路
            TextButton(onClick = { onSwitchLine(lineIndex) }) {
                Text("换源", fontSize = 11.sp)
            }
        }
        if (playing) {
            Text(
                text = "播放中",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
            Icon(
                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
