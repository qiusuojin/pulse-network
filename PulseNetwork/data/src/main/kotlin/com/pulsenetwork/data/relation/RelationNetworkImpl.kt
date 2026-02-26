package com.pulsenetwork.data.relation

import com.pulsenetwork.domain.relation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系网络层实现
 *
 * 实现赫布学习算法:
 * - 长时程增强 (LTP): 成功协作时增强连接
 * - 长时程抑制 (LTD): 失败协作时减弱连接
 * - 时间衰减: 长时间不互动的连接会逐渐衰减
 */
@Singleton
class RelationNetworkImpl @Inject constructor() : RelationNetwork {

    // 节点关系存储
    private val relations = ConcurrentHashMap<String, NodeRelation>()

    // 任务类型专家库：记录每个节点在各类任务上的表现
    private val taskTypeExpertise = ConcurrentHashMap<String, ConcurrentHashMap<String, Float>>()

    // 状态流
    private val stateFlow = MutableStateFlow(RelationNetworkState.EMPTY)

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 最近协作记录（用于统计）
    private val recentCollaborations = mutableListOf<CollaborationEvent>()
    private val recentCollaborationsLock = Any()

    init {
        // 启动定期衰减任务
        scope.launch {
            while (true) {
                delay(HebbianParams.DECAY_PERIOD_HOURS * 60 * 60 * 1000L / 10) // 更频繁地检查
                performDecay()
            }
        }
    }

    override suspend fun recordCollaboration(event: CollaborationEvent) {
        val existingRelation = relations[event.nodeId]

        // 计算强度变化
        val strengthDelta = calculateStrengthDelta(event)

        // 更新或创建关系
        val updatedRelation = if (existingRelation != null) {
            updateExistingRelation(existingRelation, event, strengthDelta)
        } else {
            createNewRelation(event, strengthDelta)
        }

        relations[event.nodeId] = updatedRelation

        // 更新任务类型专家度
        updateTaskTypeExpertise(event.nodeId, event.taskType, event.success, event.qualityScore)

        // 记录到最近协作
        synchronized(recentCollaborationsLock) {
            recentCollaborations.add(event)
            // 只保留最近1000条
            if (recentCollaborations.size > 1000) {
                recentCollaborations.removeAt(0)
            }
        }

        // 更新状态流
        updateState()
    }

    override fun getConnectionStrength(nodeId: String): Float {
        return relations[nodeId]?.connectionStrength ?: 0f
    }

    override fun getRecommendedNodes(taskType: String, limit: Int): List<NodeRelation> {
        return relations.values
            .filter { it.connectionStrength >= HebbianParams.MIN_STRENGTH }
            .sortedWith(compareByDescending<NodeRelation> {
                // 综合考虑连接强度和任务类型专家度
                val expertise = it.taskTypeExpertise[taskType] ?: 0f
                it.connectionStrength * 0.5f + expertise * 0.3f + it.successRate() * 0.2f
            })
            .take(limit)
    }

    override suspend fun updateConnectionStrength(nodeId: String, deltaStrength: Float) {
        val existing = relations[nodeId] ?: return
        val newStrength = (existing.connectionStrength + deltaStrength)
            .coerceIn(0f, HebbianParams.MAX_STRENGTH)

        relations[nodeId] = existing.copy(connectionStrength = newStrength)
        updateState()
    }

    override suspend fun recordObservation(observation: MirrorObservation) {
        val existing = relations[observation.observedNodeId] ?: return

        // 根据观察结果调整
        val learningBoost = when (observation.outcome) {
            com.pulsenetwork.domain.relation.ObservationOutcome.SUCCESS -> 0.02f
            com.pulsenetwork.domain.relation.ObservationOutcome.OPTIMAL -> 0.05f
            com.pulsenetwork.domain.relation.ObservationOutcome.FAILURE -> -0.01f
            com.pulsenetwork.domain.relation.ObservationOutcome.NEUTRAL -> 0f
        }

        val adjustedStrength = (existing.connectionStrength + learningBoost * observation.learningValue)
            .coerceIn(0f, HebbianParams.MAX_STRENGTH)

        relations[observation.observedNodeId] = existing.copy(
            connectionStrength = adjustedStrength,
            mirrorObservations = existing.mirrorObservations + 1
        )

        updateState()
    }

