@file:OptIn(ExperimentalLayoutApi::class)

package com.shiping.app.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shiping.app.ui.component.EmptyView
import com.shiping.app.ui.component.ErrorView
import com.shiping.app.ui.component.LoadingView
import com.shiping.app.ui.component.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onVideoClick: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val selectedGroup = uiState.selectedGroup
    val selectedResults = selectedGroup?.results ?: emptyList()

    // 触底加载更多（表格布局）
    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= selectedResults.size - 4
        }
    }
    // 触底加载更多（列表布局）
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= selectedResults.size - 3
        }
    }
    LaunchedEffect(shouldLoadMoreGrid, shouldLoadMoreList) {
        val should = if (uiState.layout == PosterLayout.GRID) shouldLoadMoreGrid else shouldLoadMoreList
        if (should && (selectedGroup?.hasMore == true)) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleLayout) {
                        Icon(
                            imageVector = if (uiState.layout == PosterLayout.GRID) Icons.AutoMirrored.Filled.ViewList
                            else Icons.Default.GridView,
                            contentDescription = "切换布局"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                placeholder = { Text("输入关键词搜索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
            )

            when {
                // 输入中：显示搜索建议（无结果时）
                uiState.query.isNotBlank() && uiState.suggestions.isNotEmpty() &&
                    !uiState.hasSearched -> {
                    SuggestionsList(suggestions = uiState.suggestions) {
                        viewModel.searchWithKeyword(it)
                    }
                }
                // 已搜索：聚合结果左右布局
                uiState.hasSearched && uiState.visibleGroups.isNotEmpty() -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左侧：API 源列表（过滤掉空/失败源）
                        LazyColumn(
                            modifier = Modifier
                                .width(110.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            lazyColumnItems(uiState.visibleGroups, key = { it.sourceId }) { group ->
                                val selected = group.sourceId == uiState.selectedSourceId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectSource(group.sourceId) }
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .padding(horizontal = 8.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = group.sourceName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${group.results.size} 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        // 右侧：选中源的影片列表（表格/列表布局）
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when {
                                uiState.isLoading -> LoadingView()
                                selectedResults.isEmpty() -> EmptyView(message = "该源暂无结果")
                                uiState.layout == PosterLayout.GRID -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        state = gridState,
                                        contentPadding = PaddingValues(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(selectedResults, key = { it.vodId }) { vod ->
                                            VideoCard(vod = vod, onClick = { onVideoClick(vod.vodId) })
                                        }
                                        if (uiState.selectedGroupLoadingMore) {
                                            item { LoadingFooter() }
                                        }
                                    }
                                }
                                else -> {
                                    // 列表布局：横向海报 + 标题
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        lazyColumnItems(selectedResults, key = { it.vodId }) { vod ->
                                            VideoListItem(vod = vod, onClick = { onVideoClick(vod.vodId) })
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
                uiState.hasSearched && uiState.visibleGroups.isEmpty() -> {
                    if (uiState.isLoading) LoadingView()
                    else EmptyView(message = "没有找到相关视频")
                }
                // 未输入：推荐搜索 + 历史搜索
                else -> {
                    RecommendAndHistory(
                        recommends = com.shiping.app.util.Constants.RECOMMEND_SEARCHES,
                        history = uiState.searchHistory,
                        onKeywordClick = viewModel::searchWithKeyword,
                        onClearHistory = viewModel::clearHistory
                    )
                }
            }
        }
    }
}

/** 搜索建议列表 */
@Composable
private fun SuggestionsList(suggestions: List<String>, onItemClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        lazyColumnItems(suggestions) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 推荐搜索 + 历史搜索 */
@Composable
private fun RecommendAndHistory(
    recommends: List<String>,
    history: List<String>,
    onKeywordClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索历史", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "清空",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClearHistory() }
                    )
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { keyword ->
                        KeywordChip(text = keyword, onClick = { onKeywordClick(keyword) })
                    }
                }
            }
        }
        item { Text("推荐搜索", style = MaterialTheme.typography.titleSmall) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recommends.forEach { keyword ->
                    KeywordChip(text = keyword, onClick = { onKeywordClick(keyword) })
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

/** 列表布局项：横向海报 + 标题 */
@Composable
private fun VideoListItem(vod: com.shiping.app.data.model.Vod, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // 海报
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (vod.vodPic.isNotBlank()) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(vod.vodPic),
                        contentDescription = vod.vodName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            // 标题 + 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vod.vodName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (vod.vodRemarks.isNotBlank()) {
                    Text(
                        text = vod.vodRemarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                if (vod.vodScore.isNotBlank()) {
                    Text(
                        text = "评分 ${vod.vodScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
