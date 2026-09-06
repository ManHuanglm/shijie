package com.shiping.app.util

object Constants {
    /** 默认测试 API 地址 */
    const val DEFAULT_API_URL = "http://hongniuzy2.com/api.php/provide/vod"
    const val DEFAULT_API_NAME = "红牛资源"

    /** 默认解析地址 */
    const val DEFAULT_PARSER_URL = "https://yparse.ik9.cc/index.php?url="
    const val DEFAULT_PARSER_NAME = "yparse解析"

    /** 播放源/剧集分隔符 */
    const val SEPARATOR_SOURCE = "\$\$\$"
    const val SEPARATOR_EPISODE = "#"
    const val SEPARATOR_NAME_URL = "$"

    /** 预载参数范围 */
    const val PRELOAD_THREADS_MIN = 1
    const val PRELOAD_THREADS_MAX = 10
    const val PRELOAD_CAPACITY_MIN_MB = 128
    const val PRELOAD_CAPACITY_MAX_MB = 4096
    const val PRELOAD_TIME_MIN_S = 12
    const val PRELOAD_TIME_MAX_S = 120

    /** 缓冲倍数范围 */
    const val BUFFER_MULTIPLIER_MIN = 1
    const val BUFFER_MULTIPLIER_MAX = 10

    /** 直链后缀 */
    val DIRECT_STREAM_SUFFIXES = listOf(".m3u8", ".mp4", ".flv", ".ts")

    /** 推荐搜索词 */
    val RECOMMEND_SEARCHES = listOf(
        "凡人修仙传", "斗罗大陆", "斗破苍穹", "完美世界",
        "海贼王", "火影忍者", "庆余年", "狂飙",
        "三体", "流浪地球", "长津湖", "满江红"
    )
}
