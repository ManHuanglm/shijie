package com.shiping.app.data.repository

import com.shiping.app.data.model.ApiSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 源导入导出工具测试。
 * 重点回归：JSON 数组导入必须反序列化为实体类型（泛型擦除下 TypeToken 需显式构造具体类型）。
 */
class SourceImportExportHelperTest {

    private val helper = SourceImportExportHelper(
        ApiSourceEntity::class.java,
        { name, url, note, sortOrder ->
            ApiSourceEntity(name = name, url = url, note = note, sortOrder = sortOrder)
        },
        { ApiSourceEntity(name = "", url = "") },
    ) { source ->
        // 与 ApiSourceRepository 一致：兜底恢复 JSON 显式 null 字段
        source.copy(
            name = (source.name as String?) ?: "",
            url = (source.url as String?) ?: "",
            note = (source.note as String?) ?: "",
            enabled = (source.enabled as Boolean?) ?: true,
            sortOrder = (source.sortOrder as Int?) ?: 0,
            createdAt = (source.createdAt as Long?) ?: 0L,
        )
    }

    @Test
    fun `parse json array deserializes into entity list`() {
        val text = """
            [
              {"name": "源一", "url": "http://a.com/api.php", "note": "测试", "sortOrder": 3},
              {"name": "源二", "url": "http://b.com/api.php"}
            ]
        """.trimIndent()
        val sources = helper.parse(text)
        assertEquals(2, sources.size)
        assertEquals(ApiSourceEntity::class, sources[0]::class)
        assertEquals("源一", sources[0].name)
        assertEquals("http://a.com/api.php", sources[0].url)
        assertEquals("测试", sources[0].note)
        assertEquals(3, sources[0].sortOrder)
        assertEquals(true, sources[1].enabled)
        assertEquals("", sources[1].note)
    }

    @Test
    fun `parse line format supports chinese comma comments and blank lines`() {
        val text = "# 注释行\n源一,http://a.com/api.php,备注\n\n源二，http://b.com/api.php\n只有名字"
        val sources = helper.parse(text)
        assertEquals(2, sources.size)
        assertEquals("源一", sources[0].name)
        assertEquals("http://a.com/api.php", sources[0].url)
        assertEquals("备注", sources[0].note)
        assertEquals("源二", sources[1].name)
        assertEquals("http://b.com/api.php", sources[1].url)
    }

    @Test
    fun `parse blank text returns empty list`() {
        assertTrue(helper.parse("").isEmpty())
        assertTrue(helper.parse("   \n  ").isEmpty())
    }

    @Test
    fun `parse malformed json returns empty list`() {
        assertTrue(helper.parse("[{name:}]").isEmpty())
    }

    @Test
    fun `export json roundtrip preserves fields`() {
        val list = listOf(
            ApiSourceEntity(name = "源一", url = "http://a.com/api.php", note = "备注", sortOrder = 1),
        )
        val json = helper.exportToJson(list)
        val parsed = helper.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("源一", parsed[0].name)
        assertEquals("http://a.com/api.php", parsed[0].url)
        assertEquals("备注", parsed[0].note)
        assertEquals(1, parsed[0].sortOrder)
    }

    @Test
    fun `export text roundtrip preserves fields`() {
        val list = listOf(
            ApiSourceEntity(name = "源一", url = "http://a.com/api.php", note = "备注"),
        )
        val text = helper.exportToText(list, { it.name }, { it.url }, { it.note })
        assertTrue(text.contains("源一,http://a.com/api.php,备注"))
        val parsed = helper.parse(text)
        assertEquals(1, parsed.size)
        assertEquals("备注", parsed[0].note)
    }
}
