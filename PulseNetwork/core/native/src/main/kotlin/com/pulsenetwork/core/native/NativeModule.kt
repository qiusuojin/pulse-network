package com.pulsenetwork.core.native

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Native 模块依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object NativeModule {

    @Provides
    @Singleton
    fun provideLLMInference(): LLMInference {
        return LLMInferenceImpl()
    }

    @Provides
    @Singleton
    fun provideSpeechRecognition(): SpeechRecognition {
        return SpeechRecognitionImpl()
    }
}
