package com.shiping.app.ui.screen.tv

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import com.shiping.app.data.model.TvChannel
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.ui.screen.player.PlayerViewModel

/** 收藏虚拟分组名 */
internal const val TV_FAV_GROUP = "收藏"

/** 分组名列表：有收藏时在最前面追加收藏虚拟分组 */
internal fun tvGroupNamesWithFavorites(groupNames: List<String>, favorites: Set<String>): List<String> =
    if (favorites.isEmpty()) groupNames else listOf(TV_FAV_GROUP) + groupNames

/** 取指定分组的频道列表（收藏虚拟分组 = 过滤收藏项） */
internal fun tvChannelsOfGroup(
    groups: Map<String, List<TvChannel>>,
    channels: List<TvChannel>,
    favorites: Set<String>,
    group: String?,
): List<TvChannel> = when (group) {
    null -> channels
    TV_FAV_GROUP -> channels.filter { it.favoriteKey in favorites }
    else -> groups[group].orEmpty()
}

/** 直播画幅模式（内嵌与全屏共用） */
internal enum class TvAspect(val label: String) {
    RAW("原始"),
    R16_9("16:9"),
    R4_3("4:3"),
    FILL("填充"),
    CROP("裁剪"),
}

/** 直播源切换弹窗（内嵌与全屏共用） */
@Composable
internal fun SourceSwitchDialog(
    sources: List<TvSourceEntity>,
    currentId: Long?,
    onDismiss: () -> Unit,
    onSelect: (TvSourceEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择直播源", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(sources) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = source.enabled) { onSelect(source) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = source.name,
                            fontSize = 14.sp,
                            fontWeight = if (source.id == currentId) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !source.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                source.id == currentId -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!source.enabled) {
                            Text(
                                text = "已禁用",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        } else if (source.id == currentId) {
                            Text(
                                text = "当前",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 内嵌播放器「更多」面板：与全屏播放器相同的功能集合。
 */
@Composable
internal fun TvMorePanelDialog(
    playerViewModel: PlayerViewModel,
    isHardwareDecode: Boolean,
    aspect: TvAspect,
    mirrored: Boolean,
    currentChannel: TvChannel?,
    lineIndex: Int,
    onDismiss: () -> Unit,
    onAspectChange: (TvAspect) -> Unit,
    onMirrorToggle: () -> Unit,
    onShowTracks: (Int) -> Unit,
    onSwitchLine: () -> Unit,
    onCrossGroup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放设置", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 解码模式
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("解码", fontSize = 13.sp, modifier = Modifier.width(48.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectChip("硬解", isHardwareDecode) { if (isHardwareDecode) playerViewModel.toggleDecodeMode() }
                        AspectChip("软解", !isHardwareDecode) { if (!isHardwareDecode) playerViewModel.toggleDecodeMode() }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 画幅
                Text("画幅", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvAspect.entries.forEach { mode ->
                        AspectChip(mode.label, mode == aspect) { onAspectChange(mode) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 镜像
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("画面", fontSize = 13.sp, modifier = Modifier.width(48.dp))
                    AspectChip("反转", mirrored, onMirrorToggle)
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 轨道
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onShowTracks(C.TRACK_TYPE_AUDIO) }) { Text("音轨", fontSize = 13.sp) }
                    TextButton(onClick = { onShowTracks(C.TRACK_TYPE_VIDEO) }) { Text("视轨", fontSize = 13.sp) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 线路 / 跨类
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onSwitchLine, enabled = currentChannel != null) {
                        Text(
                            text = if (currentChannel != null && currentChannel.urls.size > 1) {
                                "换线路(${lineIndex + 1}/${currentChannel.urls.size})"
                            } else "换线路",
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(onClick = onCrossGroup) { Text("跨类", fontSize = 13.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * 音轨/视轨选择弹窗：列出播放器当前轨道，点击切换（内嵌与全屏共用）。
 */
@Composable
internal fun TrackSelectDialog(
    player: Player?,
    trackType: Int,
    title: String,
    onDismiss: () -> Unit,
) {
    val groups = remember(player) {
        player?.currentTracks?.groups?.filter { it.type == trackType }.orEmpty()
    }
    // 扁平化为 (轨道组, 轨道下标) 列表，避免 LazyColumn 嵌套 items
    val options = remember(groups) {
        groups.flatMap { group -> (0 until group.length).map { group to it } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            if (options.isEmpty()) {
                Text("无可选轨道", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(options.size) { i ->
                        val (group, tIndex) = options[i]
                        val format = group.getTrackFormat(tIndex)
                        val selected = group.isTrackSelected(tIndex)
                        val label = buildString {
                            append(format.label ?: format.language ?: "轨道 ${i + 1}")
                            format.width.takeIf { it > 0 && trackType == C.TRACK_TYPE_VIDEO }?.let {
                                append(" · ${it}x${format.height}")
                            }
                            format.sampleMimeType?.substringAfterLast('/')?.let { append(" · $it") }
                        }
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val p = player ?: return@clickable
                                    p.trackSelectionParameters = p
                                        .trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(group.mediaTrackGroup, tIndex),
                                        )
                                        .build()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
