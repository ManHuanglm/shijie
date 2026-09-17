package com.shiping.app.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.FavoriteEntity
import com.shiping.app.data.model.PlaySource
import com.shiping.app.data.model.Vod
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val vod: Vod? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    /** 同类型推荐影片 */
    val recommendations: List<Vod> = emptyList(),
) {
    val currentSource: PlaySource? get() = sources.getOrNull(selectedSourceIndex)
}

class DetailViewModel : ViewModel() {

    private val repository = AppContainer.vodRepository
    private val favoriteRepository = AppContainer.favoriteRepository

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** 观察影片收藏状态 */
    fun observeFavorite(vodId: Int, sourceUrl: String): Flow<Boolean> =
        favoriteRepository.observeIsFavorite(vodId, sourceUrl)

    /** 切换收藏状态 */
    fun toggleFavorite(vodId: Int, sourceUrl: String) {
        viewModelScope.launch {
            val vod = _uiState.value.vod ?: return@launch
            favoriteRepository.toggle(
                FavoriteEntity(
                    vodId = vodId,
                    sourceUrl = sourceUrl,
                    title = vod.vodName,
                    vodPic = vod.safePic,
                ),
            )
        }
    }

    fun loadDetail(vodId: Int, sourceUrl: String = "") {
        _uiState.update {
            it.copy(isLoading = true, error = null, vod = null, sources = emptyList(), recommendations = emptyList())
        }
        viewModelScope.launch {
            // 先解析实际使用的源（详情与推荐列表保持同源）
            val resolvedSource = sourceUrl.ifBlank {
                runCatching { AppContainer.apiSourceRepository.currentApiUrlOnce() }.getOrDefault("")
            }
            runCatching { repository.getDetail(vodId, sourceUrl) }
                .onSuccess { vod ->
                    val sources = vod?.parsePlaySources() ?: emptyList()
                    val preferredIndex = sources.indexOfFirst { source ->
                        source.episodes.any { ep -> isDirectStreamUrl(ep.url) }
                    }.takeIf { it >= 0 } ?: 0
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            vod = vod,
                            sources = sources,
                            selectedSourceIndex = preferredIndex,
                        )
                    }
                    vod?.let { loadRecommendations(it, resolvedSource) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: "加载失败")
                    }
                }
        }
    }

    /** 加载同类型推荐：优先按分类 ID 拉取，缺分类时用类型名搜索兜底 */
    private fun loadRecommendations(vod: Vod, sourceUrl: String) {
        viewModelScope.launch {
            val response = runCatching {
                if (vod.typeId > 0) {
                    repository.getHome(page = 1, typeId = vod.typeId, url = sourceUrl)
                } else {
                    vod.vodClass.split(",").firstOrNull()?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { repository.search(it, url = sourceUrl) }
                }
            }.getOrNull()
            val list = response?.list
                ?.filter { it.vodId != vod.vodId && it.safePic.isNotBlank() }
                ?.take(RECOMMENDATION_COUNT)
                .orEmpty()
            if (list.isNotEmpty()) {
                _uiState.update { it.copy(recommendations = list) }
            }
        }
    }

    fun selectSource(index: Int) {
        _uiState.update { it.copy(selectedSourceIndex = index) }
    }

    private fun isDirectStreamUrl(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return Constants.DIRECT_STREAM_SUFFIXES.any { path.endsWith(it) }
    }

    companion object {
        /** 推荐影片数量上限 */
        private const val RECOMMENDATION_COUNT = 12
    }
}
