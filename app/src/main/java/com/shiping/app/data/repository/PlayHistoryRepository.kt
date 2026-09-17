package com.shiping.app.data.repository

import com.shiping.app.data.local.PlayHistoryDao
import com.shiping.app.data.model.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放记录仓库
 */
class PlayHistoryRepository(private val dao: PlayHistoryDao) {

    /** 观察全部播放记录（按观看时间倒序） */
    val historyFlow: Flow<List<PlayHistoryEntity>> = dao.observeAll()

    /** 保存/更新播放记录 */
    suspend fun save(item: PlayHistoryEntity) {
        dao.upsert(item)
    }

    /** 删除单条 */
    suspend fun delete(vodId: Int, sourceUrl: String) {
        dao.delete(vodId, sourceUrl)
    }

    /** 清空全部 */
    suspend fun clearAll() {
        dao.clearAll()
    }
}
