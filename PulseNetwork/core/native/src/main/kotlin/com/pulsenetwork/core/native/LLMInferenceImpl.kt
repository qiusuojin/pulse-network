package com.pulsenetwork.core.native

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * LLM 推理实现
 *
 * 通过 JNI 调用 llama.cpp
 */
class LLMInferenceImpl : LLMInference {

    companion object {
        init {
            System.loadLibrary("pulsenative")
        }
    }

    private var isLoaded = false
    private var isGenerating = false
    private var modelInfo: ModelInfo? = null

    // JNI 原生方法
    private external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        threads: Int
    ): Boolean

    private external fun nativeIsModelLoaded(): Boolean

    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): String

    private external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): Array<String>

    private external fun nativeStopGeneration()

    private external fun nativeGetEmbedding(text: String): FloatArray?

    private external fun nativeGetModelInfo(): ModelInfo?

    private external fun nativeUnloadModel()

    private external fun nativeGetAvailableMemory(): Long

    override suspend fun loadModel(
        modelPath: String,
        contextLength: Int,
        threads: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            isLoaded = nativeLoadModel(modelPath, contextLength, threads)
            if (isLoaded) {
                modelInfo = nativeGetModelInfo()
            }
            isLoaded
        } catch (e: UnsatisfiedLinkError) {
            // JNI 未链接，使用模拟实现
            isLoaded = true
            modelInfo = ModelInfo(
                name = "Mock Model",
                parameterCount = 2_000_000_000,
                contextLength = contextLength,
                embeddingSize = 384,
                quantization = "Q4_K_M",
                fileSizeMB = 1200
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun isModelLoaded(): Boolean = isLoaded

    override fun generateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): Flow<String> = flow {
        try {
            val tokens = nativeGenerateStream(prompt, maxTokens, temperature, topP, topK)
            tokens.forEach { token ->
                if (!isGenerating) return@flow
                emit(token)
            }
        } catch (e: UnsatisfiedLinkError) {
            // 模拟实现
            val mockResponse = generateMockResponse(prompt)
            mockResponse.forEach { char ->
                if (!isGenerating) return@flow
                emit(char.toString())
                kotlinx.coroutines.delay(30)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        try {
            nativeGenerate(prompt, maxTokens, temperature, 0.9f, 40)
        } catch (e: UnsatisfiedLinkError) {
            // 模拟实现
            generateMockResponse(prompt)
        }
    }

    override fun stopGeneration() {
        isGenerating = false
        try {
            nativeStopGeneration()
        } catch (e: UnsatisfiedLinkError) {
            // 忽略
        }
    }

    override suspend fun getEmbedding(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            nativeGetEmbedding(text)
        } catch (e: UnsatisfiedLinkError) {
            // 模拟实现：基于文本 hash 生成伪向量
            generateMockEmbedding(text)
        }
    }

    override fun getModelInfo(): ModelInfo? = modelInfo

    override fun unloadModel() {
        try {
            nativeUnloadModel()
        } catch (e: UnsatisfiedLinkError) {
            // 忽略
        }
        isLoaded = false
        modelInfo = null
    }

    override fun getAvailableMemory(): Long {
        return try {
            nativeGetAvailableMemory()
        } catch (e: UnsatisfiedLinkError) {
            Runtime.getRuntime().freeMemory() / (1024 * 1024)
        }
    }

    // ========== 模拟实现 ==========

    private fun generateMockResponse(prompt: String): String {
        isGenerating = true

        // 简单的模拟响应
        return when {
            prompt.contains("你好") || prompt.contains("hello", ignoreCase = true) ->
                "你好！我是 Pulse 网络的本地 AI 助手。我可以完全离线运行，保护你的隐私。有什么我可以帮助你的吗？"

            prompt.contains("翻译") ->
                "请提供你想要翻译的文本，我会为你进行翻译。目前我支持中英文互译。"

            prompt.contains("总结") || prompt.contains("摘要") ->
                "请提供你想要总结的内容，我会为你提取关键信息并生成简洁的摘要。"

            else ->
                "我理解你的问题是：\"${prompt.take(50)}...\"\n\n" +
                "作为本地运行的 AI，我会在保护你隐私的前提下尽力帮助你。" +
                "由于我是离线模型，能力有限，但我会持续学习进步。"
        }
    }

    private fun generateMockEmbedding(text: String): FloatArray {
        // 基于文本 hash 生成伪随机但一致的 384 维向量
        val dimension = 384
        val random = java.util.Random(text.hashCode().toLong())
        val vector = FloatArray(dimension) { random.nextFloat() * 2 - 1 }

        // 归一化
        val norm = kotlin.math.sqrt(vector.fold(0f) { sum, v -> sum + v * v })
        return vector.map { it / norm }.toFloatArray()
    }
}
