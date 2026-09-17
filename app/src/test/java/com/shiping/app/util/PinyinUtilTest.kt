package com.shiping.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拼音首字母提取测试（搜索联想用）。
 */
class PinyinUtilTest {

    @Test
    fun `chinese name extracts initials`() {
        assertEquals("dldl", PinyinUtil.initials("斗罗大陆"))
        assertEquals("xyj", PinyinUtil.initials("西游记"))
    }

    @Test
    fun `digits are skipped`() {
        assertEquals("zl", PinyinUtil.initials("战狼2"))
        assertEquals("dldl", PinyinUtil.initials("斗罗大陆2025"))
    }

    @Test
    fun `english letters are lowercased and kept`() {
        assertEquals("abc", PinyinUtil.initials("ABC"))
    }

    @Test
    fun `mixed chinese and english`() {
        assertEquals("dlavengersdl", PinyinUtil.initials("斗罗Avengers大陆"))
    }

    @Test
    fun `symbols and pure digits yield empty`() {
        assertEquals("", PinyinUtil.initials("!!!123"))
        assertEquals("", PinyinUtil.initials(""))
    }
}
