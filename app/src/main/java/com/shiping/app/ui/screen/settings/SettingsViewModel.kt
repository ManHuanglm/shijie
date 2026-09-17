package com.shiping.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.repository.ApiSourceRepository
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiManageUiState(
    val sources: List<ApiSourceEntity> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isTesting: Boolean = false,
    val testResults: Map<Long, ApiSourceRepository.TestResult> = emptyMap(),
    val message: String? = null,
    /** TVBox 配置导入进行中 */
    val tvBoxLoading: Boolean = false,
    /** 非空表示配置为多仓，待用户选择子配置（名称 to 地址） */
    val tvBoxWarehouses: List<Pair<String, String>> = emptyList(),
)

class SettingsViewModel : ViewModel() {

    private val repository: ApiSourceRepository = AppContainer.apiSourceRepository

    private val _uiState = MutableStateFlow(ApiManageUiState())
    val uiState: StateFlow<ApiManageUiState> = _uiState.asStateFlow()

    val sources: StateFlow<List<ApiSourceEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val newSelected = state.selectedIds.toMutableSet().apply {
                if (contains(id)) remove(id) else add(id)
            }
            state.copy(selectedIds = newSelected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun selectAll() {
        val allIds = sources.value.map { it.id }.toSet()
        _uiState.update { state ->
            val newSelected = if (state.selectedIds.containsAll(allIds) && allIds.isNotEmpty()) {
                emptySet()
            } else {
                allIds
            }
            state.copy(selectedIds = newSelected)
        }
    }

    fun saveSource(source: ApiSourceEntity) {
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

    fun deleteSource(source: ApiSourceEntity) {
        viewModelScope.launch {
            repository.delete(source)
            showMessage("删除成功")
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteByIds(ids)
            clearSelection()
            showMessage("已删除 ${ids.size} 项")
        }
    }

    fun toggleEnabled(source: ApiSourceEntity) {
        viewModelScope.launch {
            repository.update(source.copy(enabled = !source.enabled))
        }
    }

    /** 单个导入/添加 */
    fun singleImport(name: String, url: String, note: String = "") {
        if (url.isBlank()) {
            showMessage("URL 不能为空")
            return
        }
        viewModelScope.launch {
            repository.insert(
                ApiSourceEntity(
                    name = name.ifBlank { "未命名" },
                    url = url.trim(),
                    note = note,
                ),
            )
            showMessage("导入成功")
        }
    }

    /** 批量导入 */
    fun batchImport(text: String) {
        if (text.isBlank()) {
            showMessage("内容不能为空")
            return
        }
        viewModelScope.launch {
            val count = repository.importFromText(text)
            showMessage(if (count > 0) "成功导入 $count 条" else "导入失败，请检查格式")
        }
    }

    suspend fun exportToText(): String = repository.exportToText()

    suspend fun exportToJson(): String = repository.exportToJson()

    /** TVBox 配置导入：拉取解析单仓；多仓时先返回仓库列表待选 */
    fun importTvBoxConfig(url: String) {
        if (url.isBlank()) {
            showMessage("请输入配置链接")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(tvBoxLoading = true) }
            val result = AppContainer.tvBoxConfigImporter.import(url.trim())
            _uiState.update { state ->
                when {
                    result.error != null ->
                        state.copy(tvBoxLoading = false, message = result.error)
                    result.warehouses.isNotEmpty() ->
                        state.copy(tvBoxLoading = false, tvBoxWarehouses = result.warehouses)
                    else ->
                        state.copy(tvBoxLoading = false, message = result.messageText())
                }
            }
        }
    }

    /** 导入从多仓中选择的子配置 */
    fun importTvBoxWarehouse(url: String, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(tvBoxLoading = true, tvBoxWarehouses = emptyList()) }
            val result = AppContainer.tvBoxConfigImporter.import(url.trim(), name)
            _uiState.update { state ->
                state.copy(
                    tvBoxLoading = false,
                    message = result.error ?: result.messageText(),
                    tvBoxWarehouses = result.warehouses,
                )
            }
        }
    }

    fun dismissTvBoxWarehouses() {
        _uiState.update { it.copy(tvBoxWarehouses = emptyList()) }
    }

    /** 检测单个 API */
    fun testApi(source: ApiSourceEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val result = repository.testApi(source.url)
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    testResults = state.testResults + (source.id to result),
                )
            }
        }
    }

    /** 检测任意 URL（供添加/编辑弹窗使用，不写入 testResults） */
    fun testApiUrl(url: String) {
        viewModelScope.launch {
            val result = repository.testApi(url)
            showMessage(if (result.success) "检测成功" else result.message)
        }
    }

    /** 批量检测选中的 API */
    fun testSelected() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val targets = sources.value.filter { ids.contains(it.id) }
            val results = mutableMapOf<Long, ApiSourceRepository.TestResult>()
            targets.forEach { src ->
                results[src.id] = repository.testApi(src.url)
            }
            _uiState.update {
                it.copy(isTesting = false, testResults = it.testResults + results)
            }
        }
    }

    /** 一键检测全部启用的 API */
    fun testAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val targets = sources.value.filter { it.enabled }
            val results = mutableMapOf<Long, ApiSourceRepository.TestResult>()
            targets.forEach { src ->
                results[src.id] = repository.testApi(src.url)
            }
            val successCount = results.values.count { it.success }
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResults = it.testResults + results,
                    message = "检测完成: $successCount/${targets.size} 个源可用",
                )
            }
        }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
