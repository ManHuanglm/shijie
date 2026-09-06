package com.shiping.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.Category
import com.shiping.app.data.model.Vod
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingCovers: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedTypeId: Int = 0,
    val vodList: List<Vod> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val total: Int = 0,
    val apiSources: List<ApiSourceEntity> = emptyList(),
    val currentApiId: Long? = null,
    val currentApiName: String = ""
)

class HomeViewModel : ViewModel() {

    private val vodRepo = AppContainer.vodRepository
    private val apiRepo = AppContainer.apiSourceRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadApiSources()
        loadHome()
    }

    private fun loadApiSources() {
        viewModelScope.launch {
            apiRepo.getEnabled().collect { sources ->
                val currentId = apiRepo.currentApiId.first()
                val currentName = apiRepo.currentApiName()
                _uiState.update {
                    it.copy(apiSources = sources, currentApiId = currentId, currentApiName = currentName)
                }
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, page = 1, hasMore = true) }
            runCatching {
                vodRepo.getHome(page = 1, typeId = _uiState.value.selectedTypeId)
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = response.classList.ifEmpty { it.categories },
                        vodList = response.list,
                        page = 1,
                        hasMore = 1 < response.pageCount,
                        total = response.total
                    )
                }
                // 列表接口通常不带封面图，批量获取详情补全封面
                fetchCovers(response.list.map { v -> v.vodId })
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "加载失败")
                }
            }
        }
    }

    /**
     * 批量获取封面图并合并到列表
     */
    private fun fetchCovers(ids: List<Int>) {
        if (ids.isEmpty()) return
        _uiState.update { it.copy(isLoadingCovers = true) }
        viewModelScope.launch {
            runCatching { vodRepo.getDetails(ids) }
                .onSuccess { detailMap ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingCovers = false,
                            vodList = state.vodList.map { vod ->
                                detailMap[vod.vodId]?.let { detail ->
                                    vod.copy(
                                        vodPic = detail.vodPic,
                                        vodPicThumb = detail.vodPicThumb,
                                        vodPicSlide = detail.vodPicSlide,
                                        vodScore = detail.vodScore,
                                        vodRemarks = detail.vodRemarks.ifBlank { vod.vodRemarks }
                                    )
                                } ?: vod
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingCovers = false) }
                }
        }
    }

    fun selectCategory(typeId: Int) {
        if (_uiState.value.selectedTypeId == typeId && _uiState.value.vodList.isNotEmpty()) return
        _uiState.update { it.copy(selectedTypeId = typeId, vodList = emptyList(), page = 1, hasMore = true) }
        loadHome()
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        val nextPage = _uiState.value.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching {
                vodRepo.getHome(page = nextPage, typeId = _uiState.value.selectedTypeId)
            }.onSuccess { response ->
                val newList = response.list
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        vodList = it.vodList + newList,
                        page = nextPage,
                        hasMore = nextPage < response.pageCount
                    )
                }
                // 为新加载的列表补全封面
                fetchCovers(newList.map { v -> v.vodId })
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** 切换 API 源 */
    fun switchApi(source: ApiSourceEntity) {
        viewModelScope.launch {
            apiRepo.setCurrentApiId(source.id)
            _uiState.update {
                it.copy(
                    currentApiId = source.id,
                    currentApiName = source.name,
                    vodList = emptyList(),
                    categories = emptyList(),
                    selectedTypeId = 0,
                    page = 1,
                    hasMore = true
                )
            }
            loadHome()
        }
    }
}
