package com.pulsenetwork.domain.relation

import org.junit.Assert.*
import org.junit.Test

/**
 * 关系网络层测试
 * 测试赫布学习、信任等级、时间衰减等核心功能
 */
class RelationNetworkTest {

    // ========== 赫布学习参数测试 ==========

    @Test
    fun `HebbianParams has correct default values`() {
        assertEquals(0.05f, HebbianParams.LTP_RATE)
        assertEquals(0.03f, HebbianParams.LTD_RATE)
        assertEquals(48, HebbianParams.DECAY_PERIOD_HOURS)
        assertEquals(0.1f, HebbianParams.DECAY_RATE)
        assertEquals(1.0f, HebbianParams.MAX_STRENGTH)
        assertEquals(0.01f, HebbianParams.MIN_STRENGTH)
    }

    // ========== 信任等级测试 ==========

    @Test
    fun `TrustLevel fromCollaborationCount returns correct level`() {
        assertEquals(TrustLevel.UNKNOWN, TrustLevel.fromCollaborationCount(0))
        assertEquals(TrustLevel.NOVICE, TrustLevel.fromCollaborationCount(1))
        assertEquals(TrustLevel.NOVICE, TrustLevel.fromCollaborationCount(3))
        assertEquals(TrustLevel.FAMILIAR, TrustLevel.fromCollaborationCount(4))
        assertEquals(TrustLevel.FAMILIAR, TrustLevel.fromCollaborationCount(10))
        assertEquals(TrustLevel.TRUSTED, TrustLevel.fromCollaborationCount(11))
        assertEquals(TrustLevel.TRUSTED, TrustLevel.fromCollaborationCount(50))
        assertEquals(TrustLevel.INTIMATE, TrustLevel.fromCollaborationCount(51))
        assertEquals(TrustLevel.INTIMATE, TrustLevel.fromCollaborationCount(1000))
    }

    @Test
    fun `TrustLevel weights are in ascending order`() {
        val weights = TrustLevel.values().map { it.weight }
        for (i in 1 until weights.size) {
            assertTrue(weights[i] >= weights[i - 1])
        }
    }

    @Test
    fun `TrustLevel minCollaborations are in ascending order`() {
        val minCollabs = TrustLevel.values().map { it.minCollaborations }
        for (i in 1 until minCollabs.size) {
            assertTrue(minCollabs[i] >= minCollabs[i - 1])
        }
    }

    // ========== 节点关系测试 ==========

    @Test
    fun `NodeRelation successRate calculates correctly`() {
        val relation = NodeRelation(
            nodeId = "node1",
            connectionStrength = 0.5f,
            collaborationCount = 10,
            successCount = 8,
            failureCount = 2,
            trustLevel = TrustLevel.FAMILIAR,
            firstContactAt = System.currentTimeMillis(),
            lastCollaborationAt = System.currentTimeMillis(),
            averageQuality = 0.8f,
            averageResponseTimeMs = 500,
            taskTypeExpertise = emptyMap(),
            mirrorObservations = 0
        )

        assertEquals(0.8f, relation.successRate(), 0.001f)
    }

    @Test
    fun `NodeRelation successRate returns 0 for no collaborations`() {
        val relation = NodeRelation(
            nodeId = "node1",
            connectionStrength = 0.5f,
            collaborationCount = 0,
            successCount = 0,
            failureCount = 0,
            trustLevel = TrustLevel.UNKNOWN,
            firstContactAt = System.currentTimeMillis(),
            lastCollaborationAt = System.currentTimeMillis(),
            averageQuality = 0f,
            averageResponseTimeMs = 0,
            taskTypeExpertise = emptyMap(),
            mirrorObservations = 0
        )

        assertEquals(0f, relation.successRate(), 0.001f)
    }

