package com.pulsenetwork.data.evolution

import com.pulsenetwork.domain.evolution.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 节点进化系统实现
 *
 * 实现基于免疫记忆和经验积累的节点成长:
 * - 节点等级系统（学徒→工匠→专家→大师）
 * - 疫苗库（免疫记忆）
 * - 能力专业化
 * - 经验积累
 */
@Singleton
class NodeEvolutionImpl @Inject constructor() : NodeEvolution {

    // 总经验值
    private var totalExperience = 0L

    // 疫苗库（免疫记忆）
    private val vaccineLibrary = ConcurrentHashMap<String, VaccineEntry>()

    // 能力熟练度
    private val capabilities = ConcurrentHashMap<String, CapabilityProgress>()

    // 进化历史
    private val evolutionHistory = mutableListOf<EvolutionEvent>()
    private val historyLock = Any()

    // 进化状态流
    private val evolutionStateFlow = MutableStateFlow(EvolutionState.INITIAL)

    // 已完成任务数
    private var totalTasksCompleted = 0L

    // 成功任务数
    private var successfulTasks = 0L

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun recordSolution(problem: Problem, solution: Solution, effectiveness: Float) {
        val entryId = UUID.randomUUID().toString()

        val entry = VaccineEntry(
            id = entryId,
            problem = problem,
            solution = solution,
            effectiveness = effectiveness,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 0,
            successRate = 1.0f,
            decayFactor = 1.0f
        )

        vaccineLibrary[entryId] = entry

        // 添加到进化历史
        addEvolutionEvent(EvolutionEvent.VaccineCreated(
            vaccineId = entryId,
            problemType = problem.type,
            timestamp = System.currentTimeMillis()
        ))

        // 检查是否需要清理
        if (vaccineLibrary.size > EvolutionParams.MAX_VACCINE_LIBRARY_SIZE) {
            cleanupLowPotencyVaccines()
        }

        // 更新状态
        updateEvolutionState()
    }

    override suspend fun findSimilarSolutions(problem: Problem): List<VaccineEntry> {
        val results = mutableListOf<VaccineEntry>()

        vaccineLibrary.values.forEach { entry ->
            val similarity = calculateSimilarity(problem, entry.problem)

            if (similarity >= EvolutionParams.SIMILARITY_THRESHOLD) {
                results.add(entry.copy(decayFactor = similarity))
            }
        }

        // 按效力排序
        return results.sortedByDescending { it.calculatePotency() }
    }

    override fun getCurrentLevel(): NodeLevel {
        return NodeLevel.fromExperience(totalExperience)
    }

    override fun getGrowthProgress(): NodeGrowthProgress {
        val currentLevel = getCurrentLevel()
        val levels = NodeLevel.entries
        val currentIndex = levels.indexOf(currentLevel)
        val nextLevel = levels.getOrNull(currentIndex + 1)

        val currentLevelExp = totalExperience - currentLevel.requiredExperience
        val nextLevelExp = if (nextLevel != null) {
            nextLevel.requiredExperience - currentLevel.requiredExperience
        } else {
            0L
        }

        val progressPercent = if (nextLevel != null && nextLevelExp > 0) {
            (currentLevelExp.toFloat() / nextLevelExp.toFloat()) * 100f
        } else {
            100f
        }

        // 估算升级时间（基于近期成长速率）
        val estimatedTime = if (nextLevel != null) {
            val remainingExp = nextLevel.requiredExperience - totalExperience
            if (remainingExp > 0) {
                // 简化估算：假设每小时获得100经验
                (remainingExp / 100f * 3600000).toLong()
            } else -1L
        } else -1L

        return NodeGrowthProgress(
            currentLevel = currentLevel,
            totalExperience = totalExperience,
            currentLevelExperience = currentLevelExp,
            nextLevelExperience = nextLevelExp,
            progressPercent = progressPercent.coerceIn(0f, 100f),
            nextLevel = nextLevel,
            estimatedTimeToNextLevel = estimatedTime,
            recentGrowthRate = calculateRecentGrowthRate()
        )
    }

    override suspend fun gainExperience(capability: String, experience: Float) {
        // 增加总经验
        totalExperience += experience.toLong()

        // 更新能力熟练度
        val capProgress = capabilities.getOrPut(capability) {
            CapabilityProgress(
                capability = capability,
                proficiency = 0f,
                experiencePoints = 0L,
                taskCount = 0,
                successCount = 0
            )
        }

        val newProficiency = (capProgress.proficiency +
            EvolutionParams.PROFICIENCY_GROWTH_RATE * (experience / EvolutionParams.BASE_EXPERIENCE))
            .coerceIn(0f, 1f)

        capabilities[capability] = capProgress.copy(
            proficiency = newProficiency,
            experiencePoints = capProgress.experiencePoints + experience.toLong(),
            taskCount = capProgress.taskCount + 1
        )

        // 检查等级提升
        checkLevelUp()

        // 检查专业化提升
        checkSpecializationUpgrade(capability)

        // 更新状态
        updateEvolutionState()
    }

