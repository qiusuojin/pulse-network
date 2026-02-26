package com.pulsenetwork.domain.prediction

import org.junit.Assert.*
import org.junit.Test

/**
 * 预测引擎层测试
 * 测试临界态调控、调度决策、节点排名等核心功能
 */
class PredictionEngineTest {

    // ========== 临界态参数测试 ==========

    @Test
    fun `CriticalityParams has correct default values`() {
        assertEquals(0.2f, CriticalityParams.DEFAULT_EXPLORATION_RATIO)
        assertEquals(0.5f, CriticalityParams.LOW_ACCURACY_THRESHOLD)
        assertEquals(0.9f, CriticalityParams.HIGH_ACCURACY_THRESHOLD)
        assertEquals(0.05f, CriticalityParams.ADJUSTMENT_STEP)
        assertEquals(0.05f, CriticalityParams.MIN_EXPLORATION)
        assertEquals(0.5f, CriticalityParams.MAX_EXPLORATION)
        assertEquals(100, CriticalityParams.ACCURACY_WINDOW_SIZE)
    }

    // ========== 临界态状态测试 ==========

    @Test
    fun `CriticalityState DEFAULT has correct values`() {
        val default = CriticalityState.DEFAULT

        assertEquals(0.2f, default.explorationRatio, 0.001f)
        assertEquals(0.8f, default.exploitationRatio, 0.001f)
        assertEquals(CriticalityPhase.BALANCED, default.currentPhase)
        assertEquals(0.5f, default.recentAccuracy, 0.001f)
        assertTrue(default.adjustmentHistory.isEmpty())
    }

    @Test
    fun `CriticalityState exploration and exploitation sum to 1`() {
        val state = CriticalityState.DEFAULT
        assertEquals(1.0f, state.explorationRatio + state.exploitationRatio, 0.001f)
    }

    @Test
    fun `CriticalityPhase has all expected values`() {
        val phases = CriticalityPhase.values()
        assertTrue(phases.contains(CriticalityPhase.EXPLORATION))
        assertTrue(phases.contains(CriticalityPhase.EXPLOITATION))
        assertTrue(phases.contains(CriticalityPhase.BALANCED))
        assertTrue(phases.contains(CriticalityPhase.CHAOTIC))
        assertTrue(phases.contains(CriticalityPhase.ORDERED))
    }

    // ========== 预测状态测试 ==========

    @Test
    fun `PredictionState INITIAL has sensible defaults`() {
        val initial = PredictionState.INITIAL

        assertEquals(0.5f, initial.accuracy, 0.001f)
        assertEquals(0, initial.recentPredictions)
        assertEquals(0, initial.hits)
        assertEquals(0, initial.misses)
        assertEquals(0.5f, initial.averageConfidence, 0.001f)
        assertTrue(initial.topPredictedNeeds.isEmpty())
        assertEquals("1.0.0", initial.modelVersion)
    }

    // ========== 预测需求测试 ==========

    @Test
    fun `PredictedNeed has correct properties`() {
        val need = PredictedNeed(
            type = "work_query",
            probability = 0.8f,
            estimatedTime = 5 * 60 * 1000L,
            context = mapOf("period" to "morning"),
            suggestedPreparation = listOf("preload_models"),
            confidence = 0.7f
        )

        assertEquals("work_query", need.type)
        assertEquals(0.8f, need.probability, 0.001f)
        assertEquals(5 * 60 * 1000L, need.estimatedTime)
        assertTrue(need.context.containsKey("period"))
        assertEquals(1, need.suggestedPreparation.size)
        assertEquals(0.7f, need.confidence, 0.001f)
    }

    // ========== 调度决策测试 ==========

    @Test
    fun `ScheduleDecision ExecuteImmediately has correct properties`() {
        val decision = ScheduleDecision.ExecuteImmediately(
            assignedNodes = listOf("node1", "node2"),
            estimatedCompletionTime = 5000L
        )

        assertEquals(2, decision.assignedNodes.size)
        assertEquals(5000L, decision.estimatedCompletionTime)
    }

    @Test
    fun `ScheduleDecision Queue has correct properties`() {
        val decision = ScheduleDecision.Queue(
            queuePosition = 3,
            estimatedWaitTime = 10000L,
            reason = QueueReason.ALL_NODES_BUSY
        )

        assertEquals(3, decision.queuePosition)
        assertEquals(10000L, decision.estimatedWaitTime)
        assertEquals(QueueReason.ALL_NODES_BUSY, decision.reason)
    }

    @Test
    fun `ScheduleDecision ExecuteLocally has correct properties`() {
        val decision = ScheduleDecision.ExecuteLocally(
            reason = LocalReason.NETWORK_POOR
        )

        assertEquals(LocalReason.NETWORK_POOR, decision.reason)
    }

