package com.shiping.app.data.repository

import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.data.model.TvSourceEntity
import com.shiping.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * TVBox 配置导入编排：拉取配置 → 解析 → 按类别写入 API 源 / 解析源 / 直播源。
 */
class TvBoxConfigImporter(
    private val apiSourceRepository: ApiSourceRepository,
    private val parseSourceRepository: ParseSourceRepository,
    private val tvRepository: TvRepository,
) {

    data class Summary(
        /** 非空表示是多仓配置，需用户选择子配置后再导入 */
        val warehouses: List<Pair<String, String>> = emptyList(),
        val apiImported: Int = 0,
        val apiXmlDisabled: Int = 0,
        val parseImported: Int = 0,
        val liveImported: Int = 0,
        val duplicated: Int = 0,
        val spiderSkipped: Int = 0,
        val error: String? = null,
    ) {
        fun messageText(): String {
            if (error != null) return error
            if (warehouses.isNotEmpty()) return "发现 ${warehouses.size} 个子配置，请选择导入"
            val parts = mutableListOf<String>()
            if (apiImported > 0) parts += "API 源 $apiImported 个"
            if (apiXmlDisabled > 0) parts += "XML 接口 $apiXmlDisabled 个（已禁用）"
            if (parseImported > 0) parts += "解析源 $parseImported 个"
            if (liveImported > 0) parts += "直播源 $liveImported 个"
            if (duplicated > 0) parts += "重复跳过 $duplicated 个"
            if (spiderSkipped > 0) parts += "spider 源 $spiderSkipped 个（需爬虫运行时）"
            return if (parts.isEmpty()) {
                "配置中未发现可导入的源"
            } else {
                "导入完成：" + parts.joinToString("，")
            }
        }
    }

    /**
     * 拉取并导入一个配置地址（单仓直接导入；多仓返回仓库列表待用户选择）。
     * @param label 写入各源备注的配置名（默认用地址）
     */
    suspend fun import(url: String, label: String = ""): Summary {
        return try {
            val data = fetch(url)
            val parsed = TvBoxConfigParser.parse(data)
            when {
                !parsed.ok -> Summary(error = parsed.unsupportedReason)
                parsed.warehouses.isNotEmpty() -> Summary(warehouses = parsed.warehouses)
                else -> importParsed(parsed, label.ifBlank { url })
            }
        } catch (e: Exception) {
            Summary(error = "配置拉取失败：${e.message ?: "网络错误"}")
        }
    }

    private suspend fun importParsed(parsed: TvBoxConfigParser.Result, label: String): Summary {
        var apiImported = 0
        var apiXmlDisabled = 0
        var parseImported = 0
        var liveImported = 0
        var duplicated = 0
        val seen = mutableSetOf<String>()

        // API 源（按 URL 去重，含数据库已有）
        val existingApis = apiSourceRepository.getAllList().map { it.url.trim() }.toMutableSet()
        for (site in parsed.sites) {
            val url = site.url.trim()
            if (!seen.add("A$url") || !existingApis.add(url)) { duplicated++; continue }
            apiSourceRepository.insert(
                ApiSourceEntity(
                    name = site.name,
                    url = url,
                    note = "TVBox·$label" + if (site.isXml) "·XML" else "",
                    enabled = !site.isXml,
                ),
            )
            if (site.isXml) apiXmlDisabled++ else apiImported++
        }

        // 解析源
        val existingParses = parseSourceRepository.getAllList().map { it.url.trim() }.toMutableSet()
        for (parse in parsed.parses) {
            val url = parse.url.trim()
            if (!seen.add("P$url") || !existingParses.add(url)) { duplicated++; continue }
            parseSourceRepository.insert(
                ParseSourceEntity(name = parse.name, url = url, note = "TVBox·$label"),
            )
            parseImported++
        }

        // 直播源
        val existingLives = tvRepository.getAllList().map { it.url.trim() }.toMutableSet()
        for (live in parsed.lives) {
            val url = live.url.trim()
            if (!seen.add("L$url") || !existingLives.add(url)) { duplicated++; continue }
            tvRepository.insert(
                TvSourceEntity(name = live.name, url = url, note = "TVBox·$label"),
            )
            liveImported++
        }

        return Summary(
            apiImported = apiImported,
            apiXmlDisabled = apiXmlDisabled,
            parseImported = parseImported,
            liveImported = liveImported,
            duplicated = duplicated,
            spiderSkipped = parsed.spiderCount,
        )
    }

    private suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(normalizeUrl(url)).build()
        RetrofitClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** 中文域名转 punycode（如 饭太硬.net），否则 OkHttp 会拒绝请求 */
    private fun normalizeUrl(raw: String): String = runCatching {
        val trimmed = raw.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return@runCatching trimmed
        val rest = trimmed.substring(schemeEnd + 3)
        val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val host = if (pathStart >= 0) rest.substring(0, pathStart) else rest
        val after = if (pathStart >= 0) rest.substring(pathStart) else ""
        val asciiHost = if (host.any { it.code > 127 }) {
            java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED)
        } else {
            host
        }
        trimmed.substring(0, schemeEnd + 3) + asciiHost + after
    }.getOrDefault(raw)
}
