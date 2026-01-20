package com.tracker.location.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tracker.location.service.LocationService

/**
 * 开机自启动接收器
 * 
 * 设备重启后自动恢复定位服务。
 * 需要在 AndroidManifest.xml 中声明 RECEIVE_BOOT_COMPLETED 权限。
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机后启动定位服务
            val serviceIntent = Intent(context, LocationService::class.java).apply {
                action = LocationService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
