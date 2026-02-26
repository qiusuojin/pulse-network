package com.pulsenetwork.domain.relation

import kotlinx.coroutines.flow.Flow

/**
 * 关系网络层 (第8层) - RelationNetwork
 *
 * 基于赫布学习原理的节点关系管理系统
 * 核心理念: "一起激发的神经元连接在一起"
 *
 * 功能:
 * - 追踪节点间协作历史
 * - 基于成功/失败调整连接强度
 * - 推荐最适合的协作节点
 * - 镜像神经元观察学习
 *
 * 灵感来源: 神经科学中的赫布学习法则
 */
interface RelationNetwork {

    /**
     * 记录协作事件
     * 成功协作会增强连接(LTP)，失败协作会减弱连接(LTD)
     */
    suspend fun recordCollaboration(event: CollaborationEvent)

    /**
     * 获取与指定节点的连接强度
     * @return 0.0-1.0 的连接强度值，0表示无关系
     */
    fun getConnectionStrength(nodeId: String): Float

    /**
     * 获取指定任务类型的推荐节点
     * 基于历史协作记录和连接强度排序
     */
    fun getRecommendedNodes(taskType: String, limit: Int = 5): List<NodeRelation>

    /**
     * 手动更新连接强度
     * 用于特殊情况下的调整
     */
    suspend fun updateConnectionStrength(nodeId: String, deltaStrength: Float)

    /**
     * 记录镜像神经元观察
     * 用于观察学习其他节点的行为
     */
    suspend fun recordObservation(observation: MirrorObservation)

    /**
     * 获取关系网络状态流
     */
    fun relationStateFlow(): Flow<RelationNetworkState>

    /**
     * 获取所有节点关系
     */
    fun getAllRelations(): List<NodeRelation>

    /**
     * 获取指定节点的关系详情
     */
    fun getRelation(nodeId: String): NodeRelation?

    /**
     * 获取信任等级以上的节点
     */
    fun getTrustedNodes(): List<NodeRelation>

    /**
     * 执行周期性衰减
     * 应定期调用以实现时间衰减
     */
    suspend fun performDecay()
}

/**
 * 协作事件
 */
data class CollaborationEvent(
    val nodeId: String,
    val taskType: String,
    val success: Boolean,
    val qualityScore: Float,       // 0.0-1.0 任务完成质量
    val responseTimeMs: Long,      // 响应时间
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 节点关系
 */
data class NodeRelation(
    val nodeId: String,
    val connectionStrength: Float,     // 0.0-1.0 连接强度
    val collaborationCount: Int,       // 总协作次数
    val successCount: Int,             // 成功次数
    val failureCount: Int,             // 失败次数
    val trustLevel: TrustLevel,        // 信任等级
    val firstContactAt: Long,          // 首次接触时间
    val lastCollaborationAt: Long,     // 最近协作时间
    val averageQuality: Float,         // 平均质量评分
    val averageResponseTimeMs: Long,   // 平均响应时间
    val taskTypeExpertise: Map<String, Float>,  // 各类任务的专业度
    val mirrorObservations: Int        // 镜像观察次数
) {
    /**
     * 计算成功率
     */
    fun successRate(): Float {
        if (collaborationCount == 0) return 0f
        return successCount.toFloat() / collaborationCount
    }

    /**
     * 计算综合评分
     */
    fun overallScore(): Float {
        val trustWeight = trustLevel.weight
        val qualityWeight = averageQuality * 0.3f
        val reliabilityWeight = successRate() * 0.3f
        val strengthWeight = connectionStrength * 0.2f
        return (trustWeight + qualityWeight + reliabilityWeight + strengthWeight) / 4f
    }
}

/**
 * 信任等级
 */
enum class TrustLevel(val weight: Float, val minCollaborations: Int) {
    UNKNOWN(0.0f, 0),       // 未知，无协作历史
    NOVICE(0.2f, 1),        // 新手，1-3次协作
    FAMILIAR(0.5f, 4),      // 熟悉，4-10次协作
    TRUSTED(0.8f, 11),      // 信任，11-50次协作
    INTIMATE(1.0f, 51);     // 亲密，50+次协作

    companion object {
        /**
         * 根据协作次数确定信任等级
         */
        fun fromCollaborationCount(count: Int): TrustLevel {
            return entries.findLast { count >= it.minCollaborations } ?: UNKNOWN
        }
    }
}

/**
 * 镜像神经元观察记录
 * 用于观察学习其他节点的行为模式
 */
data class MirrorObservation(
    val observedNodeId: String,
    val taskType: String,
    val observedBehavior: String,      // 观察到的行为描述
    val outcome: ObservationOutcome,
    val learningValue: Float,          // 学习价值 0.0-1.0
    val timestamp: Long = System.currentTimeMillis(),
    val context: Map<String, Any> = emptyMap()
)

/**
 * 观察结果
 */
enum class ObservationOutcome {
    SUCCESS,           // 成功的行为，值得模仿
    FAILURE,           // 失败的行为，应该避免
    NEUTRAL,           // 中性，暂不判断
    OPTIMAL            // 最优行为，高优先级学习
}

/**
 * 关系网络状态
 */
data class RelationNetworkState(
    val totalRelations: Int,
    val averageStrength: Float,
    val trustDistribution: Map<TrustLevel, Int>,  // 各信任等级的节点数
    val strongestRelation: NodeRelation?,
    val recentCollaborations: Int,     // 最近24小时协作次数
    val topTaskTypes: List<String>,    // 最常见的任务类型
    val networkMaturity: Float         // 网络成熟度 0.0-1.0
) {
    companion object {
        val EMPTY = RelationNetworkState(
            totalRelations = 0,
            averageStrength = 0f,
            trustDistribution = emptyMap(),
            strongestRelation = null,
            recentCollaborations = 0,
            topTaskTypes = emptyList(),
            networkMaturity = 0f
        )
    }
}

/**
 * 赫布学习参数
 */
object HebbianParams {
    /** 长时程增强率 - 成功协作时的强度增加 */
    const val LTP_RATE = 0.05f

    /** 长时程抑制率 - 失败协作时的强度减少 */
    const val LTD_RATE = 0.03f

    /** 衰减周期（小时）- 强度自然衰减的时间周期 */
    const val DECAY_PERIOD_HOURS = 48

    /** 衰减率 - 每个周期的衰减比例 */
    const val DECAY_RATE = 0.1f

    /** 最大连接强度 */
    const val MAX_STRENGTH = 1.0f

    /** 最小连接强度（低于此值的关系会被移除） */
    const val MIN_STRENGTH = 0.01f

    /** 质量评分对强度变化的影响权重 */
    const val QUALITY_WEIGHT = 0.5f

    /** 响应时间对强度变化的影响权重 */
    const val RESPONSE_TIME_WEIGHT = 0.2f
}
