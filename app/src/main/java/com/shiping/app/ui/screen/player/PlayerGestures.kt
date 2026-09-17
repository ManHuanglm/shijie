package com.shiping.app.ui.screen.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class GestureMode {
    NONE, SEEK, BRIGHTNESS, VOLUME, LONG_PRESS
}

/**
 * 播放器手势层：
 * - 双击：播放/暂停
 * - 左右滑动：快进/快退
 * - 左半屏上下滑动：亮度
 * - 右半屏上下滑动：音量
 * - 长按：3 倍速快进
 */
@Composable
internal fun PlayerGestures(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isLocked: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onSingleTap: (() -> Unit)? = null,
) {
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var seekOffsetMs by remember { mutableStateOf(0L) }
    var brightnessValue by remember { mutableStateOf(0.5f) }
    var volumeValue by remember { mutableStateOf(1f) }
    var showIndicator by remember { mutableStateOf(false) }
    var showTapIcon by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }

    val autoHideIndicator: () -> Unit = {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(800L)
            showIndicator = false
            showTapIcon = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                if (isLocked) {
                    // 锁定态仅响应单击，用于唤出解锁按钮
                    detectTapGestures(onTap = { onSingleTap?.invoke() })
                    return@pointerInput
                }
                // 独立于 gestureMode 记录长按状态：
                // 长按后若手指滑动被判定为拖拽，gestureMode 会被覆盖，
                // 用此标记保证松手/取消时一定恢复倍速
                var longPressActive = false
                detectTapGestures(
                    onDoubleTap = {
                        onTogglePlay()
                        showTapIcon = true
                        autoHideIndicator()
                    },
                    onTap = { onSingleTap?.invoke() },
                    onLongPress = {
                        longPressActive = true
                        gestureMode = GestureMode.LONG_PRESS
                        onLongPressStart()
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (longPressActive) {
                            longPressActive = false
                            gestureMode = GestureMode.NONE
                            onLongPressEnd()
                        }
                    },
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val isLeftSide = offset.x < size.width / 2f
                        gestureMode = if (isLeftSide) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                        seekOffsetMs = 0L
                        showIndicator = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // 根据拖动方向判断：水平位移大于垂直则为 SEEK
                        if (abs(dragAmount.x) > abs(dragAmount.y)) {
                            if (gestureMode != GestureMode.SEEK) {
                                gestureMode = GestureMode.SEEK
                                seekOffsetMs = 0L
                            }
                        }
                        when (gestureMode) {
                            GestureMode.SEEK -> {
                                seekOffsetMs += (dragAmount.x * Constants.GESTURE_SEEK_MS_PER_PX / 10).toLong()
                                seekOffsetMs = seekOffsetMs.coerceIn(
                                    -Constants.GESTURE_SEEK_MAX_MS,
                                    Constants.GESTURE_SEEK_MAX_MS,
                                )
                            }
                            GestureMode.BRIGHTNESS -> {
                                brightnessValue = (brightnessValue - dragAmount.y / size.height)
                                    .coerceIn(0f, 1f)
                                onBrightnessChange(brightnessValue)
                            }
                            GestureMode.VOLUME -> {
                                volumeValue = (volumeValue - dragAmount.y / size.height)
                                    .coerceIn(0f, 1f)
                                onVolumeChange(volumeValue)
                            }
                            else -> Unit
                        }
                    },
                    onDragEnd = {
                        if (gestureMode == GestureMode.SEEK && seekOffsetMs != 0L) {
                            onSeekBy(seekOffsetMs)
                        }
                        gestureMode = GestureMode.NONE
                        autoHideIndicator()
                    },
                    onDragCancel = {
                        gestureMode = GestureMode.NONE
                        showIndicator = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showIndicator && gestureMode == GestureMode.SEEK && seekOffsetMs != 0L) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (seekOffsetMs > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "${if (seekOffsetMs > 0) "+" else ""}${seekOffsetMs / 1000}s",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (showIndicator && (gestureMode == GestureMode.BRIGHTNESS || gestureMode == GestureMode.VOLUME)) {
            Text(
                text = when (gestureMode) {
                    GestureMode.BRIGHTNESS -> "亮度 ${(brightnessValue * 100).toInt()}%"
                    GestureMode.VOLUME -> "音量 ${(volumeValue * 100).toInt()}%"
                    else -> ""
                },
                color = Color.White,
                fontSize = 18.sp,
            )
        }

        if (showTapIcon) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}
