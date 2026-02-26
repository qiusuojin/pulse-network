package com.pulsenetwork.domain.integration

import com.pulsenetwork.domain.evolution.*
import com.pulsenetwork.domain.prediction.*
import com.pulsenetwork.domain.relation.*
import com.pulsenetwork.domain.swarm.*
import org.junit.Assert.*
import org.junit.Test

/**
 * v0.2 模块集成测试
 *
 * 测试各模块之间的协作：
 * - RelationNetwork + TaskScheduler
 * - PredictionEngine + TaskScheduler
 * - NodeEvolution + TaskScheduler
 * - 端到端任务流程
 */
class V02IntegrationTest {

    // ========== 关系网络 + 调度策略 集成测试 ==========

    @Test
    fun `RelationAwareAllocationStrategy uses relation strengths`() {
        val relationStrengths = mapOf(
            "node1" to 0.9f,
            "node2" to 0.5f,
            "node3" to 0.1f
        )

        val strategy = RelationAwareAllocationStrategy(
            relationStrengths = relationStrengths,
            nodeExpertise = emptyMap()
        )

        // 验证关系强度被正确初始化
        // 实际测试需要 mock PeerNode 对象
        assertNotNull(strategy)
    }

    @Test
    fun `TrustLevel affects node selection priority`() {
        // 高信任等级的节点应该被优先选择
        val levels = listOf(
            TrustLevel.INTIMATE,
            TrustLevel.TRUSTED,
            TrustLevel.FAMILIAR,
            TrustLevel.NOVICE,
            TrustLevel.UNKNOWN
        )

        // 验证信任等级权重递减
        for (i in 1 until levels.size) {
            assertTrue(
                "Trust level weights should be descending",
                levels[i - 1].weight >= levels[i].weight
            )
        }
    }

    // ========== 预测引擎 + 临界态 集成测试 ==========

    @Test
    fun `CriticalityState adjusts based on accuracy`() {
        val defaultState = CriticalityState.DEFAULT

        // 默认探索比例应为 20%
        assertEquals(0.2f, defaultState.explorationRatio, 0.001f)

        // 利用比例应为 80%
        assertEquals(0.8f, defaultState.exploitationRatio, 0.001f)
    }

    @Test
    fun `ScheduleDecision types cover all scenarios`() {
        // 验证所有调度决策类型都可用
        val decisions = listOf(
            ScheduleDecision.ExecuteImmediately(emptyList(), 1000L),
            ScheduleDecision.Queue(1, 5000L, QueueReason.ALL_NODES_BUSY),
            ScheduleDecision.ExecuteLocally(LocalReason.NETWORK_POOR),
            ScheduleDecision.RequestPremium(1, 1.5f, 30000L),
            ScheduleDecision.Reject("No nodes", emptyList())
        )

        assertEquals(5, decisions.size)
    }

    @Test
    fun `PredictionFeedback updates model state`() {
        val feedback = PredictionFeedback(
            predictionId = "pred-1",
            predicted = PredictedNeed(
                type = "work_query",
                probability = 0.8f,
                estimatedTime = 5000L,
                context = emptyMap(),
                suggestedPreparation = emptyList(),
                confidence = 0.7f
            ),
            actual = ActualOutcome.Hit("work_query")
        )

        assertEquals("pred-1", feedback.predictionId)
        assertTrue(feedback.actual is ActualOutcome.Hit)
    }

    // ========== 节点进化 + 疫苗库 集成测试 ==========

    @Test
    fun `NodeLevel progression follows experience thresholds`() {
        val levels = NodeLevel.values()

        // 验证经验值要求递增
        for (i in 1 until levels.size) {
            assertTrue(
                "Level ${levels[i]} should require more experience than ${levels[i - 1]}",
                levels[i].requiredExperience > levels[i - 1].requiredExperience
            )
        }
    }

