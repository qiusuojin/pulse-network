package com.pulsenetwork.domain.swarm

/**
 * 局域网蜂群网络 (Swarm Network)
 *
 * 核心功能：
 * - 基于 mDNS/DNS-SD 的零配置设备发现
 * - 点对点通信（不依赖中心服务器）
 * - 语义缓存共享（"村口八卦"协议）
 * - 分布式任务调度
 *
 * 设计原则：
 * - 局域网优先，保护隐私
 * - 去中心化，无单点故障
 * - 自组织，自动发现和加入
 */
interface SwarmNetwork {

    /**
     * 启动蜂群网络
     * @param nodeId 本节点ID
     * @param port 通信端口
     */
    suspend fun start(nodeId: String, port: Int = 37373): SwarmStartResult

    /**
     * 停止蜂群网络
     */
    suspend fun stop()

    /**
     * 获取当前发现的节点列表
     */
    fun getDiscoveredNodes(): List<PeerNode>

    /**
     * 节点发现流（实时推送新发现的节点）
     */
    fun nodeDiscoveryFlow(): kotlinx.coroutines.flow.Flow<NodeDiscoveryEvent>

    /**
     * 发送消息给指定节点
     */
    suspend fun sendToNode(nodeId: String, message: SwarmMessage): SendMessageResult

    /**
     * 广播消息给所有节点
     */
    suspend fun broadcast(message: SwarmMessage): BroadcastResult

    /**
     * 接收消息流
     */
    fun messageFlow(): kotlinx.coroutines.flow.Flow<IncomingMessage>

    /**
     * 获取网络统计信息
     */
    fun getStats(): SwarmStats

    /**
     * 是否正在运行
     */
    val isRunning: Boolean
}

/**
 * 对等节点
 */
data class PeerNode(
    val id: String,
    val address: String,
    val port: Int,
    val deviceName: String,
    val capabilities: NodeCapabilities,
    val discoveredAt: Long,
    val lastSeenAt: Long,
    val connectionState: ConnectionState
)

/**
 * 节点能力
 */
data class NodeCapabilities(
    val hasNPU: Boolean,
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val supportedModelTypes: List<String>,
    val maxConcurrentTasks: Int,
    val cpuCores: Int,
    val gpuAvailable: Boolean
)

/**
 * 连接状态
 */
enum class ConnectionState {
    DISCOVERED,    // 已发现，未连接
    CONNECTING,    // 连接中
    CONNECTED,     // 已连接
    DISCONNECTED,  // 已断开
    UNREACHABLE    // 不可达
}

/**
 * 节点发现事件
 */
sealed class NodeDiscoveryEvent {
    data class NodeDiscovered(val node: PeerNode) : NodeDiscoveryEvent()
    data class NodeUpdated(val node: PeerNode) : NodeDiscoveryEvent()
    data class NodeLost(val nodeId: String, val reason: String) : NodeDiscoveryEvent()
}

/**
 * 蜂群消息
 */
data class SwarmMessage(
    val id: String,
    val type: MessageType,
    val senderId: String,
    val recipientId: String?,    // null 表示广播
    val timestamp: Long,
    val payload: MessagePayload,
    val ttl: Int = 3,           // 传播跳数限制
    val priority: MessagePriority = MessagePriority.NORMAL
)

enum class MessageType {
    // 节点管理
    NODE_ANNOUNCE,          // 节点公告
    NODE_GOODBYE,           // 节点离线
    HEARTBEAT,              // 心跳

    // 任务相关
    TASK_REQUEST,           // 任务请求
    TASK_RESPONSE,          // 任务响应
    TASK_STATUS,            // 任务状态更新
    TASK_CANCEL,            // 任务取消

    // 缓存相关（"村口八卦"协议）
    CACHE_SHARE,            // 缓存分享
    CACHE_QUERY,            // 缓存查询
    CACHE_RESPONSE,         // 缓存响应

    // 知识相关
    KNOWLEDGE_SYNC,         // 知识同步
    KNOWLEDGE_REQUEST,      // 知识请求

    // 系统相关
    CAPABILITY_UPDATE,      // 能力更新
    GOSSIP                  // 流言传播
}

enum class MessagePriority {
    LOW, NORMAL, HIGH, URGENT
}

/**
 * 消息负载
 */
sealed class MessagePayload {
    data class NodeAnnouncement(
        val capabilities: NodeCapabilities,
        val services: List<String>
    ) : MessagePayload()

    data class TaskRequest(
        val taskId: String,
        val taskType: String,
        val input: Map<String, Any>,
        val requirements: TaskRequirements,
        val timeout: Long
    ) : MessagePayload()

    data class TaskResponse(
        val taskId: String,
        val status: TaskStatus,
        val result: Map<String, Any>?,
        val error: String?
    ) : MessagePayload()

    data class CacheShare(
        val entries: List<SemanticCacheEntry>,
        val sourceNodeId: String
    ) : MessagePayload()

    data class CacheQuery(
        val query: String,
        val queryVector: FloatArray?,
        val similarityThreshold: Float = 0.85f
    ) : MessagePayload() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CacheQuery) return false
            return query == other.query
        }
        override fun hashCode(): Int = query.hashCode()
    }

    data class CacheResponse(
        val queryId: String,
        val matches: List<SemanticCacheEntry>
    ) : MessagePayload()

    data class Gossip(
        val key: String,
        val value: String,
        val version: Long,
        val originNodeId: String
    ) : MessagePayload()
}

/**
 * 任务需求
 */
data class TaskRequirements(
    val minMemoryMB: Long,
    val requiresNPU: Boolean = false,
    val estimatedTimeMs: Long,
    val maxRetries: Int = 3
)

/**
 * 任务状态
 */
enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}

/**
 * 语义缓存条目（"村口八卦"核心数据结构）
 */
data class SemanticCacheEntry(
    val id: String,
    val query: String,
    val queryVector: FloatArray?,    // 可选的嵌入向量
    val answer: String,
    val qualityScore: Float,         // 0.0-1.0
    val sourceNodeId: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val hitCount: Int = 0
) {
    /**
     * 计算有效分数（考虑时间和热度衰减）
     */
    fun effectiveScore(): Float {
        val ageHours = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60)
        val decay = kotlin.math.exp(-ageHours / 24.0).toFloat()  // 24小时半衰期
        val hotnessBoost = 1 + kotlin.math.ln(hitCount + 1.0).toFloat() / 10
        return qualityScore * decay * hotnessBoost
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticCacheEntry) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * 蜂群启动结果
 */
sealed class SwarmStartResult {
    data class Success(val nodeId: String, val port: Int) : SwarmStartResult()
    data class Failure(val error: String, val errorCode: Int) : SwarmStartResult()
}

/**
 * 发送消息结果
 */
sealed class SendMessageResult {
    data class Success(val messageId: String) : SendMessageResult()
    data class Failure(val error: String, val retryable: Boolean) : SendMessageResult()
}

/**
 * 广播结果
 */
data class BroadcastResult(
    val messageId: String,
    val reachedNodes: Int,
    val failedNodes: List<String>
)

/**
 * 接收到的消息
 */
data class IncomingMessage(
    val message: SwarmMessage,
    val fromNode: PeerNode
)

/**
 * 蜂群统计信息
 */
data class SwarmStats(
    val discoveredNodes: Int,
    val connectedNodes: Int,
    val messagesReceived: Long,
    val messagesSent: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val averageLatencyMs: Long,
    val uptimeMs: Long
)
