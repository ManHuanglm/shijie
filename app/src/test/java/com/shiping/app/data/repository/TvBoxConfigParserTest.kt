package com.shiping.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * TVBox 配置仓解析测试：单仓/多仓/图片壳/加密/脏 JSON 各形态。
 */
class TvBoxConfigParserTest {

    @Test
    fun `parses single config with comments garbage and all site types`() {
        val json = """
            // 收集于网络，仅供测试
            {
              "spider": "https://x.com/spider.jar;md5;abc",
              "sites": [
                {"key": "a", "name": "源A", "type": 0, "api": "http://a.com/api.php/provide/vod"},
                {"key": "b", "name": "源B", "type": 1, "api": "http://b.com/api.php/provide/vod/at/xml"},
                {"key": "c", "name": "源C", "type": 1, "api": "http://c.com/xml.php"},
                {"key": "d", "name": "源D", "type": 3, "api": "csp_XXX"},
                {"key": "e", "name": "源E", "type": 0, "api": "http://e.com/api.php/provide/vod", "jar": "http://x.com/jar"},
                {"key": "f", "name": "源F", "type": 0, "api": "ftp://bad"},
                {"key": "g", "type": 0, "api": "http://g.com/provide/vod"}
              ],
              "parses": [
                {"name": "解析1", "type": 0, "url": "http://p1.com/api/?key=x&url="},
                {"name": "网页解析", "type": 3, "url": "http://web.com/?url="},
                {"name": "无后缀", "type": 0, "url": "http://p2.com/api"}
              ],
              "lives": [
                {"name": "直播组", "type": 0, "url": "http://live.com/iptv.m3u"},
                {"name": "内嵌组", "type": 0, "group": "redirect"}
              ]
            }
        """.trimIndent()
        val result = TvBoxConfigParser.parse(json.toByteArray())

        assertTrue(result.ok)
        // A(JSON) + B(at/xml 剥离为 JSON) + C(XML) + G(key 兜底名称)
        assertEquals(4, result.sites.size)
        assertEquals("源A", result.sites[0].name)
        assertFalse(result.sites[0].isXml)
        assertEquals("http://b.com/api.php/provide/vod", result.sites[1].url)
        assertFalse(result.sites[1].isXml)
        assertEquals("源C", result.sites[2].name)
        assertTrue(result.sites[2].isXml)
        assertEquals("g", result.sites[3].name)
        assertEquals(3, result.spiderCount)

        assertEquals(1, result.parses.size)
        assertEquals("解析1", result.parses[0].name)
        assertEquals(1, result.lives.size)
        assertEquals("http://live.com/iptv.m3u", result.lives[0].url)
    }

    @Test
    fun `parses storeHouse multi warehouse with leading comment`() {
        val json = """
            //收集于网络，仅供测试
            {"storeHouse": [
            {"sourceName":"仓一","sourceUrl":"https://1.com/tv"},
            {"sourceName":"仓二","sourceUrl":"https://2.com/tv"}]}
        """.trimIndent()
        val result = TvBoxConfigParser.parse(json.toByteArray())

        assertTrue(result.ok)
        assertEquals(listOf("仓一" to "https://1.com/tv", "仓二" to "https://2.com/tv"), result.warehouses)
    }

    @Test
    fun `parses urls multi warehouse`() {
        val json = """{"urls": [{"url": "http://a.net/tv", "name": "配置A"}, {"url": "http://b.net/tv"}]}"""
        val result = TvBoxConfigParser.parse(json.toByteArray())

        assertTrue(result.ok)
        assertEquals(2, result.warehouses.size)
        assertEquals("配置A" to "http://a.net/tv", result.warehouses[0])
        assertEquals("http://b.net/tv", result.warehouses[1].first) // 缺名称时用地址兜底
    }

    @Test
    fun `parses image shell config with base64 payload after jpeg eoi`() {
        val json = """{"sites": [{"key": "k", "name": "图源", "type": 0, "api": "http://img.com/vod"}]}"""
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray())
        val data = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x11, 0x22, // JPEG 头 + 乱码
            0xFF.toByte(), 0xD9.toByte(), // EOI
        ) + "oSMBOOoS**".toByteArray() + base64.toByteArray()

        val result = TvBoxConfigParser.parse(data)

        assertTrue(result.ok)
        assertEquals(1, result.sites.size)
        assertEquals("图源", result.sites[0].name)
        assertEquals("http://img.com/vod", result.sites[0].url)
    }

    @Test
    fun `encrypted plain config reports unsupported`() {
        val data = "\$#123456\$af869bdf".toByteArray()
        val result = TvBoxConfigParser.parse(data)

        assertFalse(result.ok)
        assertTrue(result.unsupportedReason!!.contains("加密"))
    }

    @Test
    fun `hex encoded encrypted config reports unsupported`() {
        val inner = "\$#123456\$af869b"
        val hex = inner.toByteArray().joinToString("") { "%02x".format(it) }
        val result = TvBoxConfigParser.parse(hex.toByteArray())

        assertFalse(result.ok)
        assertTrue(result.unsupportedReason!!.contains("加密"))
    }

    @Test
    fun `html page reports unrecognized`() {
        val result = TvBoxConfigParser.parse("<!doctype html><html><body>游魂</body></html>".toByteArray())
        assertFalse(result.ok)
    }

    @Test
    fun `empty data reports empty`() {
        val result = TvBoxConfigParser.parse(ByteArray(0))
        assertFalse(result.ok)
    }

    @Test
    fun `comment slash inside url string is preserved`() {
        // 字符串内的 "://" 不能被注释剥离逻辑破坏
        val json = """{"sites": [{"key": "a", "name": "源A", "type": 0, "api": "http://a.com/api.php/provide/vod"}]}"""
        val result = TvBoxConfigParser.parse("// 注释\n$json".toByteArray())
        assertTrue(result.ok)
        assertEquals("http://a.com/api.php/provide/vod", result.sites[0].url)
    }

    @Test
    fun `config with only parses and lives is importable`() {
        val json = """
            {"parses": [{"name": "P", "type": 0, "url": "http://p.com/?key=1&url="}],
             "lives": [{"name": "L", "type": 0, "url": "http://l.com/list.m3u"}]}
        """.trimIndent()
        val result = TvBoxConfigParser.parse(json.toByteArray())

        assertTrue(result.ok)
        assertEquals(0, result.sites.size)
        assertEquals(1, result.parses.size)
        assertEquals(1, result.lives.size)
        assertNull(result.unsupportedReason)
    }
}
