package com.shiping.app.util

import net.sourceforge.pinyin4j.PinyinHelper

/** 拼音工具：生成片名的拼音首字母串，用于搜索页字母联想 */
object PinyinUtil {

    /**
     * 取名称的拼音首字母序列，如 "斗罗大陆" -> "dldl"、"战狼2" -> "zl"。
     * 英文字母原样保留（转小写），数字与符号跳过。
     */
    fun initials(name: String): String = buildString {
        for (char in name) {
            when {
                char.code in 0x4E00..0x9FFF -> {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char)
                    if (!pinyinArray.isNullOrEmpty()) {
                        append(pinyinArray[0][0].lowercaseChar())
                    }
                }
                char in 'a'..'z' -> append(char)
                char in 'A'..'Z' -> append(char.lowercaseChar())
                else -> Unit
            }
        }
    }
}
