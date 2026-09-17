package com.shiping.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.PlayHistoryEntity
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val history: List<PlayHistoryEntity> = emptyList(),
)

class HistoryViewModel : ViewModel() {

    private val repository = AppContainer.playHistoryRepository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.historyFlow.collect { list ->
                _uiState.value = HistoryUiState(history = list)
            }
        }
    }

    /** 删除单条记录 */
    fun deleteItem(vodId: Int, sourceUrl: String) {
        viewModelScope.launch { repository.delete(vodId, sourceUrl) }
    }

    /** 清空全部记录 */
    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
