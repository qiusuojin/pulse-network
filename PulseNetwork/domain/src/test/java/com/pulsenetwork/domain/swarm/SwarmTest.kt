package com.pulsenetwork.domain.swarm

import org.junit.Assert.*
import org.junit.Test

/**
 * Swarm 模块测试
 */
class SwarmTest {

    @Test
    fun `SemanticCacheEntry effectiveScore decreases with age`() {
        val freshEntry = SemanticCacheEntry(
            id = "test1",
            query = "测试问题",
            queryVector = null,
            answer = "测试答案",
            qualityScore = 0.9f,
            sourceNodeId = "node1",
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            hitCount = 0
        )

        val oldEntry = SemanticCacheEntry(
            id = "test2",
            query = "测试问题",
            queryVector = null,
            answer = "测试答案",
            qualityScore = 0.9f,
            sourceNodeId = "node1",
            createdAt = System.currentTimeMillis() - (48 * 60 * 60 * 1000), // 48 hours ago
            lastAccessedAt = System.currentTimeMillis() - (48 * 60 * 60 * 1000),
            hitCount = 0
        )

        // 新条目的分数应该更高
        assertTrue(freshEntry.effectiveScore() > oldEntry.effectiveScore())
    }

    @Test
    fun `SemanticCacheEntry effectiveScore increases with hit count`() {
        val lowHitEntry = SemanticCacheEntry(
            id = "test1",
            query = "测试问题",
            queryVector = null,
            answer = "测试答案",
            qualityScore = 0.8f,
            sourceNodeId = "node1",
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            hitCount = 1
        )

        val highHitEntry = SemanticCacheEntry(
            id = "test2",
            query = "测试问题",
            queryVector = null,
            answer = "测试答案",
            qualityScore = 0.8f,
            sourceNodeId = "node1",
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            hitCount = 100
        )

        // 高命中率的条目分数应该更高
        assertTrue(highHitEntry.effectiveScore() > lowHitEntry.effectiveScore())
    }

    @Test
    fun `PeerNode capabilities are correctly set`() {
        val capabilities = NodeCapabilities(
            hasNPU = true,
            totalMemoryMB = 4096,
            availableMemoryMB = 2048,
            supportedModelTypes = listOf("llama", "whisper"),
            maxConcurrentTasks = 2,
            cpuCores = 8,
            gpuAvailable = true
        )

        assertTrue(capabilities.hasNPU)
        assertEquals(4096, capabilities.totalMemoryMB)
        assertEquals(8, capabilities.cpuCores)
    }

    @Test
    fun `SwarmMessage has correct properties`() {
        val message = SwarmMessage(
            id = "msg1",
            type = MessageType.TASK_REQUEST,
            senderId = "node1",
            recipientId = "node2",
            timestamp = System.currentTimeMillis(),
            payload = MessagePayload.TaskRequest(
                taskId = "task1",
                taskType = "inference",
                input = mapOf("prompt" to "Hello"),
                requirements = TaskRequirements(
                    minMemoryMB = 1024,
                    requiresNPU = false,
                    estimatedTimeMs = 5000,
                    maxRetries = 3
                ),
                timeout = 30000
            ),
            ttl = 3,
            priority = MessagePriority.NORMAL
        )

        assertEquals("msg1", message.id)
        assertEquals(MessageType.TASK_REQUEST, message.type)
        assertEquals("node1", message.senderId)
        assertEquals(3, message.ttl)
    }

    @Test
    fun `ConnectionState enum has all expected values`() {
        val states = ConnectionState.values()
        assertTrue(states.contains(ConnectionState.DISCOVERED))
        assertTrue(states.contains(ConnectionState.CONNECTING))
        assertTrue(states.contains(ConnectionState.CONNECTED))
        assertTrue(states.contains(ConnectionState.DISCONNECTED))
        assertTrue(states.contains(ConnectionState.UNREACHABLE))
    }

    @Test
    fun `SwarmStats has correct default values`() {
        val stats = SwarmStats(
            discoveredNodes = 5,
            connectedNodes = 3,
            messagesReceived = 100,
            messagesSent = 50,
            cacheHits = 80,
            cacheMisses = 20,
            averageLatencyMs = 50,
            uptimeMs = 60000
        )

        assertEquals(5, stats.discoveredNodes)
        assertEquals(3, stats.connectedNodes)
        assertEquals(80, stats.cacheHits)
    }
}
