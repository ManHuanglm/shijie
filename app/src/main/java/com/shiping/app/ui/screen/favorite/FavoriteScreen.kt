package com.shiping.app.ui.screen.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shiping.app.data.model.FavoriteEntity
import com.shiping.app.data.model.Vod
import com.shiping.app.ui.component.EmptyView
import com.shiping.app.ui.component.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    viewModel: FavoriteViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onVideoClick: (vodId: Int, sourceUrl: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemToDelete by remember { mutableStateOf<FavoriteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        }
    ) { padding ->
        if (uiState.favorites.isEmpty()) {
            EmptyView(
                message = "暂无收藏\n去影片详情页点红心即可收藏",
                icon = Icons.Default.FavoriteBorder,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(
                    uiState.favorites,
                    key = { "${it.vodId}_${it.sourceUrl}" },
                ) { favorite ->
                    FavoriteCard(
                        favorite = favorite,
                        onClick = { onVideoClick(favorite.vodId, favorite.sourceUrl) },
                        onDelete = { itemToDelete = favorite },
                    )
                }
            }
        }
    }

    // 取消收藏确认
    itemToDelete?.let { favorite ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("取消收藏") },
            text = { Text("确定取消收藏「${favorite.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(favorite.vodId, favorite.sourceUrl)
                        itemToDelete = null
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 收藏卡片：海报 + 右上角移除按钮 */
@Composable
private fun FavoriteCard(
    favorite: FavoriteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        VideoCard(
            vod = Vod(vodId = favorite.vodId, vodName = favorite.title, vodPic = favorite.vodPic),
            onClick = onClick,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "取消收藏",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
