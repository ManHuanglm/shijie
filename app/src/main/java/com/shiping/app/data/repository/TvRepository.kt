package com.shiping.app.data.repository

import com.shiping.app.data.local.TvSourceDao
import com.shiping.app.data.model.TvChannel
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.data.remote.RetrofitClient
import com.shiping.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 电视直播仓库：直播源增删改查 + M3U 播放列表拉取解析。
 */
class TvRepository(
    private val dao: TvSourceDao,
) {

    private val importExportHelper = SourceImportExportHelper(
        TvSourceEntity::class.java,
        { name, url, note, sortOrder ->
            TvSourceEntity(name = name, url = url, note = note, sortOrder = sortOrder)
        },
        { TvSourceEntity(name = "", url = "") },
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

    fun getAll(): Flow<List<TvSourceEntity>> = dao.getAll()

    suspend fun getAllList(): List<TvSourceEntity> = dao.getAllList()

    suspend fun getById(id: Long): TvSourceEntity? = dao.getById(id)

    suspend fun insert(source: TvSourceEntity): Long = dao.insert(source)

    suspend fun update(source: TvSourceEntity) = dao.update(source)

    suspend fun delete(source: TvSourceEntity) = dao.delete(source)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** 批量导入：支持 JSON 数组或每行 "名称,URL[,备注]"，返回导入条数 */
    suspend fun importFromText(text: String): Int {
        val sources = importExportHelper.parse(text)
        if (sources.isEmpty()) return 0
        sources.forEach { dao.insert(it) }
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

    /** 首次启动插入默认直播源 */
    suspend fun initDefaultSource() {
        if (dao.count() == 0) {
            dao.insert(
                TvSourceEntity(
                    name = Constants.DEFAULT_TV_SOURCE_NAME,
                    url = Constants.DEFAULT_TV_SOURCE_URL,
                    note = "系统默认",
                    enabled = true,
                ),
            )
        }
    }

    /**
     * 拉取并解析 M3U 播放列表，返回按分组归类的频道。
     * 支持标准 #EXTM3U / #EXTINF 格式，group-title 缺失时归入「其他」。
     */
    suspend fun loadChannels(source: TvSourceEntity): List<TvChannel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(source.url).get().build()
        RetrofitClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("播放列表为空")
            parseLiveList(body)
        }
    }

    /** 按内容特征分发解析：M3U 或 TVBox TXT 格式 */
    private fun parseLiveList(content: String): List<TvChannel> =
        if (content.contains("#EXTINF", ignoreCase = true) || content.contains("#EXTM3U", ignoreCase = true)) {
            parseM3u(content)
        } else {
            parseTxt(content)
        }

    /**
     * 解析 TVBox TXT 格式播放列表：
     * 央视频道,#genre#     ← 分组行
     * CCTV1,http://a#http://b   ← 频道行（多线路以 # 分隔）
     */
    private fun parseTxt(content: String): List<TvChannel> {
        data class PendingChannel(
            val name: String,
            val group: String,
            val urls: MutableList<String> = mutableListOf(),
        )

        val ordered = ArrayList<PendingChannel>()
        val byKey = LinkedHashMap<String, PendingChannel>()
        var pendingGroup = "其他"

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            // 分组行："组名,#genre#" 或 "组名#genre#"
            if (line.endsWith("#genre#", ignoreCase = true)) {
                pendingGroup = line.removeSuffix("#genre#").trim().removeSuffix(",").trim()
                    .ifBlank { "其他" }
                return@forEach
            }
            if (line.startsWith("#")) return@forEach
            val parts = line.split(",", "，")
            if (parts.size < 2) return@forEach
            val name = parts[0].trim()
            if (name.isBlank()) return@forEach
            val urls = parts.drop(1)
                .flatMap { it.split("#") }
                .map { it.trim() }
                .filter { it.contains("://") }
            if (urls.isEmpty()) return@forEach
            val existing = byKey[name]
            if (existing != null) {
                urls.forEach { url -> if (url !in existing.urls) existing.urls.add(url) }
            } else {
                val pending = PendingChannel(name = name, group = pendingGroup, urls = urls.toMutableList())
                byKey[name] = pending
                ordered.add(pending)
            }
        }
        if (ordered.isEmpty()) throw IllegalStateException("未解析到任何频道")
        return ordered.map { TvChannel(name = it.name, urls = it.urls, group = it.group, logo = "") }
    }

    /**
     * 解析 M3U 文本。
     * 典型行：
     * #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV1" tvg-logo="http://..." group-title="央视",CCTV-1 综合
     * http://xxx/live.m3u8
     */
    private fun parseM3u(content: String): List<TvChannel> {
        data class PendingChannel(
            val name: String,
            val group: String,
            val logo: String,
            val urls: MutableList<String> = mutableListOf(),
        )

        val ordered = ArrayList<PendingChannel>()
        val byKey = LinkedHashMap<String, PendingChannel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = extractExtInfName(line)
                    pendingGroup = extractAttr(line, "group-title")?.takeIf { it.isNotBlank() }
                    pendingLogo = extractAttr(line, "tvg-logo")?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#") -> {
                    // 其他注释/指令（#EXTM3U、#EXTVLCOPT 等）忽略
                }
                else -> {
                    // URL 行：与最近一条 EXTINF 配对；同名频道合并为一条、追加线路
                    val name = (pendingName ?: line.substringAfterLast('/').substringBefore('?'))
                        .ifBlank { "未命名频道" }
                    val key = name
                    val existing = byKey[name]
                    if (existing != null) {
                        if (line !in existing.urls) existing.urls.add(line)
                    } else {
                        val pending = PendingChannel(
                            name = name,
                            group = pendingGroup?.takeIf { it.isNotBlank() } ?: "其他",
                            logo = pendingLogo.orEmpty(),
                            urls = mutableListOf(line),
                        )
                        byKey[name] = pending
                        ordered.add(pending)
                    }
                    pendingName = null
                    pendingGroup = null
                    pendingLogo = null
                }
            }
        }
        if (ordered.isEmpty()) throw IllegalStateException("未解析到任何频道")
        return ordered.map { TvChannel(name = it.name, urls = it.urls, group = it.group, logo = it.logo) }
    }

    /** 提取 #EXTINF 行中逗号后的频道名称 */
    private fun extractExtInfName(line: String): String? {
        val idx = line.lastIndexOf(',')
        return if (idx >= 0 && idx < line.length - 1) line.substring(idx + 1).trim() else null
    }

    /** 从 #EXTINF 属性段提取 key="value" 形式的属性值 */
    private fun extractAttr(line: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1)
    }
}
