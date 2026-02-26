package com.pulsenetwork.app.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.app.service.VoiceRecorderService
import com.pulsenetwork.core.native.LLMInference
import com.pulsenetwork.core.native.SpeechRecognition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * 聊天界面 ViewModel
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmInference: LLMInference,
    private val speechRecognition: SpeechRecognition,
    private val voiceRecorderService: VoiceRecorderService
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _networkStatus = MutableLiveData<NetworkStatus>()
    val networkStatus: LiveData<NetworkStatus> = _networkStatus

    private val _isGenerating = MutableLiveData<Boolean>()
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val _recordingAmplitude = MutableLiveData<Float>()
    val recordingAmplitude: LiveData<Float> = _recordingAmplitude

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val messageList = mutableListOf<ChatMessage>()

    init {
        _messages.value = emptyList()
        _networkStatus.value = NetworkStatus.Offline
        _isRecording.value = false
        checkModelStatus()
        observeRecordingState()
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            if (!llmInference.isModelLoaded()) {
                // TODO: 从设置获取模型路径
            }
        }
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            voiceRecorderService.amplitude.collect { amplitude ->
                _recordingAmplitude.value = amplitude
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        addMessage(userMessage)
        generateResponse(text)
    }

    fun startRecording() {
        val started = voiceRecorderService.startRecording()
        _isRecording.value = started
        if (!started) {
            _error.value = "无法启动录音"
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        val filePath = voiceRecorderService.stopRecording()

        if (filePath != null) {
            // 转录录音
            transcribeRecording(filePath)
        }
    }

    private fun transcribeRecording(filePath: String) {
        viewModelScope.launch {
            // 添加用户消息占位（转录中）
            val placeholderMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "🎤 正在转录...",
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            addMessage(placeholderMessage)

            try {
                // 读取音频文件并转录
                val result = speechRecognition.transcribeFile(filePath, "zh")

                if (result != null && result.text.isNotEmpty()) {
                    // 更新为转录结果
                    val index = messageList.indexOfFirst { it.id == placeholderMessage.id }
                    if (index >= 0) {
                        messageList[index] = placeholderMessage.copy(
                            content = "🎤 ${result.text}"
                        )
                        _messages.value = messageList.toList()

                        // 生成 AI 响应
                        generateResponse(result.text)
                    }
                } else {
                    // 转录失败
                    removeMessage(placeholderMessage.id)
                    _error.value = "语音识别失败"
                }
            } catch (e: Exception) {
                removeMessage(placeholderMessage.id)
                _error.value = "语音识别错误: ${e.message}"
            }
        }
    }

    private fun generateResponse(prompt: String) {
        _isGenerating.value = true

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
                val index = messageList.indexOfFirst { it.id == aiMessage.id }
                if (index >= 0) {
                    val updated = messageList[index].copy(
                        content = messageList[index].content + token
                    )
                    messageList[index] = updated
                    _messages.value = messageList.toList()
                }
            }

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

    private fun removeMessage(messageId: String) {
        messageList.removeAll { it.id == messageId }
        _messages.value = messageList.toList()
    }

    fun stopGeneration() {
        llmInference.stopGeneration()
        _isGenerating.value = false
    }

    fun clearError() {
        _error.value = ""
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
