package com.pulsenetwork.domain.swarm

/**
 * 分布式任务调度器
 *
 * 核心功能：
 * - 根据节点能力智能分配任务
 * - 支持任务拆分和合并
 * - 失败重试和故障转移
 * - 负载均衡
 *
 * v0.2 新增：
 * - 预测式调度（基于用户行为预测）
 * - 关系优先调度（基于节点关系强度）
 * - 进化感知调度（考虑节点等级和专业化）
 */
interface TaskScheduler {

    /**
     * 提交任务
     */
    suspend fun submitTask(request: TaskSubmitRequest): TaskSubmitResult

    /**
     * 取消任务
     */
    suspend fun cancelTask(taskId: String): Boolean

    /**
     * 获取任务状态
     */
    suspend fun getTaskStatus(taskId: String): TaskInfo?

    /**
     * 获取任务状态流（实时更新）
     */
    fun taskStatusFlow(taskId: String): kotlinx.coroutines.flow.Flow<TaskInfo>

    /**
     * 获取所有活动任务
     */
    fun getActiveTasks(): List<TaskInfo>

    /**
     * 获取本节点待执行的任务
     */
    fun getPendingLocalTasks(): List<TaskInfo>

    // ========== v0.2 新增方法 ==========

    /**
     * 预测式调度
     * 基于用户上下文预测下一步需求，提前准备资源
     * @param context 用户上下文信息
     * @return 预调度的任务列表
     */
    suspend fun getPredictiveSchedule(context: PredictiveContext): List<PredictiveTask>

    /**
     * 关系优先调度
     * 优先将任务分配给关系强度高的节点
     * @param request 任务请求
     * @param relationStrengths 节点关系强度映射
     * @return 调度结果
     */
    suspend fun scheduleWithRelationPriority(
        request: TaskSubmitRequest,
        relationStrengths: Map<String, Float>
    ): TaskSubmitResult

    /**
     * 获取调度统计信息
     */
    fun getSchedulerStats(): SchedulerStats
}

/**
 * 预测式上下文（v0.2）
 */
data class PredictiveContext(
    val recentTaskTypes: List<String>,
    val timeOfDay: Int,
    val sessionComplexity: Float,
    val userPatterns: Map<String, Float>
)

/**
 * 预调度任务（v0.2）
 */
data class PredictiveTask(
    val predictedType: String,
    val probability: Float,
    val preAllocatedNodes: List<String>,
    val预热Resources: List<String>,
    val estimatedTriggerTime: Long
)

/**
 * 调度器统计信息（v0.2）
 */
data class SchedulerStats(
    val totalTasksScheduled: Long,
    val successfulTasks: Long,
    val failedTasks: Long,
    val averageSchedulingTimeMs: Long,
    val predictiveHitRate: Float,
    val relationOptimizedTasks: Long,
    val currentQueueSize: Int
)

/**
 * 任务提交请求
 */
data class TaskSubmitRequest(
    val type: String,
    val input: Map<String, Any>,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val requirements: TaskRequirements,
    val callbackUrl: String? = null,    // 完成回调URL
    val maxNodes: Int = 1,               // 最大并行节点数（用于任务拆分）
    val timeout: Long = 60000
)

/**
 * 任务提交结果
 */
sealed class TaskSubmitResult {
    data class Success(
        val taskId: String,
        val assignedNodes: List<String>
    ) : TaskSubmitResult()

    data class Queued(
        val taskId: String,
        val queuePosition: Int,
        val estimatedWaitMs: Long
    ) : TaskSubmitResult()

    data class Rejected(
        val reason: String,
        val suggestedAlternatives: List<String>?
    ) : TaskSubmitResult()
}

/**
 * 任务信息
 */
data class TaskInfo(
    val id: String,
    val type: String,
    val status: TaskStatus,
    val priority: MessagePriority,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val assignedNodes: List<String>,
    val progress: Float,              // 0.0-1.0
    val result: Map<String, Any>?,
    val error: String?,
    val retryCount: Int
)

/**
 * 任务分配策略
 */
interface TaskAllocationStrategy {

