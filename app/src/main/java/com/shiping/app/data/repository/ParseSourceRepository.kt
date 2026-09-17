package com.shiping.app.data.repository

import com.shiping.app.data.local.ParseSourceDao
import com.shiping.app.data.local.PreferencesManager
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 解析源管理仓库
 */
class ParseSourceRepository(
    private val dao: ParseSourceDao,
    private val preferences: PreferencesManager,
) {

    private val importExportHelper = SourceImportExportHelper(
        ParseSourceEntity::class.java,
        { name, url, note, sortOrder ->
            ParseSourceEntity(name = name, url = url, note = note, sortOrder = sortOrder)
        },
        { ParseSourceEntity(name = "", url = "") },
    ) { source ->
        // 兜底：JSON 显式 null 字段在此恢复
        source.copy(
            name = (source.name as String?) ?: "",
            url = (source.url as String?) ?: "",
            note = (source.note as String?) ?: "",
            enabled = (source.enabled as Boolean?) ?: true,
            sortOrder = (source.sortOrder as Int?) ?: 0,
            createdAt = (source.createdAt as Long?) ?: 0L,
        )
    }

    fun getAll(): Flow<List<ParseSourceEntity>> = dao.getAll()

    fun getEnabled(): Flow<List<ParseSourceEntity>> = dao.getEnabled()

    suspend fun getById(id: Long): ParseSourceEntity? = dao.getById(id)

    suspend fun insert(source: ParseSourceEntity): Long = dao.insert(source)

    /** 一次性获取全部源列表（TVBox 导入去重用） */
    suspend fun getAllList(): List<ParseSourceEntity> = dao.getAllList()

    suspend fun update(source: ParseSourceEntity) = dao.update(source)

    suspend fun delete(source: ParseSourceEntity) = dao.delete(source)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun deleteAll() = dao.deleteAll()

    /** 当前选中的解析源 ID */
    val currentParserId: Flow<Long?> = preferences.currentParserId

    suspend fun setCurrentParserId(id: Long?) = preferences.setCurrentParserId(id)

    /** 获取当前选中的解析源实体 */
    suspend fun getCurrentParser(): ParseSourceEntity? {
        val selectedId = preferences.currentParserId.first()
        if (selectedId != null) {
            dao.getById(selectedId)?.let { return it }
        }
        return dao.getEnabled().first().firstOrNull()
    }

    /** 获取当前解析地址前缀 */
    suspend fun currentParserUrl(): String = getCurrentParser()?.url ?: Constants.DEFAULT_PARSER_URL

    /** 获取当前解析源名称 */
    suspend fun currentParserName(): String = getCurrentParser()?.name ?: Constants.DEFAULT_PARSER_NAME

    /** 批量导入，支持 JSON 数组 或 每行 "名称,URL[,备注]" 格式 */
    suspend fun importFromText(text: String): Int {
        val sources = importExportHelper.parse(text)
        if (sources.isEmpty()) return 0
        dao.insertAll(sources)
        return sources.size
    }

    /** 导出为 JSON 数组文本 */
    suspend fun exportToJson(): String = importExportHelper.exportToJson(dao.getAllList())

    /** 导出为每行 "名称,URL,备注" 文本 */
    suspend fun exportToText(): String = importExportHelper.exportToText(
        list = dao.getAllList(),
        nameOf = { it.name },
        urlOf = { it.url },
        noteOf = { it.note },
    )

    /** 首次启动时插入默认解析源 */
    suspend fun initDefaultSource() {
        if (dao.count() == 0) {
            dao.insert(
                ParseSourceEntity(
                    name = Constants.DEFAULT_PARSER_NAME,
                    url = Constants.DEFAULT_PARSER_URL,
                    note = "默认解析",
                    enabled = true,
                ),
            )
        }
    }
}
