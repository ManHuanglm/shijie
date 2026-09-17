package com.shiping.app.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m3u8 去广告过滤器单元测试：
 * 覆盖关键词规则、目录指纹规则、时长分块规则与安全策略。
 */
class HlsAdFilterTest {

    @Test
    fun `master playlist is not filtered`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1920x1080
            high/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=640000
            low/index.m3u8
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(0, result.removedSegments)
        assertEquals(playlist, result.text)
    }

    @Test
    fun `keyword ad segment is removed`() {
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            video/seg1.ts
            #EXTINF:10.0,
            promo/ad.ts
            #EXTINF:10.0,
            video/seg2.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(1, result.removedSegments)
        assertTrue(result.text.contains("video/seg1.ts"))
        assertTrue(result.text.contains("video/seg2.ts"))
        assertFalse(result.text.contains("promo"))
    }

    @Test
    fun `foreign directory block is removed even with similar duration`() {
        // 广告块切片时长与正片一致，仅目录不同 → 只能靠目录指纹规则命中
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            /20260710/movie/2000kb/seg1.ts
            #EXTINF:10.0,
            /20260710/movie/2000kb/seg2.ts
            #EXTINF:10.0,
            /20260710/movie/2000kb/seg3.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,
            /20260831/movie/10092kb/ad1.ts
            #EXTINF:10.0,
            /20260831/movie/10092kb/ad2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,
            /20260710/movie/2000kb/seg4.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(2, result.removedSegments)
        assertFalse(result.text.contains("10092kb"))
        assertTrue(result.text.contains("/20260710/movie/2000kb/seg4.ts"))
    }

    @Test
    fun `short duration block in same directory is removed`() {
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            video/seg1.ts
            #EXTINF:10.0,
            video/seg2.ts
            #EXTINF:10.0,
            video/seg3.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:2.0,
            video/ad1.ts
            #EXTINF:2.0,
            video/ad2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,
            video/seg4.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(2, result.removedSegments)
        assertFalse(result.text.contains("ad1.ts"))
        assertTrue(result.text.contains("video/seg4.ts"))
    }

    @Test
    fun `key tag inside ad block is removed but global key is kept`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.com/key.key"
            #EXTINF:10.0,
            video/seg1.ts
            #EXTINF:10.0,
            video/seg2.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:5.0,
            ads/ad1.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,
            video/seg3.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(1, result.removedSegments)
        // 广告块的 METHOD=NONE 声明随切片一并移除，避免污染后续正片解析
        assertFalse(result.text.contains("METHOD=NONE"))
        assertTrue(result.text.contains("METHOD=AES-128"))
        assertTrue(result.text.contains("video/seg1.ts"))
        assertTrue(result.text.contains("video/seg2.ts"))
        assertTrue(result.text.contains("video/seg3.ts"))
    }

    @Test
    fun `all segments flagged is treated as false positive and returns original`() {
        val playlist = """
            #EXTM3U
            #EXTINF:10.0,
            promo/a.ts
            #EXTINF:10.0,
            preroll/b.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(0, result.removedSegments)
        assertEquals(playlist, result.text)
    }

    @Test
    fun `discontinuity sequence header is not a block boundary`() {
        val playlist = """
            #EXTM3U
            #EXT-X-DISCONTINUITY-SEQUENCE:2
            #EXTINF:10.0,
            video/seg1.ts
            #EXTINF:10.0,
            promo/ad.ts
            #EXTINF:10.0,
            video/seg2.ts
        """.trimIndent()
        val result = HlsAdFilter.filter(playlist)
        assertEquals(1, result.removedSegments)
        assertTrue(result.text.contains("video/seg1.ts"))
        assertTrue(result.text.contains("video/seg2.ts"))
    }

    @Test
    fun `playlist without segments is unchanged`() {
        val result = HlsAdFilter.filter("#EXTM3U\n#EXT-X-ENDLIST")
        assertEquals(0, result.removedSegments)
        assertEquals("#EXTM3U\n#EXT-X-ENDLIST", result.text)
    }
}
