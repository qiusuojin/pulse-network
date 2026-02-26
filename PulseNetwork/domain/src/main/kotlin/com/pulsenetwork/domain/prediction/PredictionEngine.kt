package com.pulsenetwork.domain.prediction

import com.pulsenetwork.domain.swarm.PeerNode
import kotlinx.coroutines.flow.Flow

/**
 * 预测引擎层 (第9层) - PredictionEngine
 *
 * 基于预测编码原理的智能预测系统
 * 核心理念: 大脑不断预测下一刻的感觉输入
 *
 * 功能:
 * - 预测用户下一步需求
 * - 预热常用能力
 * - 智能任务调度
 * - 临界态调控（探索vs利用）
 *
 * 灵感来源: 大脑预测机制、复杂系统理论
 */
interface PredictionEngine {

    /**
     * 预测用户下一步需求
     * 基于历史上下文和行为模式
     */
    suspend fun predictNextNeeds(context: UserContext): List<PredictedNeed>

    /**
     * 预热能力
     * 提前加载可能需要的资源
     */
    suspend fun preheatCapability(capability: CapabilityPrediction, priority: PreheatPriority)

    /**
     * 为任务寻找最优节点
     * 综合考虑能力、负载、关系强度等因素
     */
    fun findOptimalNodes(
        task: TaskPrediction,
        availableNodes: List<NodePredictionProfile>
    ): List<RankedNode>

    /**
     * 获取调度决策
     * 决定任务应该立即执行、排队还是本地执行
     */
    suspend fun getScheduleDecision(
        task: TaskPrediction,
        networkState: NetworkPredictionState
    ): ScheduleDecision

    /**
     * 更新预测模型
     * 基于反馈持续优化预测准确率
     */
    suspend fun updateModel(feedback: PredictionFeedback)

    /**
     * 获取临界态状态
     * 返回当前的探索/利用比例
     */
    fun getCriticalityState(): CriticalityState

    /**
     * 获取预测状态流
     */
    fun predictionStateFlow(): Flow<PredictionState>

    /**
     * 获取当前预测准确率
     */
    fun getAccuracy(): Float

    /**
     * 重置预测模型
     */
    suspend fun resetModel()
}

/**
 * 用户上下文
 */
