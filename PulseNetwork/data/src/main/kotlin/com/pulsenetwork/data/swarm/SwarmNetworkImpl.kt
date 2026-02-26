package com.pulsenetwork.data.swarm

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.pulsenetwork.domain.swarm.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 蜂群网络服务实现
 *
 * 使用 Android 原生 NsdManager 实现 mDNS 设备发现
 * 使用 Socket 实现 P2P 通信
 */
@Singleton
class SwarmNetworkImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SwarmNetwork {

    companion object {
        const val SERVICE_TYPE = "_pulsenetwork._tcp."
        const val DEFAULT_PORT = 37373
        const val BUFFER_SIZE = 65536
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var _isRunning = false
    override val isRunning: Boolean get() = _isRunning

    private var nodeId: String = ""
    private var port: Int = DEFAULT_PORT
    private var serverSocket: ServerSocket? = null

    // 已发现的节点
    private val discoveredNodes = ConcurrentHashMap<String, PeerNode>()

    // 活跃连接
    private val activeConnections = ConcurrentHashMap<String, Socket>()

    // 消息处理器
    private val messageChannel = Channel<IncomingMessage>(capacity = Channel.UNLIMITED)

    // 服务发现监听器
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun start(nodeId: String, port: Int): SwarmStartResult {
        this.nodeId = nodeId
        this.port = port

        return withContext(Dispatchers.IO) {
            try {
                // 启动 TCP 服务器
                serverSocket = ServerSocket(port)

                // 启动接受连接的协程
                scope.launch {
                    acceptConnections()
                }

                // 注册 mDNS 服务
                registerService(port)

                // 开始发现服务
                startDiscovery()

                _isRunning = true

                SwarmStartResult.Success(nodeId, port)
            } catch (e: Exception) {
                SwarmStartResult.Failure(e.message ?: "启动失败", -1)
            }
        }
    }

    override suspend fun stop() {
        _isRunning = false

        // 注销服务
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            // 忽略
        }

        // 关闭所有连接
        activeConnections.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                // 忽略
            }
        }
        activeConnections.clear()

        // 关闭服务器
        serverSocket?.close()
        serverSocket = null

        // 取消所有协程
        scope.cancel()
    }

    override fun getDiscoveredNodes(): List<PeerNode> {
        return discoveredNodes.values.toList()
    }

    override fun nodeDiscoveryFlow(): Flow<NodeDiscoveryEvent> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val node = PeerNode(
                            id = serviceInfo.serviceName,
                            address = serviceInfo.host?.hostAddress ?: "",
                            port = serviceInfo.port,
                            deviceName = serviceInfo.serviceName,
                            capabilities = NodeCapabilities(
                                hasNPU = false,
                                totalMemoryMB = 0,
                                availableMemoryMB = 0,
                                supportedModelTypes = emptyList(),
                                maxConcurrentTasks = 1,
                                cpuCores = Runtime.getRuntime().availableProcessors(),
                                gpuAvailable = false
                            ),
                            discoveredAt = System.currentTimeMillis(),
                            lastSeenAt = System.currentTimeMillis(),
                            connectionState = ConnectionState.DISCOVERED
                        )

                        discoveredNodes[node.id] = node
                        trySend(NodeDiscoveryEvent.NodeDiscovered(node))
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val nodeId = service.serviceName
                discoveredNodes.remove(nodeId)
                trySend(NodeDiscoveryEvent.NodeLost(nodeId, "服务丢失"))
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                trySend(NodeDiscoveryEvent.DiscoveryFailed("发现启动失败: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    override suspend fun sendToNode(nodeId: String, message: SwarmMessage): SendMessageResult {
        val node = discoveredNodes[nodeId]
            ?: return SendMessageResult.Failure("节点未找到", false)

        return withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateConnection(node)
                val outputStream = socket.getOutputStream()

                // 序列化消息（简化实现，实际应使用 protobuf）
                val data = serializeMessage(message)
                outputStream.write(data)
                outputStream.flush()

                SendMessageResult.Success(message.id)
            } catch (e: Exception) {
                activeConnections.remove(nodeId)
                SendMessageResult.Failure(e.message ?: "发送失败", true)
            }
        }
    }

    override suspend fun broadcast(message: SwarmMessage): BroadcastResult {
        val results = mutableListOf<String>()
        val failedNodes = mutableListOf<String>()

        discoveredNodes.values.forEach { node ->
            when (val result = sendToNode(node.id, message)) {
                is SendMessageResult.Success -> results.add(node.id)
                is SendMessageResult.Failure -> failedNodes.add(node.id)
            }
        }

        return BroadcastResult(
            messageId = message.id,
            reachedNodes = results.size,
            failedNodes = failedNodes
        )
    }

    override fun messageFlow(): Flow<IncomingMessage> = messageChannel.receiveAsFlow()

    override fun getStats(): SwarmStats {
        return SwarmStats(
            discoveredNodes = discoveredNodes.size,
            connectedNodes = activeConnections.size,
            messagesReceived = 0,
            messagesSent = 0,
            cacheHits = 0,
            cacheMisses = 0,
            averageLatencyMs = 0,
            uptimeMs = 0
        )
    }

    // ========== 私有方法 ==========

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Pulse-$nodeId"
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun startDiscovery() {
        // 发现逻辑在 nodeDiscoveryFlow() 中实现
    }

    private suspend fun acceptConnections() {
        while (_isRunning) {
            try {
                val socket = serverSocket?.accept() ?: break

                scope.launch {
                    handleConnection(socket)
                }
            } catch (e: Exception) {
                if (_isRunning) {
                    // 记录错误但继续接受连接
                }
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            while (_isRunning && socket.isConnected) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val data = buffer.copyOf(bytesRead)
                val message = deserializeMessage(data)

                // 查找发送者节点
                val fromNode = discoveredNodes[message.senderId] ?: PeerNode(
                    id = message.senderId,
                    address = socket.inetAddress.hostAddress ?: "",
                    port = socket.port,
                    deviceName = "Unknown",
                    capabilities = NodeCapabilities(
                        hasNPU = false,
                        totalMemoryMB = 0,
                        availableMemoryMB = 0,
                        supportedModelTypes = emptyList(),
                        maxConcurrentTasks = 1,
                        cpuCores = 0,
                        gpuAvailable = false
                    ),
                    discoveredAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                    connectionState = ConnectionState.CONNECTED
                )

                messageChannel.send(IncomingMessage(message, fromNode))
            }
        } catch (e: Exception) {
            // 连接断开
        } finally {
            socket.close()
        }
    }

    @Synchronized
    private fun getOrCreateConnection(node: PeerNode): Socket {
        return activeConnections.getOrPut(node.id) {
            Socket(node.address, node.port).apply {
                soTimeout = 30000
                keepAlive = true
            }
        }
    }

    private fun serializeMessage(message: SwarmMessage): ByteArray {
        // 简化实现，实际应使用 protobuf
        val sb = StringBuilder()
        sb.append(message.id).append("|")
        sb.append(message.type.name).append("|")
        sb.append(message.senderId).append("|")
        sb.append(message.recipientId ?: "").append("|")
        sb.append(message.timestamp).append("|")
        sb.append(message.ttl).append("|")
        sb.append(message.priority.name).append("|")
        // payload 序列化需要更复杂的实现
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun deserializeMessage(data: ByteArray): SwarmMessage {
        // 简化实现
        val parts = String(data, Charsets.UTF_8).split("|")
        return SwarmMessage(
            id = parts.getOrElse(0) { UUID.randomUUID().toString() },
            type = try { MessageType.valueOf(parts.getOrElse(1) { "HEARTBEAT" }) } catch (e: Exception) { MessageType.HEARTBEAT },
            senderId = parts.getOrElse(2) { "" },
            recipientId = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
            timestamp = parts.getOrElse(4) { "0" }.toLongOrNull() ?: System.currentTimeMillis(),
            payload = MessagePayload.Gossip("key", "value", 0, ""),
            ttl = parts.getOrElse(5) { "3" }.toIntOrNull() ?: 3,
            priority = try { MessagePriority.valueOf(parts.getOrElse(6) { "NORMAL" }) } catch (e: Exception) { MessagePriority.NORMAL }
        )
    }
}
