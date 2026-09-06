package com.shiping.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自定义的视频 API 源
 */
@Entity(tableName = "api_sources")
data class ApiSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 名称 */
    val name: String,
    /** 接口地址 */
    val url: String,
    /** 备注/说明 */
    val note: String = "",
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 排序 */
    val sortOrder: Int = 0,
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
