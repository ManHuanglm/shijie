package com.shiping.app.ui.screen.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.TvChannel
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.data.repository.TvRepository
import com.shiping.app.di.AppContainer
import com.shiping.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val currentSource: TvSourceEntity? = null,
    val channels: List<TvChannel> = emptyList(),
    /** 分组名 -> 频道列表 */
    val groups: Map<String, List<TvChannel>> = emptyMap(),
    val groupNames: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val currentChannel: TvChannel? = null,
    /** 当前播放的线路下标（同名频道多线路） */
    val currentLineIndex: Int = 0,
    /** 自动换源提示（播放失败自动切线路时设置） */
    val autoSwitchMessage: String? = null,
)

class TvViewModel(
    private val repository: TvRepository = AppContainer.tvRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    /** 全部直播源（切换源弹窗用；禁用源置灰） */
    val sources: StateFlow<List<TvSourceEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏频道 key 集合 */
    val favorites: StateFlow<Set<String>> = AppContainer.preferences.favoriteTvChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadedSourceId: Long? = null

    /** 播放失败自动换线路时已尝试的线路数（防循环重试） */
    private var triedLines = 1

    init {
        // 源列表就绪后自动加载上次选中的启用源（无记忆或已失效时用第一个启用源）
        viewModelScope.launch {
            val savedSourceId = AppContainer.preferences.currentTvSourceId.first()
            sources.collect { list ->
                if (loadedSourceId == null) {
                    val target = list.firstOrNull { it.enabled && it.id == savedSourceId }
                        ?: list.firstOrNull { it.enabled }
                    target?.let { loadSource(it) }
                }
            }
        }
    }

    fun loadSource(source: TvSourceEntity) {
        if (!source.enabled) return
        loadedSourceId = source.id
        // 记住选中的直播源，下次启动自动恢复
        viewModelScope.launch { AppContainer.preferences.setCurrentTvSourceId(source.id) }
        _uiState.update {
            it.copy(loading = true, error = null, currentSource = source, currentChannel = null, currentLineIndex = 0)
        }
        viewModelScope.launch {
            runCatching { repository.loadChannels(source) }
                .onSuccess { channels ->
                    val groups = channels.groupBy { it.group }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            channels = channels,
                            groups = groups,
                            groupNames = groups.keys.toList(),
                            selectedGroup = groups.keys.firstOrNull(),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "频道加载失败")
                    }
                }
        }
    }

    fun selectGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group) }
    }

    /** 选择频道播放（从第一条线路开始） */
    fun setCurrentChannel(channel: TvChannel) {
        triedLines = 1
        _uiState.update { it.copy(currentChannel = channel, currentLineIndex = 0, autoSwitchMessage = null) }
    }

    /** 切换到指定线路并播放 */
    fun playLine(channel: TvChannel, lineIndex: Int) {
        triedLines = lineIndex + 1
        AppLog.i("TvLive", "播放线路 ${lineIndex + 1}/${channel.urls.size} 频道=${channel.name} url=${channel.urls[lineIndex]}")
        _uiState.update { it.copy(currentChannel = channel, currentLineIndex = lineIndex) }
    }

    /**
     * 播放失败时自动切换下一条线路。
     * @return 需要播放的 (频道, 线路下标)，无更多线路返回 null
     */
    fun advanceLineOnError(): Pair<TvChannel, Int>? {
        val state = _uiState.value
        val channel = state.currentChannel ?: return null
        val next = state.currentLineIndex + 1
        if (next >= channel.urls.size || triedLines >= channel.urls.size) {
            AppLog.w("TvLive", "频道 ${channel.name} 已无更多线路可换（共 ${channel.urls.size} 条）")
            return null
        }
        triedLines = next + 1
        AppLog.w("TvLive", "自动换线路：${channel.name} -> ${next + 1}/${channel.urls.size}")
        _uiState.update {
            it.copy(
                currentChannel = channel,
                currentLineIndex = next,
                autoSwitchMessage = "播放失败，已自动切换线路 ${next + 1}/${channel.urls.size}",
            )
        }
        return channel to next
    }

    /** 消费自动换源提示 */
    fun consumeAutoSwitchMessage() {
        _uiState.update { it.copy(autoSwitchMessage = null) }
    }

    fun retry() {
        _uiState.value.currentSource?.let { loadSource(it) }
    }

    /** 切换频道收藏 */
    fun toggleFavorite(channel: TvChannel) {
        viewModelScope.launch {
            AppContainer.preferences.toggleFavoriteTvChannel(channel.favoriteKey)
        }
    }

    /** 收藏分组（虚拟分组，置顶显示） */
    fun favoriteChannels(state: TvUiState, favoriteKeys: Set<String>): List<TvChannel> =
        state.channels.filter { it.favoriteKey in favoriteKeys }
}
