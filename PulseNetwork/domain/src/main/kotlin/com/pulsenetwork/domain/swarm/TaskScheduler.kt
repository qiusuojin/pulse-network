package com.pulsenetwork.domain.swarm

/**
 * 分布式任务调度器
 *
 * 核心功能：
 * - 根据节点能力智能分配任务
 * - 支持任务拆分和合并
 * - 失败重试和故障转移
 * - 负载均衡
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
}

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
