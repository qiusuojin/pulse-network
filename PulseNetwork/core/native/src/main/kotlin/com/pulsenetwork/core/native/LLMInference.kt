package com.pulsenetwork.core.native

/**
 * LLM 推理接口
 *
 * 通过 JNI 调用 llama.cpp 实现本地大模型推理
 */
interface LLMInference {

    /**
     * 加载模型
     * @param modelPath 模型文件路径 (GGUF 格式)
     * @param contextLength 上下文长度
     * @param threads 使用的线程数
     * @return 是否加载成功
     */
    suspend fun loadModel(
        modelPath: String,
        contextLength: Int = 2048,
        threads: Int = 4
    ): Boolean

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean

    /**
     * 生成文本（流式）
     * @param prompt 输入提示
     * @param maxTokens 最大生成 token 数
     * @param temperature 温度参数
     * @param topP Top-p 采样
     * @param topK Top-k 采样
     * @return 生成的 token 流
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): kotlinx.coroutines.flow.Flow<String>

    /**
     * 生成文本（阻塞式）
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String

    /**
     * 停止生成
     */
    fun stopGeneration()

    /**
     * 获取嵌入向量
     * @param text 输入文本
     * @return 嵌入向量
     */
    suspend fun getEmbedding(text: String): FloatArray?

    /**
     * 获取模型信息
     */
    fun getModelInfo(): ModelInfo?

    /**
     * 释放模型
     */
    fun unloadModel()

    /**
     * 获取可用内存（MB）
     */
    fun getAvailableMemory(): Long
}

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val parameterCount: Long,
    val contextLength: Int,
    val embeddingSize: Int,
    val quantization: String,
    val fileSizeMB: Long
)

/**
 * LLM 服务状态
 */
enum class LLMState {
    NOT_LOADED,
    LOADING,
    READY,
    GENERATING,
    ERROR
}
