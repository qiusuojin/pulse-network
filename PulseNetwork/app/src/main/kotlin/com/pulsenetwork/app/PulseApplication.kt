package com.pulsenetwork.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Pulse Network Application
 *
 * 核心定位：用千万台手机，挑战一座数据中心
 */
@HiltAndroidApp
class PulseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化将在后续实现
    }
}
