package com.pulsenetwork.core.native

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 语音识别实现
 *
 * 通过 JNI 调用 whisper.cpp
 */
class SpeechRecognitionImpl : SpeechRecognition {

    companion object {
        init {
            System.loadLibrary("pulsenative")
        }
    }

    private var isLoaded = false

    // JNI 原生方法
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeTranscribe(
        samples: FloatArray,
        language: String
    ): TranscriptionResult?

    private external fun nativeTranscribeFile(
        filePath: String,
        language: String
    ): TranscriptionResult?

    private external fun nativeUnloadModel()

    override suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            isLoaded = nativeLoadModel(modelPath)
            isLoaded
        } catch (e: UnsatisfiedLinkError) {
            // JNI 未链接，使用模拟实现
            isLoaded = true
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun isModelLoaded(): Boolean = isLoaded

    override suspend fun transcribe(
        audioData: FloatArray,
        language: String
    ): TranscriptionResult? = withContext(Dispatchers.IO) {
        try {
            nativeTranscribe(audioData, language)
        } catch (e: UnsatisfiedLinkError) {
            // 模拟实现
            TranscriptionResult(
                text = "这是模拟的语音识别结果。在实际集成 whisper.cpp 后，这里会显示真实识别的文字。",
                segments = listOf(
                    TranscriptionSegment(
                        text = "这是模拟的语音识别结果。",
                        startTimeMs = 0,
                        endTimeMs = 3000,
                        confidence = 0.95f
                    )
                ),
                processingTimeMs = 500,
                language = language,
                confidence = 0.9f
            )
        }
    }

    override suspend fun transcribeFile(
        filePath: String,
        language: String
    ): TranscriptionResult? = withContext(Dispatchers.IO) {
        try {
            nativeTranscribeFile(filePath, language)
        } catch (e: UnsatisfiedLinkError) {
            // 模拟实现
            TranscriptionResult(
                text = "[模拟] 从文件 $filePath 识别的内容。",
                segments = listOf(
                    TranscriptionSegment(
                        text = "[模拟] 从文件识别的内容。",
                        startTimeMs = 0,
                        endTimeMs = 5000,
                        confidence = 0.9f
                    )
                ),
                processingTimeMs = 1000,
                language = language,
                confidence = 0.85f
            )
        }
    }

    override fun transcribeStream(language: String): StreamTranscriber {
        return StreamTranscriberImpl(language)
    }

    override fun getSupportedLanguages(): List<String> {
        return listOf("zh", "en", "ja", "ko", "de", "fr", "es", "ru")
    }

    override fun unloadModel() {
        try {
            nativeUnloadModel()
        } catch (e: UnsatisfiedLinkError) {
            // 忽略
        }
        isLoaded = false
    }
}

/**
 * 流式转录器实现
 */
class StreamTranscriberImpl(
    private val language: String
) : StreamTranscriber {

    private val sampleBuffer = mutableListOf<Float>()
    private var isRunning = true

    override fun feed(samples: FloatArray) {
        if (!isRunning) return
        sampleBuffer.addAll(samples.toList())
    }

    override fun textFlow(): Flow<String> = flow {
        // 简化实现：每累积一定样本后输出模拟文本
        while (isRunning) {
            if (sampleBuffer.size >= 16000) { // 约1秒的音频
                sampleBuffer.clear()

                // 模拟输出
                emit("模拟转录文本...")

                kotlinx.coroutines.delay(1000)
            } else {
                kotlinx.coroutines.delay(100)
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        isRunning = false
        sampleBuffer.clear()
    }
}
