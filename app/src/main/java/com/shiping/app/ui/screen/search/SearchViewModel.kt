package com.shiping.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.repository.SearchGroup
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import com.shiping.app.util.PinyinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val layout: PosterLayout = PosterLayout.GRID,
    /** 输入联想标签：字母按首字母匹配片名，中文按名称包含匹配 */
    val suggestionTags: List<String> = emptyList(),
) {
    /** 当前选中的分组 */
    val selectedGroup: SearchGroup?
        get() = groups.firstOrNull { it.sourceId == selectedSourceId }

    /** 过滤掉失败且无结果的源 */
    val visibleGroups: List<SearchGroup>
        get() = groups.filter { it.error == null && it.results.isNotEmpty() }
}

class SearchViewModel : ViewModel() {

    private val repository = AppContainer.vodRepository
    private val preferences = AppContainer.preferences

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    /** 联想候选池：热搜词 + 当前源首页片名（进入页面时异步补充） */
    private var namePool: List<String> = Constants.RECOMMEND_SEARCHES

    init {
        viewModelScope.launch {
            preferences.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val names = buildList {
                for (page in 1..Constants.SUGGESTION_POOL_PAGES) {
                    val list = runCatching { repository.getHome(page = page).list }
                        .getOrDefault(emptyList())
                    addAll(list.map { it.vodName })
                    if (list.isEmpty()) break
                }
            }.filter { it.isNotBlank() }.distinct()
            if (names.isNotEmpty()) {
                namePool = (Constants.RECOMMEND_SEARCHES + names).distinct()
            }
        }
    }

    /** 更新输入：只做本地联想，不触发搜索（全量搜索由回车/点击词条触发） */
    fun updateQuery(q: String) {
        _uiState.update {
            it.copy(query = q, hasSearched = false, groups = emptyList(), isLoading = false)
        }
        searchJob?.cancel()
        suggestJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(suggestionTags = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(Constants.SEARCH_DEBOUNCE_MS)
            // 关键词已变化则放弃本次联想结果
            if (_uiState.value.query.trim() != trimmed) return@launch
            val tags = withContext(Dispatchers.Default) { buildSuggestionTags(trimmed) }
            if (_uiState.value.query.trim() != trimmed) return@launch
            _uiState.update { it.copy(suggestionTags = tags) }
        }
    }

    /** 构建联想标签：纯字母按拼音首字母前缀匹配，其余按名称包含匹配 */
    private fun buildSuggestionTags(keyword: String): List<String> {
        val matched = if (keyword.matches(Regex("^[a-zA-Z]+$"))) {
            val prefix = keyword.lowercase()
            namePool.filter { PinyinUtil.initials(it).startsWith(prefix) }
        } else {
            namePool.filter { it.contains(keyword, ignoreCase = true) }
        }
        return matched.distinct().take(Constants.MAX_SUGGESTION_TAGS)
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

    private fun searchInternal(q: String) {
        if (q.isEmpty()) return
        _uiState.update {
            it.copy(isLoading = true, hasSearched = true, groups = emptyList(), selectedSourceId = null)
        }
        searchJob = viewModelScope.launch {
            preferences.addSearchHistory(q)
            val sources = runCatching { repository.getEnabledSources() }.getOrDefault(emptyList())
            if (sources.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            // 每个源独立并发搜索，完成一个上屏一个，不互相等待
            sources.map { source ->
                launch {
                    val group = repository.searchOneSource(source, q, page = 1)
                    _uiState.update { state ->
                        state.copy(
                            groups = state.groups + group,
                            selectedSourceId = state.selectedSourceId
                                ?: group.takeIf { it.error == null && it.results.isNotEmpty() }?.sourceId,
                        )
                    }
                }
            }.joinAll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** 切换海报布局：表格/列表 */
    fun toggleLayout() {
        _uiState.update {
            it.copy(
                layout = if (it.layout == PosterLayout.GRID) PosterLayout.LIST else PosterLayout.GRID,
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { preferences.clearSearchHistory() }
    }

    fun selectSource(sourceId: Long) {
        _uiState.update { it.copy(selectedSourceId = sourceId) }
    }

    /** 滑动接近底部时触发：由界面传入最后可见下标，内部判断是否加载下一页 */
    fun loadMoreIfNeeded(lastVisibleIndex: Int) {
        val state = _uiState.value
        val group = state.selectedGroup ?: return
        if (lastVisibleIndex < group.results.size - Constants.LOAD_MORE_THRESHOLD) return
        loadMore()
    }

    /** 对当前选中的源加载下一页 */
    fun loadMore() {
        val state = _uiState.value
        val group = state.selectedGroup ?: return
        if (state.selectedGroupLoadingMore || !group.hasMore || state.query.isBlank()) return

        val nextPage = group.page + 1
        _uiState.update { it.copy(selectedGroupLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.search(state.query.trim(), page = nextPage, url = group.sourceUrl) }
                .onSuccess { response ->
                    val detailMap = runCatching {
                        repository.getDetails(response.list.map { it.vodId }, url = group.sourceUrl)
                    }.getOrDefault(emptyMap())
                    val newList = response.list.map { vod ->
                        detailMap[vod.vodId]?.let { vod.mergeWithDetail(it) } ?: vod
                    }
                    _uiState.update { s ->
                        s.copy(
                            selectedGroupLoadingMore = false,
                            groups = s.groups.map { g ->
                                if (g.sourceId == group.sourceId) {
                                    g.copy(
                                        results = (g.results + newList).distinctBy { it.vodId },
                                        page = nextPage,
                                        hasMore = nextPage < response.pageCount,
                                    )
                                } else {
                                    g
                                }
                            },
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(selectedGroupLoadingMore = false) }
                }
        }
    }
}