data class UserContext(
    val recentQueries: List<String>,        // 最近查询
    val currentSession: SessionInfo,         // 当前会话信息
    val timeContext: TimeContext,            // 时间上下文
    val interactionPattern: InteractionPattern,  // 交互模式
    val deviceState: DeviceState,            // 设备状态
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 会话信息
 */
data class SessionInfo(
    val sessionId: String,
    val startedAt: Long,
    val queryCount: Int,
    val topics: List<String>,               // 涉及的主题
    val complexity: SessionComplexity       // 会话复杂度
)

/**
 * 会话复杂度
 */
enum class SessionComplexity {
    SIMPLE,      // 简单问答
    MODERATE,    // 中等复杂
    COMPLEX,     // 复杂多轮
    EXPERT       // 专家级
}

/**
 * 时间上下文
 */
data class TimeContext(
    val hourOfDay: Int,         // 0-23
    val dayOfWeek: Int,         // 1-7
    val isWorkHour: Boolean,    // 是否工作时段
    val typicalPatterns: List<String>  // 该时段的典型需求
)

/**
 * 交互模式
 */
data class InteractionPattern(
    val averageQueryLength: Float,
    val preferredResponseType: ResponseType,
    val patience: Float,          // 0.0-1.0 用户耐心程度
    val frequency: Float          // 每小时查询频率
)

/**
 * 响应类型偏好
 */
enum class ResponseType {
    CONCISE,      // 简洁
    DETAILED,     // 详细
    TECHNICAL,    // 技术性
    CONVERSATIONAL  // 对话式
}

/**
 * 设备状态
 */
data class DeviceState(
    val batteryLevel: Float,
    val isCharging: Boolean,
    val networkQuality: NetworkQuality,
    val availableMemoryMB: Long,
    val cpuUsage: Float
)

/**
 * 网络质量
 */
enum class NetworkQuality {
    EXCELLENT, GOOD, FAIR, POOR, OFFLINE
}

/**
 * 预测需求
 */
data class PredictedNeed(
    val type: String,              // 需求类型
    val probability: Float,        // 发生概率 0.0-1.0
    val estimatedTime: Long,       // 预计发生时间（相对于现在，毫秒）
    val context: Map<String, Any>, // 相关上下文
    val suggestedPreparation: List<String>,  // 建议的准备工作
    val confidence: Float          // 预测置信度
)

/**
 * 能力预测
 */
data class CapabilityPrediction(
    val capabilityId: String,
    val capabilityType: String,
    val requiredResources: ResourceRequirement,
    val estimatedUsageTime: Long,  // 预计使用时间
    val priority: PreheatPriority
)

/**
 * 资源需求
 */
data class ResourceRequirement(
    val memoryMB: Long,
    val requiresNPU: Boolean,
    val requiresGPU: Boolean,
    val estimatedDurationMs: Long,
    val modelSizeMB: Long = 0
)

/**
 * 预热优先级
 */
enum class PreheatPriority {
    LOW,       // 低优先级，空闲时加载
    NORMAL,    // 正常优先级
    HIGH,      // 高优先级，尽快加载
    URGENT     // 紧急，立即加载
}

/**
 * 任务预测
 */
data class TaskPrediction(
    val taskId: String,
    val taskType: String,
    val estimatedComplexity: TaskComplexity,
    val estimatedDurationMs: Long,
    val resourceRequirements: ResourceRequirement,
    val preferredCapabilities: List<String>,
    val deadline: Long? = null
)

/**
 * 任务复杂度
 */
enum class TaskComplexity {
    TRIVIAL,      // 微不足道 <100ms
    SIMPLE,       // 简单 100ms-1s
    MODERATE,     // 中等 1s-10s
    COMPLEX,      // 复杂 10s-60s
    HEAVY         // 繁重 >60s
}

/**
 * 节点预测配置
 */
data class NodePredictionProfile(
    val node: PeerNode,
    val predictedAvailability: Float,    // 预测可用性 0.0-1.0
    val predictedLoad: Float,            // 预测负载 0.0-1.0
    val specializationScore: Float,      // 专业化分数
    val historicalReliability: Float,    // 历史可靠性
    val predictedResponseTimeMs: Long    // 预测响应时间
)

/**
 * 排名后的节点
 */
data class RankedNode(
    val profile: NodePredictionProfile,
    val rank: Int,
    val score: Float,                    // 综合评分
    val reasons: List<RankReason>        // 排名原因
)

/**
 * 排名原因
 */
enum class RankReason {
    BEST_CAPABILITY,      // 最佳能力匹配
    LOWEST_LOAD,          // 最低负载
    HIGHEST_RELIABILITY,  // 最高可靠性
    FASTEST_RESPONSE,     // 最快响应
    STRONGEST_RELATION,   // 最强关系
    SPECIALIZED,          // 专业化
    GEOGRAPHICALLY_CLOSE, // 地理位置近
    HISTORICAL_SUCCESS    // 历史成功率高
}

/**
 * 调度决策
 */
sealed class ScheduleDecision {
    /** 立即执行 */
    data class ExecuteImmediately(
        val assignedNodes: List<String>,
        val estimatedCompletionTime: Long
    ) : ScheduleDecision()

    /** 加入队列 */
    data class Queue(
        val queuePosition: Int,
        val estimatedWaitTime: Long,
        val reason: QueueReason
    ) : ScheduleDecision()

    /** 本地执行 */
    data class ExecuteLocally(
        val reason: LocalReason
    ) : ScheduleDecision()

    /** 请求溢价（紧急任务） */
    data class RequestPremium(
        val premiumLevel: Int,
        val additionalCost: Float,
        val guaranteedResponseTime: Long
    ) : ScheduleDecision()

    /** 拒绝 */
    data class Reject(
        val reason: String,
        val alternatives: List<String>
    ) : ScheduleDecision()
}

/**
 * 排队原因
 */
enum class QueueReason {
    ALL_NODES_BUSY,       // 所有节点繁忙
    WAITING_FOR_PREHEAT,  // 等待预热
    LOAD_BALANCING,       // 负载均衡
    PRIORITY_TOO_LOW      // 优先级过低
}

/**
 * 本地执行原因
 */
enum class LocalReason {
    NETWORK_POOR,         // 网络不佳
    NO_SUITABLE_NODES,    // 无合适节点
    PRIVACY_SENSITIVE,    // 隐私敏感
    FASTER_LOCALLY,       // 本地更快
    OFFLINE_MODE          // 离线模式
}

/**
 * 预测反馈
 */
data class PredictionFeedback(
    val predictionId: String,
    val predicted: PredictedNeed,
    val actual: ActualOutcome,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 实际结果
 */
sealed class ActualOutcome {
    data class Hit(val actualType: String) : ActualOutcome()        // 预测命中
    data class Miss(val actualType: String) : ActualOutcome()       // 预测失误
    data class PartialHit(val actualType: String, val similarity: Float) : ActualOutcome()  // 部分命中
}

/**
 * 临界态状态
 * 控制探索与利用的平衡
 */
data class CriticalityState(
    val explorationRatio: Float,        // 探索比例 0.0-1.0
    val exploitationRatio: Float,       // 利用比例 0.0-1.0
    val currentPhase: CriticalityPhase, // 当前阶段
    val recentAccuracy: Float,          // 近期预测准确率
    val adjustmentHistory: List<AdjustmentRecord>  // 调整历史
) {
    companion object {
        val DEFAULT = CriticalityState(
            explorationRatio = 0.2f,
            exploitationRatio = 0.8f,
            currentPhase = CriticalityPhase.BALANCED,
            recentAccuracy = 0.5f,
            adjustmentHistory = emptyList()
        )
    }
}

/**
 * 临界态阶段
 */
enum class CriticalityPhase {
    EXPLORATION,    // 探索阶段 - 尝试新节点
    EXPLOITATION,   // 利用阶段 - 使用已知好节点
    BALANCED,       // 平衡阶段
    CHAOTIC,        // 混沌阶段 - 高探索
    ORDERED         // 有序阶段 - 高利用
}

/**
 * 调整记录
 */
data class AdjustmentRecord(
    val timestamp: Long,
    val oldRatio: Float,
    val newRatio: Float,
    val reason: String
)

/**
 * 预测状态
 */
data class PredictionState(
    val accuracy: Float,                 // 总体准确率
    val recentPredictions: Int,          // 近期预测数
    val hits: Int,                       // 命中数
    val misses: Int,                     // 失误数
    val averageConfidence: Float,        // 平均置信度
    val topPredictedNeeds: List<PredictedNeed>,  // 热门预测需求
    val criticalityState: CriticalityState,      // 临界态状态
    val modelVersion: String             // 模型版本
) {
    companion object {
        val INITIAL = PredictionState(
            accuracy = 0.5f,
            recentPredictions = 0,
            hits = 0,
            misses = 0,
            averageConfidence = 0.5f,
            topPredictedNeeds = emptyList(),
            criticalityState = CriticalityState.DEFAULT,
            modelVersion = "1.0.0"
        )
    }
}

/**
 * 网络预测状态
 */
data class NetworkPredictionState(
    val totalNodes: Int,
    val availableNodes: Int,
    val averageLoad: Float,
    val networkLatency: Long,
    val criticalityState: CriticalityState
)

/**
 * 临界态调控参数
 */
object CriticalityParams {
    /** 默认探索比例 */
    const val DEFAULT_EXPLORATION_RATIO = 0.2f

    /** 准确率低于此值时增加探索 */
    const val LOW_ACCURACY_THRESHOLD = 0.5f

    /** 准确率高于此值时减少探索 */
    const val HIGH_ACCURACY_THRESHOLD = 0.9f

    /** 探索比例调整步长 */
    const val ADJUSTMENT_STEP = 0.05f

    /** 最小探索比例 */
    const val MIN_EXPLORATION = 0.05f

    /** 最大探索比例 */
    const val MAX_EXPLORATION = 0.5f

    /** 准确率计算窗口大小（最近N次预测） */
    const val ACCURACY_WINDOW_SIZE = 100
}
