package com.shiping.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParserManageUiState(
    val selectedParserId: Long? = null,
    val message: String? = null,
)

class ParserManageViewModel : ViewModel() {

    private val repository = AppContainer.parseSourceRepository

    private val _uiState = MutableStateFlow(ParserManageUiState())
    val uiState: StateFlow<ParserManageUiState> = _uiState.asStateFlow()

    val sources: StateFlow<List<ParseSourceEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.currentParserId.collect { id ->
                _uiState.update { it.copy(selectedParserId = id) }
            }
        }
    }

    fun saveSource(source: ParseSourceEntity) {
        viewModelScope.launch {
            if (source.id == 0L) {
                repository.insert(source)
                showMessage("添加成功")
            } else {
                repository.update(source)
                showMessage("修改成功")
            }
        }
    }

    fun deleteSource(source: ParseSourceEntity) {
        viewModelScope.launch {
            repository.delete(source)
            showMessage("删除成功")
        }
    }

    fun toggleEnabled(source: ParseSourceEntity) {
        viewModelScope.launch {
            repository.update(source.copy(enabled = !source.enabled))
        }
    }

    fun setCurrentParser(id: Long) {
        viewModelScope.launch {
            repository.setCurrentParserId(id)
            showMessage("已设为当前解析源")
        }
    }

    fun importSources(text: String) {
        viewModelScope.launch {
            val count = repository.importFromText(text)
            showMessage(if (count > 0) "成功导入 $count 条" else "导入失败，格式不正确")
        }
    }

    suspend fun exportJson(): String = repository.exportToJson()

    suspend fun exportText(): String = repository.exportToText()

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
