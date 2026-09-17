package com.shiping.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.data.remote.RetrofitClient
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvSourceManageUiState(
    val message: String? = null,
    val isTesting: Boolean = false,
    /** 检测结果：源 id -> 是否可用 */
    val testResults: Map<Long, Boolean> = emptyMap(),
)

class TvSourceManageViewModel : ViewModel() {

    private val repository = AppContainer.tvRepository

    private val _uiState = MutableStateFlow(TvSourceManageUiState())
    val uiState: StateFlow<TvSourceManageUiState> = _uiState.asStateFlow()

    val sources: StateFlow<List<TvSourceEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveSource(id: Long, name: String, url: String, note: String) {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isEmpty() || trimmedUrl.isEmpty()) {
            showMessage("名称和地址不能为空")
            return
        }
        viewModelScope.launch {
            if (id == 0L) {
                repository.insert(
                    TvSourceEntity(name = trimmedName, url = trimmedUrl, note = note.trim()),
                )
                showMessage("添加成功")
            } else {
                val existing = repository.getById(id)
                if (existing != null) {
                    repository.update(
                        existing.copy(name = trimmedName, url = trimmedUrl, note = note.trim()),
                    )
                    showMessage("修改成功")
                }
            }
        }
    }

    fun deleteSource(source: TvSourceEntity) {
        viewModelScope.launch {
            repository.delete(source)
            showMessage("已删除")
        }
    }

    fun toggleEnabled(source: TvSourceEntity) {
        viewModelScope.launch {
            repository.update(source.copy(enabled = !source.enabled))
        }
    }

    /** 批量导入：JSON 数组或每行 "名称,URL[,备注]" */
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

    // region 可用性检测

    /** 检测单个直播源（GET 拉取 M3U 头部判断可达） */
    fun testSource(source: TvSourceEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val ok = withContext(Dispatchers.IO) { probe(source.url) }
            _uiState.update {
                it.copy(isTesting = false, testResults = it.testResults + (source.id to ok))
            }
        }
    }

    /** 并行检测全部直播源 */
    fun testAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val list = sources.value
            val results = coroutineScope {
                list.map { entity ->
                    async { entity.id to withContext(Dispatchers.IO) { probe(entity.url) } }
                }.awaitAll().toMap()
            }
            _uiState.update {
                it.copy(isTesting = false, testResults = it.testResults + results)
            }
        }
    }

    /** GET 播放列表地址，读响应头部片段判断是否可达 */
    private fun probe(url: String): Boolean = try {
        val request = okhttp3.Request.Builder().url(url).build()
        RetrofitClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val body = response.body ?: return@use false
            // 只读头部片段，不下载完整播放列表
            body.byteStream().use { stream ->
                val head = ByteArray(64)
                var total = 0
                while (total < head.size) {
                    val read = stream.read(head, total, head.size - total)
                    if (read < 0) break
                    total += read
                }
                total > 0
            }
        }
    } catch (_: Exception) {
        false
    }

    // endregion

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
