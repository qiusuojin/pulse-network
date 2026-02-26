package com.pulsenetwork.domain.workflow

/**
 * 加密工作流
 *
 * 工作流核心逻辑被加密保护：
 * - 网络节点无法窥探工作流内容
 * - 只能通过定义的 API 接口调用
 * - 支持多级加密（公开/私有/机密/绝密）
 */
data class EncryptedWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val creatorId: String,
    val createdAt: Long,
    val encryptedCore: ByteArray,           // 加密的工作流核心逻辑
    val encryptionLevel: EncryptionLevel,
    val publicInterface: WorkflowInterface, // 公开的调用接口
    val metadata: WorkflowMetadata
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedWorkflow) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * 加密级别
 */
enum class EncryptionLevel {
    PUBLIC,       // 公开：任何人可见可调用
    PRIVATE,      // 私有：仅创建者可见
    CONFIDENTIAL, // 机密：需要授权才能调用
    TOP_SECRET    // 绝密：端到端加密，仅创建者可解密
}

/**
 * 工作流公开接口
 * 网络节点只能看到这个接口定义
 */
data class WorkflowInterface(
    val inputs: List<InputSpec>,
    val outputs: List<OutputSpec>,
    val estimatedCost: CostEstimate,
    val executionConstraints: ExecutionConstraints
)

data class InputSpec(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

data class OutputSpec(
    val name: String,
    val type: String,
    val description: String
)

data class CostEstimate(
    val minTokens: Int,
    val maxTokens: Int,
    val estimatedTimeMs: Long,
    val requiredMemoryMB: Long
)

data class ExecutionConstraints(
    val maxExecutionTimeMs: Long = 60000,
    val requiresNPU: Boolean = false,
    val requiresNetwork: Boolean = false,
    val maxRetries: Int = 3
)

/**
 * 工作流元数据
 */
data class WorkflowMetadata(
    val version: String,
    val tags: List<String>,
    val category: String,
    val useCount: Long = 0,
    val rating: Float = 0f,
    val lastUsedAt: Long? = null
)

/**
 * 工作流执行器接口
 */
interface WorkflowExecutor {

    /**
     * 执行加密工作流
     * @param workflow 加密的工作流
     * @param inputs 输入参数
     * @param decryptionKey 解密密钥（如果需要）
     */
    suspend fun execute(
        workflow: EncryptedWorkflow,
        inputs: Map<String, Any>,
        decryptionKey: ByteArray? = null
    ): WorkflowResult

    /**
     * 流式执行（支持实时输出）
     */
    suspend fun executeStreaming(
        workflow: EncryptedWorkflow,
        inputs: Map<String, Any>,
        decryptionKey: ByteArray? = null,
        onChunk: (String) -> Unit
    ): WorkflowResult
}

/**
 * 工作流执行结果
 */
sealed class WorkflowResult {
    data class Success(
        val outputs: Map<String, Any>,
        val executionTimeMs: Long,
        val tokensUsed: Int,
        val nodeId: String
    ) : WorkflowResult()

    data class PartialSuccess(
        val outputs: Map<String, Any>,
        val warnings: List<String>,
        val executionTimeMs: Long
    ) : WorkflowResult()

    data class Failure(
        val error: String,
        val errorCode: ErrorCode,
        val retryable: Boolean,
        val partialOutputs: Map<String, Any>? = null
    ) : WorkflowResult()
}

enum class ErrorCode {
    TIMEOUT,
    OUT_OF_MEMORY,
    INPUT_VALIDATION_FAILED,
    DECRYPTION_FAILED,
    INTERNAL_ERROR,
    NODE_UNAVAILABLE
}
