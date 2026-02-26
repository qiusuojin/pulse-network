package com.pulsenetwork.app.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.core.native.LLMInference
import com.pulsenetwork.core.native.LLMState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * 聊天界面 ViewModel
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmInference: LLMInference
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _networkStatus = MutableLiveData<NetworkStatus>()
    val networkStatus: LiveData<NetworkStatus> = _networkStatus

    private val _isGenerating = MutableLiveData<Boolean>()
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val messageList = mutableListOf<ChatMessage>()

    init {
        _messages.value = emptyList()
        _networkStatus.value = NetworkStatus.Offline
        checkModelStatus()
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            // 检查模型是否已加载
            if (!llmInference.isModelLoaded()) {
                // 尝试加载默认模型
                // TODO: 从设置获取模型路径
            }
        }
    }

    fun sendMessage(text: String) {
        // 添加用户消息
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        addMessage(userMessage)

        // 生成 AI 响应
        generateResponse(text)
    }

    private fun generateResponse(prompt: String) {
        _isGenerating.value = true

        // 添加 AI 消息占位
        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = "",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        addMessage(aiMessage)

        viewModelScope.launch {
            llmInference.generateStream(prompt).collect { token ->
                // 流式更新消息
                val index = messageList.indexOfFirst { it.id == aiMessage.id }
                if (index >= 0) {
                    val updated = messageList[index].copy(
                        content = messageList[index].content + token
                    )
                    messageList[index] = updated
                    _messages.value = messageList.toList()
                }
            }

            // 完成生成
            val index = messageList.indexOfFirst { it.id == aiMessage.id }
            if (index >= 0) {
                messageList[index] = messageList[index].copy(isStreaming = false)
                _messages.value = messageList.toList()
            }
            _isGenerating.value = false
        }
    }

    private fun addMessage(message: ChatMessage) {
        messageList.add(message)
        _messages.value = messageList.toList()
    }

    fun stopGeneration() {
        llmInference.stopGeneration()
        _isGenerating.value = false
    }
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isStreaming: Boolean = false
)

/**
 * 网络状态
 */
sealed class NetworkStatus {
    object Offline : NetworkStatus()
    data class Local(val peerCount: Int) : NetworkStatus()
    data class Online(val peerCount: Int) : NetworkStatus()

    val displayText: String
        get() = when (this) {
            is Offline -> "离线模式"
            is Local -> "局域网"
            is Online -> "已连接"
        }

    val peerCountText: String
        get() = when (this) {
            is Local -> "发现 $peerCount 个节点"
            is Online -> "在线 $peerCount 节点"
            else -> ""
        }
}
