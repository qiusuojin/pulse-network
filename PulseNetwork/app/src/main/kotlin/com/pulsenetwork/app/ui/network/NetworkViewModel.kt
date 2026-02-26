package com.pulsenetwork.app.ui.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.domain.swarm.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 网络状态 ViewModel
 */
@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val swarmNetwork: SwarmNetwork
) : ViewModel() {

    private val _isRunning = MutableLiveData<Boolean>()
    val isRunning: LiveData<Boolean> = _isRunning

    private val _nodes = MutableLiveData<List<PeerNode>>()
    val nodes: LiveData<List<PeerNode>> = _nodes

    private val _stats = MutableLiveData<SwarmStats>()
    val stats: LiveData<SwarmStats> = _stats

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val nodeId = UUID.randomUUID().toString().take(8)

    init {
        _isRunning.value = false
        _nodes.value = emptyList()
        _stats.value = SwarmStats(
            discoveredNodes = 0,
            connectedNodes = 0,
            messagesReceived = 0,
            messagesSent = 0,
            cacheHits = 0,
            cacheMisses = 0,
            averageLatencyMs = 0,
            uptimeMs = 0
        )
    }

    fun startNetwork() {
        viewModelScope.launch {
            when (val result = swarmNetwork.start(nodeId, 37373)) {
                is SwarmStartResult.Success -> {
                    _isRunning.value = true
                    observeNetwork()
                }
                is SwarmStartResult.Failure -> {
                    _error.value = "启动失败: ${result.error}"
                }
            }
        }
    }

    fun stopNetwork() {
        viewModelScope.launch {
            swarmNetwork.stop()
            _isRunning.value = false
            _nodes.value = emptyList()
        }
    }

    fun connectToNode(nodeId: String) {
        viewModelScope.launch {
            // 发送心跳消息测试连接
            val message = SwarmMessage(
                id = UUID.randomUUID().toString(),
                type = MessageType.HEARTBEAT,
                senderId = this@NetworkViewModel.nodeId,
                recipientId = nodeId,
                timestamp = System.currentTimeMillis(),
                payload = MessagePayload.Gossip("ping", "1", 1, this@NetworkViewModel.nodeId)
            )

            when (val result = swarmNetwork.sendToNode(nodeId, message)) {
                is SendMessageResult.Success -> {
                    // 连接成功
                }
                is SendMessageResult.Failure -> {
                    _error.value = "连接失败: ${result.error}"
                }
            }
        }
    }

    private fun observeNetwork() {
        // 观察节点发现
        viewModelScope.launch {
            swarmNetwork.nodeDiscoveryFlow().collect { event ->
                when (event) {
                    is NodeDiscoveryEvent.NodeDiscovered,
                    is NodeDiscoveryEvent.NodeUpdated -> {
                        _nodes.value = swarmNetwork.getDiscoveredNodes()
                    }
                    is NodeDiscoveryEvent.NodeLost -> {
                        _nodes.value = swarmNetwork.getDiscoveredNodes()
                    }
                    is NodeDiscoveryEvent.DiscoveryFailed -> {
                        _error.value = event.toString()
                    }
                }
            }
        }

        // 定期更新统计
        viewModelScope.launch {
            while (_isRunning.value == true) {
                _stats.value = swarmNetwork.getStats()
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value == true) {
            viewModelScope.launch {
                swarmNetwork.stop()
            }
        }
    }
}
