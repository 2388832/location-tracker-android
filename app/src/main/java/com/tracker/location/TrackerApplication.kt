package com.tracker.location

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.tracker.location.data.SettingsManager

/**
 * 应用程序类
 * 
 * 在应用启动时执行初始化操作：
 * - 创建通知渠道（Android 8.0+ 必需）
 * - 初始化设置管理器
 */
class TrackerApplication : Application() {
    
    companion object {
        /** 定位服务通知渠道 ID */
        const val CHANNEL_LOCATION = "location_service"
    }
    
    /** 设置管理器单例 */
    lateinit var settingsManager: SettingsManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化设置管理器
        settingsManager = SettingsManager(this)
        
        // 创建通知渠道
        createNotificationChannels()
    }
    
    /**
     * 创建通知渠道
     * 
     * Android 8.0（API 26）及以上版本必须创建通知渠道才能显示通知。
     * 前台服务需要显示持续通知，所以必须提前创建渠道。
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_LOCATION,
                "定位服务",
                NotificationManager.IMPORTANCE_LOW  // 低重要性，不发出声音
            ).apply {
                description = "后台定位服务运行时显示的通知"
                // 关闭振动和铃声
                enableVibration(false)
                setSound(null, null)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
