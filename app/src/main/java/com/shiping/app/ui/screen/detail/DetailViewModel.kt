package com.shiping.app.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.PlayEpisode
import com.shiping.app.data.model.PlaySource
import com.shiping.app.data.model.Vod
import com.shiping.app.di.AppContainer
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
    val selectedSourceIndex: Int = 0
) {
    val currentSource: PlaySource? get() = sources.getOrNull(selectedSourceIndex)
}

class DetailViewModel : ViewModel() {

    private val repository = AppContainer.vodRepository

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(vodId: Int) {
        _uiState.value = DetailUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { repository.getDetail(vodId) }
                .onSuccess { vod ->
                    val sources = vod?.parsePlaySources() ?: emptyList()
                    // 优先选择 m3u8/直链播放源
                    val preferredIndex = sources.indexOfFirst { src ->
                        src.episodes.any { ep ->
                            ep.url.contains("m3u8", ignoreCase = true) ||
                            ep.url.endsWith(".mp4", ignoreCase = true) ||
                            ep.url.endsWith(".flv", ignoreCase = true)
                        }
                    }.takeIf { it >= 0 } ?: 0
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            vod = vod,
                            sources = sources,
                            selectedSourceIndex = preferredIndex
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "加载失败") }
                }
        }
    }

    fun selectSource(index: Int) {
        _uiState.update { it.copy(selectedSourceIndex = index) }
    }
}
