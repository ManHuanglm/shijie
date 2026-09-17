package com.shiping.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 苹果CMS 播放源解析（$$$ / # / $ 分隔符）与封面字段合并逻辑测试。
 */
class VodTest {

    @Test
    fun `parsePlaySources handles multi source with episodes`() {
        val vod = Vod(
            vodPlayFrom = "ken\$\$\$HD高清",
            vodPlayUrl = "第01集\$http://a.com/1.m3u8#第02集\$http://a.com/2.m3u8\$\$\$第01集\$http://b.com/1.mp4",
        )
        val sources = vod.parsePlaySources()
        assertEquals(2, sources.size)

        assertEquals("ken", sources[0].name)
        assertEquals(2, sources[0].episodes.size)
        assertEquals("第01集", sources[0].episodes[0].name)
        assertEquals("http://a.com/1.m3u8", sources[0].episodes[0].url)
        assertEquals("第02集", sources[0].episodes[1].name)
        assertEquals("http://a.com/2.m3u8", sources[0].episodes[1].url)

        assertEquals("HD高清", sources[1].name)
        assertEquals(1, sources[1].episodes.size)
        assertEquals("http://b.com/1.mp4", sources[1].episodes[0].url)
    }

    @Test
    fun `parsePlaySources returns empty for blank play url`() {
        assertTrue(Vod(vodPlayFrom = "ken", vodPlayUrl = "").parsePlaySources().isEmpty())
        assertTrue(Vod().parsePlaySources().isEmpty())
    }

    @Test
    fun `parsePlaySources skips episodes without name url separator`() {
        val vod = Vod(
            vodPlayFrom = "ken",
            vodPlayUrl = "无效条目#第01集\$http://a.com/1.m3u8",
        )
        val sources = vod.parsePlaySources()
        assertEquals(1, sources.size)
        assertEquals(1, sources[0].episodes.size)
        assertEquals("第01集", sources[0].episodes[0].name)
    }

    @Test
    fun `safePic falls back in order`() {
        assertEquals("p1", Vod(vodPic = "p1", vodPicThumb = "p2", vodPicSlide = "p3").safePic)
        assertEquals("p2", Vod(vodPicThumb = "p2", vodPicSlide = "p3").safePic)
        assertEquals("p3", Vod(vodPicSlide = "p3").safePic)
        assertEquals("", Vod().safePic)
    }

    @Test
    fun `mergeWithDetail fills missing cover fields`() {
        val listItem = Vod(vodId = 1, vodName = "片名")
        val detail = Vod(vodId = 1, vodPic = "pic", vodScore = "8.5", vodRemarks = "更新至10集")
        val merged = listItem.mergeWithDetail(detail)
        assertEquals("pic", merged.vodPic)
        assertEquals("8.5", merged.vodScore)
        assertEquals("更新至10集", merged.vodRemarks)
        assertEquals("片名", merged.vodName)
    }

    @Test
    fun `mergeWithDetail keeps existing remarks when detail blank`() {
        val listItem = Vod(vodId = 1, vodName = "片名", vodRemarks = "集数01")
        val detail = Vod(vodId = 1, vodPic = "pic", vodRemarks = "")
        val merged = listItem.mergeWithDetail(detail)
        assertEquals("集数01", merged.vodRemarks)
    }
}
