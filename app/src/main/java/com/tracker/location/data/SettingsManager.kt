package com.tracker.location.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 设置管理器
 * 
 * 管理应用配置，使用 SharedPreferences 持久化存储：
 * - 设备 ID（自动生成的唯一标识）
 * - 上报频率设置
 * - 位移阈值设置
 * - 服务器地址配置
 */
class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "tracker_settings"
        
        // 设置项键名
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_UPLOAD_INTERVAL = "upload_interval"
        private const val KEY_DISTANCE_THRESHOLD = "distance_threshold"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_UPLOAD_MODE = "upload_mode"
        
        // 默认值
        const val DEFAULT_UPLOAD_INTERVAL = 60_000L  // 1 分钟
        const val DEFAULT_DISTANCE_THRESHOLD = 50f   // 50 米
        const val DEFAULT_SERVER_URL = "http://192.168.1.100:8000"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 设备唯一标识
     * 
     * 首次访问时自动生成 UUID，之后保持不变。
     * 格式：tracker_xxxxxxxx（8 位随机字符）
     */
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                // 生成新的设备 ID
                id = "tracker_${UUID.randomUUID().toString().take(8)}"
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
    
    /**
     * 上报间隔（毫秒）
     * 
     * 定时上报模式下的上报周期。
     * 可选值：10秒、30秒、1分钟、5分钟
     */
    var uploadInterval: Long
        get() = prefs.getLong(KEY_UPLOAD_INTERVAL, DEFAULT_UPLOAD_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_UPLOAD_INTERVAL, value).apply()
    
    /**
     * 位移阈值（米）
     * 
     * 位移触发模式下，移动超过此距离才上报。
     */
    var distanceThreshold: Float
        get() = prefs.getFloat(KEY_DISTANCE_THRESHOLD, DEFAULT_DISTANCE_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_DISTANCE_THRESHOLD, value).apply()
    
    /**
     * 服务器 URL
     * 
     * 位置数据上报的目标服务器地址。
     * 格式：http(s)://host:port
     */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()
    
    /**
     * API 签名密钥
     * 
     * 用于生成接口签名，需要与服务端配置一致。
     */
    var apiSecret: String
        get() = prefs.getString(KEY_API_SECRET, "your-secret-key-change-in-production") ?: ""
        set(value) = prefs.edit().putString(KEY_API_SECRET, value).apply()
    
    /**
     * 上报模式
     * 
     * 0 = 定时上报
     * 1 = 位移触发上报
     */
    var uploadMode: Int
        get() = prefs.getInt(KEY_UPLOAD_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_UPLOAD_MODE, value).apply()
    
    /**
     * 上报间隔选项列表
     */
    val intervalOptions = listOf(
        10_000L to "10 秒",
        30_000L to "30 秒",
        60_000L to "1 分钟",
        300_000L to "5 分钟"
    )
    
    /**
     * 位移阈值选项列表
     */
    val distanceOptions = listOf(
        10f to "10 米",
        30f to "30 米",
        50f to "50 米",
        100f to "100 米"
    )
}
