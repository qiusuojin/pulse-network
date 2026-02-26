package com.pulsenetwork.domain.workflow

import kotlinx.coroutines.flow.Flow

/**
 * 工作流执行引擎
 *
 * 核心功能：
 * - 执行由 WorkflowInterviewer 生成的工作流
 * - 支持分布式执行（将步骤分配给网络节点）
 * - 进度追踪和结果聚合
 * - 错误处理和重试机制
 */
interface WorkflowExecutor {

    /**
     * 执行工作流
     * @param workflow 要执行的工作流
     * @return 执行结果
     */
    suspend fun execute(workflow: ExecutableWorkflow): WorkflowExecutionResult

    /**
     * 获取执行进度流
     */
    fun executionProgressFlow(workflowId: String): Flow<ExecutionProgress>

    /**
     * 取消执行
     */
    suspend fun cancel(workflowId: String): Boolean

    /**
     * 获取当前执行状态
     */
    fun getExecutionStatus(workflowId: String): ExecutionStatus?

    /**
     * 获取所有正在执行的工作流
     */
    fun getActiveExecutions(): List<ActiveExecution>

    /**
     * 暂停执行
     */
    suspend fun pause(workflowId: String): Boolean

    /**
     * 恢复执行
     */
    suspend fun resume(workflowId: String): Boolean
}

/**
 * 可执行工作流
 */
data class ExecutableWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val inputs: Map<String, Any>,
    val timeout: Long = 300000,  // 5分钟默认超时
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val executionMode: ExecutionMode = ExecutionMode.SEQUENTIAL,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 工作流步骤
 */
data class WorkflowStep(
    val id: String,
    val name: String,
    val type: StepType,
    val config: StepConfig,
    val dependencies: List<String> = emptyList(),  // 依赖的步骤ID
    val timeout: Long = 60000,
    val retryCount: Int = 3,
    val order: Int
)

/**
 * 步骤类型
 */
enum class StepType {
    LOCAL_INFERENCE,      // 本地推理
    REMOTE_INFERENCE,     // 远程推理（分配给网络节点）
    DATA_TRANSFORM,       // 数据转换
    CONDITION_CHECK,      // 条件检查
    PARALLEL_BATCH,       // 并行批处理
    AGGREGATION,          // 结果聚合
    EXTERNAL_API,         // 外部API调用
    USER_INPUT            // 用户输入（暂停等待）
}

/**
 * 步骤配置
 */
sealed class StepConfig {
    data class LocalInference(
        val modelType: String,
        val promptTemplate: String,
        val maxTokens: Int = 512,
        val temperature: Float = 0.7f
    ) : StepConfig()

    data class RemoteInference(
        val modelType: String,
        val promptTemplate: String,
        val preferredNodes: List<String> = emptyList(),
        val requireTrustLevel: TrustLevelRequirement = TrustLevelRequirement.ANY
    ) : StepConfig()

    data class DataTransform(
        val transformType: String,
        val inputMapping: Map<String, String>,
        val outputMapping: Map<String, String>
    ) : StepConfig()

    data class ConditionCheck(
        val condition: String,  // 表达式
        val trueBranch: String?,  // 步骤ID
        val falseBranch: String?
    ) : StepConfig()

    data class ParallelBatch(
        val batchSize: Int,
        val stepTemplate: WorkflowStep
    ) : StepConfig()

    data class Aggregation(
        val aggregationType: AggregationType,
        val inputKeys: List<String>
    ) : StepConfig()

    data class ExternalApi(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val bodyTemplate: String?
    ) : StepConfig()

    data class UserInput(
        val prompt: String,
        val inputType: InputType,
        val timeout: Long = 300000
    ) : StepConfig()
}

/**
 * 信任等级要求
 */
enum class TrustLevelRequirement {
    ANY,        // 任意节点
    FAMILIAR,   // 熟悉以上
    TRUSTED,    // 信任以上
    INTIMATE    // 仅亲密节点
}

/**
 * 聚合类型
 */
enum class AggregationType {
    CONCAT,         // 字符串拼接
    MERGE_MAP,      // Map合并
    AVERAGE,        // 数值平均
    MAX,            // 取最大值
    MIN,            // 取最小值
    VOTE,           // 投票决定
    FIRST_VALID     // 取第一个有效结果
}

/**
 * 输入类型
 */
enum class InputType {
    TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    CONFIRMATION
}

/**
 * 执行模式
 */
enum class ExecutionMode {
    SEQUENTIAL,     // 顺序执行
    PARALLEL,       // 并行执行（无依赖步骤）
    ADAPTIVE        // 自适应（根据负载动态调整）
}

/**
 * 重试策略
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000,
    val exponentialBackoff: Boolean = true,
    val retryOnErrors: List<ErrorType> = listOf(ErrorType.TIMEOUT, ErrorType.NETWORK)
)

/**
 * 错误类型
 */
enum class ErrorType {
    TIMEOUT,
    NETWORK,
    RESOURCE_UNAVAILABLE,
    INVALID_INPUT,
    EXECUTION_FAILED,
    CANCELLED
}

/**
 * 执行进度
 */
data class ExecutionProgress(
    val workflowId: String,
    val status: ExecutionStatus,
    val currentStep: Int,
    val totalSteps: Int,
    val completedSteps: List<String>,
    val failedSteps: List<FailedStep>,
    val percentComplete: Float,
    val elapsedTimeMs: Long,
    val estimatedRemainingMs: Long,
    val stepResults: Map<String, StepResult>
) {
    fun isComplete(): Boolean = status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED
}

/**
 * 执行状态
 */
enum class ExecutionStatus {
    PENDING,        // 等待中
    RUNNING,        // 执行中
    PAUSED,         // 已暂停
    COMPLETED,      // 已完成
    FAILED,         // 失败
    CANCELLED       // 已取消
}

/**
 * 步骤结果
 */
data class StepResult(
    val stepId: String,
    val status: StepStatus,
    val output: Map<String, Any>?,
    val error: String?,
    val executionTimeMs: Long,
    val executedBy: String?,  // 节点ID（如果是远程执行）
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 步骤状态
 */
enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * 失败步骤
 */
data class FailedStep(
    val stepId: String,
    val error: String,
    val retryCount: Int,
    val lastAttemptAt: Long
)

/**
 * 活跃执行
 */
data class ActiveExecution(
    val workflowId: String,
    val workflowName: String,
    val startedAt: Long,
    val progress: ExecutionProgress
)

/**
 * 工作流执行结果
 */
sealed class WorkflowExecutionResult {
    data class Success(
        val workflowId: String,
        val outputs: Map<String, Any>,
        val totalExecutionTimeMs: Long,
        val stepResults: Map<String, StepResult>
    ) : WorkflowExecutionResult()

    data class PartialSuccess(
        val workflowId: String,
        val outputs: Map<String, Any>,
        val failedSteps: List<FailedStep>,
        val totalExecutionTimeMs: Long
    ) : WorkflowExecutionResult()

    data class Failure(
        val workflowId: String,
        val error: String,
        val errorType: ErrorType,
        val failedAtStep: String?,
        val partialResults: Map<String, StepResult>
    ) : WorkflowExecutionResult()

    data class Cancelled(
        val workflowId: String,
        val cancelledAt: Long,
        val partialResults: Map<String, StepResult>
    ) : WorkflowExecutionResult()
}
