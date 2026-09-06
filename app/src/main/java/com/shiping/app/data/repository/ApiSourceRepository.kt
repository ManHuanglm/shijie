package com.shiping.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shiping.app.data.local.ApiSourceDao
import com.shiping.app.data.local.PreferencesManager
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.remote.ApiService
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * API 源管理仓库：增删改查、导入导出、检测
 */
class ApiSourceRepository(
    private val dao: ApiSourceDao,
    private val apiService: ApiService,
    private val preferences: PreferencesManager
) {

    fun getAll(): Flow<List<ApiSourceEntity>> = dao.getAll()

    fun getEnabled(): Flow<List<ApiSourceEntity>> = dao.getEnabled()

    suspend fun getById(id: Long): ApiSourceEntity? = dao.getById(id)

    suspend fun insert(source: ApiSourceEntity): Long = dao.insert(source)

    suspend fun update(source: ApiSourceEntity) = dao.update(source)

    suspend fun delete(source: ApiSourceEntity) = dao.delete(source)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun deleteAll() = dao.deleteAll()

    /**
     * 批量导入，支持 JSON 数组 或 每行 "名称,URL[,备注]" 格式
     */
    suspend fun importFromText(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0

        val sources = parseImportText(trimmed)
        if (sources.isEmpty()) return 0

        dao.insertAll(sources)
        return sources.size
    }

    private fun parseImportText(text: String): List<ApiSourceEntity> {
        // 尝试 JSON 数组
        if (text.startsWith("[")) {
            return runCatching {
                val type = object : TypeToken<List<ApiSourceEntity>>() {}.type
                Gson().fromJson<List<ApiSourceEntity>>(text, type)
                    .map { it.copy(id = 0) }
            }.getOrDefault(emptyList())
        }

        // 每行: name,url[,note]
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(",", "，").map { it.trim() }
                if (parts.size >= 2) {
                    ApiSourceEntity(
                        name = parts[0],
                        url = parts[1],
                        note = parts.getOrElse(2) { "" },
                        sortOrder = 0
                    )
                } else null
            }
    }

    /**
     * 导出为 JSON 数组文本
     */
    suspend fun exportToJson(): String {
        val list = dao.getAllList()
        return Gson().newBuilder().setPrettyPrinting().create().toJson(list)
    }

    /**
     * 导出为每行 "名称,URL,备注" 文本
     */
    suspend fun exportToText(): String {
        val list = dao.getAllList()
        return list.joinToString("\n") { "${it.name},${it.url},${it.note}" }
    }

    /**
     * 检测 API 是否可用
     */
    suspend fun testApi(url: String): TestResult {
        return runCatching {
            val response = apiService.getVodList(baseUrl = url, page = 1)
            if (response.code == 1 && response.list.isNotEmpty()) {
                TestResult(true, "连接成功，共 ${response.total} 条数据")
            } else if (response.code == 1) {
                TestResult(true, "连接成功，但暂无数据")
            } else {
                TestResult(false, "返回异常: ${response.msg}")
            }
        }.getOrElse {
            TestResult(false, "连接失败: ${it.message ?: "未知错误"}")
        }
    }

    data class TestResult(val success: Boolean, val message: String)

    /** 当前选中的 API 源 ID */
    val currentApiId: Flow<Long?> = preferences.currentApiId

    /** 设置当前 API 源 */
    suspend fun setCurrentApiId(id: Long) = preferences.setCurrentApiId(id)

    /**
     * 获取当前选中的 API 源实体
     */
    suspend fun getCurrentApiSource(): ApiSourceEntity? {
        val selectedId = preferences.currentApiId.first()
        if (selectedId != null) {
            dao.getById(selectedId)?.let { return it }
        }
        // 回退到第一个启用的源
        return dao.getEnabled().first().firstOrNull()
    }

    /**
     * 获取当前 API 地址（优先取用户选中的，否则第一个启用的，最后默认）
     */
    fun currentApiUrl(): Flow<String> = preferences.currentApiId.map { selectedId ->
        val all = dao.getEnabled().first()
        val selected = selectedId?.let { id -> all.firstOrNull { it.id == id } }
        selected?.url ?: all.firstOrNull()?.url ?: Constants.DEFAULT_API_URL
    }

    suspend fun currentApiUrlOnce(): String {
        val selectedId = preferences.currentApiId.first()
        val all = dao.getEnabled().first()
        val selected = selectedId?.let { id -> all.firstOrNull { it.id == id } }
        return selected?.url ?: all.firstOrNull()?.url ?: Constants.DEFAULT_API_URL
    }

    /** 获取当前 API 源名称 */
    suspend fun currentApiName(): String {
        return getCurrentApiSource()?.name ?: Constants.DEFAULT_API_NAME
    }

    /**
     * 首次启动时插入默认 API 源（如果数据库为空）
     */
    suspend fun initDefaultSource() {
        if (dao.count() == 0) {
            dao.insert(
                ApiSourceEntity(
                    name = Constants.DEFAULT_API_NAME,
                    url = Constants.DEFAULT_API_URL,
                    note = "默认源",
                    enabled = true
                )
            )
        }
    }
}
