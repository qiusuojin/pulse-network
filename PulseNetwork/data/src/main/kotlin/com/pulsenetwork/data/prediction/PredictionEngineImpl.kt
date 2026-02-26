package com.pulsenetwork.data.prediction

import com.pulsenetwork.domain.prediction.*
import com.pulsenetwork.domain.swarm.PeerNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预测引擎层实现
 *
 * 实现基于预测编码的智能预测系统:
 * - 用户行为预测
 * - 能力预热
 * - 智能调度
 * - 临界态调控（探索vs利用）
 */
@Singleton
class PredictionEngineImpl @Inject constructor() : PredictionEngine {

    // 预测历史记录
    private val predictionHistory = ConcurrentHashMap<String, PredictedNeed>()

    // 反馈记录（用于计算准确率）
    private val feedbackRecords = mutableListOf<PredictionFeedback>()
    private val feedbackLock = Any()

    // 临界态状态
    private val criticalityState = MutableStateFlow(CriticalityState.DEFAULT)

    // 预测状态流
    private val predictionStateFlow = MutableStateFlow(PredictionState.INITIAL)

    // 用户行为模式（简化版，实际应使用更复杂的模型）
    private val userPatterns = ConcurrentHashMap<String, PatternStats>()

    // 能力预热队列
    private val preheatQueue = mutableListOf<CapabilityPrediction>()
    private val preheatLock = Any()

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 当前预测ID计数器
    private var predictionCounter = 0L

    override suspend fun predictNextNeeds(context: UserContext): List<PredictedNeed> {
        val predictions = mutableListOf<PredictedNeed>()

        // 1. 基于时间上下文预测
        predictions.addAll(predictFromTimeContext(context))

        // 2. 基于会话历史预测
        predictions.addAll(predictFromSessionHistory(context))

        // 3. 基于交互模式预测
        predictions.addAll(predictFromInteractionPattern(context))

        // 4. 应用临界态调控（探索vs利用）
        val adjustedPredictions = applyCriticalityAdjustment(predictions)

        // 记录预测
        adjustedPredictions.forEach { prediction ->
            val id = "pred-${predictionCounter++}"
            predictionHistory[id] = prediction
        }

        // 更新状态
        updatePredictionState()

        return adjustedPredictions.sortedByDescending { it.probability }
    }

    override suspend fun preheatCapability(capability: CapabilityPrediction, priority: PreheatPriority) {
        synchronized(preheatLock) {
            // 根据优先级插入队列
            when (priority) {
                PreheatPriority.URGENT -> preheatQueue.add(0, capability)
                PreheatPriority.HIGH -> {
                    val insertPos = preheatQueue.indexOfFirst {
                        it.priority == PreheatPriority.LOW || it.priority == PreheatPriority.NORMAL
                    }
                    if (insertPos >= 0) preheatQueue.add(insertPos, capability)
                    else preheatQueue.add(capability)
                }
                else -> preheatQueue.add(capability)
            }

            // 限制队列大小
            while (preheatQueue.size > 100) {
                preheatQueue.removeAt(preheatQueue.size - 1)
            }
        }

        // 模拟预热过程（实际实现中会触发真实的资源加载）
        scope.launch {
            delay(calculatePreheatDelay(priority))
            // 这里可以触发实际的预热逻辑
        }
    }

    override fun findOptimalNodes(
        task: TaskPrediction,
        availableNodes: List<NodePredictionProfile>
    ): List<RankedNode> {
        if (availableNodes.isEmpty()) return emptyList()

        val criticality = criticalityState.value

        return availableNodes.map { profile ->
            val score = calculateNodeScore(task, profile, criticality)
            val reasons = determineRankReasons(task, profile)

            RankedNode(
                profile = profile,
                rank = 0, // 后面会排序后赋值
                score = score,
                reasons = reasons
            )
        }
        .sortedByDescending { it.score }
        .mapIndexed { index, rankedNode ->
            rankedNode.copy(rank = index + 1)
        }
    }

