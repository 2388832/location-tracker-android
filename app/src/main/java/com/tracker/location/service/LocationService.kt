package com.tracker.location.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.*
import com.tracker.location.MainActivity
import com.tracker.location.R
import com.tracker.location.TrackerApplication
import com.tracker.location.data.LocationRecord
import com.tracker.location.data.SettingsManager
import com.tracker.location.network.LocationUploader
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 定位前台服务
 * 
 * 作为前台服务运行，确保后台定位不被系统杀死。
 * 支持两种上报模式：
 * 1. 定时上报：按设定的时间间隔上报
 * 2. 位移触发：移动超过设定距离时上报
 */
class LocationService : LifecycleService() {
    
    companion object {
        private const val NOTIFICATION_ID = 1
        
        // Intent Action
        const val ACTION_START = "com.tracker.location.START"
        const val ACTION_STOP = "com.tracker.location.STOP"
        const val ACTION_MANUAL_UPLOAD = "com.tracker.location.MANUAL_UPLOAD"
        
        // 广播 Action
        const val BROADCAST_LOCATION_UPDATE = "com.tracker.location.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_STATUS = "status"
    }
    
    private lateinit var locationManager: android.location.LocationManager
    private lateinit var locationListener: android.location.LocationListener
    private lateinit var settingsManager: SettingsManager
    private lateinit var uploader: LocationUploader
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    
    // 上次上报的位置（用于计算位移）
    private var lastReportedLocation: Location? = null
    // 上次上报时间
    private var lastReportTime = 0L
    // 当前定位状态
    private var currentStatus = "未启动"
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化
        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        settingsManager = (application as TrackerApplication).settingsManager
        uploader = LocationUploader(this)
        
        // 创建位置回调
        locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                handleNewLocation(location)
            }
            
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
    }

    /**
     * 启动位置更新
     */
    private fun startLocationUpdates() {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            currentStatus = "缺少定位权限"
            broadcastStatus()
            return
        }
        
        // 启动前台服务
        startForegroundService()
        
        try {
            var providerEnabled = false
            
            // 请求 GPS 定位
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    2000L, // 最小时间间隔 2秒
                    0f,    // 最小距离间隔 0米
                    locationListener
                )
                providerEnabled = true
            }
            
            // 请求网络定位
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER,
                    2000L,
                    0f,
                    locationListener
                )
                providerEnabled = true
            }
            
            if (!providerEnabled) {
                currentStatus = "错误: GPS和网络定位均未开启"
                broadcastStatus()
                return
            }
            
            currentStatus = "定位中... (等待数据)"
            broadcastStatus()
            
        } catch (e: Exception) {
            currentStatus = "启动失败: ${e.message}"
            broadcastStatus()
        }
    }

    /**
     * 停止位置更新
     */
    private fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        currentStatus = "已停止"
        broadcastStatus()
    }

    /**
     * 手动触发上报
     */
    private fun manualUpload() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        // 获取最后的已知位置
        val lastGps = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        
        // 选一个更新的或更精确的
        var location = lastGps
        if (lastNet != null) {
            if (location == null || lastNet.time > location.time) {
                location = lastNet
            }
        }
        
        location?.let {
            serviceScope.launch {
                uploadLocation(it, true)
            }
        } ?: run {
            Toast.makeText(this, "暂无位置信息", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理新的位置更新
     */
    private fun handleNewLocation(location: Location) {
        val now = System.currentTimeMillis()
        val shouldUpload: Boolean
        
        when (settingsManager.uploadMode) {
            0 -> {
                // 定时上报模式
                shouldUpload = (now - lastReportTime) >= settingsManager.uploadInterval
            }
            1 -> {
                // 位移触发模式
                val lastLocation = lastReportedLocation
                shouldUpload = if (lastLocation == null) {
                    true  // 首次定位
                } else {
                    location.distanceTo(lastLocation) >= settingsManager.distanceThreshold
                }
            }
            else -> shouldUpload = false
        }
        
        if (shouldUpload) {
            serviceScope.launch {
                uploadLocation(location, false)
            }
        }
        
        // 广播当前位置（用于 UI 更新）
        broadcastLocation(location)
    }
    
    /**
     * 上报位置
     */
    private suspend fun uploadLocation(location: Location, isManual: Boolean) {
        val record = LocationRecord(
            deviceId = settingsManager.deviceId,
            longitude = location.longitude,
            latitude = location.latitude,
            locationTime = dateFormat.format(Date()),
            accuracy = location.accuracy,
            networkStatus = uploader.getNetworkType()
        )
        
        val success = uploader.upload(record)
        
        if (success || !uploader.isNetworkAvailable()) {
            // 上报成功或离线模式，更新记录
            lastReportedLocation = location
            lastReportTime = System.currentTimeMillis()
        }
        
        currentStatus = if (success) {
            "上报成功"
        } else if (!uploader.isNetworkAvailable()) {
            "离线缓存 (${uploader.getCachedCount()}条)"
        } else {
            "上报失败"
        }
        
        broadcastStatus()
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, TrackerApplication.CHANNEL_LOCATION)
            .setContentTitle("定位跟踪运行中")
            .setContentText("正在后台记录位置信息")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 广播位置更新
     */
    private fun broadcastLocation(location: Location) {
        val intent = Intent(BROADCAST_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ACCURACY, location.accuracy)
            putExtra(EXTRA_STATUS, currentStatus)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 广播状态更新
     */
    private fun broadcastStatus() {
        val intent = Intent(BROADCAST_LOCATION_UPDATE).apply {
            putExtra(EXTRA_STATUS, currentStatus)
        }
        sendBroadcast(intent)
    }
}
