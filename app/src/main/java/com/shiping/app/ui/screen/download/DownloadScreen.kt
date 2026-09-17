package com.shiping.app.ui.screen.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shiping.app.data.model.DownloadEntity

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toFloat())
        bytes >= 1L shl 20 -> "%.2f MB".format(bytes / (1L shl 20).toFloat())
        bytes >= 1L shl 10 -> "%.2f KB".format(bytes / (1L shl 10).toFloat())
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return if (bytesPerSecond <= 0) "" else "${formatBytes(bytesPerSecond)}/s"
}

private fun statusText(status: Int): String = when (status) {
    DownloadEntity.STATUS_QUEUED -> "等待中"
    DownloadEntity.STATUS_DOWNLOADING -> "下载中"
    DownloadEntity.STATUS_COMPLETED -> "已完成"
    DownloadEntity.STATUS_FAILED -> "失败"
    DownloadEntity.STATUS_PAUSED -> "已暂停"
    else -> "未知"
}

/** 从标题中提取剧集/电影名：标题格式为 "vodName - episodeName"，无分隔符则视为电影 */
private fun extractSeriesName(title: String): String {
    val idx = title.indexOf(" - ")
    return if (idx > 0) title.substring(0, idx) else title
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onPlay: (title: String, url: String) -> Unit,
) {
    val downloads by viewModel.downloads.collectAsState()
    val totalBytes by viewModel.totalBytes.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    // 分组展开状态，默认全部展开
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空全部")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SdStorage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "暂无缓存视频",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "在视频详情页点击缓存按钮即可离线下载",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${downloads.size} 个任务",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "已占用 ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 按剧集/电影分组
                val grouped = remember(downloads) {
                    downloads.groupBy { extractSeriesName(it.title) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (seriesName, items) ->
                        val expanded = expandedGroups[seriesName] ?: true
                        item(key = "group_$seriesName") {
                            GroupHeader(
                                seriesName = seriesName,
                                count = items.size,
                                expanded = expanded,
                                onToggle = {
                                    expandedGroups[seriesName] = !expanded
                                },
                            )
                        }
                        if (expanded) {
                            items(items, key = { it.contentId }) { item ->
                                DownloadItemCard(
                                    item = item,
                                    onPause = { viewModel.pause(item.contentId) },
                                    onResume = { viewModel.resume(item.contentId) },
                                    onDelete = { itemToDelete = item },
                                    onRetry = { viewModel.retry(item.contentId) },
                                    onPlay = { onPlay(item.title, item.localPath.ifBlank { item.url }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("删除缓存") },
            text = { Text("确定删除「${item.title}」的缓存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(item.contentId)
                    itemToDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部缓存") },
            text = { Text("确定删除所有已缓存视频吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    seriesName: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = seriesName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count 集",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    val completed = item.status == DownloadEntity.STATUS_COMPLETED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(if (completed) Modifier.clickable(onClick = onPlay) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText(item.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.status) {
                            DownloadEntity.STATUS_DOWNLOADING -> MaterialTheme.colorScheme.primary
                            DownloadEntity.STATUS_COMPLETED -> Color(0xFF4CAF50)
                            DownloadEntity.STATUS_FAILED -> Color(0xFFE53935)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (item.quality.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    if (item.status == DownloadEntity.STATUS_COMPLETED && item.localPath.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已保存到相册",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                if (item.status != DownloadEntity.STATUS_COMPLETED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (item.progress >= 0) {
                        // 已知总大小：确定进度条
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    } else {
                        // HLS 等未知总大小：不确定进度条（保持动画表示正在下载）
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (item.progress >= 0) "${item.progress}%"
                            else "已缓存 ${formatBytes(item.bytesDownloaded)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val speed = formatSpeed(item.speedBytesPerSecond)
                        if (speed.isNotEmpty()) {
                            Text(
                                text = speed,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已完成 · ${formatBytes(maxOf(item.totalBytes, item.bytesDownloaded))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (item.status) {
                    DownloadEntity.STATUS_DOWNLOADING, DownloadEntity.STATUS_QUEUED -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                    }
                    DownloadEntity.STATUS_PAUSED, DownloadEntity.STATUS_FAILED -> {
                        IconButton(
                            onClick = if (item.status == DownloadEntity.STATUS_FAILED) onRetry else onResume,
                        ) {
                            Icon(
                                if (item.status == DownloadEntity.STATUS_FAILED) {
                                    Icons.Default.Refresh
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = if (item.status == DownloadEntity.STATUS_FAILED) "重试" else "继续",
                            )
                        }
                    }
                    DownloadEntity.STATUS_COMPLETED -> {
                        IconButton(onClick = onPlay) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "播放",
                                tint = Color(0xFF4CAF50),
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