    override suspend fun getScheduleDecision(
        task: TaskPrediction,
        networkState: NetworkPredictionState
    ): ScheduleDecision {
        // 1. 检查网络状态
        if (networkState.availableNodes == 0) {
            return ScheduleDecision.ExecuteLocally(LocalReason.NO_SUITABLE_NODES)
        }

        // 2. 检查网络质量
        if (networkState.networkLatency > 5000) {
            return ScheduleDecision.ExecuteLocally(LocalReason.NETWORK_POOR)
        }

        // 3. 检查负载
        if (networkState.averageLoad > 0.9f) {
            return ScheduleDecision.Queue(
                queuePosition = 1,
                estimatedWaitTime = calculateWaitTime(networkState),
                reason = QueueReason.ALL_NODES_BUSY
            )
        }

        // 4. 根据任务复杂度和网络状态决定
        return when (task.estimatedComplexity) {
            TaskComplexity.TRIVIAL, TaskComplexity.SIMPLE -> {
                ScheduleDecision.ExecuteImmediately(
                    assignedNodes = listOf("local"),
                    estimatedCompletionTime = task.estimatedDurationMs
                )
            }
            TaskComplexity.MODERATE, TaskComplexity.COMPLEX -> {
                ScheduleDecision.ExecuteImmediately(
                    assignedNodes = selectBestNodes(task, networkState),
                    estimatedCompletionTime = task.estimatedDurationMs * 2 // 考虑网络开销
                )
            }
            TaskComplexity.HEAVY -> {
                // 繁重任务可能需要请求溢价服务
                if (task.deadline != null && task.deadline - System.currentTimeMillis() < 60000) {
                    ScheduleDecision.RequestPremium(
                        premiumLevel = 2,
                        additionalCost = 1.5f,
                        guaranteedResponseTime = 30000
                    )
                } else {
                    ScheduleDecision.Queue(
                        queuePosition = 1,
                        estimatedWaitTime = calculateWaitTime(networkState) * 2,
                        reason = QueueReason.LOAD_BALANCING
                    )
                }
            }
        }
    }

    override suspend fun updateModel(feedback: PredictionFeedback) {
        synchronized(feedbackLock) {
            feedbackRecords.add(feedback)

            // 保持记录数量在限制内
            while (feedbackRecords.size > CriticalityParams.ACCURACY_WINDOW_SIZE) {
                feedbackRecords.removeAt(0)
            }
        }

        // 更新临界态状态
        adjustCriticalityState()

        // 更新预测状态
        updatePredictionState()
    }

    override fun getCriticalityState(): CriticalityState = criticalityState.value

    override fun predictionStateFlow(): Flow<PredictionState> = predictionStateFlow.asStateFlow()

    override fun getAccuracy(): Float {
        return calculateRecentAccuracy()
    }

    override suspend fun resetModel() {
        predictionHistory.clear()
        synchronized(feedbackLock) {
            feedbackRecords.clear()
        }
        userPatterns.clear()
        synchronized(preheatLock) {
            preheatQueue.clear()
        }
        criticalityState.value = CriticalityState.DEFAULT
        predictionStateFlow.value = PredictionState.INITIAL
    }

    // ========== 私有方法 ==========

    private fun predictFromTimeContext(context: UserContext): List<PredictedNeed> {
        val predictions = mutableListOf<PredictedNeed>()
        val hour = context.timeContext.hourOfDay

        // 基于时段的简单预测规则
        when {
            hour in 9..11 -> {
                predictions.add(PredictedNeed(
                    type = "work_query",
                    probability = 0.7f,
                    estimatedTime = 5 * 60 * 1000L,
                    context = mapOf("period" to "morning"),
                    suggestedPreparation = listOf("preload_work_models"),
                    confidence = 0.6f
                ))
            }
            hour in 14..17 -> {
                predictions.add(PredictedNeed(
                    type = "complex_analysis",
                    probability = 0.6f,
                    estimatedTime = 10 * 60 * 1000L,
                    context = mapOf("period" to "afternoon"),
                    suggestedPreparation = listOf("warmup_analysis_tools"),
                    confidence = 0.5f
                ))
            }
            hour in 20..23 -> {
                predictions.add(PredictedNeed(
                    type = "casual_chat",
                    probability = 0.5f,
                    estimatedTime = 15 * 60 * 1000L,
                    context = mapOf("period" to "evening"),
                    suggestedPreparation = listOf("load_conversation_models"),
                    confidence = 0.5f
                ))
            }
        }

        return predictions
    }

