package com.shiping.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shiping.app.data.local.ParseSourceDao
import com.shiping.app.data.local.PreferencesManager
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 解析源管理仓库
 */
class ParseSourceRepository(
    private val dao: ParseSourceDao,
    private val preferences: PreferencesManager
) {

    fun getAll(): Flow<List<ParseSourceEntity>> = dao.getAll()

    fun getEnabled(): Flow<List<ParseSourceEntity>> = dao.getEnabled()

    suspend fun getById(id: Long): ParseSourceEntity? = dao.getById(id)

    suspend fun insert(source: ParseSourceEntity): Long = dao.insert(source)

    suspend fun update(source: ParseSourceEntity) = dao.update(source)

    suspend fun delete(source: ParseSourceEntity) = dao.delete(source)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun deleteAll() = dao.deleteAll()

    /** 当前选中的解析源 ID */
    val currentParserId: Flow<Long?> = preferences.currentParserId

    suspend fun setCurrentParserId(id: Long?) = preferences.setCurrentParserId(id)

    /**
     * 获取当前选中的解析源实体
     */
    suspend fun getCurrentParser(): ParseSourceEntity? {
        val selectedId = preferences.currentParserId.first()
        if (selectedId != null) {
            dao.getById(selectedId)?.let { return it }
        }
        // 回退到第一个启用的源
        return dao.getEnabled().first().firstOrNull()
    }

    /**
     * 获取当前解析地址前缀
     */
    suspend fun currentParserUrl(): String {
        return getCurrentParser()?.url ?: Constants.DEFAULT_PARSER_URL
    }

    /** 获取当前解析源名称 */
    suspend fun currentParserName(): String {
        return getCurrentParser()?.name ?: Constants.DEFAULT_PARSER_NAME
    }

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

    private fun parseImportText(text: String): List<ParseSourceEntity> {
        if (text.startsWith("[")) {
            return runCatching {
                val type = object : TypeToken<List<ParseSourceEntity>>() {}.type
                Gson().fromJson<List<ParseSourceEntity>>(text, type).map { it.copy(id = 0) }
            }.getOrDefault(emptyList())
        }
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(",", "，").map { it.trim() }
                if (parts.size >= 2) {
                    ParseSourceEntity(name = parts[0], url = parts[1], note = parts.getOrElse(2) { "" }, sortOrder = 0)
                } else null
            }
    }

    /** 导出为 JSON 数组文本 */
    suspend fun exportToJson(): String {
        return Gson().newBuilder().setPrettyPrinting().create().toJson(dao.getAllList())
    }

    /** 导出为每行 "名称,URL,备注" 文本 */
    suspend fun exportToText(): String {
        return dao.getAllList().joinToString("\n") { "${it.name},${it.url},${it.note}" }
    }

    /**
     * 首次启动时插入默认解析源
     */
    suspend fun initDefaultSource() {
        if (dao.count() == 0) {
            dao.insert(
                ParseSourceEntity(
                    name = Constants.DEFAULT_PARSER_NAME,
                    url = Constants.DEFAULT_PARSER_URL,
                    note = "默认解析",
                    enabled = true
                )
            )
        }
    }
}
