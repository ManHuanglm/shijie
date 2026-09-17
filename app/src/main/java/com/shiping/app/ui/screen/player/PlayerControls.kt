package com.shiping.app.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

/** 毫秒转 mm:ss / hh:mm:ss 文本 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * 播放器自定义控制层。
 * 布局：顶栏（返回/片名/定时/更多设置/影片信息）-> 屏幕中央（上一集/播放/下一集）-> 进度条 -> 功能按钮行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerControls(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onTimerClick: () -> Unit,
    onMorePanelClick: () -> Unit,
    onInfoClick: () -> Unit,
    onToggleDecodeClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onReplayClick: () -> Unit,
    onToggleRepeat: () -> Unit,
    onAspectClick: () -> Unit,
    onCoreClick: () -> Unit,
    onEpisodeListClick: () -> Unit = {},
    hasEpisodes: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val positionMs = if (dragging) dragValue.toLong() else state.currentPositionMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        // 顶部栏：右上角依次为 定时/更多设置/影片信息，集中管理避免重叠
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTimerClick) {
                Icon(Icons.Default.Timer, contentDescription = "定时", tint = Color.White)
            }
            IconButton(onClick = onMorePanelClick) {
                Icon(Icons.Default.Tune, contentDescription = "更多设置", tint = Color.White)
            }
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "影片信息", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 屏幕中央：上一集 / 播放暂停 / 下一集
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = state.hasPrevEpisode) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一集",
                        tint = if (state.hasPrevEpisode) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNextEpisode) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一集",
                        tint = if (state.hasNextEpisode) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部：进度条 + 功能按钮行
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(positionMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onSeekTo(dragValue.toLong())
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Text(
                    text = formatDuration(state.durationMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp),
                )
            }

            // 功能按钮行：左组（选集/内核/解码/刷新/重播/循环/倍速/画幅） 右组（字幕/音轨/视轨/静音）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左组靠左端、右组靠右端，两端对齐；内容超出时各自横向滚动
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasEpisodes) {
                        BottomAction(label = "选集", onClick = onEpisodeListClick)
                    }
                    BottomAction(label = "EXO", onClick = onCoreClick)
                    BottomAction(
                        label = if (state.isHardwareDecode) "硬解" else "软解",
                        onClick = onToggleDecodeClick,
                    )
                    BottomAction(label = "刷新", onClick = onRefreshClick)
                    BottomAction(label = "重播", onClick = onReplayClick)
                    BottomAction(
                        label = when (state.repeatMode) {
                            Player.REPEAT_MODE_OFF -> "循环"
                            Player.REPEAT_MODE_ONE -> "单循"
                            else -> "全循"
                        },
                        highlighted = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onToggleRepeat,
                    )
                    BottomAction(
                        label = "${state.playbackSpeed}x",
                        onClick = onSpeedClick,
                    )
                    BottomAction(
                        label = state.aspectRatio.label,
                        onClick = onAspectClick,
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomAction(
                        label = "字幕",
                        highlighted = state.subtitleEnabled,
                        onClick = onSubtitleClick,
                    )
                    BottomAction(label = "音轨", onClick = onAudioClick)
                    BottomAction(label = "视轨", onClick = onQualityClick)
                    BottomAction(
                        label = "静音",
                        highlighted = state.isMuted,
                        onClick = onToggleMute,
                    )
                }
            }
        }
    }
}

/** 底部功能按钮：纯文字，高亮表示开启状态 */
@Composable
private fun BottomAction(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White,
        fontSize = 11.sp,
        maxLines = 1,
    )
}