    private fun predictFromSessionHistory(context: UserContext): List<PredictedNeed> {
        val predictions = mutableListOf<PredictedNeed>()

        // 基于会话中的主题预测后续需求
        context.currentSession.topics.forEach { topic ->
            val pattern = userPatterns[topic]
            if (pattern != null) {
                predictions.add(PredictedNeed(
                    type = "follow_up_$topic",
                    probability = pattern.frequency * 0.8f,
                    estimatedTime = 5 * 60 * 1000L,
                    context = mapOf("topic" to topic),
                    suggestedPreparation = pattern.suggestedActions,
                    confidence = pattern.confidence
                ))
            }
        }

        return predictions
    }

    private fun predictFromInteractionPattern(context: UserContext): List<PredictedNeed> {
        val predictions = mutableListOf<PredictedNeed>()
        val pattern = context.interactionPattern

        // 基于交互频率预测
        if (pattern.frequency > 10) { // 高频用户
            predictions.add(PredictedNeed(
                type = "quick_query",
                probability = 0.8f,
                estimatedTime = 1 * 60 * 1000L,
                context = mapOf("user_type" to "power_user"),
                suggestedPreparation = listOf("enable_fast_path"),
                confidence = 0.7f
            ))
        }

        return predictions
    }

    private fun applyCriticalityAdjustment(predictions: List<PredictedNeed>): List<PredictedNeed> {
        val state = criticalityState.value
        val explorationRatio = state.explorationRatio

        // 在探索阶段，增加一些随机/新颖的预测
        return if (kotlin.random.Random.nextFloat() < explorationRatio) {
            // 添加探索性预测
            predictions + PredictedNeed(
                type = "exploration_${System.currentTimeMillis() % 10}",
                probability = 0.3f,
                estimatedTime = 10 * 60 * 1000L,
                context = mapOf("mode" to "exploration"),
                suggestedPreparation = listOf("try_new_approach"),
                confidence = 0.3f
            )
        } else {
            predictions
        }
    }

    private fun calculateNodeScore(
        task: TaskPrediction,
        profile: NodePredictionProfile,
        criticality: CriticalityState
    ): Float {
        var score = 0f

        // 能力匹配（40%）
        val capabilityMatch = if (task.preferredCapabilities.any { cap ->
            profile.node.capabilities.supportedModelTypes.contains(cap)
        }) 1f else 0.5f
        score += capabilityMatch * 0.4f

        // 可用性（20%）
        score += profile.predictedAvailability * 0.2f

        // 负载（20%，负载越低越好）
        score += (1 - profile.predictedLoad) * 0.2f

        // 可靠性（10%）
        score += profile.historicalReliability * 0.1f

        // 专业化（10%）
        score += profile.specializationScore * 0.1f

        // 探索加成（在探索阶段给新节点加分）
        if (criticality.currentPhase == CriticalityPhase.EXPLORATION) {
            score *= (1 + kotlin.random.Random.nextFloat() * 0.2f)
        }

        return score
    }

    private fun determineRankReasons(task: TaskPrediction, profile: NodePredictionProfile): List<RankReason> {
        val reasons = mutableListOf<RankReason>()

        if (task.preferredCapabilities.any { cap ->
            profile.node.capabilities.supportedModelTypes.contains(cap)
        }) {
            reasons.add(RankReason.BEST_CAPABILITY)
        }

        if (profile.predictedLoad < 0.3f) {
            reasons.add(RankReason.LOWEST_LOAD)
        }

        if (profile.historicalReliability > 0.9f) {
            reasons.add(RankReason.HIGHEST_RELIABILITY)
        }

        if (profile.predictedResponseTimeMs < 500) {
            reasons.add(RankReason.FASTEST_RESPONSE)
        }

        if (profile.specializationScore > 0.8f) {
            reasons.add(RankReason.SPECIALIZED)
        }

        return reasons
    }

