package com.tracker.location.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.tracker.location.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 位置上报器
 * 
 * 负责将位置数据发送到服务器，包括：
 * - 单条上报
 * - 批量上报（离线缓存补发）
 * - 网络状态检测
 * - 签名生成
 */
class LocationUploader(private val context: Context) {
    
    companion object {
        private const val API_PATH = "/api/v1/location"
        private const val BATCH_API_PATH = "/api/v1/location/batch"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val settingsManager = SettingsManager(context)
    private val offlineCache = OfflineCache(context)
    
    /**
     * 上报单条位置数据
     * 
     * @param record 位置记录
     * @return 上报是否成功
     */
    suspend fun upload(record: LocationRecord): Boolean = withContext(Dispatchers.IO) {
        // 检查网络状态
        if (!isNetworkAvailable()) {
            // 网络不可用，存入离线缓存
            offlineCache.addRecord(record)
            return@withContext false
        }
        
        try {
            val timestamp = System.currentTimeMillis() / 1000
            val signature = generateSignature(record.deviceId, timestamp)
            
            val request = LocationUploadRequest(
                device_id = record.deviceId,
                longitude = record.longitude,
                latitude = record.latitude,
                location_time = record.locationTime,
                accuracy = record.accuracy,
                network_status = record.networkStatus,
                timestamp = timestamp,
                signature = signature
            )
            
            val json = gson.toJson(request)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            
            val httpRequest = Request.Builder()
                .url("${settingsManager.serverUrl}$API_PATH")
                .post(body)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                // 上报成功后，尝试补发离线缓存
                uploadCachedRecords()
                return@withContext true
            } else {
                // 上报失败，存入离线缓存
                offlineCache.addRecord(record)
                return@withContext false
            }
        } catch (e: IOException) {
            // 网络异常，存入离线缓存
            offlineCache.addRecord(record)
            return@withContext false
        }
    }
    
    /**
     * 批量上报离线缓存的数据
     * 
     * @return 成功上报的数量
     */
    suspend fun uploadCachedRecords(): Int = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext 0
        }
        
        val records = offlineCache.getRecords()
        if (records.isEmpty()) {
            return@withContext 0
        }
        
        try {
            val timestamp = System.currentTimeMillis() / 1000
            val deviceId = settingsManager.deviceId
            val signature = generateSignature(deviceId, timestamp)
            
            val locations = records.map { record ->
                LocationItem(
                    longitude = record.longitude,
                    latitude = record.latitude,
                    location_time = record.locationTime,
                    accuracy = record.accuracy,
                    network_status = record.networkStatus
                )
            }
            
            val request = BatchUploadRequest(
                device_id = deviceId,
                locations = locations,
                timestamp = timestamp,
                signature = signature
            )
            
            val json = gson.toJson(request)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            
            val httpRequest = Request.Builder()
                .url("${settingsManager.serverUrl}$BATCH_API_PATH")
                .post(body)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                // 上报成功，清空缓存
                offlineCache.clear()
                return@withContext records.size
            }
        } catch (e: IOException) {
            // 忽略异常，保留缓存
        }
        
        return@withContext 0
    }
    
    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * 获取当前网络类型
     * 
     * @return wifi / 4g / 5g / unknown
     */
    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "offline"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "offline"
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // 简化处理，实际可以通过 TelephonyManager 获取更详细的网络类型
                "4g"
            }
            else -> "unknown"
        }
    }
    
    /**
     * 生成 HMAC-SHA256 签名
     * 
     * @param deviceId 设备 ID
     * @param timestamp Unix 时间戳（秒）
     * @return 64 位十六进制签名字符串
     */
    private fun generateSignature(deviceId: String, timestamp: Long): String {
        val message = "$deviceId$timestamp"
        val secretKey = settingsManager.apiSecret
        
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 获取离线缓存数量
     */
    fun getCachedCount(): Int = offlineCache.getCount()
}