    /**
     * 选择最佳执行节点
     * @param task 任务信息
     * @param availableNodes 可用节点列表
     * @return 选中的节点，或 null 表示无合适节点
     */
    fun selectNode(
        task: TaskSubmitRequest,
        availableNodes: List<PeerNode>
    ): PeerNode?

    /**
     * 检查节点是否满足任务需求
     */
    fun nodeMeetsRequirements(
        node: PeerNode,
        requirements: TaskRequirements
    ): Boolean
}

/**
 * 默认任务分配策略
 */
class DefaultTaskAllocationStrategy : TaskAllocationStrategy {

    override fun selectNode(
        task: TaskSubmitRequest,
        availableNodes: List<PeerNode>
    ): PeerNode? {
        // 筛选满足条件的节点
        val candidates = availableNodes
            .filter { it.connectionState == ConnectionState.CONNECTED }
            .filter { nodeMeetsRequirements(it, task.requirements) }

        if (candidates.isEmpty()) return null

        // 选择负载最低的节点（简单实现）
        return candidates.minByOrNull {
            it.capabilities.maxConcurrentTasks
        }
    }

    override fun nodeMeetsRequirements(
        node: PeerNode,
        requirements: TaskRequirements
    ): Boolean {
        val caps = node.capabilities

        // 检查内存
        if (caps.availableMemoryMB < requirements.minMemoryMB) return false

        // 检查 NPU
        if (requirements.requiresNPU && !caps.hasNPU) return false

        return true
    }
}

/**
 * 关系感知任务分配策略（v0.2）
 * 综合考虑节点能力、关系强度和专业化程度
 */
class RelationAwareAllocationStrategy(
    private val relationStrengths: Map<String, Float> = emptyMap(),
    private val nodeExpertise: Map<String, Map<String, Float>> = emptyMap()
) : TaskAllocationStrategy {

    companion object {
        // 权重配置
        const val CAPABILITY_WEIGHT = 0.4f
        const val RELATION_WEIGHT = 0.3f
        const val EXPERTISE_WEIGHT = 0.2f
        const val LOAD_WEIGHT = 0.1f
    }

    override fun selectNode(
        task: TaskSubmitRequest,
        availableNodes: List<PeerNode>
    ): PeerNode? {
        val candidates = availableNodes
            .filter { it.connectionState == ConnectionState.CONNECTED }
            .filter { nodeMeetsRequirements(it, task.requirements) }

        if (candidates.isEmpty()) return null

        // 计算每个候选节点的综合评分
        return candidates.maxByOrNull { node ->
            calculateNodeScore(node, task.type)
        }
    }

    override fun nodeMeetsRequirements(
        node: PeerNode,
        requirements: TaskRequirements
    ): Boolean {
        val caps = node.capabilities

        if (caps.availableMemoryMB < requirements.minMemoryMB) return false
        if (requirements.requiresNPU && !caps.hasNPU) return false

        return true
    }

    /**
     * 计算节点综合评分
     */
    fun calculateNodeScore(node: PeerNode, taskType: String): Float {
        // 能力分数
        val capabilityScore = node.capabilities.maxConcurrentTasks.toFloat() / 10f

        // 关系分数
        val relationScore = relationStrengths[node.id] ?: 0f

        // 专业化分数
        val expertiseScore = nodeExpertise[node.id]?.get(taskType) ?: 0f

        // 负载分数（可用内存比例）
        val loadScore = if (node.capabilities.totalMemoryMB > 0) {
            node.capabilities.availableMemoryMB.toFloat() / node.capabilities.totalMemoryMB.toFloat()
        } else 0f

        return capabilityScore * CAPABILITY_WEIGHT +
               relationScore * RELATION_WEIGHT +
               expertiseScore * EXPERTISE_WEIGHT +
               loadScore * LOAD_WEIGHT
    }

    /**
     * 更新关系强度
     */
    fun updateRelationStrength(nodeId: String, strength: Float) {
        // 不可变对象，需要创建新实例
    }

    /**
     * 更新节点专家度
     */
    fun updateNodeExpertise(nodeId: String, taskType: String, expertise: Float) {
        // 不可变对象，需要创建新实例
    }
}
