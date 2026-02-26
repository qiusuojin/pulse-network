package com.pulsenetwork.app.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.domain.evolution.*
import com.pulsenetwork.domain.prediction.*
import com.pulsenetwork.domain.relation.*
import com.pulsenetwork.domain.swarm.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 仪表盘 ViewModel
 *
 * 整合所有 v0.2 模块的状态，提供统一的仪表盘视图
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val swarmNetwork: SwarmNetwork,
    private val relationNetwork: RelationNetwork,
    private val predictionEngine: PredictionEngine,
    private val nodeEvolution: NodeEvolution
) : ViewModel() {

    // 网络状态
    private val _networkState = MutableLiveData<NetworkDashboardState>()
    val networkState: LiveData<NetworkDashboardState> = _networkState

    // 关系网络状态
    private val _relationState = MutableLiveData<RelationDashboardState>()
    val relationState: LiveData<RelationDashboardState> = _relationState

    // 节点进化状态
    private val _evolutionState = MutableLiveData<EvolutionDashboardState>()
    val evolutionState: LiveData<EvolutionDashboardState> = _evolutionState

    // 预测状态
    private val _predictionState = MutableLiveData<PredictionDashboardState>()
    val predictionState: LiveData<PredictionDashboardState> = _predictionState

    // 整体仪表盘状态
    private val _dashboardState = MutableLiveData<DashboardState>()
    val dashboardState: LiveData<DashboardState> = _dashboardState

    // 错误信息
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // 本地节点ID
    private var localNodeId: String = "local-${System.currentTimeMillis() % 10000}"

    init {
        loadInitialState()
        observeStates()
    }

    private fun loadInitialState() {
        // 加载初始状态
        updateNetworkState()
        updateRelationState()
        updateEvolutionState()
        updatePredictionState()
        updateDashboardState()
    }

    private fun observeStates() {
        // 观察关系网络状态
        viewModelScope.launch {
            relationNetwork.relationStateFlow().collectLatest { state ->
                _relationState.value = RelationDashboardState(
                    totalRelations = state.totalRelations,
                    averageStrength = state.averageStrength,
                    trustedNodes = state.trustDistribution[TrustLevel.TRUSTED] ?: 0 +
                                   state.trustDistribution[TrustLevel.INTIMATE] ?: 0,
                    networkMaturity = state.networkMaturity,
                    recentCollaborations = state.recentCollaborations
                )
                updateDashboardState()
            }
        }

        // 观察进化状态
        viewModelScope.launch {
            nodeEvolution.evolutionStateFlow().collectLatest { state ->
                _evolutionState.value = EvolutionDashboardState(
                    currentLevel = state.level,
                    totalExperience = state.progress.totalExperience,
                    levelProgress = state.progress.progressPercent,
                    specializations = state.specializations.take(3),
                    vaccineCount = state.vaccineLibraryStats.totalEntries,
                    tasksCompleted = state.totalTasksCompleted,
                    successRate = state.averageSuccessRate
                )
                updateDashboardState()
            }
        }

        // 观察预测状态
        viewModelScope.launch {
            predictionEngine.predictionStateFlow().collectLatest { state ->
                _predictionState.value = PredictionDashboardState(
                    accuracy = state.accuracy,
                    recentPredictions = state.recentPredictions,
                    explorationRatio = state.criticalityState.explorationRatio,
                    currentPhase = state.criticalityState.currentPhase,
                    topPredictedNeeds = state.topPredictedNeeds.take(3)
                )
                updateDashboardState()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateNetworkState()
            updateRelationState()
            updateEvolutionState()
            updatePredictionState()
            updateDashboardState()
        }
    }

    fun startNetwork() {
        viewModelScope.launch {
            when (val result = swarmNetwork.start(localNodeId, 37373)) {
                is SwarmStartResult.Success -> {
                    updateNetworkState()
                }
                is SwarmStartResult.Failure -> {
                    _error.value = "网络启动失败: ${result.error}"
                }
            }
        }
    }

    fun stopNetwork() {
        viewModelScope.launch {
            swarmNetwork.stop()
            updateNetworkState()
        }
    }

    private fun updateNetworkState() {
        val stats = swarmNetwork.getStats()
        val nodes = swarmNetwork.getDiscoveredNodes()

        _networkState.value = NetworkDashboardState(
            isRunning = swarmNetwork.isRunning,
            discoveredNodes = stats.discoveredNodes,
            connectedNodes = stats.connectedNodes,
            cacheHits = stats.cacheHits,
            cacheMisses = stats.cacheMisses,
            averageLatency = stats.averageLatencyMs,
            uptime = stats.uptimeMs,
            nodes = nodes.take(5)
        )
    }

    private fun updateRelationState() {
        val relations = relationNetwork.getAllRelations()
        val trustedCount = relationNetwork.getTrustedNodes().size
        val state = com.pulsenetwork.domain.relation.RelationNetworkState.EMPTY

        _relationState.value = RelationDashboardState(
            totalRelations = relations.size,
            averageStrength = if (relations.isNotEmpty()) {
                relations.map { it.connectionStrength }.average().toFloat()
            } else 0f,
            trustedNodes = trustedCount,
            networkMaturity = 0.5f, // 从relationStateFlow获取
            recentCollaborations = 0
        )
    }

    private fun updateEvolutionState() {
        val level = nodeEvolution.getCurrentLevel()
        val progress = nodeEvolution.getGrowthProgress()
        val specializations = nodeEvolution.getSpecializations()
        val stats = nodeEvolution.getVaccineLibraryStats()

        _evolutionState.value = EvolutionDashboardState(
            currentLevel = level,
            totalExperience = progress.totalExperience,
            levelProgress = progress.progressPercent,
            specializations = specializations.take(3),
            vaccineCount = stats.totalEntries,
            tasksCompleted = 0,
            successRate = 0f
        )
    }

    private fun updatePredictionState() {
        val accuracy = predictionEngine.getAccuracy()
        val criticality = predictionEngine.getCriticalityState()

        _predictionState.value = PredictionDashboardState(
            accuracy = accuracy,
            recentPredictions = 0,
            explorationRatio = criticality.explorationRatio,
            currentPhase = criticality.currentPhase,
            topPredictedNeeds = emptyList()
        )
    }

    private fun updateDashboardState() {
        val network = _networkState.value
        val relation = _relationState.value
        val evolution = _evolutionState.value
        val prediction = _predictionState.value

        _dashboardState.value = DashboardState(
            networkHealth = calculateNetworkHealth(network, relation),
            nodeMaturity = evolution?.levelProgress ?: 0f,
            predictionAccuracy = prediction?.accuracy ?: 0.5f,
            overallScore = calculateOverallScore(network, relation, evolution, prediction),
            statusMessage = generateStatusMessage(network, evolution)
        )
    }

    private fun calculateNetworkHealth(
        network: NetworkDashboardState?,
        relation: RelationDashboardState?
    ): Float {
        if (network == null) return 0f

        val connectionScore = if (network.discoveredNodes > 0) {
            network.connectedNodes.toFloat() / network.discoveredNodes.toFloat()
        } else 0f

        val relationScore = relation?.averageStrength ?: 0f

        return (connectionScore * 0.6f + relationScore * 0.4f)
    }

    private fun calculateOverallScore(
        network: NetworkDashboardState?,
        relation: RelationDashboardState?,
        evolution: EvolutionDashboardState?,
        prediction: PredictionDashboardState?
    ): Float {
        var score = 0f
        var count = 0

        network?.let {
            score += if (it.isRunning) 1f else 0f
            count++
        }

        relation?.let {
            score += it.networkMaturity
            count++
        }

        evolution?.let {
            score += it.levelProgress / 100f
            count++
        }

        prediction?.let {
            score += it.accuracy
            count++
        }

        return if (count > 0) score / count else 0f
    }

    private fun generateStatusMessage(
        network: NetworkDashboardState?,
        evolution: EvolutionDashboardState?
    ): String {
        return when {
            network?.isRunning != true -> "网络离线"
            network.connectedNodes == 0 -> "网络已启动，正在寻找节点..."
            evolution?.currentLevel == NodeLevel.APPRENTICE -> "新手节点，积累经验中..."
            evolution?.currentLevel == NodeLevel.MASTER -> "大师级节点！"
            else -> "运行正常"
        }
    }
}

// ========== 仪表盘状态数据类 ==========

/**
 * 整体仪表盘状态
 */
data class DashboardState(
    val networkHealth: Float,        // 0-1 网络健康度
    val nodeMaturity: Float,         // 0-1 节点成熟度
    val predictionAccuracy: Float,   // 0-1 预测准确率
    val overallScore: Float,         // 0-1 总体评分
    val statusMessage: String        // 状态消息
)

/**
 * 网络仪表盘状态
 */
data class NetworkDashboardState(
    val isRunning: Boolean,
    val discoveredNodes: Int,
    val connectedNodes: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val averageLatency: Long,
    val uptime: Long,
    val nodes: List<PeerNode>
)

/**
 * 关系网络仪表盘状态
 */
data class RelationDashboardState(
    val totalRelations: Int,
    val averageStrength: Float,
    val trustedNodes: Int,
    val networkMaturity: Float,
    val recentCollaborations: Int
)

/**
 * 节点进化仪表盘状态
 */
data class EvolutionDashboardState(
    val currentLevel: NodeLevel,
    val totalExperience: Long,
    val levelProgress: Float,        // 0-100
    val specializations: List<Specialization>,
    val vaccineCount: Int,
    val tasksCompleted: Long,
    val successRate: Float
)

/**
 * 预测引擎仪表盘状态
 */
data class PredictionDashboardState(
    val accuracy: Float,
    val recentPredictions: Int,
    val explorationRatio: Float,
    val currentPhase: CriticalityPhase,
    val topPredictedNeeds: List<PredictedNeed>
)
