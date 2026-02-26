package com.pulsenetwork.core.native

/**
 * 语音识别接口
 *
 * 通过 JNI 调用 whisper.cpp 实现离线语音识别
 */
interface SpeechRecognition {

    /**
     * 加载模型
     * @param modelPath 模型文件路径
     * @return 是否加载成功
     */
    suspend fun loadModel(modelPath: String): Boolean

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean

    /**
     * 转录音频
     * @param audioData 音频数据 (16kHz, 16bit, mono)
     * @param language 语言代码 (zh, en, etc.)
     * @return 转录结果
     */
    suspend fun transcribe(
        audioData: FloatArray,
        language: String = "zh"
    ): TranscriptionResult?

    /**
     * 转录音频文件
     * @param filePath 音频文件路径
     * @param language 语言代码
     * @return 转录结果
     */
    suspend fun transcribeFile(
        filePath: String,
        language: String = "zh"
    ): TranscriptionResult?

    /**
     * 流式转录（实时）
     * @return 音频样本流 -> 转录文本流
     */
    fun transcribeStream(
        language: String = "zh"
    ): StreamTranscriber

    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<String>

    /**
     * 释放模型
     */
    fun unloadModel()
}

/**
 * 转录结果
 */
data class TranscriptionResult(
    val text: String,
    val segments: List<TranscriptionSegment>,
    val processingTimeMs: Long,
    val language: String,
    val confidence: Float
)

/**
 * 转录片段
 */
data class TranscriptionSegment(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float
)

/**
 * 流式转录器
 */
interface StreamTranscriber {
    /**
     * 输入音频样本
     */
    fun feed(samples: FloatArray)

    /**
     * 获取转录文本流
     */
    fun textFlow(): kotlinx.coroutines.flow.Flow<String>

    /**
     * 停止转录
     */
    fun stop()
}

/**
 * 语音识别状态
 */
enum class SpeechState {
    NOT_LOADED,
    LOADING,
    READY,
    PROCESSING,
    ERROR
}