    override fun relationStateFlow(): Flow<RelationNetworkState> = stateFlow.asStateFlow()

    override fun getAllRelations(): List<NodeRelation> = relations.values.toList()

    override fun getRelation(nodeId: String): NodeRelation? = relations[nodeId]

    override fun getTrustedNodes(): List<NodeRelation> {
        return relations.values.filter {
            it.trustLevel == com.pulsenetwork.domain.relation.TrustLevel.TRUSTED ||
            it.trustLevel == com.pulsenetwork.domain.relation.TrustLevel.INTIMATE
        }
    }

    override suspend fun performDecay() {
        val now = System.currentTimeMillis()
        val decayThreshold = HebbianParams.DECAY_PERIOD_HOURS * 60 * 60 * 1000L

        val toRemove = mutableListOf<String>()

        relations.forEach { (nodeId, relation) ->
            val timeSinceLastCollab = now - relation.lastCollaborationAt

            if (timeSinceLastCollab > decayThreshold) {
                // 应用衰减
                val decayCycles = timeSinceLastCollab / decayThreshold
                val decayFactor = kotlin.math.pow(1.0 - HebbianParams.DECAY_RATE, decayCycles.toDouble()).toFloat()
                val newStrength = relation.connectionStrength * decayFactor

                if (newStrength < HebbianParams.MIN_STRENGTH) {
                    toRemove.add(nodeId)
                } else {
                    relations[nodeId] = relation.copy(connectionStrength = newStrength)
                }
            }
        }

        // 移除过弱的关系
        toRemove.forEach { relations.remove(it) }

        if (toRemove.isNotEmpty()) {
            updateState()
        }
    }

    // ========== 私有方法 ==========

    private fun calculateStrengthDelta(event: CollaborationEvent): Float {
        val baseDelta = if (event.success) {
            HebbianParams.LTP_RATE
        } else {
            -HebbianParams.LTD_RATE
        }

        // 根据质量评分调整
        val qualityModifier = (event.qualityScore - 0.5f) * 2 * HebbianParams.QUALITY_WEIGHT

        // 根据响应时间调整（越快越好）
        val responseTimeModifier = calculateResponseTimeModifier(event.responseTimeMs)

        return baseDelta * (1 + qualityModifier + responseTimeModifier)
    }

    private fun calculateResponseTimeModifier(responseTimeMs: Long): Float {
        // 响应时间越短，奖励越高
        return when {
            responseTimeMs < 100 -> 0.2f
            responseTimeMs < 500 -> 0.1f
            responseTimeMs < 1000 -> 0f
            responseTimeMs < 5000 -> -0.1f
            else -> -0.2f
        } * HebbianParams.RESPONSE_TIME_WEIGHT
    }

