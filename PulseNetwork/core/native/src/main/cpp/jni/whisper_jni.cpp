#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "PulseNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// whisper.cpp 前向声明
struct whisper_context;

// 全局状态
static whisper_context* g_whisper_ctx = nullptr;

/**
 * 加载模型
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsenetwork_core_native_SpeechRecognitionImpl_nativeLoadModel(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);

    LOGI("Loading whisper model from: %s", path);

    // TODO: 实际调用 whisper.cpp 加载模型
    // g_whisper_ctx = whisper_init_from_file(path);

    env->ReleaseStringUTFChars(model_path, path);

    return JNI_TRUE;
}

/**
 * 检查模型是否已加载
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsenetwork_core_native_SpeechRecognitionImpl_nativeIsModelLoaded(
        JNIEnv* env,
        jobject thiz) {
    return g_whisper_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

/**
 * 转录音频
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_pulsenetwork_core_native_SpeechRecognitionImpl_nativeTranscribe(
        JNIEnv* env,
        jobject thiz,
        jfloatArray samples,
        jstring language) {

    jsize sample_count = env->GetArrayLength(samples);
    jfloat* sample_data = env->GetFloatArrayElements(samples, nullptr);

    const char* lang = env->GetStringUTFChars(language, nullptr);

    LOGI("Transcribing %d samples, language: %s", sample_count, lang);

    // TODO: 实际调用 whisper.cpp 转录
    // whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    // params.language = lang;
    // whisper_full(g_whisper_ctx, params, sample_data, sample_count);

    env->ReleaseFloatArrayElements(samples, sample_data, 0);
    env->ReleaseStringUTFChars(language, lang);

    // 返回模拟结果
    jclass resultClass = env->FindClass("com/pulsenetwork/core/native/TranscriptionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>",
        "(Ljava/util/List;JLjava/lang/String;F)V");

    // 创建 segments 列表
    jclass segmentClass = env->FindClass("com/pulsenetwork/core/native/TranscriptionSegment");
    jclass listClass = env->FindClass("java/util/ArrayList");
    jobject segmentList = env->NewObject(listClass, env->GetMethodID(listClass, "<init>", "()V"));

    // 创建单个 segment
    jmethodID segmentConstructor = env->GetMethodID(segmentClass, "<init>",
        "(Ljava/lang/String;JJF)V");
    jobject segment = env->NewObject(segmentClass, segmentConstructor,
        env->NewStringUTF("[JNI] 模拟转录结果"),
        0LL,      // startTimeMs
        3000LL,   // endTimeMs
        0.95f     // confidence
    );

    jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    env->CallBooleanMethod(segmentList, addMethod, segment);

    return env->NewObject(resultClass, constructor,
        env->NewStringUTF("[JNI] 模拟转录文本"),
        segmentList,
        500LL,     // processingTimeMs
        env->NewStringUTF(lang),
        0.9f       // confidence
    );
}

/**
 * 转录音频文件
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_pulsenetwork_core_native_SpeechRecognitionImpl_nativeTranscribeFile(
        JNIEnv* env,
        jobject thiz,
        jstring file_path,
        jstring language) {

    const char* path = env->GetStringUTFChars(file_path, nullptr);
    const char* lang = env->GetStringUTFChars(language, nullptr);

    LOGI("Transcribing file: %s", path);

    // TODO: 实际读取文件并转录

    env->ReleaseStringUTFChars(file_path, path);
    env->ReleaseStringUTFChars(language, lang);

    // 返回 null 表示未实现
    return nullptr;
}

/**
 * 卸载模型
 */
extern "C" JNIEXPORT void JNICALL
Java_com_pulsenetwork_core_native_SpeechRecognitionImpl_nativeUnloadModel(
        JNIEnv* env,
        jobject thiz) {

    // TODO: 实际释放模型
    // if (g_whisper_ctx) whisper_free(g_whisper_ctx);

    g_whisper_ctx = nullptr;

    LOGI("Whisper model unloaded");
}
