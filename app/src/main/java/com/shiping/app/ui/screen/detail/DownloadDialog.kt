package com.shiping.app.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 缓存下载对话框：选择剧集（单选/全选/批量），自动以源片最高清晰度下载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadDialog(
    episodes: List<Pair<String, String>>,
    vodName: String,
    vodPic: String,
    onDismiss: () -> Unit,
    onDownload: (List<Pair<String, String>>) -> Unit,
) {
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    // 全选状态直接由已选集数派生，避免两个状态不一致
    val allSelected = episodes.isNotEmpty() && selectedIndices.size == episodes.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("缓存下载", fontWeight = FontWeight.Bold)
        },
        text = {
            // 整体不套 verticalScroll：剧集网格自身懒加载滚动，集数多时低配机也不卡
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 全选
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedIndices = if (allSelected) {
                                emptySet()
                            } else {
                                episodes.indices.toSet()
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (allSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (allSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        "全选（${episodes.size} 集）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 剧集网格：懒加载
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Constants.EPISODE_GRID_COLUMNS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Constants.DOWNLOAD_DIALOG_GRID_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        episodes,
                        key = { index, _ -> index },
                    ) { index, (name, _) ->
                        val selected = selectedIndices.contains(index)
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .clickable {
                                    selectedIndices = if (selected) {
                                        selectedIndices - index
                                    } else {
                                        selectedIndices + index
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = selectedIndices.map { episodes[it] }
                    if (selected.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            AppContainer.downloadRepository.addDownloads(
                                episodes = selected.map { (name, url) ->
                                    Triple(url, "$vodName - $name", vodPic)
                                },
                            )
                        }
                        onDownload(selected)
                    }
                },
                enabled = selectedIndices.isNotEmpty(),
            ) {
                Text("缓存 ${selectedIndices.size} 集")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
