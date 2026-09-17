package com.shiping.app.data.repository

import com.shiping.app.data.model.ApiResponse
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.Category
import com.shiping.app.data.model.Vod
import com.shiping.app.data.remote.ApiService
import com.shiping.app.util.Constants
import kotlinx.coroutines.flow.first

/**
 * 视频数据仓库
 */
class VodRepository(
    private val apiService: ApiService,
    private val apiSourceRepository: ApiSourceRepository,
) {

    /** 获取首页视频列表（附带分类） */
    suspend fun getHome(page: Int = 1, typeId: Int = 0, url: String? = null): ApiResponse<Vod> {
        val baseUrl = url?.takeIf { it.isNotBlank() } ?: apiSourceRepository.currentApiUrlOnce()
        return apiService.getVodList(baseUrl = baseUrl, page = page, typeId = typeId)
    }

    /** 搜索 */
    suspend fun search(keyword: String, page: Int = 1, url: String? = null): ApiResponse<Vod> {
        val baseUrl = url ?: apiSourceRepository.currentApiUrlOnce()
        return apiService.getVodList(baseUrl = baseUrl, page = page, keyword = keyword)
    }

    /** 获取视频详情 */
    suspend fun getDetail(vodId: Int, url: String? = null): Vod? {
        val baseUrl = url?.takeIf { it.isNotBlank() } ?: apiSourceRepository.currentApiUrlOnce()
        val response = apiService.getVodDetail(baseUrl = baseUrl, ids = vodId.toString())
        return response.list.firstOrNull()
    }

    /**
     * 批量获取视频详情（用于补全列表中的封面图等字段）
     * @return vodId -> Vod 的映射
     */
    suspend fun getDetails(ids: List<Int>, url: String? = null): Map<Int, Vod> {
        if (ids.isEmpty()) return emptyMap()
        val baseUrl = url ?: apiSourceRepository.currentApiUrlOnce()
        return ids.chunked(Constants.DETAIL_BATCH_SIZE).flatMap { batch ->
            runCatching {
                val idsStr = batch.joinToString(",")
                apiService.getVodDetail(baseUrl = baseUrl, ids = idsStr).list
            }.getOrDefault(emptyList())
        }.associateBy { it.vodId }
    }

    /** 获取分类列表 */
    suspend fun getCategories(): List<Category> {
        return runCatching { getHome(page = 1, typeId = 0).classList }.getOrDefault(emptyList())
    }

    /** 获取所有启用的 API 源 */
    suspend fun getEnabledSources(): List<ApiSourceEntity> = apiSourceRepository.getEnabled().first()

    /**
     * 在单个 API 源上搜索（含详情补全）
     * 失败时返回带 error 的分组，不抛异常
     */
    suspend fun searchOneSource(
        source: ApiSourceEntity,
        keyword: String,
        page: Int = 1,
    ): SearchGroup = runCatching {
        val response = search(keyword, page = page, url = source.url)
        val detailMap = runCatching {
            getDetails(response.list.map { it.vodId }, url = source.url)
        }.getOrDefault(emptyMap())
        val list = response.list.map { vod ->
            detailMap[vod.vodId]?.let { vod.mergeWithDetail(it) } ?: vod
        }
        SearchGroup(
            sourceId = source.id,
            sourceName = source.name,
            sourceUrl = source.url,
            results = list,
            page = page,
            hasMore = page < response.pageCount,
        )
    }.getOrElse { e ->
        SearchGroup(
            sourceId = source.id,
            sourceName = source.name,
            sourceUrl = source.url,
            error = e.message ?: "搜索失败",
        )
    }

    companion object {
        val DEFAULT_URL: String = Constants.DEFAULT_API_URL
    }
}

/** 聚合搜索结果分组（按 API 源） */
data class SearchGroup(
    val sourceId: Long,
    val sourceName: String,
    val sourceUrl: String = "",
    val results: List<Vod> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
)
