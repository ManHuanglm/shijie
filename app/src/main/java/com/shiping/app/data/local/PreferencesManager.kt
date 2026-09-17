package com.shiping.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shiping_prefs")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val CURRENT_API_ID = longPreferencesKey("current_api_id")
        val CURRENT_PARSER_ID = longPreferencesKey("current_parser_id")

        // 主题模式: 0=暗色, 1=亮色
        val THEME_MODE = intPreferencesKey("theme_mode")

        // 背景样式: 0=默认, 1-5=渐变预设, 6=自定义图片
        val BACKGROUND_TYPE = intPreferencesKey("background_type")
        val CUSTOM_BG_URI = stringPreferencesKey("custom_bg_uri")

        // 缓冲倍数 1-10
        val BUFFER_MULTIPLIER = intPreferencesKey("buffer_multiplier")

        /** 缓冲线路倍数（同时连接数） */
        val BUFFER_LINE_MULTIPLIER = intPreferencesKey("buffer_line_multiplier")

        // 长按倍速（用于播放器长按快进，存储为 100 倍整数，如 300 = 3.0x）
        val LONG_PRESS_SPEED = intPreferencesKey("long_press_speed")

        /** m3u8 去广告开关 */
        val AD_FILTER_ENABLED = booleanPreferencesKey("ad_filter_enabled")

        /** 画中画开关（按 Home 键自动进入 PiP） */
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")

        // 预载设置
        val PRELOAD_ENABLED = booleanPreferencesKey("preload_enabled")
        val PRELOAD_NEXT_EPISODE = booleanPreferencesKey("preload_next_episode")
        val PRELOAD_THREADS = intPreferencesKey("preload_threads")
        val PRELOAD_CAPACITY_MB = intPreferencesKey("preload_capacity_mb")
        val PRELOAD_TIME_S = intPreferencesKey("preload_time_s")

        // 搜索历史（逗号分隔，最多 SEARCH_HISTORY_MAX 条）
        val SEARCH_HISTORY = stringPreferencesKey("search_history")

        // 下载设置
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val DOWNLOAD_SPEED_LIMIT_KBPS = intPreferencesKey("download_speed_limit_kbps")
        val DOWNLOAD_MAX_PARALLEL = intPreferencesKey("download_max_parallel")

        // 电视直播频道收藏（key = "频道名|线路地址" 集合）
        val FAVORITE_TV_CHANNELS = stringSetPreferencesKey("favorite_tv_channels")

        // 当前选中的电视直播源 ID
        val CURRENT_TV_SOURCE_ID = longPreferencesKey("current_tv_source_id")
    }

    companion object {
        private const val SEARCH_HISTORY_MAX = 10
        private const val SEARCH_HISTORY_SEP = ","
    }

    // region API 源
    val currentApiId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_API_ID]
    }

    suspend fun setCurrentApiId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_API_ID] = id
        }
    }
    // endregion

    // region 电视直播源
    /** 当前选中的电视直播源 ID（null=未选择） */
    val currentTvSourceId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_TV_SOURCE_ID]
    }

    suspend fun setCurrentTvSourceId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_TV_SOURCE_ID] = id
        }
    }
    // endregion

    // region 片头片尾跳过（按影片独立记忆，key 前缀 + vodId）
    /** 某影片的片头跳过秒数（0=未设置） */
    fun introSkipSeconds(vodId: Int): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[intPreferencesKey("intro_skip_$vodId")] ?: 0
    }

    suspend fun setIntroSkipSeconds(vodId: Int, seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[intPreferencesKey("intro_skip_$vodId")] = seconds.coerceIn(0, 300)
        }
    }

    /** 某影片的片尾跳过提前秒数（0=未设置） */
    fun outroSkipSeconds(vodId: Int): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[intPreferencesKey("outro_skip_$vodId")] ?: 0
    }

    suspend fun setOutroSkipSeconds(vodId: Int, seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[intPreferencesKey("outro_skip_$vodId")] = seconds.coerceIn(0, 300)
        }
    }
    // endregion

    // region 解析源
    val currentParserId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_PARSER_ID]
    }

    suspend fun setCurrentParserId(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id != null) {
                prefs[Keys.CURRENT_PARSER_ID] = id
            } else {
                prefs.remove(Keys.CURRENT_PARSER_ID)
            }
        }
    }
    // endregion

    // region 主题
    val themeMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: 0
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    val backgroundType: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_TYPE] ?: 0
    }

    suspend fun setBackgroundType(type: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_TYPE] = type
        }
    }

    val customBgUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_BG_URI] ?: ""
    }

    suspend fun setCustomBgUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BG_URI] = uri
        }
    }
    // endregion

    // region 缓冲
    val bufferMultiplier: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.BUFFER_MULTIPLIER] ?: 1
    }

    suspend fun setBufferMultiplier(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BUFFER_MULTIPLIER] = value.coerceIn(1, 10)
        }
    }

    /** 缓冲线路倍数（同时连接数，1=单连接不加速） */
    val bufferLineMultiplier: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.BUFFER_LINE_MULTIPLIER] ?: Constants.BUFFER_LINE_MULTIPLIER_DEFAULT
    }

    suspend fun setBufferLineMultiplier(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BUFFER_LINE_MULTIPLIER] = value.coerceIn(
                1,
                Constants.BUFFER_LINE_MULTIPLIER_MAX,
            )
        }
    }
    // endregion

    // region 长按倍速
    /** 长按倍速（Float，默认 3.0x） */
    val longPressSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[Keys.LONG_PRESS_SPEED]?.let { it / 100f }) ?: Constants.LONG_PRESS_SPEED
    }

    suspend fun setLongPressSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LONG_PRESS_SPEED] = (speed.coerceIn(
                Constants.LONG_PRESS_SPEED_OPTIONS.first(),
                Constants.LONG_PRESS_SPEED_OPTIONS.last(),
            ) * 100).toInt()
        }
    }
    // endregion

    // region 广告过滤
    /** m3u8 去广告开关（默认开启） */
    val adFilterEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AD_FILTER_ENABLED] ?: true
    }

    suspend fun setAdFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AD_FILTER_ENABLED] = enabled
        }
    }
    // endregion

    // region 画中画
    /** 画中画开关（默认开启） */
    val pipEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PIP_ENABLED] ?: true
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PIP_ENABLED] = enabled
        }
    }
    // endregion

    // region 预载
    val preloadEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_ENABLED] ?: false
    }

    suspend fun setPreloadEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRELOAD_ENABLED] = enabled
        }
    }

    val preloadNextEpisode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_NEXT_EPISODE] ?: false
    }

    suspend fun setPreloadNextEpisode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRELOAD_NEXT_EPISODE] = enabled
        }
    }

    val preloadThreads: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_THREADS] ?: 3
    }

    suspend fun setPreloadThreads(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRELOAD_THREADS] = value.coerceIn(1, 10)
        }
    }

    val preloadCapacityMb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_CAPACITY_MB] ?: 512
    }

    suspend fun setPreloadCapacityMb(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRELOAD_CAPACITY_MB] = value.coerceIn(128, 4096)
        }
    }

    val preloadTimeS: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_TIME_S] ?: 30
    }

    suspend fun setPreloadTimeS(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRELOAD_TIME_S] = value.coerceIn(12, 120)
        }
    }
    // endregion

    // region 搜索历史
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY]
            ?.split(SEARCH_HISTORY_SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    /** 收藏的电视频道 key 集合（"频道名|线路地址"） */
    val favoriteTvChannels: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_TV_CHANNELS] ?: emptySet()
    }

    /** 切换频道收藏状态 */
    suspend fun toggleFavoriteTvChannel(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_TV_CHANNELS] ?: emptySet()
            prefs[Keys.FAVORITE_TV_CHANNELS] =
                if (key in current) current - key else current + key
        }
    }

    /**
     * 添加搜索历史：去重并移到最前，超过上限丢弃末尾
     */
    suspend fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY]
                ?.split(SEARCH_HISTORY_SEP)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(SEARCH_HISTORY_MAX)
            prefs[Keys.SEARCH_HISTORY] = updated.joinToString(SEARCH_HISTORY_SEP)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SEARCH_HISTORY)
        }
    }
    // endregion

    // region 下载设置

    /** 仅 WiFi 下载 */
    val downloadWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_WIFI_ONLY] ?: false
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_WIFI_ONLY] = enabled
        }
    }

    /** 下载限速（KB/s），0 表示不限速 */
    val downloadSpeedLimitKbps: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_SPEED_LIMIT_KBPS] ?: 0
    }

    suspend fun setDownloadSpeedLimitKbps(kbps: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0)
        }
    }

    /** 批量下载最大并发数 */
    val downloadMaxParallel: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_MAX_PARALLEL] ?: 2
    }

    suspend fun setDownloadMaxParallel(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_MAX_PARALLEL] = value.coerceIn(1, 5)
        }
    }
    // endregion
}
