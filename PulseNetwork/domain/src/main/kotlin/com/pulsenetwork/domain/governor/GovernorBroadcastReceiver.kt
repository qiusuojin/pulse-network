package com.pulsenetwork.domain.governor

/**
 * 总督广播接收器接口
 *
 * 监听 Android 系统广播，实时响应设备状态变化
 */
interface GovernorBroadcastReceiver {

    /**
     * 注册所有必要的广播监听
     */
    fun registerListeners()

    /**
     * 注销广播监听
     */
    fun unregisterListeners()

    /**
     * 获取广播事件流
     */
    fun eventsFlow(): kotlinx.coroutines.flow.Flow<GovernorEvent>
}

/**
 * 总督事件
 */
sealed class GovernorEvent {
    data class BatteryChanged(
        val level: Int,
        val isCharging: Boolean,
        val temperature: Float
    ) : GovernorEvent()

    data class ScreenStateChanged(
        val isScreenOn: Boolean
    ) : GovernorEvent()

    data class ThermalStateChanged(
        val state: ThermalState
    ) : GovernorEvent()

    data class PowerConnected(
        val isFastCharging: Boolean
    ) : GovernorEvent()

    data class PowerDisconnected(
        val batteryLevel: Int
    ) : GovernorEvent()

    data class LowMemory(
        val availableMB: Long
    ) : GovernorEvent()
}
