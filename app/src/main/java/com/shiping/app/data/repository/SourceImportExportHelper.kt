package com.shiping.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * 源（API/解析）导入导出通用工具，消除 ApiSourceRepository 与 ParseSourceRepository 的重复逻辑。
 *
 * @param T 源实体类型
 * @param elementClass 实体的 Class，用于构造 JSON 反序列化的具体 List 泛型类型
 * @param factory 由 (name, url, note, sortOrder) 构造实体的函数
 * @param defaultInstance 构造带 Kotlin 默认值的空实例，用于补齐 JSON 缺失字段
 * @param sanitize JSON 反序列化后的归一化处理（如显式 null 字段兜底）
 */
class SourceImportExportHelper<T : Any>(
    private val elementClass: Class<T>,
    private val factory: (name: String, url: String, note: String, sortOrder: Int) -> T,
    private val defaultInstance: () -> T,
    private val sanitize: (T) -> T = { it },
) {

    /**
     * 解析导入文本，支持 JSON 数组 或 每行 "名称,URL[,备注]" 格式。
     */
    fun parse(text: String, gson: Gson = Gson()): List<T> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[")) {
            return runCatching {
                // 泛型 T 会被擦除，必须用 elementClass 构造具体类型，否则反序列化出的是 Map 而非实体
                val type = TypeToken.getParameterized(List::class.java, elementClass).type
                // Gson 绕过构造器创建实例，Kotlin 字段默认值不生效（基础类型取 Java 默认，
                // 如 Boolean 变 false），先用默认实例把 JSON 里缺失的字段补齐
                val defaults = gson.toJsonTree(defaultInstance()).asJsonObject
                val filled = JsonArray()
                JsonParser.parseString(trimmed).asJsonArray.forEach { element ->
                    val obj = element.asJsonObject
                    defaults.entrySet().forEach { (key, value) ->
                        if (!obj.has(key)) obj.add(key, value)
                    }
                    filled.add(obj)
                }
                gson.fromJson<List<T>>(filled, type).orEmpty().map(sanitize)
            }.getOrDefault(emptyList())
        }

        return trimmed.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(",", "，").map { it.trim() }
                if (parts.size >= 2) {
                    factory(parts[0], parts[1], parts.getOrElse(2) { "" }, 0)
                } else {
                    null
                }
            }
    }

    /** 导出为 JSON 数组文本 */
    fun exportToJson(list: List<T>): String {
        return Gson().newBuilder().setPrettyPrinting().create().toJson(list)
    }

    /** 导出为每行 "名称,URL,备注" 文本 */
    fun exportToText(
        list: List<T>,
        nameOf: (T) -> String,
        urlOf: (T) -> String,
        noteOf: (T) -> String,
    ): String {
        return list.joinToString("\n") { "${nameOf(it)},${urlOf(it)},${noteOf(it)}" }
    }
}