    private fun updateExistingRelation(
        existing: NodeRelation,
        event: CollaborationEvent,
        strengthDelta: Float
    ): NodeRelation {
        val newStrength = (existing.connectionStrength + strengthDelta)
            .coerceIn(0f, HebbianParams.MAX_STRENGTH)

        val newCollaborationCount = existing.collaborationCount + 1
        val newSuccessCount = existing.successCount + (if (event.success) 1 else 0)
        val newFailureCount = existing.failureCount + (if (!event.success) 1 else 0)

        // 更新平均质量
        val newAverageQuality = (existing.averageQuality * existing.collaborationCount + event.qualityScore) /
            newCollaborationCount

        // 更新平均响应时间
        val newAverageResponseTime = (existing.averageResponseTimeMs * existing.collaborationCount +
            event.responseTimeMs) / newCollaborationCount

        // 更新任务类型专家度
        val newTaskTypeExpertise = existing.taskTypeExpertise.toMutableMap()
        val currentExpertise = newTaskTypeExpertise[event.taskType] ?: 0f
        val expertiseDelta = if (event.success) 0.05f else -0.02f
        newTaskTypeExpertise[event.taskType] = (currentExpertise + expertiseDelta).coerceIn(0f, 1f)

        // 更新信任等级
        val newTrustLevel = TrustLevel.fromCollaborationCount(newCollaborationCount)

        return existing.copy(
            connectionStrength = newStrength,
            collaborationCount = newCollaborationCount,
            successCount = newSuccessCount,
            failureCount = newFailureCount,
            trustLevel = newTrustLevel,
            lastCollaborationAt = event.timestamp,
            averageQuality = newAverageQuality,
            averageResponseTimeMs = newAverageResponseTime,
            taskTypeExpertise = newTaskTypeExpertise
        )
    }

    private fun createNewRelation(event: CollaborationEvent, strengthDelta: Float): NodeRelation {
        val initialStrength = (0.1f + strengthDelta).coerceIn(0f, HebbianParams.MAX_STRENGTH)

        return NodeRelation(
            nodeId = event.nodeId,
            connectionStrength = initialStrength,
            collaborationCount = 1,
            successCount = if (event.success) 1 else 0,
            failureCount = if (!event.success) 1 else 0,
            trustLevel = TrustLevel.NOVICE,
            firstContactAt = event.timestamp,
            lastCollaborationAt = event.timestamp,
            averageQuality = event.qualityScore,
            averageResponseTimeMs = event.responseTimeMs,
            taskTypeExpertise = mapOf(event.taskType to if (event.success) 0.1f else 0f),
            mirrorObservations = 0
        )
    }

    private fun updateTaskTypeExpertise(
        nodeId: String,
        taskType: String,
        success: Boolean,
        qualityScore: Float
    ) {
        val nodeExpertise = taskTypeExpertise.getOrPut(nodeId) { ConcurrentHashMap() }
        val currentExpertise = nodeExpertise[taskType] ?: 0f
        val delta = if (success) qualityScore * 0.1f else -0.05f
        nodeExpertise[taskType] = (currentExpertise + delta).coerceIn(0f, 1f)
    }

    private fun updateState() {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000

        // 计算最近协作次数
        val recentCount = synchronized(recentCollaborationsLock) {
            recentCollaborations.count { it.timestamp > dayAgo }
        }

        // 计算信任等级分布
        val trustDistribution = relations.values.groupingBy { it.trustLevel }.eachCount()

        // 找出最常见的任务类型
        val taskTypes = synchronized(recentCollaborationsLock) {
            recentCollaborations
                .filter { it.timestamp > dayAgo }
                .groupingBy { it.taskType }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
        }

        // 计算网络成熟度
        val maturity = calculateNetworkMaturity()

        val state = RelationNetworkState(
            totalRelations = relations.size,
            averageStrength = if (relations.isEmpty()) 0f else relations.values.map { it.connectionStrength }.average().toFloat(),
            trustDistribution = trustDistribution,
            strongestRelation = relations.values.maxByOrNull { it.connectionStrength },
            recentCollaborations = recentCount,
            topTaskTypes = taskTypes,
            networkMaturity = maturity
        )

        stateFlow.value = state
    }

    private fun calculateNetworkMaturity(): Float {
        if (relations.isEmpty()) return 0f

        // 成熟度基于：
        // 1. 关系数量（越多越成熟）
        val quantityScore = kotlin.math.min(relations.size / 20f, 1f)

        // 2. 平均信任等级
        val avgTrustWeight = relations.values.map { it.trustLevel.weight }.average().toFloat()

        // 3. 平均连接强度
        val avgStrength = relations.values.map { it.connectionStrength }.average().toFloat()

        return (quantityScore * 0.3f + avgTrustWeight * 0.4f + avgStrength * 0.3f)
    }
}
