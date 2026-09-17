package com.shiping.app.data.repository

import com.shiping.app.data.local.FavoriteDao
import com.shiping.app.data.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏仓库
 */
class FavoriteRepository(private val dao: FavoriteDao) {

    /** 观察全部收藏（按收藏时间倒序） */
    val favoritesFlow: Flow<List<FavoriteEntity>> = dao.observeAll()

    /** 观察某影片是否已收藏 */
    fun observeIsFavorite(vodId: Int, sourceUrl: String): Flow<Boolean> =
        dao.observeIsFavorite(vodId, sourceUrl)

    /** 添加收藏 */
    suspend fun add(item: FavoriteEntity) {
        dao.upsert(item)
    }

    /** 取消收藏 */
    suspend fun delete(vodId: Int, sourceUrl: String) {
        dao.delete(vodId, sourceUrl)
    }

    /** 切换收藏状态：已收藏则取消，未收藏则添加 */
    suspend fun toggle(item: FavoriteEntity) {
        if (dao.exists(item.vodId, item.sourceUrl)) {
            dao.delete(item.vodId, item.sourceUrl)
        } else {
            dao.upsert(item)
        }
    }
}
