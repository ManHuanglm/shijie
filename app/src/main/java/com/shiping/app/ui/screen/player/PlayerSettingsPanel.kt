package com.shiping.app.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import com.shiping.app.ui.components.EpisodeGridContent
import com.shiping.app.util.Constants

/** 底部弹出的设置面板类型 */
enum class PlayerPanelType {
    NONE, SPEED, QUALITY, AUDIO, SUBTITLE, SLEEP_TIMER, MORE, EPISODES
}

/**
 * 播放器设置面板（速度/清晰度/音轨/字幕/定时/更多）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsPanel(
    panelType: PlayerPanelType,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (Int) -> Unit,
    onAudioSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleToggled: () -> Unit,
    onSleepTimerSelected: (Int) -> Unit,
    onSleepTimerCancel: () -> Unit,
    onIntroSecondsChange: (Int) -> Unit,
    onOutroSecondsChange: (Int) -> Unit,
    onToggleDecode: () -> Unit,
    episodes: List<Pair<String, String>> = emptyList(),
    currentEpisodeIndex: Int = 0,
    onEpisodeSelected: (Int) -> Unit = {},
) {
    if (panelType == PlayerPanelType.NONE) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        title = {
            Text(
                    text = when (panelType) {
                        PlayerPanelType.SPEED -> "播放倍速"
                        PlayerPanelType.QUALITY -> "清晰度"
                        PlayerPanelType.AUDIO -> "音轨"
                        PlayerPanelType.SUBTITLE -> "字幕"
                        PlayerPanelType.SLEEP_TIMER -> "睡眠定时"
                        PlayerPanelType.MORE -> "更多设置"
                        PlayerPanelType.EPISODES -> "选集"
                        else -> ""
                    },
                    fontWeight = FontWeight.Bold,
                )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (panelType) {
                    PlayerPanelType.SPEED -> SpeedOptions(state.playbackSpeed, onSpeedSelected)
                    PlayerPanelType.QUALITY -> QualityOptions(state.qualityTracks, onQualitySelected)
                    PlayerPanelType.AUDIO -> AudioOptions(state.audioTracks, onAudioSelected)
                    PlayerPanelType.SUBTITLE -> SubtitleOptions(
                        state.subtitleEnabled,
                        state.subtitleTracks,
                        onSubtitleToggled,
                        onSubtitleSelected,
                    )
                    PlayerPanelType.SLEEP_TIMER -> SleepTimerOptions(
                        onSleepTimerSelected,
                        onSleepTimerCancel,
                    )
                    PlayerPanelType.MORE -> MoreOptions(
                        isHardware = state.isHardwareDecode,
                        introSeconds = if (state.introSkipAtMs > 0) {
                            (state.introSkipAtMs / 1000L).toInt()
                        } else {
                            0
                        },
                        outroSeconds = if (state.outroSkipMs > 0) {
                            (state.outroSkipMs / 1000L).toInt()
                        } else {
                            0
                        },
                        onIntroChange = onIntroSecondsChange,
                        onOutroChange = onOutroSecondsChange,
                        onToggleDecode = onToggleDecode,
                    )
                    PlayerPanelType.EPISODES -> EpisodeGridContent(
                        episodeNames = episodes.map { it.first },
                        currentIndex = currentEpisodeIndex,
                        onSelected = onEpisodeSelected,
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun SpeedOptions(current: Float, onSelected: (Float) -> Unit) {
    OptionList(
        items = Constants.PLAYBACK_SPEEDS.map { "${it}x" },
        selectedIndex = Constants.PLAYBACK_SPEEDS.indexOf(current),
        onSelected = { index ->
            if (index >= 0) onSelected(Constants.PLAYBACK_SPEEDS[index])
        },
    )
}

@Composable
private fun QualityOptions(tracks: List<TrackInfo>, onSelected: (Int) -> Unit) {
    if (tracks.isEmpty()) {
        Text("当前视频无多清晰度可选", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OptionList(
        items = tracks.map { it.label },
        selectedIndex = tracks.indexOfFirst { it.isSelected },
        onSelected = { index ->
            if (index >= 0) onSelected(tracks[index].index)
        },
    )
}

@Composable
private fun AudioOptions(tracks: List<TrackInfo>, onSelected: (Int) -> Unit) {
    if (tracks.isEmpty()) {
        Text("当前视频无多音轨可选", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OptionList(
        items = tracks.map { it.label },
        selectedIndex = tracks.indexOfFirst { it.isSelected },
        onSelected = { index ->
            if (index >= 0) onSelected(tracks[index].index)
        },
    )
}

@Composable
private fun SubtitleOptions(
    enabled: Boolean,
    tracks: List<TrackInfo>,
    onToggle: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("字幕开关", color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (enabled) "开启" else "关闭",
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (tracks.isEmpty()) {
        Text("当前视频无字幕轨", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OptionList(
        items = tracks.map { it.label },
        selectedIndex = tracks.indexOfFirst { it.isSelected },
        onSelected = { index ->
            if (index >= 0) onSelected(tracks[index].index)
        },
    )
}

@Composable
private fun SleepTimerOptions(
    onSelected: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "关闭定时",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCancel)
            .padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
    )
    Spacer(modifier = Modifier.height(4.dp))
    OptionList(
        items = Constants.SLEEP_TIMER_MINUTES.map { "${it} 分钟" },
        selectedIndex = -1,
        onSelected = { index ->
            if (index >= 0) onSelected(Constants.SLEEP_TIMER_MINUTES[index])
        },
    )
}

@Composable
private fun MoreOptions(
    isHardware: Boolean,
    introSeconds: Int,
    outroSeconds: Int,
    onIntroChange: (Int) -> Unit,
    onOutroChange: (Int) -> Unit,
    onToggleDecode: () -> Unit,
) {
    SkipTimeItem(label = "片头", seconds = introSeconds, onChange = onIntroChange)
    SkipTimeItem(label = "片尾", seconds = outroSeconds, onChange = onOutroChange)
    Text(
        text = "0 不跳，最大 300 s",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    MoreItem(
        label = "解码方式：${if (isHardware) "硬解" else "软解"}",
        onClick = onToggleDecode,
    )
}

/** 片头/片尾跳过秒数行内设置：输入即生效，0 不跳过 */
@Composable
private fun SkipTimeItem(
    label: String,
    seconds: Int,
    onChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(seconds.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { value ->
                val digits = value.filter { it.isDigit() }.take(3)
                text = digits
                onChange(digits.toIntOrNull()?.coerceAtMost(300) ?: 0)
            },
            modifier = Modifier.width(56.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "秒",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoreItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
    )
}

@Composable
private fun OptionList(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    items.forEachIndexed { index, label ->
        val selected = index == selectedIndex
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    },
                )
                .clickable { onSelected(index) }
                .padding(vertical = 10.dp, horizontal = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
        )
    }
}