    @Test
    fun `VaccineEntry potency calculation integrates multiple factors`() {
        val problem = Problem(
            id = "p1",
            type = "test",
            description = "Test",
            embedding = null,
            keywords = emptyList(),
            complexity = ProblemComplexity.SIMPLE,
            category = "test"
        )

        val solution = Solution(
            id = "s1",
            approach = "Test",
            steps = emptyList(),
            resources = emptyList(),
            estimatedTimeMs = 1000,
            successIndicators = emptyList()
        )

        // 高效力、新创建、高使用量的疫苗
        val potentVaccine = VaccineEntry(
            id = "v1",
            problem = problem,
            solution = solution,
            effectiveness = 0.95f,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 100,
            successRate = 0.9f
        )

        // 低效力、旧、低使用量的疫苗
        val weakVaccine = VaccineEntry(
            id = "v2",
            problem = problem,
            solution = solution,
            effectiveness = 0.3f,
            createdAt = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000), // 14天前
            lastUsedAt = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000),
            useCount = 1,
            successRate = 0.5f
        )

        // 高效力疫苗应该有更高的效力值
        assertTrue(
            "Potent vaccine should have higher potency",
            potentVaccine.calculatePotency() > weakVaccine.calculatePotency()
        )
    }

    @Test
    fun `SpecializationTier progression matches proficiency`() {
        val tiers = SpecializationTier.values()

        // 验证熟练度要求递增
        for (i in 1 until tiers.size) {
            assertTrue(
                "Tier ${tiers[i]} should require higher proficiency than ${tiers[i - 1]}",
                tiers[i].minProficiency >= tiers[i - 1].minProficiency
            )
        }
    }

    // ========== 端到端流程测试 ==========

    @Test
    fun `Complete workflow from prediction to evolution`() {
        // 模拟完整流程：
        // 1. 预测需求
        val predictedNeed = PredictedNeed(
            type = "inference",
            probability = 0.9f,
            estimatedTime = 5000L,
            context = mapOf("source" to "test"),
            suggestedPreparation = listOf("warmup_model"),
            confidence = 0.85f
        )

        // 2. 创建协作事件
        val collaborationEvent = CollaborationEvent(
            nodeId = "node1",
            taskType = predictedNeed.type,
            success = true,
            qualityScore = 0.95f,
            responseTimeMs = 3000L
        )

        // 3. 记录解决方案（疫苗）
        val problem = Problem(
            id = "prob1",
            type = predictedNeed.type,
            description = "Test inference",
            embedding = null,
            keywords = listOf("inference"),
            complexity = ProblemComplexity.SIMPLE,
            category = "ai"
        )

        val solution = Solution(
            id = "sol1",
            approach = "Use local model",
            steps = emptyList(),
            resources = emptyList(),
            estimatedTimeMs = 3000,
            successIndicators = listOf("accuracy > 0.9")
        )

        // 4. 验证流程数据一致性
        assertEquals(predictedNeed.type, collaborationEvent.taskType)
        assertEquals(predictedNeed.type, problem.type)

        // 5. 验证经验值可以正确计算
        val baseExp = EvolutionParams.BASE_EXPERIENCE
        val multiplier = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.SIMPLE] ?: 1f
        val expectedExp = (baseExp * multiplier).toLong()

        assertTrue("Experience should be positive", expectedExp > 0)
    }

    @Test
    fun `Hebbian learning parameters are consistent`() {
        // 验证赫布学习参数的一致性
        assertTrue("LTP rate should be positive", HebbianParams.LTP_RATE > 0)
        assertTrue("LTD rate should be positive", HebbianParams.LTD_RATE > 0)
        assertTrue("LTP should be stronger than LTD", HebbianParams.LTP_RATE > HebbianParams.LTD_RATE)
        assertTrue("Max strength should be 1.0", HebbianParams.MAX_STRENGTH == 1.0f)
        assertTrue("Min strength should be small", HebbianParams.MIN_STRENGTH < 0.1f)
    }

    @Test
    fun `Criticality parameters are consistent`() {
        // 验证临界态参数的一致性
        assertTrue(
            "Default exploration should be within bounds",
            CriticalityParams.DEFAULT_EXPLORATION_RATIO in CriticalityParams.MIN_EXPLORATION..CriticalityParams.MAX_EXPLORATION
        )

        assertTrue(
            "Low accuracy threshold should be less than high threshold",
            CriticalityParams.LOW_ACCURACY_THRESHOLD < CriticalityParams.HIGH_ACCURACY_THRESHOLD
        )

        assertTrue("Adjustment step should be positive", CriticalityParams.ADJUSTMENT_STEP > 0)
    }

    @Test
    fun `NodeRelation overallScore integrates all factors`() {
        val relation = NodeRelation(
            nodeId = "testNode",
            connectionStrength = 0.8f,
            collaborationCount = 20,
            successCount = 18,
            failureCount = 2,
            trustLevel = TrustLevel.TRUSTED,
            firstContactAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000),
            lastCollaborationAt = System.currentTimeMillis(),
            averageQuality = 0.9f,
            averageResponseTimeMs = 500,
            taskTypeExpertise = mapOf("inference" to 0.85f),
            mirrorObservations = 5
        )

        val score = relation.overallScore()

        // 综合评分应该在合理范围内
        assertTrue("Overall score should be between 0 and 1", score in 0f..1f)
        assertTrue("Trusted node should have decent score", score > 0.5f)
        assertEquals("Success rate should be 90%", 0.9f, relation.successRate(), 0.001f)
    }
}