    override fun getSpecializations(): List<Specialization> {
        return capabilities.values.map { progress ->
            val successRate = if (progress.taskCount > 0) {
                progress.successCount.toFloat() / progress.taskCount
            } else 0f

            Specialization(
                capability = progress.capability,
                proficiency = progress.proficiency,
                experiencePoints = progress.experiencePoints,
                taskCount = progress.taskCount,
                successRate = successRate,
                lastUsedAt = progress.lastUsedAt,
                tier = SpecializationTier.fromProficiency(progress.proficiency)
            )
        }.sortedByDescending { it.proficiency }
    }

    override fun getVaccineLibraryStats(): VaccineLibraryStats {
        val entries = vaccineLibrary.values.toList()

        val effectiveEntries = entries.count { it.calculatePotency() > 0.5f }

        val avgPotency = if (entries.isEmpty()) 0f
            else entries.map { it.calculatePotency() }.average().toFloat()

        val categories = entries.groupBy { it.problem.category }
            .map { (category, items) ->
                CategoryCount(
                    category = category,
                    count = items.size,
                    averageEffectiveness = items.map { it.effectiveness }.average().toFloat()
                )
            }
            .sortedByDescending { it.count }
            .take(10)

        val recentHits = entries.count {
            System.currentTimeMillis() - it.lastUsedAt < 24 * 60 * 60 * 1000 && it.useCount > 0
        }

        val avgAge = if (entries.isEmpty()) 0L
            else entries.map { (System.currentTimeMillis() - it.createdAt) / (1000 * 60 * 60) }.average().toLong()

        return VaccineLibraryStats(
            totalEntries = entries.size,
            effectiveEntries = effectiveEntries,
            averagePotency = avgPotency,
            topCategories = categories,
            recentHits = recentHits,
            averageAge = avgAge,
            storageUsedMB = estimateStorageUsed()
        )
    }

    override fun evolutionStateFlow(): Flow<EvolutionState> = evolutionStateFlow.asStateFlow()

    override fun getMaxConcurrentTasks(): Int {
        return getCurrentLevel().maxConcurrentTasks
    }

    override fun hasCapability(capability: String): Boolean {
        return capabilities.containsKey(capability)
    }

    override fun getCapabilityProficiency(capability: String): Float {
        return capabilities[capability]?.proficiency ?: 0f
    }

    override suspend fun cleanupExpiredVaccines() {
        val toRemove = vaccineLibrary.values
            .filter { it.isExpired(EvolutionParams.MAX_VACCINE_AGE_DAYS) }
            .map { it.id }

        toRemove.forEach { vaccineLibrary.remove(it) }

        if (toRemove.isNotEmpty()) {
            updateEvolutionState()
        }
    }

    override fun getEvolutionHistory(): List<EvolutionEvent> {
        synchronized(historyLock) {
            return evolutionHistory.toList()
        }
    }

    // ========== 公共方法（供外部调用） ==========

    /**
     * 记录任务完成（用于更新成功率等）
     */
    suspend fun recordTaskCompletion(capability: String, success: Boolean) {
        totalTasksCompleted++
        if (success) successfulTasks++

        val progress = capabilities[capability]
        if (progress != null) {
            capabilities[capability] = progress.copy(
                successCount = progress.successCount + (if (success) 1 else 0),
                lastUsedAt = System.currentTimeMillis()
            )
        }

        updateEvolutionState()
    }

    /**
     * 使用疫苗（找到匹配时调用）
     */
    suspend fun useVaccine(vaccineId: String, success: Boolean) {
        val entry = vaccineLibrary[vaccineId] ?: return

        val newSuccessRate = (entry.successRate * entry.useCount + (if (success) 1f else 0f)) /
            (entry.useCount + 1)

        vaccineLibrary[vaccineId] = entry.copy(
            lastUsedAt = System.currentTimeMillis(),
            useCount = entry.useCount + 1,
            successRate = newSuccessRate
        )

        addEvolutionEvent(EvolutionEvent.VaccineUsed(
            vaccineId = vaccineId,
            success = success,
            timestamp = System.currentTimeMillis()
        ))

        updateEvolutionState()
    }