    private fun selectBestNodes(task: TaskPrediction, networkState: NetworkPredictionState): List<String> {
        // 简化实现：返回假设的最佳节点列表
        return listOf("node_1", "node_2")
    }

    private fun calculateWaitTime(networkState: NetworkPredictionState): Long {
        // 基于当前负载估算等待时间
        return (networkState.averageLoad * 60000).toLong()
    }

    private fun calculatePreheatDelay(priority: PreheatPriority): Long {
        return when (priority) {
            PreheatPriority.URGENT -> 0
            PreheatPriority.HIGH -> 100
            PreheatPriority.NORMAL -> 500
            PreheatPriority.LOW -> 2000
        }
    }

    private fun calculateRecentAccuracy(): Float {
        synchronized(feedbackLock) {
            if (feedbackRecords.isEmpty()) return 0.5f

            val recentRecords = feedbackRecords.takeLast(CriticalityParams.ACCURACY_WINDOW_SIZE)
            val hits = recentRecords.count { it.actual is ActualOutcome.Hit }
            val partialHits = recentRecords.count {
                it.actual is ActualOutcome.PartialHit && (it.actual as ActualOutcome.PartialHit).similarity > 0.7f
            }

            return (hits + partialHits * 0.5f) / recentRecords.size.toFloat()
        }
    }

    private fun adjustCriticalityState() {
        val accuracy = calculateRecentAccuracy()
        val currentState = criticalityState.value

        val newExplorationRatio = when {
            accuracy < CriticalityParams.LOW_ACCURACY_THRESHOLD -> {
                // 准确率低，增加探索
                (currentState.explorationRatio + CriticalityParams.ADJUSTMENT_STEP)
                    .coerceIn(CriticalityParams.MIN_EXPLORATION, CriticalityParams.MAX_EXPLORATION)
            }
            accuracy > CriticalityParams.HIGH_ACCURACY_THRESHOLD -> {
                // 准确率高，减少探索
                (currentState.explorationRatio - CriticalityParams.ADJUSTMENT_STEP)
                    .coerceIn(CriticalityParams.MIN_EXPLORATION, CriticalityParams.MAX_EXPLORATION)
            }
            else -> currentState.explorationRatio
        }

        val newPhase = determineCriticalityPhase(newExplorationRatio)

        val adjustment = AdjustmentRecord(
            timestamp = System.currentTimeMillis(),
            oldRatio = currentState.explorationRatio,
            newRatio = newExplorationRatio,
            reason = "Accuracy: ${String.format("%.2f", accuracy)}"
        )

        criticalityState.value = currentState.copy(
            explorationRatio = newExplorationRatio,
            exploitationRatio = 1 - newExplorationRatio,
            currentPhase = newPhase,
            recentAccuracy = accuracy,
            adjustmentHistory = (currentState.adjustmentHistory + adjustment).takeLast(100)
        )
    }

    private fun determineCriticalityPhase(explorationRatio: Float): CriticalityPhase {
        return when {
            explorationRatio > 0.4f -> CriticalityPhase.EXPLORATION
            explorationRatio < 0.1f -> CriticalityPhase.EXPLOITATION
            explorationRatio in 0.1f..0.2f -> CriticalityPhase.ORDERED
            explorationRatio in 0.3f..0.4f -> CriticalityPhase.CHAOTIC
            else -> CriticalityPhase.BALANCED
        }
    }

    private fun updatePredictionState() {
        val state = predictionStateFlow.value
        val accuracy = calculateRecentAccuracy()

        synchronized(feedbackLock) {
            val hits = feedbackRecords.count { it.actual is ActualOutcome.Hit }
            val misses = feedbackRecords.count { it.actual is ActualOutcome.Miss }

            predictionStateFlow.value = state.copy(
                accuracy = accuracy,
                recentPredictions = feedbackRecords.size,
                hits = hits,
                misses = misses,
                averageConfidence = if (predictionHistory.isEmpty()) 0.5f
                    else predictionHistory.values.map { it.confidence }.average().toFloat(),
                criticalityState = criticalityState.value
            )
        }
    }

    /**
     * 用户行为模式统计
     */
    private data class PatternStats(
        val frequency: Float,
        val confidence: Float,
        val suggestedActions: List<String>
    )
}
