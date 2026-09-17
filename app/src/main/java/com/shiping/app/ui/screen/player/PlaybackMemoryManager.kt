package com.shiping.app.ui.screen.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackMemoryStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_memory",
)

/** 单条播放记录 */
data class PlaybackRecord(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 播放记忆管理器：按视频 URL 记录播放进度，支持续播。
 * 使用 DataStore + JSON Map 存储，避免每条记录单独建 key。
 */
class PlaybackMemoryManager(context: Context) {

    private val dataStore = context.playbackMemoryStore
    private val gson = Gson()

    private val recordsKey = stringPreferencesKey("playback_records")

    private val records: Flow<Map<String, PlaybackRecord>> = dataStore.data.map { prefs ->
        val json = prefs[recordsKey] ?: return@map emptyMap()
        runCatching {
            val type = object : TypeToken<Map<String, PlaybackRecord>>() {}.type
            gson.fromJson<Map<String, PlaybackRecord>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    /** 获取某视频的播放记录 */
    suspend fun getRecord(url: String): PlaybackRecord? = records.first()[url]

    /** 保存播放进度，仅当进度大于阈值且未接近结尾时记录 */
    suspend fun savePosition(url: String, positionMs: Long, durationMs: Long) {
        if (positionMs < Constants.PLAYBACK_MEMORY_MIN_MS) return
        if (durationMs > 0 && durationMs - positionMs < Constants.RESUME_THRESHOLD_MS) return
        dataStore.edit { prefs ->
            val current = runCatching {
                val json = prefs[recordsKey] ?: return@runCatching emptyMap()
                val type = object : TypeToken<Map<String, PlaybackRecord>>() {}.type
                gson.fromJson<Map<String, PlaybackRecord>>(json, type) ?: emptyMap()
            }.getOrDefault(emptyMap())
            val updated = current.toMutableMap().apply {
                put(url, PlaybackRecord(positionMs, durationMs))
            }
            prefs[recordsKey] = gson.toJson(updated)
        }
    }

    /** 清除某视频的播放记录 */
    suspend fun clearRecord(url: String) {
        dataStore.edit { prefs ->
            val current = runCatching {
                val json = prefs[recordsKey] ?: return@runCatching emptyMap()
                val type = object : TypeToken<Map<String, PlaybackRecord>>() {}.type
                gson.fromJson<Map<String, PlaybackRecord>>(json, type) ?: emptyMap()
            }.getOrDefault(emptyMap())
            if (current.containsKey(url)) {
                val updated = current.toMutableMap().apply { remove(url) }
                prefs[recordsKey] = gson.toJson(updated)
            }
        }
    }

    /** 清除全部播放记录 */
    suspend fun clearAll() {
        dataStore.edit { it.remove(recordsKey) }
    }
}
