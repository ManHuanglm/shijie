package com.shiping.app.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * TVBox 配置仓解析器。
 *
 * 支持的配置形态：
 * - 纯文本 JSON（容忍 BOM、前导杂字符、// 行注释与块注释）
 * - 图片壳：JPEG/PNG 图片尾部追加 Base64 编码的 JSON（饭太硬等）
 * - 多仓：{"urls" | "warehouse" | "storeHouse": [...]}
 * - 单仓：{"sites": [...], "parses": [...], "lives": [...]}
 *
 * 提取范围（应用能力内可直连的部分）：
 * - sites：type 0（MacCMS V10 JSON）与 type 1（XML，/at/xml 结尾的自动转 JSON 地址），
 *   带 jar 或其余 type（spider 等）需要爬虫运行时，不提取仅计数
 * - parses：前缀拼接式解析接口（url 以 = 结尾），转应用的解析源
 * - lives：type 0 的播放列表地址（M3U），转应用的直播源
 *
 * 不支持：$#key#$ 加密配置（各家加密实现不统一，明确提示）。
 */
object TvBoxConfigParser {

    /** 单仓中可提取的 CMS 点播站点 */
    data class TvBoxSite(val name: String, val url: String, val isXml: Boolean)

    /** 前缀拼接式解析接口 */
    data class TvBoxParse(val name: String, val url: String)

    /** 直播播放列表地址 */
    data class TvBoxLive(val name: String, val url: String)

    data class Result(
        val ok: Boolean = false,
        /** 非空表示这是多仓配置，元素为 名称 to 子配置地址 */
        val warehouses: List<Pair<String, String>> = emptyList(),
        val sites: List<TvBoxSite> = emptyList(),
        val parses: List<TvBoxParse> = emptyList(),
        val lives: List<TvBoxLive> = emptyList(),
        /** 需要 spider 运行时而被跳过的站点数 */
        val spiderCount: Int = 0,
        /** ok=false 时的原因说明 */
        val unsupportedReason: String? = null,
    )

    fun parse(data: ByteArray): Result {
        if (data.isEmpty()) return Result(unsupportedReason = "配置内容为空")

        val text = decodeText(data)
        if (text.startsWith("\$#")) {
            return Result(unsupportedReason = "加密配置暂不支持导入")
        }

        val jsonText = when {
            isJpeg(data) -> imagePayloadText(data, JPEG_EOI)
            isPng(data) -> imagePayloadText(data, PNG_IEND)
            else -> text
        } ?: return Result(unsupportedReason = "配置格式无法识别")

        val sanitized = sanitizeJson(jsonText)
            ?: return Result(unsupportedReason = "配置中未找到 JSON 内容")
        val element = runCatching { JsonParser.parseString(sanitized) }.getOrNull()
            ?: return Result(unsupportedReason = "JSON 解析失败")

        return when {
            element.isJsonObject -> parseObject(element.asJsonObject)
            element.isJsonArray -> parseSitesArray(element.asJsonArray)
            else -> Result(unsupportedReason = "配置格式无法识别")
        }
    }

    // region 文本与图片壳预处理

    private fun isJpeg(data: ByteArray) =
        data.size > 4 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()

    private fun isPng(data: ByteArray) =
        data.size > 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte()

    /** 取图片结束符之后的最长 Base64 段并解码为文本（配置藏在图片字节后面） */
    private fun imagePayloadText(data: ByteArray, terminator: ByteArray): String? {
        val end = lastIndexOf(data, terminator)
        if (end < 0) return null
        val payload = String(
            data.copyOfRange(end + terminator.size, data.size),
            Charsets.ISO_8859_1,
        )
        val longestBase64 = Regex("[A-Za-z0-9+/=]{16,}").findAll(payload)
            .map { it.value }
            .maxByOrNull { it.length }
            ?.replace(Regex("\\s"), "")
            ?: return null
        val decoded = runCatching {
            Base64.getMimeDecoder().decode(longestBase64)
        }.getOrNull() ?: return null
        return String(decoded, Charsets.UTF_8)
    }