    // ========== 私有方法 ==========

    private fun calculateSimilarity(problem1: Problem, problem2: Problem): Float {
        // 1. 类型匹配
        if (problem1.type != problem2.type) return 0f

        // 2. 关键词重叠
        val keywordOverlap = if (problem1.keywords.isEmpty() || problem2.keywords.isEmpty()) {
            0.5f
        } else {
            val intersection = problem1.keywords.intersect(problem2.keywords.toSet())
            val union = problem1.keywords.union(problem2.keywords.toSet())
            intersection.size.toFloat() / union.size.toFloat()
        }

        // 3. 向量相似度（如果有嵌入向量）
        val vectorSimilarity = if (problem1.embedding != null && problem2.embedding != null) {
            cosineSimilarity(problem1.embedding!!, problem2.embedding!!)
        } else {
            0.5f
        }

        // 综合计算
        return keywordOverlap * 0.4f + vectorSimilarity * 0.6f
    }

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0f

        return (dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))).toFloat()
    }

    private fun checkLevelUp() {
        val currentLevel = getCurrentLevel()
        val previousLevel = NodeLevel.fromExperience(totalExperience - 100)

        if (currentLevel != previousLevel) {
            // 等级提升！
            addEvolutionEvent(EvolutionEvent.LevelUp(
                newLevel = currentLevel,
                timestamp = System.currentTimeMillis()
            ))

            // 检查解锁能力
            currentLevel.unlockAbilities.forEach { ability ->
                addEvolutionEvent(EvolutionEvent.CapabilityUnlocked(
                    capability = ability,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }

    private fun checkSpecializationUpgrade(capability: String) {
        val progress = capabilities[capability] ?: return
        val newTier = SpecializationTier.fromProficiency(progress.proficiency)

        // 检查是否升级
        if (progress.lastTier != null && progress.lastTier != newTier && newTier.ordinal > progress.lastTier.ordinal) {
            addEvolutionEvent(EvolutionEvent.SpecializationGained(
                capability = capability,
                tier = newTier,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    private fun cleanupLowPotencyVaccines() {
        val toRemove = vaccineLibrary.values
            .filter { it.calculatePotency() < EvolutionParams.LOW_POTENCY_THRESHOLD }
            .sortedBy { it.calculatePotency() }
            .take(vaccineLibrary.size - EvolutionParams.MAX_VACCINE_LIBRARY_SIZE)
            .map { it.id }

        toRemove.forEach { vaccineLibrary.remove(it) }
    }

    private fun calculateRecentGrowthRate(): Float {
        // 简化实现：基于最近的能力使用情况估算
        val recentCapabilities = capabilities.values.filter {
            System.currentTimeMillis() - it.lastUsedAt < 24 * 60 * 60 * 1000
        }

        if (recentCapabilities.isEmpty()) return 0f

        return recentCapabilities.map { it.experiencePoints.toFloat() }.average().toFloat() / 24f
    }

    private fun estimateStorageUsed(): Float {
        // 粗略估算存储使用量
        var totalBytes = 0L

        vaccineLibrary.values.forEach { entry ->
            totalBytes += 1000 // 基础大小
            totalBytes += entry.problem.description.length.toLong()
            totalBytes += entry.solution.approach.length.toLong()
            totalBytes += (entry.problem.embedding?.size ?: 0) * 4L
        }

        return totalBytes / (1024f * 1024f) // 转换为MB
    }

    private fun addEvolutionEvent(event: EvolutionEvent) {
        synchronized(historyLock) {
            evolutionHistory.add(event)
            // 保持历史记录在合理大小
            while (evolutionHistory.size > 1000) {
                evolutionHistory.removeAt(0)
            }
        }
    }

    private fun updateEvolutionState() {
        val state = EvolutionState(
            level = getCurrentLevel(),
            progress = getGrowthProgress(),
            specializations = getSpecializations(),
            vaccineLibraryStats = getVaccineLibraryStats(),
            totalTasksCompleted = totalTasksCompleted,
            averageSuccessRate = if (totalTasksCompleted > 0) {
                successfulTasks.toFloat() / totalTasksCompleted
            } else 0f,
            evolutionPoints = totalExperience / 1000 // 每1000经验值获得1进化点
        )

        evolutionStateFlow.value = state
    }

    /**
     * 能力进度（内部类）
     */
    private data class CapabilityProgress(
        val capability: String,
        val proficiency: Float,
        val experiencePoints: Long,
        val taskCount: Int,
        val successCount: Int,
        val lastUsedAt: Long = System.currentTimeMillis(),
        val lastTier: SpecializationTier? = null
    )
}
