package com.shiping.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

        // 预载设置
        val PRELOAD_ENABLED = booleanPreferencesKey("preload_enabled")
        val PRELOAD_NEXT_EPISODE = booleanPreferencesKey("preload_next_episode")
        val PRELOAD_THREADS = intPreferencesKey("preload_threads")
        val PRELOAD_CAPACITY_MB = intPreferencesKey("preload_capacity_mb")
        val PRELOAD_TIME_S = intPreferencesKey("preload_time_s")

        // 搜索历史（逗号分隔，最多 SEARCH_HISTORY_MAX 条）
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
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
}