    private fun lastIndexOf(data: ByteArray, pattern: ByteArray): Int {
        for (i in data.size - pattern.size downTo 0) {
            var matched = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) { matched = false; break }
            }
            if (matched) return i
        }
        return -1
    }

    /**
     * 文本解码：若整体为 hex 文本且解码后是加密配置（"$#" 魔数），返回解码结果；
     * 否则原样返回 UTF-8 文本。
     */
    private fun decodeText(data: ByteArray): String {
        val text = String(data, Charsets.UTF_8).trim()
        if (text.length > 16 && text.length % 2 == 0 && HEX_ONLY.matches(text)) {
            runCatching {
                val decoded = String(hexToBytes(text), Charsets.ISO_8859_1)
                if (decoded.startsWith("\$#")) return decoded
            }
        }
        return text
    }

    /**
     * 剥离 JSON 前后杂质与注释：定位首个 '{'/'[' 到最后一个 '}'/']'，
     * 再移除字符串外的行注释与块注释（字符串内的 "//" 不受影响）。
     */
    private fun sanitizeJson(raw: String): String? {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        val start = trimmed.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val end = maxOf(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'))
        if (end <= start) return null
        val body = trimmed.substring(start, end + 1)

        val sb = StringBuilder(body.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (inString) {
                sb.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when {
                c == '"' -> { inString = true; sb.append(c); i++ }
                c == '/' && i + 1 < body.length && body[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < body.length && !(body[i] == '*' && body[i + 1] == '/')) i++
                    i = minOf(i + 2, body.length)
                }
                c == '/' && i + 1 < body.length && body[i + 1] == '/' -> {
                    while (i < body.length && body[i] != '\n') i++
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString().trim().ifEmpty { null }
    }

    // endregion

    // region 结构解析

    private fun parseObject(obj: JsonObject): Result {
        // 多仓：urls / warehouse / storeHouse 数组
        val warehouses = mutableListOf<Pair<String, String>>()
        for (key in listOf("urls", "warehouse", "storeHouse", "storehouses")) {
            val arr = obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            for (item in arr) {
                val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val name = optString(o, "name", "title", "sourceName").orEmpty()
                val url = optString(o, "url", "sourceUrl")
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    warehouses += name.ifBlank { url } to url.trim()
                }
            }
        }
        if (warehouses.isNotEmpty()) return Result(ok = true, warehouses = warehouses)

        // sites 缺失时容忍：只要 parses/lives 有内容仍可导入
        val sites = obj.get("sites")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val sitesResult = parseSitesArray(sites).let { r ->
            r.copy(
                parses = extractParses(obj),
                lives = extractLives(obj),
            )
        }
        if (sitesResult.sites.isEmpty() && sitesResult.parses.isEmpty() &&
            sitesResult.lives.isEmpty() && sitesResult.spiderCount == 0
        ) {
            return Result(unsupportedReason = "配置中未找到 sites/parses/lives 内容")
        }
        return sitesResult
    }

    private fun parseSitesArray(arr: JsonArray): Result {
        val sites = mutableListOf<TvBoxSite>()
        var spider = 0
        for (item in arr) {
            val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val api = optString(o, "api")?.trim().orEmpty()
            val hasJar = optString(o, "jar")?.isNotBlank() == true
            val type = optInt(o, "type")
            if (!api.startsWith("http") || hasJar || (type != 0 && type != 1)) {
                spider++
                continue
            }
            val name = optString(o, "name") ?: optString(o, "key") ?: "未命名"
            val xmlEndpoint = XML_SUFFIX.find(api) != null
            // XML 接口地址以 /at/xml 结尾时剥掉后缀即为 JSON 接口（与 type 无关，V1/V10 均适用）
            when {
                xmlEndpoint -> sites += TvBoxSite(name, api.replace(XML_SUFFIX, ""), isXml = false)
                type == 1 -> sites += TvBoxSite(name, api, isXml = true)
                else -> sites += TvBoxSite(name, api, isXml = false)
            }
        }
        return Result(ok = true, sites = sites, spiderCount = spider)
    }

    private fun extractParses(obj: JsonObject): List<TvBoxParse> {
        val arr = obj.get("parses")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val result = mutableListOf<TvBoxParse>()
        for (item in arr) {
            val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val url = optString(o, "url")?.trim().orEmpty()
            val type = optInt(o, "type")
            // 仅取前缀拼接式（url 以 = 结尾），type 0/1；网页壳解析对直连播放无意义
            if (!url.startsWith("http") || !url.endsWith("=")) continue
            if (type != 0 && type != 1) continue
            val name = optString(o, "name") ?: "未命名"
            result += TvBoxParse(name, url)
        }
        return result
    }

    private fun extractLives(obj: JsonObject): List<TvBoxLive> {
        val arr = obj.get("lives")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val result = mutableListOf<TvBoxLive>()
        for (item in arr) {
            val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val url = optString(o, "url")?.trim().orEmpty()
            val type = optInt(o, "type")
            // type 0 为远程播放列表（m3u/txt），其余为内嵌频道组，不处理
            if (type != 0 || !url.startsWith("http")) continue
            val name = optString(o, "name") ?: "未命名"
            result += TvBoxLive(name, url)
        }
        return result
    }

    // endregion

    // region 容错取值

    private fun optString(o: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = o.get(key)?.takeIf { it.isJsonPrimitive } ?: continue
            val s = runCatching { value.asString }.getOrNull() ?: continue
            if (s.isNotBlank()) return s.trim()
        }
        return null
    }

    private fun optInt(o: JsonObject, key: String): Int =
        optString(o, key)?.toDoubleOrNull()?.toInt() ?: -1

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }

    // endregion

    private val HEX_ONLY = Regex("^[0-9a-fA-F]+$")
    private val XML_SUFFIX = Regex("/at/xml\\b", RegexOption.IGNORE_CASE)
    private val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    private val PNG_IEND = byteArrayOf(
        0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )
}
