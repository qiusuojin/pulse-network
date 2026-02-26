package com.pulsenetwork.app.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.pulsenetwork.core.native.SpeechRecognition
import com.pulsenetwork.core.native.TranscriptionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时语音识别服务
 *
 * 使用 AudioRecord 捕获原始 PCM 数据，传给 whisper.cpp 识别
 */
@Singleton
class RealTimeSpeechService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechRecognition: SpeechRecognition
) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private val _listeningState = MutableStateFlow<ListeningState>(ListeningState.Idle)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val bufferSize by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }

    /**
     * 开始实时监听
     */
    fun startListening(): Boolean {
        if (isListening) return false

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _listeningState.value = ListeningState.Error("音频初始化失败")
                return false
            }

            audioRecord?.startRecording()
            isListening = true
            _listeningState.value = ListeningState.Listening

            // 开始录音循环
            startRecordingLoop()

            return true
        } catch (e: SecurityException) {
            _listeningState.value = ListeningState.Error("缺少录音权限")
            return false
        } catch (e: Exception) {
            _listeningState.value = ListeningState.Error("启动失败: ${e.message}")
            return false
        }
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        if (!isListening) return

        isListening = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        _listeningState.value = ListeningState.Stopped
    }

    private fun startRecordingLoop() {
        Thread {
            val buffer = ShortArray(bufferSize / 2)

            while (isListening && audioRecord != null) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readCount > 0) {
                    // 转换为 float 数组（whisper.cpp 需要）
                    val floatBuffer = FloatArray(readCount) { i ->
                        buffer[i] / 32768f
                    }

                    // 发送录音数据
                    _listeningState.value = ListeningState.AudioData(floatBuffer)
                }
            }
        }.start()
    }

    /**
     * 转录音频数据
     */
    suspend fun transcribe(audioData: FloatArray, language: String = "zh"): TranscriptionResult? {
        return withContext(Dispatchers.IO) {
            speechRecognition.transcribe(audioData, language)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopListening()
    }
}

/**
 * 监听状态
 */
sealed class ListeningState {
    object Idle : ListeningState()
    object Listening : ListeningState()
    data class AudioData(val data: FloatArray) : ListeningState()
    object Stopped : ListeningState()
    data class Error(val message: String) : ListeningState()
}
