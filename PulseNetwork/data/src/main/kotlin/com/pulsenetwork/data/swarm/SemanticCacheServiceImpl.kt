package com.pulsenetwork.data.swarm

import com.pulsenetwork.domain.swarm.SemanticCacheEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 语义缓存服务实现
 *
 * "村口八卦"协议核心：
 * - 本地缓存问答对
 * - 通过语义相似度匹配查询
 * - 与邻居节点共享缓存
 * - 质量评分和热度衰减
 */
@Singleton
class SemanticCacheService @Inject constructor() {

    // 本地缓存
    private val localCache = mutableMapOf<String, SemanticCacheEntry>()

    // 网络缓存（来自邻居）
    private val networkCache = mutableMapOf<String, SemanticCacheEntry>()

    // 相似度阈值
    private val similarityThreshold = 0.85f

    // 最大缓存大小
    private val maxCacheSize = 1000

    // 统计
    private val _cacheHits = MutableStateFlow(0L)
    val cacheHits: StateFlow<Long> = _cacheHits.asStateFlow()

    private val _cacheMisses = MutableStateFlow(0L)
    val cacheMisses: StateFlow<Long> = _cacheMisses.asStateFlow()

    /**
     * 添加到本地缓存
     */
    fun addToLocalCache(
        query: String,
        answer: String,
        queryVector: FloatArray? = null,
        qualityScore: Float = 0.8f,
        nodeId: String
    ) {
        val id = "${nodeId}_${System.currentTimeMillis()}"

        val entry = SemanticCacheEntry(
            id = id,
            query = query,
            queryVector = queryVector,
            answer = answer,
            qualityScore = qualityScore,
            sourceNodeId = nodeId,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            hitCount = 0
        )

        localCache[id] = entry
        cleanupIfNeeded()
    }

    /**
     * 添加网络缓存（来自邻居）
     */
    fun addNetworkCache(entry: SemanticCacheEntry) {
        networkCache[entry.id] = entry
        cleanupIfNeeded()
    }

    /**
     * 批量添加网络缓存
     */
    fun addNetworkCacheBatch(entries: List<SemanticCacheEntry>) {
        entries.forEach { entry ->
            networkCache[entry.id] = entry
        }
        cleanupIfNeeded()
    }

    /**
     * 查询缓存（带向量）
     */
    fun query(queryVector: FloatArray): SemanticCacheEntry? {
        var bestMatch: SemanticCacheEntry? = null
        var bestSimilarity = similarityThreshold

        // 先搜索本地缓存
        for (entry in localCache.values) {
            val vector = entry.queryVector ?: continue
            val similarity = cosineSimilarity(queryVector, vector)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = entry
            }
        }

        // 再搜索网络缓存（考虑有效分数）
        for (entry in networkCache.values) {
            val vector = entry.queryVector ?: continue
            val similarity = cosineSimilarity(queryVector, vector)
            val adjustedSimilarity = similarity * entry.effectiveScore()

            if (adjustedSimilarity > bestSimilarity) {
                bestSimilarity = adjustedSimilarity
                bestMatch = entry
            }
        }

        if (bestMatch != null) {
            _cacheHits.value++
            // 更新命中信息
            updateHitInfo(bestMatch.id)
        } else {
            _cacheMisses.value++
        }

        return bestMatch
    }

    /**
     * 按文本查询（简化版，无向量时使用）
     */
    fun queryByText(query: String): SemanticCacheEntry? {
        // 先搜索本地缓存
        for (entry in localCache.values) {
            if (textSimilarity(query, entry.query) > similarityThreshold) {
                _cacheHits.value++
                updateHitInfo(entry.id)
                return entry
            }
        }

        // 再搜索网络缓存
        for (entry in networkCache.values) {
            val similarity = textSimilarity(query, entry.query)
            val adjustedSimilarity = similarity * entry.effectiveScore()

            if (adjustedSimilarity > similarityThreshold) {
                _cacheHits.value++
                updateHitInfo(entry.id)
                return entry
            }
        }

        _cacheMisses.value++
        return null
    }

    /**
     * 获取可分享的缓存条目
     */
    fun getShareableEntries(limit: Int = 100): List<SemanticCacheEntry> {
        val allEntries = localCache.values + networkCache.values

        return allEntries
            .sortedByDescending { it.effectiveScore() }
            .take(limit)
    }

    /**
     * 清除过期缓存
     */
    fun clearExpired(maxAgeHours: Int = 48) {
        val cutoff = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)

        localCache.entries.removeIf { it.value.lastAccessedAt < cutoff }
        networkCache.entries.removeIf { it.value.lastAccessedAt < cutoff }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "local_cache_size" to localCache.size,
            "network_cache_size" to networkCache.size,
            "total_cache_size" to (localCache.size + networkCache.size),
            "cache_hits" to _cacheHits.value,
            "cache_misses" to _cacheMisses.value,
            "hit_rate" to if (_cacheHits.value + _cacheMisses.value > 0) {
                _cacheHits.value.toFloat() / (_cacheHits.value + _cacheMisses.value)
            } else 0f
        )
    }

    /**
     * 清空缓存
     */
    fun clear() {
        localCache.clear()
        networkCache.clear()
    }

    // ========== 私有方法 ==========

    private fun updateHitInfo(entryId: String) {
        val entry = localCache[entryId] ?: networkCache[entryId] ?: return

        val updated = entry.copy(
            lastAccessedAt = System.currentTimeMillis(),
            hitCount = entry.hitCount + 1
        )

        if (localCache.containsKey(entryId)) {
            localCache[entryId] = updated
        } else {
            networkCache[entryId] = updated
        }
    }

    private fun cleanupIfNeeded() {
        val totalSize = localCache.size + networkCache.size
        if (totalSize <= maxCacheSize) return

        // 按有效分数排序，移除最低的
        val allEntries = (localCache.values + networkCache.values)
            .sortedByDescending { it.effectiveScore() }

        val toKeep = allEntries.take(maxCacheSize)
        val toRemove = allEntries.drop(maxCacheSize).map { it.id }.toSet()

        localCache.entries.removeIf { it.key in toRemove }
        networkCache.entries.removeIf { it.key in toRemove }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0f || normB == 0f) return 0f

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun textSimilarity(a: String, b: String): Float {
        val wordsA = a.lowercase().split(Regex("\\s+")).toSet()
        val wordsB = b.lowercase().split(Regex("\\s+")).toSet()

        val intersection = wordsA.intersect(wordsB)
        val union = wordsA.union(wordsB)

        if (union.isEmpty()) return 0f

        return intersection.size.toFloat() / union.size.toFloat()
    }
}
