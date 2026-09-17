@file:OptIn(ExperimentalLayoutApi::class)

package com.shiping.app.ui.screen.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.shiping.app.data.model.Vod
import com.shiping.app.ui.component.EmptyView
import com.shiping.app.ui.component.LoadingView
import com.shiping.app.ui.component.VideoCard
import com.shiping.app.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    initialQuery: String = "",
    onBack: () -> Unit,
    onVideoClick: (Int, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val selectedGroup = uiState.selectedGroup
    val selectedResults = selectedGroup?.results ?: emptyList()
    val keyboardController = LocalSoftwareKeyboardController.current
    // 点击海报：先收键盘再跳转，避免 IME 动画引起布局抖动
    val onVideoTap: (Int) -> Unit = { id ->
        keyboardController?.hide()
        onVideoClick(id, selectedGroup?.sourceUrl ?: "")
    }

    // 从详情页片名跳入：自动填入关键字并直接执行聚合搜索
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.updateQuery(initialQuery)
            viewModel.search()
        }
    }

    // 滑动接近底部时加载当前源下一页（snapshotFlow 持续监听，翻页后可继续触发）
    LaunchedEffect(gridState, listState) {
        snapshotFlow {
            if (uiState.layout == PosterLayout.GRID) {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }
        }.collect { lastVisible ->
            if (lastVisible >= 0) viewModel.loadMoreIfNeeded(lastVisible)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 无边框搜索框，放在返回按钮右侧
                    SearchField(
                        query = uiState.query,
                        onQueryChange = viewModel::updateQuery,
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.search()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleLayout) {
                        Icon(
                            imageVector = if (uiState.layout == PosterLayout.GRID) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = "切换布局",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.hasSearched && uiState.visibleGroups.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 仍有源在搜索中：显示顶部细进度条
                        if (uiState.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                modifier = Modifier
                                    .width(110.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            ) {
                                lazyColumnItems(uiState.visibleGroups, key = { it.sourceId }) { group ->
                                    val selected = group.sourceId == uiState.selectedSourceId
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectSource(group.sourceId) }
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    androidx.compose.ui.graphics.Color.Transparent
                                                },
                                            )
                                            .padding(horizontal = 8.dp, vertical = 14.dp),
                                    ) {
                                        Text(
                                            text = group.sourceName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${group.results.size} 条",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when {
                                    // 选中源已有结果立即渲染；否则按整体状态显示加载/空视图
                                    selectedResults.isEmpty() -> {
                                        if (uiState.isLoading) LoadingView()
                                        else EmptyView(message = "该源暂无结果")
                                    }
                                    uiState.layout == PosterLayout.GRID -> {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            state = gridState,
                                            contentPadding = PaddingValues(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            items(selectedResults, key = { it.vodId }) { vod ->
                                                VideoCard(vod = vod, onClick = { onVideoTap(vod.vodId) })
                                            }
                                            if (uiState.selectedGroupLoadingMore) {
                                                item { LoadingFooter() }
                                            }
                                        }
                                    }

                                    else -> {
                                        LazyColumn(
                                            state = listState,
                                            contentPadding = PaddingValues(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            lazyColumnItems(selectedResults, key = { it.vodId }) { vod ->
                                                VideoListItem(vod = vod, onClick = { onVideoTap(vod.vodId) })
                                            }
                                            if (uiState.selectedGroupLoadingMore) {
                                                item { LoadingFooter() }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                uiState.hasSearched && uiState.visibleGroups.isEmpty() -> {
                    if (uiState.isLoading) LoadingView()
                    else EmptyView(message = "没有找到相关视频")
                }

                else -> {
                    HistoryAndSuggestions(
                        query = uiState.query,
                        suggestionTags = uiState.suggestionTags,
                        hotKeywords = Constants.RECOMMEND_SEARCHES,
                        history = uiState.searchHistory,
                        onKeywordClick = viewModel::searchWithKeyword,
                        onClearHistory = viewModel::clearHistory,
                    )
                }
            }
        }
    }
}

/** 无边框搜索框：无背景无图标，完全融入顶栏背景 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "关键字...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 搜索历史 + 热搜/输入联想标签 */
@Composable
private fun HistoryAndSuggestions(
    query: String,
    suggestionTags: List<String>,
    hotKeywords: List<String>,
    history: List<String>,
    onKeywordClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("历史", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "清空",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClearHistory() },
                    )
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { keyword ->
                        KeywordChip(text = keyword, onClick = { onKeywordClick(keyword) })
                    }
                }
            }
        }
        // 未输入时展示热搜词；输入后展示与输入联动的片名标签
        val tags = if (query.isBlank()) hotKeywords else suggestionTags
        if (tags.isNotEmpty()) {
            item { Text("热搜", style = MaterialTheme.typography.titleSmall) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { keyword ->
                        KeywordChip(text = keyword, onClick = { onKeywordClick(keyword) })
                    }
                }
            }
        }
    }
}



/** 关键词标签 */
@Composable
private fun KeywordChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

/** 列表布局项：横向海报 + 标题 */
@Composable
private fun VideoListItem(vod: Vod, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (vod.vodPic.isNotBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(vod.vodPic),
                        contentDescription = vod.vodName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vod.vodName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (vod.vodRemarks.isNotBlank()) {
                    Text(
                        text = vod.vodRemarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                if (vod.vodScore.isNotBlank()) {
                    Text(
                        text = "评分 ${vod.vodScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 加载更多尾部指示器 */
@Composable
private fun LoadingFooter() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
