package com.tracker.location.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 离线缓存管理器
 * 
 * 当网络不可用时，将位置数据缓存到本地。
 * 网络恢复后自动批量上传缓存数据。
 * 
 * 使用 SharedPreferences 存储（简化实现，正式项目建议使用 Room 数据库）
 * 最多缓存 100 条记录，超出时删除最旧的记录。
 */
class OfflineCache(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "offline_cache"
        private const val KEY_LOCATIONS = "cached_locations"
        private const val MAX_CACHE_SIZE = 100
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 添加位置记录到缓存
     * 
     * @param record 位置记录
     */
    @Synchronized
    fun addRecord(record: LocationRecord) {
        val records = getRecords().toMutableList()
        
        // 如果超过最大缓存数，删除最旧的记录
        while (records.size >= MAX_CACHE_SIZE) {
            records.removeAt(0)
        }
        
        records.add(record)
        saveRecords(records)
    }
    
    /**
     * 获取所有缓存的记录
     * 
     * @return 缓存记录列表
     */
    @Synchronized
    fun getRecords(): List<LocationRecord> {
        val json = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocationRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取缓存记录数量
     */
    fun getCount(): Int = getRecords().size
    
    /**
     * 清空所有缓存
     */
    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_LOCATIONS).apply()
    }
    
    /**
     * 移除指定数量的记录（从头部移除，即最旧的记录）
     * 
     * @param count 要移除的数量
     */
    @Synchronized
    fun removeFirst(count: Int) {
        val records = getRecords().toMutableList()
        repeat(minOf(count, records.size)) {
            records.removeAt(0)
        }
        saveRecords(records)
    }
    
    /**
     * 保存记录到 SharedPreferences
     */
    private fun saveRecords(records: List<LocationRecord>) {
        val json = gson.toJson(records)
        prefs.edit().putString(KEY_LOCATIONS, json).apply()
    }
}
