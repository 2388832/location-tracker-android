package com.tracker.location.data

/**
 * 位置记录数据类
 * 
 * 表示一条待上报或已缓存的位置数据。
 * 用于网络传输和本地存储。
 */
data class LocationRecord(
    /** 设备唯一标识 */
    val deviceId: String,
    
    /** 经度 */
    val longitude: Double,
    
    /** 纬度 */
    val latitude: Double,
    
    /** 定位时间（ISO 8601 格式） */
    val locationTime: String,
    
    /** 定位精度（米） */
    val accuracy: Float?,
    
    /** 网络状态（wifi / 4g / 5g / offline） */
    val networkStatus: String?,
    
    /** 本地 ID（用于数据库存储，上报时不传） */
    val localId: Long = 0
)

/**
 * 位置上报请求体
 * 
 * 发送到服务器的 JSON 格式数据。
 */
data class LocationUploadRequest(
    val device_id: String,
    val longitude: Double,
    val latitude: Double,
    val location_time: String,
    val accuracy: Float?,
    val network_status: String?,
    val timestamp: Long,
    val signature: String
)

/**
 * 批量上报请求体
 */
data class BatchUploadRequest(
    val device_id: String,
    val locations: List<LocationItem>,
    val timestamp: Long,
    val signature: String
)

/**
 * 批量上报中的单条位置
 */
data class LocationItem(
    val longitude: Double,
    val latitude: Double,
    val location_time: String,
    val accuracy: Float?,
    val network_status: String?
)

/**
 * 上报响应
 */
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val record_id: Long?
)
