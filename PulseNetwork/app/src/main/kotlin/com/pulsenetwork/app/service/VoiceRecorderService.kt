package com.pulsenetwork.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音录制服务
 *
 * 支持录音并转换为 PCM 数据，供 whisper.cpp 识别
 */
@Singleton
class VoiceRecorderService @Inject constructor(
    private val context: Context
) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /**
     * 检查是否有录音权限
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开始录音
     * @param outputFilePath 输出文件路径（可选）
     */
    fun startRecording(outputFilePath: String? = null): Boolean {
        if (isRecording) return false
        if (!hasRecordPermission()) return false

        try {
            val filePath = outputFilePath ?: File(
                context.cacheDir,
                "recording_${System.currentTimeMillis()}.m4a"
            ).absolutePath

            outputFile = File(filePath)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(filePath)

                prepare()
                start()
            }

            isRecording = true
            _recordingState.value = RecordingState.Recording(filePath)

            // 开始监测音量
            startAmplitudeMonitoring()

            return true
        } catch (e: IOException) {
            _recordingState.value = RecordingState.Error("录音启动失败: ${e.message}")
            return false
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("录音失败: ${e.message}")
            return false
        }
    }

    /**
     * 停止录音
     * @return 录音文件路径
     */
    fun stopRecording(): String? {
        if (!isRecording) return null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val path = outputFile?.absolutePath
            _recordingState.value = RecordingState.Stopped(path)

            return path
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("停止录音失败: ${e.message}")
            return null
        }
    }

    /**
     * 取消录音（删除文件）
     */
    fun cancelRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // 删除录音文件
            outputFile?.delete()
            outputFile = null

            _recordingState.value = RecordingState.Cancelled
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("取消录音失败: ${e.message}")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        if (isRecording) {
            cancelRecording()
        }
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun startAmplitudeMonitoring() {
        Thread {
            while (isRecording) {
                try {
                    val amp = mediaRecorder?.maxAmplitude ?: 0
                    // 归一化到 0-1 范围
                    val normalizedAmplitude = (amp / 32767f).coerceIn(0f, 1f)
                    _amplitude.value = normalizedAmplitude
                    Thread.sleep(100)
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }
}

/**
 * 录音状态
 */
sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val filePath: String) : RecordingState()
    data class Stopped(val filePath: String?) : RecordingState()
    object Cancelled : RecordingState()
    data class Error(val message: String) : RecordingState()
}
