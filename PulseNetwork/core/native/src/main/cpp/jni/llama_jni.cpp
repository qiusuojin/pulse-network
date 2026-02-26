#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#define LOG_TAG "PulseNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// llama.cpp 前向声明
struct llama_model;
struct llama_context;

// 全局状态
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static bool g_is_generating = false;

/**
 * 加载模型
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeLoadModel(
        JNIEnv* env,
        jobject thiz,
        jstring model_path,
        jint context_length,
        jint threads) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);

    LOGI("Loading model from: %s", path);
    LOGI("Context length: %d, Threads: %d", context_length, threads);

    // TODO: 实际调用 llama.cpp 加载模型
    // auto params = llama_context_default_params();
    // params.n_ctx = context_length;
    // params.n_threads = threads;
    // g_model = llama_load_model_from_file(path, params);
    // g_ctx = llama_new_context_with_model(g_model, params);

    env->ReleaseStringUTFChars(model_path, path);

    // 暂时返回 true（模拟）
    return JNI_TRUE;
}

/**
 * 检查模型是否已加载
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeIsModelLoaded(
        JNIEnv* env,
        jobject thiz) {
    return g_model != nullptr ? JNI_TRUE : JNI_FALSE;
}

/**
 * 生成文本（阻塞式）
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeGenerate(
        JNIEnv* env,
        jobject thiz,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k) {

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

    LOGI("Generating text, max_tokens=%d, temp=%.2f", max_tokens, temperature);

    // TODO: 实际调用 llama.cpp 生成文本
    // std::string result = llama_generate(g_ctx, prompt_str, max_tokens, temperature, top_p, top_k);

    std::string result = "[JNI Bridge] 模型未实际加载，这是占位响应。";

    env->ReleaseStringUTFChars(prompt, prompt_str);

    return env->NewStringUTF(result.c_str());
}

/**
 * 生成文本（流式）
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeGenerateStream(
        JNIEnv* env,
        jobject thiz,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k) {

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

    LOGI("Generating text (stream), max_tokens=%d", max_tokens);

    // TODO: 实际流式生成
    // 返回 token 数组

    // 模拟返回
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(5, stringClass, nullptr);

    env->SetObjectArrayElement(result, 0, env->NewStringUTF("这是"));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF("模拟"));
    env->SetObjectArrayElement(result, 2, env->NewStringUTF("的"));
    env->SetObjectArrayElement(result, 3, env->NewStringUTF("流式"));
    env->SetObjectArrayElement(result, 4, env->NewStringUTF("响应。"));

    env->ReleaseStringUTFChars(prompt, prompt_str);

    return result;
}

/**
 * 停止生成
 */
extern "C" JNIEXPORT void JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeStopGeneration(
        JNIEnv* env,
        jobject thiz) {
    g_is_generating = false;
    LOGI("Generation stopped");
}

/**
 * 获取文本嵌入向量
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeGetEmbedding(
        JNIEnv* env,
        jobject thiz,
        jstring text) {

    const char* text_str = env->GetStringUTFChars(text, nullptr);

    // TODO: 实际获取嵌入向量
    // float* embedding = llama_get_embedding(g_ctx);

    // 返回 384 维模拟向量
    const int dimension = 384;
    jfloatArray result = env->NewFloatArray(dimension);

    float* data = new float[dimension];
    for (int i = 0; i < dimension; i++) {
        data[i] = (float)(rand() % 200 - 100) / 100.0f;
    }

    env->SetFloatArrayRegion(result, 0, dimension, data);
    delete[] data;

    env->ReleaseStringUTFChars(text, text_str);

    return result;
}

/**
 * 获取模型信息
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeGetModelInfo(
        JNIEnv* env,
        jobject thiz) {

    // TODO: 返回实际模型信息

    jclass infoClass = env->FindClass("com/pulsenetwork/core/native/ModelInfo");
    jmethodID constructor = env->GetMethodID(infoClass, "<init>",
        "(Ljava/lang/String;JIILjava/lang/String;J)V");

    return env->NewObject(infoClass, constructor,
        env->NewStringUTF("Unknown Model"),
        0LL,      // parameterCount
        2048,     // contextLength
        384,      // embeddingSize
        env->NewStringUTF("Unknown"),
        0LL       // fileSizeMB
    );
}

/**
 * 卸载模型
 */
extern "C" JNIEXPORT void JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeUnloadModel(
        JNIEnv* env,
        jobject thiz) {

    // TODO: 实际释放模型
    // if (g_ctx) llama_free(g_ctx);
    // if (g_model) llama_free_model(g_model);

    g_ctx = nullptr;
    g_model = nullptr;

    LOGI("Model unloaded");
}

/**
 * 获取可用内存
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_pulsenetwork_core_native_LLMInferenceImpl_nativeGetAvailableMemory(
        JNIEnv* env,
        jobject thiz) {

    // Android 内存信息
    FILE* fp = fopen("/proc/meminfo", "r");
    if (!fp) return 0;

    long availableKB = 0;
    char line[256];

    while (fgets(line, sizeof(line), fp)) {
        if (sscanf(line, "MemAvailable: %ld kB", &availableKB) == 1) {
            break;
        }
    }

    fclose(fp);
    return availableKB / 1024; // 转换为 MB
}
