package com.pulsenetwork.domain.swarm

/**
 * mDNS 设备发现服务接口
 *
 * 使用 Android 倪原生 NsdManager 实现零配置设备发现
 */
interface NodeDiscoveryService {

    /**
     * 开始广播本节点
     * @param serviceName 服务名称
     * @param port 服务端口
     */
    suspend fun startBroadcasting(serviceName: String, port: Int): Boolean

    /**
     * 停止广播
     */
    fun stopBroadcasting()

    /**
     * 开始发现其他节点
     */
    suspend fun startDiscovery()

    /**
     * 停止发现
     */
    fun stopDiscovery()

    /**
     * 发现事件流
     */
    fun discoveryFlow(): kotlinx.coroutines.flow.Flow<DiscoveryEvent>
}

/**
 * 发现事件
 */
sealed class DiscoveryEvent {
    data class ServiceFound(
        val serviceName: String,
        val host: String,
        val port: Int
    ) : DiscoveryEvent()

    data class ServiceResolved(
        val node: PeerNode
    ) : DiscoveryEvent()

    data class ServiceLost(
        val serviceName: String
    ) : DiscoveryEvent()

    data class DiscoveryFailed(
        val error: String
    ) : DiscoveryEvent()
}
