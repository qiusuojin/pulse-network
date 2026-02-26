package com.pulsenetwork.domain.evolution

import kotlinx.coroutines.flow.Flow

/**
 * 节点进化系统 - NodeEvolution
 *
 * 基于免疫记忆和突触可塑性的节点成长系统
 * 核心理念: 经验积累带来能力提升
 *
 * 功能:
 * - 节点等级系统（学徒→工匠→专家→大师）
 * - 免疫记忆（疫苗库）- 记录问题-解决方案对
 * - 经验积累和专业化
 * - 能力进化追踪
 *
 * 灵感来源: 免疫系统的记忆机制、神经可塑性
 */
interface NodeEvolution {

    /**
     * 记录解决方案
     * 将问题-解决方案对存入疫苗库
     */
    suspend fun recordSolution(
        problem: Problem,
        solution: Solution,
        effectiveness: Float
    )

    /**
     * 查找相似问题的解决方案
     * 基于向量相似度匹配疫苗库
     */
    suspend fun findSimilarSolutions(problem: Problem): List<VaccineEntry>

    /**
     * 获取当前节点等级
     */
    fun getCurrentLevel(): NodeLevel

    /**
     * 获取成长进度
     */
    fun getGrowthProgress(): NodeGrowthProgress

    /**
     * 获得经验值
     * 完成任务后调用
     */
    suspend fun gainExperience(capability: String, experience: Float)

    /**
     * 获取专业化领域
     */
    fun getSpecializations(): List<Specialization>

    /**
     * 获取疫苗库统计
     */
    fun getVaccineLibraryStats(): VaccineLibraryStats

    /**
     * 进化状态流
     */
    fun evolutionStateFlow(): Flow<EvolutionState>

    /**
     * 获取最大并发任务数
     * 基于当前等级
     */
    fun getMaxConcurrentTasks(): Int

    /**
     * 检查是否具备某能力
     */
    fun hasCapability(capability: String): Boolean

    /**
     * 获取能力熟练度
     */
    fun getCapabilityProficiency(capability: String): Float

    /**
     * 清理过期的疫苗条目
     */
    suspend fun cleanupExpiredVaccines()

    /**
     * 获取进化历史
     */
    fun getEvolutionHistory(): List<EvolutionEvent>
}

/**
 * 问题定义
 */
data class Problem(
    val id: String,
    val type: String,
    val description: String,
    val embedding: FloatArray?,           // 问题的向量嵌入
    val keywords: List<String>,
    val complexity: ProblemComplexity,
    val category: String,
    val metadata: Map<String, Any> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Problem) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * 问题复杂度
 */
enum class ProblemComplexity {
    TRIVIAL,     // 微不足道
    SIMPLE,      // 简单
    MODERATE,    // 中等
    COMPLEX,     // 复杂
    EXPERT       // 专家级
}

/**
 * 解决方案
 */
data class Solution(
    val id: String,
    val approach: String,              // 解决方法描述
    val steps: List<SolutionStep>,     // 解决步骤
    val resources: List<String>,       // 所需资源
    val estimatedTimeMs: Long,         // 预计时间
    val successIndicators: List<String>, // 成功指标
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 解决步骤
 */
data class SolutionStep(
    val order: Int,
    val description: String,
    val expectedOutput: String?,
    val alternativeActions: List<String> = emptyList()
)

/**
 * 疫苗条目（免疫记忆）
 * 记录问题-解决方案对，用于快速响应相似问题
 */
data class VaccineEntry(
    val id: String,
    val problem: Problem,
    val solution: Solution,
    val effectiveness: Float,           // 有效性 0.0-1.0
    val createdAt: Long,
    val lastUsedAt: Long,
    val useCount: Int,
    val successRate: Float,             // 复用成功率
    val decayFactor: Float = 1.0f       // 时间衰减因子
) {
    /**
     * 计算效力
     * 效力 = 有效性 × 时间衰减 × 使用次数加成
     */
    fun calculatePotency(): Float {
        val ageHours = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60)
        val timeDecay = kotlin.math.exp(-ageHours / 168.0).toFloat()  // 7天半衰期
        val usageBoost = 1 + kotlin.math.ln(useCount + 1.0).toFloat() / 5
        return effectiveness * timeDecay * usageBoost * decayFactor
    }

    /**
     * 检查是否过期
     */
    fun isExpired(maxAgeDays: Int = 30): Boolean {
        val ageDays = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60 * 24)
        return ageDays > maxAgeDays && useCount == 0
    }
}

/**
 * 节点等级
 */
enum class NodeLevel(
    val displayName: String,
    val requiredExperience: Long,
    val maxConcurrentTasks: Int,
    val unlockAbilities: List<String>
) {
    APPRENTICE(
        displayName = "学徒",
        requiredExperience = 0,
        maxConcurrentTasks = 1,
        unlockAbilities = emptyList()
    ),
    CRAFTSMAN(
        displayName = "工匠",
        requiredExperience = 1_000,
        maxConcurrentTasks = 3,
        unlockAbilities = listOf("task_splitting", "basic_caching")
    ),
    EXPERT(
        displayName = "专家",
        requiredExperience = 10_000,
        maxConcurrentTasks = 5,
        unlockAbilities = listOf("advanced_caching", "predictive_loading", "task_optimization")
    ),
    MASTER(
        displayName = "大师",
        requiredExperience = 100_000,
        maxConcurrentTasks = 10,
        unlockAbilities = listOf("full_caching", "auto_optimization", "teaching", "vaccine_creation")
    );

    companion object {
        /**
         * 根据经验值确定等级
         */
        fun fromExperience(experience: Long): NodeLevel {
            return entries.findLast { experience >= it.requiredExperience } ?: APPRENTICE
        }
    }
}

