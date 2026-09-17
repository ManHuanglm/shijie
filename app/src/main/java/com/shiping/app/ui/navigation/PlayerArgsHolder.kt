package com.shiping.app.ui.navigation

/**
 * 待播放参数持有者。
 * 用于下载完成通知跳转时在 MainActivity -> AppNavigation 之间传递 URL/标题。
 * 剧集列表等播放数据由全局共享的 PlayerViewModel 持有，无需经此传递。
 * 使用后立即清空，避免内存泄漏。
 */
object PlayerArgsHolder {

    /** 待播放的视频（来自下载完成通知跳转） */
    private var pendingPlayUrl: String? = null
    private var pendingPlayTitle: String? = null

    /** 设置待播放视频（通知跳转用） */
    fun setPendingPlay(url: String, title: String) {
        pendingPlayUrl = url
        pendingPlayTitle = title
    }

    /** 消费待播放视频，返回 null 表示无待播放项 */
    fun consumePendingPlay(): Pair<String, String>? {
        val url = pendingPlayUrl ?: return null
        val title = pendingPlayTitle ?: "视频"
        pendingPlayUrl = null
        pendingPlayTitle = null
        return url to title
    }
}
