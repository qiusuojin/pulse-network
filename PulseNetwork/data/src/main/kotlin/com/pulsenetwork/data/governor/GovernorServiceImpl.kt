package com.pulsenetwork.data.governor

import android.content.*
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.WindowManager
import com.pulsenetwork.domain.governor.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地总督服务实现
 *
 * 核心职责：
 * - 实时监控设备物理状态
 * - 监听系统广播
 * - 智能决策是否释放算力
 */
@Singleton
class GovernorServiceImpl @Inject constructor(
    private val context: Context,
    private val batteryManager: BatteryManager,
    private val powerManager: PowerManager,
    private val windowManager: WindowManager,
    private val wifiManager: WifiManager
) : Governor {

    private var isMonitoring = false
    private var protectionMode = false
    private var protectionReason = ""
    private val statusListeners = mutableListOf<(GovernorStatus) -> Unit>()

    // 广播接收器
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    notifyStatusChange()
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> {
                    notifyStatusChange()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    notifyStatusChange()
                }
            }
        }
    }

    override suspend fun getCurrentStatus(): GovernorStatus {
        return buildCurrentStatus()
    }

    override fun startMonitoring(onStatusChange: (GovernorStatus) -> Unit) {
        statusListeners.add(onStatusChange)

        if (!isMonitoring) {
            isMonitoring = true
            registerBroadcastReceiver()
        }

        // 立即发送一次状态
        notifyStatusChange()
    }

    override fun stopMonitoring() {
        isMonitoring = false
        statusListeners.clear()
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
    }

    override suspend fun canExecuteTask(
        estimatedMemoryMB: Long,
        estimatedDurationSeconds: Int,
        requiresNPU: Boolean
    ): TaskDecision {
        val status = getCurrentStatus()

        // 检查保护模式
        if (protectionMode) {
            return TaskDecision(
                allowed = false,
                reason = "保护模式: $protectionReason"
            )
        }

        // 检查内存
        if (status.availableMemoryMB < estimatedMemoryMB) {
            return TaskDecision(
                allowed = false,
                reason = "内存不足: 需要 ${estimatedMemoryMB}MB, 可用 ${status.availableMemoryMB}MB"
            )
        }

        // 检查 NPU
        if (requiresNPU && !status.npuAvailable) {
            return TaskDecision(
                allowed = false,
                reason = "需要 NPU 但设备不支持"
            )
        }

        // 检查热状态
        if (status.thermalState == ThermalState.CRITICAL) {
            return TaskDecision(
                allowed = false,
                reason = "设备过热，进入保护状态"
            )
        }

        // 检查网络任务条件（四条件）
        val canAcceptNetwork = status.meetsNetworkTaskRequirements()

        return TaskDecision(
            allowed = true,
            reason = if (canAcceptNetwork) "满足所有条件" else "仅允许本地任务",
            suggestedPriority = if (canAcceptNetwork) TaskPriority.NORMAL else TaskPriority.LOW
        )
    }

    override fun statusFlow(): Flow<GovernorStatus> = callbackFlow {
        val listener: (GovernorStatus) -> Unit = { status ->
            trySend(status)
        }

        startMonitoring(listener)

        awaitClose {
            statusListeners.remove(listener)
        }
    }

    override suspend fun enterProtectionMode(reason: String) {
        protectionMode = true
        protectionReason = reason
        notifyStatusChange()
    }

    override suspend fun exitProtectionMode() {
        protectionMode = false
        protectionReason = ""
        notifyStatusChange()
    }

    // ========== 私有方法 ==========

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        try {
            context.registerReceiver(broadcastReceiver, filter)
        } catch (e: Exception) {
            // Android 13+ 需要权限
        }
    }

    private fun notifyStatusChange() {
        val status = buildCurrentStatus()
        statusListeners.forEach { listener ->
            listener(status)
        }
    }

    private fun buildCurrentStatus(): GovernorStatus {
        // 电池信息
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
        } ?: 0

        val batteryStatusInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = batteryStatusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatusInt == BatteryManager.BATTERY_STATUS_FULL

        // 电池温度
        val batteryTemperature = batteryStatus?.let {
            it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        } ?: 25f

        // 屏幕状态
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            val display = windowManager.defaultDisplay
            display.state == Display.STATE_ON
        } else {
            powerManager.isScreenOn
        }

        // 内存信息
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
        val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)

        // CPU 使用率（简化计算）
        val cpuUsage = getCpuUsage()

        // NPU 可用性
        val npuAvailable = checkNpuAvailability()

        // 热状态
        val thermalState = getThermalState()

        // 是否可接受网络任务
        val canAcceptNetwork = !protectionMode &&
                isCharging &&
                batteryLevel >= 80 &&
                !isScreenOn &&
                batteryTemperature < 40f &&
                thermalState != ThermalState.CRITICAL

        return GovernorStatus(
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            batteryTemperature = batteryTemperature,
            isScreenOn = isScreenOn,
            cpuUsage = cpuUsage,
            availableMemoryMB = availableMemoryMB,
            totalMemoryMB = totalMemoryMB,
            npuAvailable = npuAvailable,
            thermalState = thermalState,
            canAcceptNetworkTasks = canAcceptNetwork
        )
    }

    private fun getCpuUsage(): Float {
        return try {
            val reader = java.io.RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val tokens = load.split("\\s+".toRegex())
            if (tokens.size >= 5) {
                val idle = tokens[4].toLong()
                val total = tokens.sumOf { it.toLongOrNull() ?: 0L }
                if (total > 0) {
                    1f - (idle.toFloat() / total.toFloat())
                } else 0f
            } else 0f
        } catch (e: Exception) {
            0.3f // 默认值
        }
    }

    private fun checkNpuAvailability(): Boolean {
        // 检查是否有 NPU（神经网络处理器）
        // 简化实现：检查是否支持 NNAPI
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                Build.SUPPORTED_ABIS.any { it.contains("arm64") }
    }

    private fun getThermalState(): ThermalState {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thermalService = context.getSystemService(android.os.PowerManager::class.java)
                when (thermalService?.currentThermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> ThermalState.NORMAL
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NORMAL
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARNING
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SERIOUS
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL,
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY,
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                    else -> ThermalState.NORMAL
                }
            } else {
                // Android 9 以下，用温度估算
                val status = getCurrentStatus()
                when {
                    status.batteryTemperature >= 45 -> ThermalState.CRITICAL
                    status.batteryTemperature >= 40 -> ThermalState.SERIOUS
                    status.batteryTemperature >= 38 -> ThermalState.WARNING
                    else -> ThermalState.NORMAL
                }
            }
        } catch (e: Exception) {
            ThermalState.NORMAL
        }
    }
}
