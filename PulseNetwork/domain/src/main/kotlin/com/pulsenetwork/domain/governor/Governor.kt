package com.pulsenetwork.domain.governor

/**
 * 本地总督 (Governor) - 物理级算力阀门
 *
 * 核心职责：
 * - 实时监控设备物理状态（电池、温度、CPU、内存）
 * - 监听系统广播（充电状态、屏幕开关、低电量警告）
 * - 智能决策是否释放算力接单
 *
 * 设计原则：
 * - 保护用户设备优先，永远不牺牲用户体验
 * - 只在"充电 + 高电量 + 息屏 + 低温"四条件同时满足时才接网络任务
 */
interface Governor {

    /**
     * 获取当前设备状态
     */
    suspend fun getCurrentStatus(): GovernorStatus

    /**
     * 启动持续监控
     * @param onStatusChange 状态变化回调
     */
    fun startMonitoring(onStatusChange: (GovernorStatus) -> Unit)

    /**
     * 停止监控
     */
    fun stopMonitoring()

    /**
     * 检查是否可以执行指定资源的任务
     * @param estimatedMemoryMB 预估内存需求
     * @param estimatedDurationSeconds 预估执行时间
     * @param requiresNPU 是否需要 NPU 加速
     */
    suspend fun canExecuteTask(
        estimatedMemoryMB: Long,
        estimatedDurationSeconds: Int,
        requiresNPU: Boolean
    ): TaskDecision

    /**
     * 获取状态流（响应式）
     */
    fun statusFlow(): kotlinx.coroutines.flow.Flow<GovernorStatus>

    /**
     * 强制进入保护模式（如检测到过热）
     */
    suspend fun enterProtectionMode(reason: String)

    /**
     * 退出保护模式
     */
    suspend fun exitProtectionMode()
}

/**
 * 总督状态数据类
 */
data class GovernorStatus(
    val isCharging: Boolean,
    val batteryLevel: Int,              // 0-100
    val batteryTemperature: Float,       // 摄氏度
    val isScreenOn: Boolean,
    val cpuUsage: Float,                 // 0.0-1.0
    val availableMemoryMB: Long,
    val totalMemoryMB: Long,
    val npuAvailable: Boolean,
    val thermalState: ThermalState,
    val canAcceptNetworkTasks: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 四条件检查：充电 + 高电量 + 息屏 + 低温
     */
    fun meetsNetworkTaskRequirements(): Boolean {
        return isCharging &&
                batteryLevel >= 80 &&
                !isScreenOn &&
                batteryTemperature < 40f &&
                thermalState != ThermalState.CRITICAL
    }
}

/**
 * 热状态
 */
enum class ThermalState {
    NORMAL,      // 正常
    WARNING,     // 警告（轻度发热）
    SERIOUS,     // 严重（中度发热）
    CRITICAL     // 危急（严重过热，需立即降频）
}

/**
 * 任务执行决策
 */
data class TaskDecision(
    val allowed: Boolean,
    val reason: String,
    val recommendedDelayMs: Long = 0,    // 建议延迟执行时间
    val suggestedPriority: TaskPriority = TaskPriority.NORMAL
)

enum class TaskPriority {
    LOW, NORMAL, HIGH, URGENT
}
