package com.shiping.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.Vod
import com.shiping.app.data.repository.SearchGroup
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 海报布局模式 */
enum class PosterLayout { GRID, LIST }

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val groups: List<SearchGroup> = emptyList(),
    val selectedSourceId: Long? = null,
    val selectedGroupLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val layout: PosterLayout = PosterLayout.GRID
) {
    /** 当前选中的分组 */
    val selectedGroup: SearchGroup?
        get() = groups.firstOrNull { it.sourceId == selectedSourceId }

    /** 过滤掉失败且无结果的源（空结果/失败的 API 不显示） */
    val visibleGroups: List<SearchGroup>
        get() = groups.filter { it.error == null && it.results.isNotEmpty() }

    /** 搜索建议：从历史与推荐中匹配当前输入 */
    val suggestions: List<String>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            return (searchHistory + Constants.RECOMMEND_SEARCHES)
                .distinct()
                .filter { it.contains(q, ignoreCase = true) }
                .take(8)
        }
}

class SearchViewModel : ViewModel() {

    private val repository = AppContainer.vodRepository
    private val preferences = AppContainer.preferences

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 防抖实时搜索任务 */
    private var searchJob: Job? = null

    init {
        // 加载搜索历史
        viewModelScope.launch {
            preferences.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    /**
     * 更新输入并触发防抖实时搜索（边输入边搜索）
     */
    fun updateQuery(q: String) {
        _uiState.update { it.copy(query = q) }
        val trimmed = q.trim()
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(groups = emptyList(), hasSearched = false, isLoading = false) }
            return
        }
        // 防抖 300ms
        searchJob = viewModelScope.launch {
            delay(300)
            searchInternal(trimmed)
        }
    }

    /** 点击建议词/历史词直接搜索 */
    fun searchWithKeyword(keyword: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = keyword) }
        searchInternal(keyword.trim())
    }

    /** 回车键触发搜索 */
    fun search() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchInternal(q)
    }

    /**
     * 聚合搜索内部实现：在所有启用的 API 源上并发搜索
     */
    private fun searchInternal(q: String) {
        if (q.isEmpty()) return
        _uiState.update {
            it.copy(isLoading = true, hasSearched = true, groups = emptyList(), selectedSourceId = null)
        }
        viewModelScope.launch {
            // 保存搜索历史
            preferences.addSearchHistory(q)
            runCatching { repository.searchAll(q, page = 1) }
                .onSuccess { groups ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            groups = groups,
                            // 默认选中第一个有结果的源（跳过空/失败）
                            selectedSourceId = groups.firstOrNull { it.error == null && it.results.isNotEmpty() }?.sourceId
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, groups = emptyList()) }
                }
        }
    }

    /** 切换海报布局：表格/列表 */
    fun toggleLayout() {
        _uiState.update {
            it.copy(layout = if (it.layout == PosterLayout.GRID) PosterLayout.LIST else PosterLayout.GRID)
        }
    }

    /** 清空搜索历史 */
    fun clearHistory() {
        viewModelScope.launch { preferences.clearSearchHistory() }
    }

    /** 切换左侧选中的 API 源 */
    fun selectSource(sourceId: Long) {
        _uiState.update { it.copy(selectedSourceId = sourceId) }
    }

    /**
     * 对当前选中的源加载下一页
     */
    fun loadMore() {
        val state = _uiState.value
        val group = state.selectedGroup ?: return
        if (state.selectedGroupLoadingMore || !group.hasMore || state.query.isBlank()) return

        val nextPage = group.page + 1
        _uiState.update { it.copy(selectedGroupLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.search(state.query.trim(), page = nextPage, url = group.sourceUrl) }
                .onSuccess { response ->
                    // 补全封面
                    val detailMap = runCatching {
                        repository.getDetails(response.list.map { it.vodId }, url = group.sourceUrl)
                    }.getOrDefault(emptyMap())
                    val newList = response.list.map { vod ->
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
                    _uiState.update { s ->
                        s.copy(
                            selectedGroupLoadingMore = false,
                            groups = s.groups.map { g ->
                                if (g.sourceId == group.sourceId) {
                                    g.copy(
                                        results = g.results + newList,
                                        page = nextPage,
                                        hasMore = nextPage < response.pageCount
                                    )
                                } else g
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(selectedGroupLoadingMore = false) }
                }
        }
    }
}
