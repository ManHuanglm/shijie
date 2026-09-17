package com.shiping.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.Category
import com.shiping.app.data.model.PlayHistoryEntity
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
    val currentApiName: String = "",
    /** 继续观看：最近观看记录（按时间倒序，取前 N 条） */
    val continueWatching: List<PlayHistoryEntity> = emptyList(),
)

class HomeViewModel : ViewModel() {

    private val vodRepository = AppContainer.vodRepository
    private val apiSourceRepository = AppContainer.apiSourceRepository
    private val playHistoryRepository = AppContainer.playHistoryRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadApiSources()
        loadHome()
        observeContinueWatching()
    }

    /** 订阅播放历史，驱动首页“继续观看” */
    private fun observeContinueWatching() {
        viewModelScope.launch {
            playHistoryRepository.historyFlow.collect { history ->
                _uiState.update { it.copy(continueWatching = history.take(CONTINUE_WATCHING_LIMIT)) }
            }
        }
    }

    private fun loadApiSources() {
        viewModelScope.launch {
            apiSourceRepository.getEnabled().collect { sources ->
                val currentId = apiSourceRepository.currentApiId.first()
                val currentName = apiSourceRepository.currentApiName()
                _uiState.update {
                    it.copy(
                        apiSources = sources,
                        currentApiId = currentId,
                        currentApiName = currentName,
                    )
                }
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            val page = 1
            _uiState.update { it.copy(isLoading = true, error = null, page = page, hasMore = true) }
            runCatching {
                vodRepository.getHome(page = page, typeId = _uiState.value.selectedTypeId)
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = response.classList.ifEmpty { it.categories },
                        vodList = response.list,
                        page = page,
                        hasMore = page < response.pageCount,
                        total = response.total,
                    )
                }
                fetchCovers(response.list.map { it.vodId })
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "加载失败")
                }
            }
        }
    }

    /** 批量获取封面图并合并到列表 */
    private fun fetchCovers(ids: List<Int>) {
        if (ids.isEmpty()) return
        _uiState.update { it.copy(isLoadingCovers = true) }
        viewModelScope.launch {
            runCatching { vodRepository.getDetails(ids) }
                .onSuccess { detailMap ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingCovers = false,
                            vodList = state.vodList.map { vod ->
                                detailMap[vod.vodId]?.let { vod.mergeWithDetail(it) } ?: vod
                            },
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
        _uiState.update {
            it.copy(selectedTypeId = typeId, vodList = emptyList(), page = 1, hasMore = true)
        }
        loadHome()
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        val nextPage = _uiState.value.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching {
                vodRepository.getHome(page = nextPage, typeId = _uiState.value.selectedTypeId)
            }.onSuccess { response ->
                val newList = response.list
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        vodList = it.vodList + newList,
                        page = nextPage,
                        hasMore = nextPage < response.pageCount,
                    )
                }
                fetchCovers(newList.map { it.vodId })
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** 切换 API 源 */
    fun switchApi(source: ApiSourceEntity) {
        viewModelScope.launch {
            apiSourceRepository.setCurrentApiId(source.id)
            _uiState.update {
                it.copy(
                    currentApiId = source.id,
                    currentApiName = source.name,
                    vodList = emptyList(),
                    categories = emptyList(),
                    selectedTypeId = 0,
                    page = 1,
                    hasMore = true,
                )
            }
            loadHome()
        }
    }

    companion object {
        /** 继续观看展示条数上限 */
        private const val CONTINUE_WATCHING_LIMIT = 20
    }
}
