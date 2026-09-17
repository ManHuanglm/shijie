package com.shiping.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.util.Constants
import kotlinx.coroutines.launch

/**
 * 选集弹窗：正/倒序切换 + [Constants.EPISODE_GRID_COLUMNS] 列懒加载网格 +
 * 底部分段跳转（每段 [Constants.EPISODE_PAGE_SIZE] 集）。
 *
 * @param episodeNames 剧集名称列表（正序）
 * @param currentIndex 当前播放集下标
 * @param onSelected 点击剧集回调（原始正序下标）
 */
@Composable
fun EpisodeSelectionDialog(
    episodeNames: List<String>,
    currentIndex: Int,
    title: String,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "共 ${episodeNames.size} 集",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            EpisodeGridContent(
                episodeNames = episodeNames,
                currentIndex = currentIndex,
                onSelected = onSelected,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 选集表格内容（正倒序 + 懒加载网格 + 底部分段跳转），可嵌入任意弹窗/面板。
 */
@Composable
fun EpisodeGridContent(
    episodeNames: List<String>,
    currentIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (episodeNames.isEmpty()) {
        Text("无剧集数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val pageSize = Constants.EPISODE_PAGE_SIZE
    val columns = Constants.EPISODE_GRID_COLUMNS
    val totalPages = ((episodeNames.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    var descending by remember { mutableStateOf(false) }
    // 展示顺序：正序为原始下标 0..n-1，倒序为 n-1..0
    val orderedIndices = remember(episodeNames.size, descending) {
        if (descending) episodeNames.indices.reversed().toList()
        else episodeNames.indices.toList()
    }
    // 懒加载网格：任意集数量级都只组合可见项，打开时自动定位到当前播放集
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(orderedIndices, currentIndex) {
        val pos = orderedIndices.indexOf(currentIndex)
        if (pos >= 0) gridState.scrollToItem(pos)
    }
    // 当前分段页由首个可见项派生
    val currentPage by remember {
        derivedStateOf { gridState.firstVisibleItemIndex / pageSize }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 正/倒序切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { descending = !descending },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (descending) "倒序" else "正序", fontSize = 13.sp)
            }
        }

        // 表格：懒加载网格，列数 [Constants.EPISODE_GRID_COLUMNS]
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(Constants.EPISODE_DIALOG_GRID_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                orderedIndices,
                key = { _, actualIndex -> actualIndex },
            ) { _, actualIndex ->
                EpisodeCell(
                    name = episodeNames[actualIndex],
                    selected = actualIndex == currentIndex,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onSelected(actualIndex)
                }
            }
        }

        // 分段翻页：点击跳转对应分段
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上一页
            PageNavButton(
                enabled = currentPage > 0,
                iconLeft = true,
                label = "上一页",
            ) {
                scope.launch {
                    gridState.scrollToItem((currentPage - 1).coerceAtLeast(0) * pageSize)
                }
            }
            // 分段页签：显示该页集数范围
            for (p in 0 until totalPages) {
                val start = p * pageSize
                val end = minOf(start + pageSize, episodeNames.size)
                SegmentPageChip(
                    label = "${start + 1}-${end}",
                    selected = p == currentPage,
                    onClick = { scope.launch { gridState.scrollToItem(start) } },
                )
            }
            // 下一页
            PageNavButton(
                enabled = currentPage < totalPages - 1,
                iconLeft = false,
                label = "下一页",
            ) {
                scope.launch {
                    gridState.scrollToItem((currentPage + 1).coerceAtMost(totalPages - 1) * pageSize)
                }
            }
        }
    }
}

@Composable
private fun EpisodeCell(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(enabled = name.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun SegmentPageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageNavButton(
    enabled: Boolean,
    iconLeft: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        if (iconLeft) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint,
        )
        if (!iconLeft) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