/**
 * 节点成长进度
 */
data class NodeGrowthProgress(
    val currentLevel: NodeLevel,
    val totalExperience: Long,
    val currentLevelExperience: Long,   // 当前等级获得的经验
    val nextLevelExperience: Long,      // 升级所需经验
    val progressPercent: Float,         // 升级进度 0-100
    val nextLevel: NodeLevel?,
    val estimatedTimeToNextLevel: Long, // 预计升级时间（毫秒），-1表示已满级
    val recentGrowthRate: Float         // 近期成长速率（经验/小时）
) {
    companion object {
        val INITIAL = NodeGrowthProgress(
            currentLevel = NodeLevel.APPRENTICE,
            totalExperience = 0,
            currentLevelExperience = 0,
            nextLevelExperience = NodeLevel.CRAFTSMAN.requiredExperience,
            progressPercent = 0f,
            nextLevel = NodeLevel.CRAFTSMAN,
            estimatedTimeToNextLevel = -1,
            recentGrowthRate = 0f
        )
    }
}

/**
 * 专业化领域
 */
data class Specialization(
    val capability: String,
    val proficiency: Float,             // 熟练度 0.0-1.0
    val experiencePoints: Long,
    val taskCount: Int,
    val successRate: Float,
    val lastUsedAt: Long,
    val tier: SpecializationTier
)

/**
 * 专业化层级
 */
enum class SpecializationTier(val minProficiency: Float) {
    NOVICE(0.0f),       // 新手
    COMPETENT(0.25f),   // 胜任
    PROFICIENT(0.5f),   // 熟练
    EXPERT(0.75f),      // 专家
    MASTER(1.0f);       // 大师

    companion object {
        fun fromProficiency(proficiency: Float): SpecializationTier {
            return entries.findLast { proficiency >= it.minProficiency } ?: NOVICE
        }
    }
}

/**
 * 疫苗库统计
 */
data class VaccineLibraryStats(
    val totalEntries: Int,
    val effectiveEntries: Int,          // 效力>0.5的条目数
    val averagePotency: Float,
    val topCategories: List<CategoryCount>,
    val recentHits: Int,                // 最近命中率
    val averageAge: Long,               // 平均年龄（小时）
    val storageUsedMB: Float
)

/**
 * 分类计数
 */
data class CategoryCount(
    val category: String,
    val count: Int,
    val averageEffectiveness: Float
)

/**
 * 进化状态
 */
data class EvolutionState(
    val level: NodeLevel,
    val progress: NodeGrowthProgress,
    val specializations: List<Specialization>,
    val vaccineLibraryStats: VaccineLibraryStats,
    val totalTasksCompleted: Long,
    val averageSuccessRate: Float,
    val evolutionPoints: Long           // 可用于解锁能力的点数
) {
    companion object {
        val INITIAL = EvolutionState(
            level = NodeLevel.APPRENTICE,
            progress = NodeGrowthProgress.INITIAL,
            specializations = emptyList(),
            vaccineLibraryStats = VaccineLibraryStats(
                totalEntries = 0,
                effectiveEntries = 0,
                averagePotency = 0f,
                topCategories = emptyList(),
                recentHits = 0,
                averageAge = 0,
                storageUsedMB = 0f
            ),
            totalTasksCompleted = 0,
            averageSuccessRate = 0f,
            evolutionPoints = 0
        )
    }
}

/**
 * 进化事件
 */
sealed class EvolutionEvent {
    data class LevelUp(
        val newLevel: NodeLevel,
        val timestamp: Long
    ) : EvolutionEvent()

    data class CapabilityUnlocked(
        val capability: String,
        val timestamp: Long
    ) : EvolutionEvent()

    data class SpecializationGained(
        val capability: String,
        val tier: SpecializationTier,
        val timestamp: Long
    ) : EvolutionEvent()

    data class VaccineCreated(
        val vaccineId: String,
        val problemType: String,
        val timestamp: Long
    ) : EvolutionEvent()

    data class VaccineUsed(
        val vaccineId: String,
        val success: Boolean,
        val timestamp: Long
    ) : EvolutionEvent()
}

/**
 * 进化参数
 */
object EvolutionParams {
    /** 基础经验值（完成简单任务） */
    const val BASE_EXPERIENCE = 10f

    /** 复杂度经验乘数 */
    val COMPLEXITY_MULTIPLIER = mapOf(
        ProblemComplexity.TRIVIAL to 0.5f,
        ProblemComplexity.SIMPLE to 1.0f,
        ProblemComplexity.MODERATE to 2.0f,
        ProblemComplexity.COMPLEX to 5.0f,
        ProblemComplexity.EXPERT to 10.0f
    )

    /** 疫苗效力阈值（低于此值的疫苗会被标记为低效） */
    const val LOW_POTENCY_THRESHOLD = 0.3f

    /** 疫苗最大年龄（天） */
    const val MAX_VACCINE_AGE_DAYS = 30

    /** 相似度阈值（用于匹配相似问题） */
    const val SIMILARITY_THRESHOLD = 0.85f

    /** 最大疫苗库大小 */
    const val MAX_VACCINE_LIBRARY_SIZE = 10000

    /** 专业化熟练度增长速率 */
    const val PROFICIENCY_GROWTH_RATE = 0.01f
}
