package com.shiping.app.data.repository

import com.shiping.app.data.model.ApiResponse
import com.shiping.app.data.model.Category
import com.shiping.app.data.model.Vod
import com.shiping.app.data.remote.ApiService
import com.shiping.app.util.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope

/**
 * 视频数据仓库
 */
class VodRepository(
    private val apiService: ApiService,
    private val apiSourceRepository: ApiSourceRepository
) {

    /**
     * 获取首页视频列表（附带分类）
     */
    suspend fun getHome(page: Int = 1, typeId: Int = 0): ApiResponse<Vod> {
        val url = apiSourceRepository.currentApiUrlOnce()
        return apiService.getVodList(baseUrl = url, page = page, typeId = typeId)
    }

    /**
     * 搜索
     */
    suspend fun search(keyword: String, page: Int = 1, url: String? = null): ApiResponse<Vod> {
        val baseUrl = url ?: apiSourceRepository.currentApiUrlOnce()
        return apiService.getVodList(baseUrl = baseUrl, page = page, keyword = keyword)
    }

    /**
     * 获取视频详情
     */
    suspend fun getDetail(vodId: Int): Vod? {
        val url = apiSourceRepository.currentApiUrlOnce()
        val response = apiService.getVodDetail(baseUrl = url, ids = vodId.toString())
        return response.list.firstOrNull()
    }

    /**
     * 批量获取视频详情（用于补全列表中的封面图等字段）
     * @param ids 视频ID列表
     * @return vodId -> Vod 的映射
     */
    suspend fun getDetails(ids: List<Int>, url: String? = null): Map<Int, Vod> {
        if (ids.isEmpty()) return emptyMap()
        val baseUrl = url ?: apiSourceRepository.currentApiUrlOnce()
        // 分批请求，每批最多 20 个，避免 URL 过长
        return ids.chunked(20).flatMap { batch ->
            runCatching {
                val idsStr = batch.joinToString(",")
                apiService.getVodDetail(baseUrl = baseUrl, ids = idsStr).list
            }.getOrDefault(emptyList())
        }.associateBy { it.vodId }
    }

    /**
     * 获取分类列表（调用一次首页获取）
     */
    suspend fun getCategories(): List<Category> {
        return runCatching { getHome(page = 1, typeId = 0).classList }.getOrDefault(emptyList())
    }

    /**
     * 聚合搜索：在所有启用的 API 源上并发搜索
     * @return 按源分组的搜索结果
     */
    suspend fun searchAll(keyword: String, page: Int = 1): List<SearchGroup> = coroutineScope {
        val sources = apiSourceRepository.getEnabled().first()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        sources.map { source ->
            async {
                runCatching {
                    val response = search(keyword, page = page, url = source.url)
                    // 补全封面图
                    val detailMap = runCatching {
                        getDetails(response.list.map { it.vodId }, url = source.url)
                    }.getOrDefault(emptyMap())
                    val list = response.list.map { vod ->
                        detailMap[vod.vodId]?.let { detail ->
                            vod.copy(
                                vodPic = detail.vodPic,
                                vodPicThumb = detail.vodPicThumb,
                                vodPicSlide = detail.vodPicSlide,
                                vodScore = detail.vodScore,
                                vodRemarks = detail.vodRemarks.ifBlank { vod.vodRemarks }
                            )
                        } ?: vod
                    }
                    SearchGroup(
                        sourceId = source.id,
                        sourceName = source.name,
                        sourceUrl = source.url,
                        results = list,
                        page = page,
                        hasMore = page < response.pageCount
                    )
                }.getOrElse { e ->
                    SearchGroup(
                        sourceId = source.id,
                        sourceName = source.name,
                        sourceUrl = source.url,
                        error = e.message ?: "搜索失败"
                    )
                }
            }
        }.map { it.await() }
    }

    companion object {
        val DEFAULT_URL: String = Constants.DEFAULT_API_URL
    }
}

/**
 * 聚合搜索结果分组（按 API 源）
 */
data class SearchGroup(
    val sourceId: Long,
    val sourceName: String,
    val sourceUrl: String = "",
    val results: List<Vod> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true
)