    @Test
    fun `NodeRelation overallScore considers multiple factors`() {
        val lowRelation = NodeRelation(
            nodeId = "node1",
            connectionStrength = 0.1f,
            collaborationCount = 1,
            successCount = 0,
            failureCount = 1,
            trustLevel = TrustLevel.NOVICE,
            firstContactAt = System.currentTimeMillis(),
            lastCollaborationAt = System.currentTimeMillis(),
            averageQuality = 0.3f,
            averageResponseTimeMs = 5000,
            taskTypeExpertise = emptyMap(),
            mirrorObservations = 0
        )

        val highRelation = NodeRelation(
            nodeId = "node2",
            connectionStrength = 0.9f,
            collaborationCount = 100,
            successCount = 95,
            failureCount = 5,
            trustLevel = TrustLevel.INTIMATE,
            firstContactAt = System.currentTimeMillis(),
            lastCollaborationAt = System.currentTimeMillis(),
            averageQuality = 0.95f,
            averageResponseTimeMs = 100,
            taskTypeExpertise = mapOf("inference" to 0.9f),
            mirrorObservations = 10
        )

        // 高关系节点的综合评分应该更高
        assertTrue(highRelation.overallScore() > lowRelation.overallScore())
    }

    // ========== 协作事件测试 ==========

    @Test
    fun `CollaborationEvent has correct properties`() {
        val event = CollaborationEvent(
            nodeId = "node1",
            taskType = "inference",
            success = true,
            qualityScore = 0.9f,
            responseTimeMs = 500,
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("key" to "value")
        )

        assertEquals("node1", event.nodeId)
        assertEquals("inference", event.taskType)
        assertTrue(event.success)
        assertEquals(0.9f, event.qualityScore, 0.001f)
        assertEquals(500L, event.responseTimeMs)
        assertTrue(event.metadata.containsKey("key"))
    }

    // ========== 镜像神经元观察测试 ==========

    @Test
    fun `MirrorObservation has correct properties`() {
        val observation = MirrorObservation(
            observedNodeId = "node1",
            taskType = "translation",
            observedBehavior = "Used efficient caching",
            outcome = ObservationOutcome.SUCCESS,
            learningValue = 0.8f,
            timestamp = System.currentTimeMillis(),
            context = mapOf("cache_hit" to true)
        )

        assertEquals("node1", observation.observedNodeId)
        assertEquals("translation", observation.taskType)
        assertEquals(ObservationOutcome.SUCCESS, observation.outcome)
        assertEquals(0.8f, observation.learningValue, 0.001f)
    }

    @Test
    fun `ObservationOutcome has all expected values`() {
        val outcomes = ObservationOutcome.values()
        assertTrue(outcomes.contains(ObservationOutcome.SUCCESS))
        assertTrue(outcomes.contains(ObservationOutcome.FAILURE))
        assertTrue(outcomes.contains(ObservationOutcome.NEUTRAL))
        assertTrue(outcomes.contains(ObservationOutcome.OPTIMAL))
    }

    // ========== 关系网络状态测试 ==========

    @Test
    fun `RelationNetworkState EMPTY has sensible defaults`() {
        val empty = RelationNetworkState.EMPTY

        assertEquals(0, empty.totalRelations)
        assertEquals(0f, empty.averageStrength, 0.001f)
        assertTrue(empty.trustDistribution.isEmpty())
        assertNull(empty.strongestRelation)
        assertEquals(0, empty.recentCollaborations)
        assertTrue(empty.topTaskTypes.isEmpty())
        assertEquals(0f, empty.networkMaturity, 0.001f)
    }

    @Test
    fun `RelationNetworkState can be created with custom values`() {
        val state = RelationNetworkState(
            totalRelations = 10,
            averageStrength = 0.75f,
            trustDistribution = mapOf(TrustLevel.TRUSTED to 5, TrustLevel.FAMILIAR to 5),
            strongestRelation = null,
            recentCollaborations = 50,
            topTaskTypes = listOf("inference", "translation"),
            networkMaturity = 0.8f
        )

        assertEquals(10, state.totalRelations)
        assertEquals(0.75f, state.averageStrength, 0.001f)
        assertEquals(2, state.trustDistribution.size)
        assertEquals(50, state.recentCollaborations)
        assertEquals(2, state.topTaskTypes.size)
        assertEquals(0.8f, state.networkMaturity, 0.001f)
    }
}
