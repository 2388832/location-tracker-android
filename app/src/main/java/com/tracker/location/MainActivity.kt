package com.tracker.location

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tracker.location.data.SettingsManager
import com.tracker.location.databinding.ActivityMainBinding
import com.tracker.location.network.LocationUploader
import com.tracker.location.service.LocationService

/**
 * 主界面
 * 
 * 极简 UI 设计，显示：
 * - 当前上报模式和状态
 * - 定位信息（经纬度、精度）
 * - 控制按钮（启动/停止、手动上报）
 * - 设置入口（上报频率、服务器地址等）
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var uploader: LocationUploader
    
    // 位置更新广播接收器
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationService.BROADCAST_LOCATION_UPDATE) {
                val lat = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0.0)
                val accuracy = intent.getFloatExtra(LocationService.EXTRA_ACCURACY, 0f)
                val status = intent.getStringExtra(LocationService.EXTRA_STATUS) ?: ""
                
                updateLocationDisplay(lat, lng, accuracy)
                updateStatusDisplay(status)
            }
        }
    }
    
    // 权限请求
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // 前台定位权限已授予
                checkBackgroundLocationPermission()
            }
            else -> {
                Toast.makeText(this, "需要位置权限才能使用定位功能", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // 后台定位权限请求（Android 10+）
    private val backgroundLocationRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startLocationService()
        } else {
            // 引导用户手动开启
            showBackgroundLocationGuide()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsManager = (application as TrackerApplication).settingsManager
        uploader = LocationUploader(this)
        
        setupUI()
        updateSettingsDisplay()
    }
    
    override fun onResume() {
        super.onResume()
        // 注册位置更新广播
        val filter = IntentFilter(LocationService.BROADCAST_LOCATION_UPDATE)
        ContextCompat.registerReceiver(this, locationReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        
        // 每次回到界面，如果服务应该在运行，就请求一次状态更新
        if (isServiceRunning) {
             startService(Intent(this, LocationService::class.java).apply {
                 action = LocationService.ACTION_MANUAL_UPLOAD // 这里临时借用触发，或者最好加个 ACTION_PING
             })
        }
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
    }
    
    /**
     * 设置 UI 事件
     */
    private fun setupUI() {
        // 启动/停止按钮
        binding.btnToggleService.setOnClickListener {
            if (hasLocationPermission()) {
                toggleService()
            } else {
                requestLocationPermissions()
            }
        }
        
        // 手动上报按钮
        binding.btnManualUpload.setOnClickListener {
            sendManualUploadCommand()
        }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        // 显示设备 ID
        binding.tvDeviceId.text = "设备ID: ${settingsManager.deviceId}"
    }
    
    /**
     * 检查是否有位置权限
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 请求位置权限
     */
    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    /**
     * 检查后台定位权限（Android 10+）
     */
    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 先显示说明对话框
                AlertDialog.Builder(this)
                    .setTitle("需要后台定位权限")
                    .setMessage("为了在应用退到后台时继续记录位置，请在接下来的权限请求中选择「始终允许」")
                    .setPositiveButton("继续") { _, _ ->
                        backgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }
        startLocationService()
    }
    
    /**
     * 显示后台定位权限引导
     */
    private fun showBackgroundLocationGuide() {
        AlertDialog.Builder(this)
            .setTitle("后台定位权限")
            .setMessage("后台定位权限被拒绝，应用将无法在后台记录位置。\n\n请前往设置 → 应用 → 定位跟踪 → 权限 → 位置，选择「始终允许」")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("以后再说", null)
            .show()
    }
    
    /**
     * 切换服务状态
     */
    private var isServiceRunning = false

    /**
     * 切换服务状态
     */
    private fun toggleService() {
        if (isServiceRunning) {
            stopLocationService()
        } else {
            checkBackgroundLocationPermission()
        }
    }
    
    /**
     * 启动定位服务
     */
    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        isServiceRunning = true
        binding.btnToggleService.text = "停止定位"
        Toast.makeText(this, "定位服务已启动", Toast.LENGTH_SHORT).show()
    }

    /**
     * 停止定位服务
     */
    private fun stopLocationService() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        startService(intent)
        
        isServiceRunning = false
        binding.btnToggleService.text = "启动定位"
        Toast.makeText(this, "定位服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 发送手动上报命令
     */
    private fun sendManualUploadCommand() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_MANUAL_UPLOAD
        }
        startService(intent)
        Toast.makeText(this, "正在上报...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 更新位置显示
     */
    private fun updateLocationDisplay(lat: Double, lng: Double, accuracy: Float) {
        if (lat != 0.0 || lng != 0.0) {
            binding.tvLatitude.text = "纬度: %.6f".format(lat)
            binding.tvLongitude.text = "经度: %.6f".format(lng)
            binding.tvAccuracy.text = "精度: %.1f 米".format(accuracy)
        }
    }
    
    /**
     * 更新状态显示
     */
    private fun updateStatusDisplay(status: String) {
        binding.tvStatus.text = "状态: $status"
    }
    
    /**
     * 更新设置显示
     */
    private fun updateSettingsDisplay() {
        val modeText = if (settingsManager.uploadMode == 0) {
            val interval = settingsManager.intervalOptions.find { 
                it.first == settingsManager.uploadInterval 
            }?.second ?: "1 分钟"
            "定时上报 ($interval)"
        } else {
            val distance = settingsManager.distanceOptions.find { 
                it.first == settingsManager.distanceThreshold 
            }?.second ?: "50 米"
            "位移触发 ($distance)"
        }
        binding.tvMode.text = "上报模式: $modeText"
    }
    
    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val modes = arrayOf("定时上报", "位移触发", "配置服务器")
        var selectedMode = settingsManager.uploadMode
        
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setSingleChoiceItems(modes, if (selectedMode > 1) -1 else selectedMode) { dialog, which ->
                when (which) {
                    0, 1 -> {
                        // 上报模式
                        settingsManager.uploadMode = which
                        updateSettingsDisplay()
                        dialog.dismiss()
                        if (which == 0) showIntervalDialog() else showDistanceDialog()
                    }
                    2 -> {
                        // 服务器配置
                        dialog.dismiss()
                        showServerDialog()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示服务器配置对话框
     */
    private fun showServerDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "http://192.168.1.x:8000"
            setText(settingsManager.serverUrl)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        
        val container = android.widget.FrameLayout(this).apply {
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        
        AlertDialog.Builder(this)
            .setTitle("服务器地址")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    settingsManager.serverUrl = url
                    Toast.makeText(this, "地址已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "地址格式无效", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示上报间隔设置对话框
     */
    private fun showIntervalDialog() {
        val options = settingsManager.intervalOptions.map { it.second }.toTypedArray()
        val currentIndex = settingsManager.intervalOptions.indexOfFirst { 
            it.first == settingsManager.uploadInterval 
        }.coerceAtLeast(0)
        
        AlertDialog.Builder(this)
            .setTitle("上报间隔")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                settingsManager.uploadInterval = settingsManager.intervalOptions[which].first
                updateSettingsDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示位移阈值设置对话框
     */
    private fun showDistanceDialog() {
        val options = settingsManager.distanceOptions.map { it.second }.toTypedArray()
        val currentIndex = settingsManager.distanceOptions.indexOfFirst { 
            it.first == settingsManager.distanceThreshold 
        }.coerceAtLeast(0)
        
        AlertDialog.Builder(this)
            .setTitle("位移阈值")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                settingsManager.distanceThreshold = settingsManager.distanceOptions[which].first
                updateSettingsDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
