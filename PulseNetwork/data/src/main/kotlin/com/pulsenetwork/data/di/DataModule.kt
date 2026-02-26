package com.pulsenetwork.data.di

import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.view.WindowManager
import com.pulsenetwork.data.evolution.NodeEvolutionImpl
import com.pulsenetwork.data.governor.GovernorServiceImpl
import com.pulsenetwork.data.prediction.PredictionEngineImpl
import com.pulsenetwork.data.relation.RelationNetworkImpl
import com.pulsenetwork.data.swarm.SwarmNetworkImpl
import com.pulsenetwork.data.workflow.WorkflowInterviewerImpl
import com.pulsenetwork.domain.evolution.NodeEvolution
import com.pulsenetwork.domain.governor.Governor
import com.pulsenetwork.domain.prediction.PredictionEngine
import com.pulsenetwork.domain.relation.RelationNetwork
import com.pulsenetwork.domain.swarm.SwarmNetwork
import com.pulsenetwork.domain.workflow.WorkflowInterviewer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideGovernor(
        @ApplicationContext context: Context,
        batteryManager: BatteryManager,
        powerManager: PowerManager,
        windowManager: WindowManager,
        wifiManager: WifiManager
    ): Governor {
        return GovernorServiceImpl(context, batteryManager, powerManager, windowManager, wifiManager)
    }

    @Provides
    @Singleton
    fun provideSwarmNetwork(
        @ApplicationContext context: Context
    ): SwarmNetwork {
        return SwarmNetworkImpl(context)
    }

    @Provides
    @Singleton
    fun provideWorkflowInterviewer(): WorkflowInterviewer {
        return WorkflowInterviewerImpl()
    }

    @Provides
    @Singleton
    fun provideBatteryManager(
        @ApplicationContext context: Context
    ): BatteryManager {
        return context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    @Provides
    @Singleton
    fun providePowerManager(
        @ApplicationContext context: Context
    ): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Provides
    @Singleton
    fun provideWindowManager(
        @ApplicationContext context: Context
    ): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Provides
    @Singleton
    fun provideWifiManager(
        @ApplicationContext context: Context
    ): WifiManager {
        return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // ========== v0.2 新增模块 ==========

    /**
     * 提供关系网络层
     * 基于赫布学习的节点关系管理
     */
    @Provides
    @Singleton
    fun provideRelationNetwork(): RelationNetwork {
        return RelationNetworkImpl()
    }

    /**
     * 提供预测引擎层
     * 基于预测编码的智能预测系统
     */
    @Provides
    @Singleton
    fun providePredictionEngine(): PredictionEngine {
        return PredictionEngineImpl()
    }

    /**
     * 提供节点进化系统
     * 基于免疫记忆的节点成长系统
     */
    @Provides
    @Singleton
    fun provideNodeEvolution(): NodeEvolution {
        return NodeEvolutionImpl()
    }
}