    @Test
    fun `ScheduleDecision RequestPremium has correct properties`() {
        val decision = ScheduleDecision.RequestPremium(
            premiumLevel = 2,
            additionalCost = 1.5f,
            guaranteedResponseTime = 30000L
        )

        assertEquals(2, decision.premiumLevel)
        assertEquals(1.5f, decision.additionalCost, 0.001f)
        assertEquals(30000L, decision.guaranteedResponseTime)
    }

    @Test
    fun `QueueReason has all expected values`() {
        val reasons = QueueReason.values()
        assertTrue(reasons.contains(QueueReason.ALL_NODES_BUSY))
        assertTrue(reasons.contains(QueueReason.WAITING_FOR_PREHEAT))
        assertTrue(reasons.contains(QueueReason.LOAD_BALANCING))
        assertTrue(reasons.contains(QueueReason.PRIORITY_TOO_LOW))
    }

    @Test
    fun `LocalReason has all expected values`() {
        val reasons = LocalReason.values()
        assertTrue(reasons.contains(LocalReason.NETWORK_POOR))
        assertTrue(reasons.contains(LocalReason.NO_SUITABLE_NODES))
        assertTrue(reasons.contains(LocalReason.PRIVACY_SENSITIVE))
        assertTrue(reasons.contains(LocalReason.FASTER_LOCALLY))
        assertTrue(reasons.contains(LocalReason.OFFLINE_MODE))
    }

    // ========== 用户上下文测试 ==========

    @Test
    fun `UserContext contains all required components`() {
        val context = UserContext(
            recentQueries = listOf("query1", "query2"),
            currentSession = SessionInfo(
                sessionId = "session1",
                startedAt = System.currentTimeMillis(),
                queryCount = 2,
                topics = listOf("topic1"),
                complexity = SessionComplexity.MODERATE
            ),
            timeContext = TimeContext(
                hourOfDay = 10,
                dayOfWeek = 3,
                isWorkHour = true,
                typicalPatterns = listOf("work_query")
            ),
            interactionPattern = InteractionPattern(
                averageQueryLength = 50f,
                preferredResponseType = ResponseType.DETAILED,
                patience = 0.8f,
                frequency = 5f
            ),
            deviceState = DeviceState(
                batteryLevel = 0.9f,
                isCharging = true,
                networkQuality = NetworkQuality.EXCELLENT,
                availableMemoryMB = 2048,
                cpuUsage = 0.3f
            )
        )

        assertEquals(2, context.recentQueries.size)
        assertEquals("session1", context.currentSession.sessionId)
        assertEquals(10, context.timeContext.hourOfDay)
        assertEquals(ResponseType.DETAILED, context.interactionPattern.preferredResponseType)
        assertTrue(context.deviceState.isCharging)
    }

    // ========== 预热优先级测试 ==========

    @Test
    fun `PreheatPriority has all expected values in order`() {
        val priorities = PreheatPriority.values()
        assertEquals(PreheatPriority.LOW, priorities[0])
        assertEquals(PreheatPriority.NORMAL, priorities[1])
        assertEquals(PreheatPriority.HIGH, priorities[2])
        assertEquals(PreheatPriority.URGENT, priorities[3])
    }

    // ========== 任务复杂度测试 ==========

    @Test
    fun `TaskComplexity has all expected values`() {
        val complexities = TaskComplexity.values()
        assertTrue(complexities.contains(TaskComplexity.TRIVIAL))
        assertTrue(complexities.contains(TaskComplexity.SIMPLE))
        assertTrue(complexities.contains(TaskComplexity.MODERATE))
        assertTrue(complexities.contains(TaskComplexity.COMPLEX))
        assertTrue(complexities.contains(TaskComplexity.HEAVY))
    }

    // ========== 排名原因测试 ==========

    @Test
    fun `RankReason has all expected values`() {
        val reasons = RankReason.values()
        assertTrue(reasons.contains(RankReason.BEST_CAPABILITY))
        assertTrue(reasons.contains(RankReason.LOWEST_LOAD))
        assertTrue(reasons.contains(RankReason.HIGHEST_RELIABILITY))
        assertTrue(reasons.contains(RankReason.FASTEST_RESPONSE))
        assertTrue(reasons.contains(RankReason.STRONGEST_RELATION))
        assertTrue(reasons.contains(RankReason.SPECIALIZED))
        assertTrue(reasons.contains(RankReason.GEOGRAPHICALLY_CLOSE))
        assertTrue(reasons.contains(RankReason.HISTORICAL_SUCCESS))
    }

    // ========== 实际结果测试 ==========

    @Test
    fun `ActualOutcome types work correctly`() {
        val hit = ActualOutcome.Hit("work_query")
        val miss = ActualOutcome.Miss("casual_chat")
        val partial = ActualOutcome.PartialHit("similar_query", 0.7f)

        assertTrue(hit is ActualOutcome.Hit)
        assertTrue(miss is ActualOutcome.Miss)
        assertTrue(partial is ActualOutcome.PartialHit)
        assertEquals("work_query", (hit as ActualOutcome.Hit).actualType)
        assertEquals(0.7f, (partial as ActualOutcome.PartialHit).similarity, 0.001f)
    }
}
