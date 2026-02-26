package com.pulsenetwork.domain.evolution

import org.junit.Assert.*
import org.junit.Test

/**
 * 节点进化系统测试
 * 测试等级系统、疫苗库、专业化等核心功能
 */
class NodeEvolutionTest {

    // ========== 进化参数测试 ==========

    @Test
    fun `EvolutionParams has correct default values`() {
        assertEquals(10f, EvolutionParams.BASE_EXPERIENCE)
        assertEquals(0.3f, EvolutionParams.LOW_POTENCY_THRESHOLD)
        assertEquals(30, EvolutionParams.MAX_VACCINE_AGE_DAYS)
        assertEquals(0.85f, EvolutionParams.SIMILARITY_THRESHOLD)
        assertEquals(10000, EvolutionParams.MAX_VACCINE_LIBRARY_SIZE)
        assertEquals(0.01f, EvolutionParams.PROFICIENCY_GROWTH_RATE)
    }

    @Test
    fun `EvolutionParams COMPLEXITY_MULTIPLIER has all complexity levels`() {
        assertTrue(EvolutionParams.COMPLEXITY_MULTIPLIER.containsKey(ProblemComplexity.TRIVIAL))
        assertTrue(EvolutionParams.COMPLEXITY_MULTIPLIER.containsKey(ProblemComplexity.SIMPLE))
        assertTrue(EvolutionParams.COMPLEXITY_MULTIPLIER.containsKey(ProblemComplexity.MODERATE))
        assertTrue(EvolutionParams.COMPLEXITY_MULTIPLIER.containsKey(ProblemComplexity.COMPLEX))
        assertTrue(EvolutionParams.COMPLEXITY_MULTIPLIER.containsKey(ProblemComplexity.EXPERT))
    }

    @Test
    fun `EvolutionParams complexity multipliers are in ascending order`() {
        val trivial = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.TRIVIAL]!!
        val simple = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.SIMPLE]!!
        val moderate = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.MODERATE]!!
        val complex = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.COMPLEX]!!
        val expert = EvolutionParams.COMPLEXITY_MULTIPLIER[ProblemComplexity.EXPERT]!!

        assertTrue(trivial < simple)
        assertTrue(simple < moderate)
        assertTrue(moderate < complex)
        assertTrue(complex < expert)
    }

    // ========== 节点等级测试 ==========

    @Test
    fun `NodeLevel fromExperience returns correct level`() {
        assertEquals(NodeLevel.APPRENTICE, NodeLevel.fromExperience(0))
        assertEquals(NodeLevel.APPRENTICE, NodeLevel.fromExperience(500))
        assertEquals(NodeLevel.CRAFTSMAN, NodeLevel.fromExperience(1000))
        assertEquals(NodeLevel.CRAFTSMAN, NodeLevel.fromExperience(5000))
        assertEquals(NodeLevel.EXPERT, NodeLevel.fromExperience(10000))
        assertEquals(NodeLevel.EXPERT, NodeLevel.fromExperience(50000))
        assertEquals(NodeLevel.MASTER, NodeLevel.fromExperience(100000))
        assertEquals(NodeLevel.MASTER, NodeLevel.fromExperience(1000000))
    }

    @Test
    fun `NodeLevel has correct properties`() {
        assertEquals("学徒", NodeLevel.APPRENTICE.displayName)
        assertEquals(0, NodeLevel.APPRENTICE.requiredExperience)
        assertEquals(1, NodeLevel.APPRENTICE.maxConcurrentTasks)

        assertEquals("工匠", NodeLevel.CRAFTSMAN.displayName)
        assertEquals(1000, NodeLevel.CRAFTSMAN.requiredExperience)
        assertEquals(3, NodeLevel.CRAFTSMAN.maxConcurrentTasks)

        assertEquals("专家", NodeLevel.EXPERT.displayName)
        assertEquals(10000, NodeLevel.EXPERT.requiredExperience)
        assertEquals(5, NodeLevel.EXPERT.maxConcurrentTasks)

        assertEquals("大师", NodeLevel.MASTER.displayName)
        assertEquals(100000, NodeLevel.MASTER.requiredExperience)
        assertEquals(10, NodeLevel.MASTER.maxConcurrentTasks)
    }

    @Test
    fun `NodeLevel unlockAbilities increase with level`() {
        assertTrue(NodeLevel.APPRENTICE.unlockAbilities.isEmpty())
        assertTrue(NodeLevel.CRAFTSMAN.unlockAbilities.isNotEmpty())
        assertTrue(NodeLevel.EXPERT.unlockAbilities.size > NodeLevel.CRAFTSMAN.unlockAbilities.size)
        assertTrue(NodeLevel.MASTER.unlockAbilities.size > NodeLevel.EXPERT.unlockAbilities.size)
    }

    @Test
    fun `NodeLevel maxConcurrentTasks increase with level`() {
        assertTrue(NodeLevel.CRAFTSMAN.maxConcurrentTasks > NodeLevel.APPRENTICE.maxConcurrentTasks)
        assertTrue(NodeLevel.EXPERT.maxConcurrentTasks > NodeLevel.CRAFTSMAN.maxConcurrentTasks)
        assertTrue(NodeLevel.MASTER.maxConcurrentTasks > NodeLevel.EXPERT.maxConcurrentTasks)
    }

    // ========== 专业化层级测试 ==========

    @Test
    fun `SpecializationTier fromProficiency returns correct tier`() {
        assertEquals(SpecializationTier.NOVICE, SpecializationTier.fromProficiency(0.0f))
        assertEquals(SpecializationTier.NOVICE, SpecializationTier.fromProficiency(0.2f))
        assertEquals(SpecializationTier.COMPETENT, SpecializationTier.fromProficiency(0.25f))
        assertEquals(SpecializationTier.COMPETENT, SpecializationTier.fromProficiency(0.4f))
        assertEquals(SpecializationTier.PROFICIENT, SpecializationTier.fromProficiency(0.5f))
        assertEquals(SpecializationTier.PROFICIENT, SpecializationTier.fromProficiency(0.7f))
        assertEquals(SpecializationTier.EXPERT, SpecializationTier.fromProficiency(0.75f))
        assertEquals(SpecializationTier.EXPERT, SpecializationTier.fromProficiency(0.9f))
        assertEquals(SpecializationTier.MASTER, SpecializationTier.fromProficiency(1.0f))
    }

    @Test
    fun `SpecializationTier minProficiency is in ascending order`() {
        val tiers = SpecializationTier.values()
        for (i in 1 until tiers.size) {
            assertTrue(tiers[i].minProficiency >= tiers[i - 1].minProficiency)
        }
    }

    // ========== 成长进度测试 ==========

    @Test
    fun `NodeGrowthProgress INITIAL has sensible defaults`() {
        val initial = NodeGrowthProgress.INITIAL

        assertEquals(NodeLevel.APPRENTICE, initial.currentLevel)
        assertEquals(0, initial.totalExperience)
        assertEquals(0f, initial.progressPercent, 0.001f)
        assertEquals(NodeLevel.CRAFTSMAN, initial.nextLevel)
        assertEquals(0f, initial.recentGrowthRate, 0.001f)
    }

    // ========== 进化状态测试 ==========

    @Test
    fun `EvolutionState INITIAL has sensible defaults`() {
        val initial = EvolutionState.INITIAL

        assertEquals(NodeLevel.APPRENTICE, initial.level)
        assertEquals(0, initial.totalTasksCompleted)
        assertEquals(0f, initial.averageSuccessRate, 0.001f)
        assertEquals(0, initial.evolutionPoints)
        assertTrue(initial.specializations.isEmpty())
    }

    // ========== 疫苗条目测试 ==========

    @Test
    fun `VaccineEntry calculatePotency decreases with age`() {
        val problem = Problem(
            id = "p1",
            type = "inference",
            description = "Test problem",
            embedding = null,
            keywords = listOf("test"),
            complexity = ProblemComplexity.SIMPLE,
            category = "test"
        )

        val solution = Solution(
            id = "s1",
            approach = "Test solution",
            steps = emptyList(),
            resources = emptyList(),
            estimatedTimeMs = 1000,
            successIndicators = emptyList()
        )

        val freshVaccine = VaccineEntry(
            id = "v1",
            problem = problem,
            solution = solution,
            effectiveness = 0.9f,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 0,
            successRate = 1.0f
        )

        val oldVaccine = VaccineEntry(
            id = "v2",
            problem = problem,
            solution = solution,
            effectiveness = 0.9f,
            createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000), // 7 days ago
            lastUsedAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000),
            useCount = 0,
            successRate = 1.0f
        )

        // 新疫苗的效力应该更高
        assertTrue(freshVaccine.calculatePotency() > oldVaccine.calculatePotency())
    }

    @Test
    fun `VaccineEntry calculatePotency increases with use count`() {
        val problem = Problem(
            id = "p1",
            type = "inference",
            description = "Test problem",
            embedding = null,
            keywords = listOf("test"),
            complexity = ProblemComplexity.SIMPLE,
            category = "test"
        )

        val solution = Solution(
            id = "s1",
            approach = "Test solution",
            steps = emptyList(),
            resources = emptyList(),
            estimatedTimeMs = 1000,
            successIndicators = emptyList()
        )

        val lowUseVaccine = VaccineEntry(
            id = "v1",
            problem = problem,
            solution = solution,
            effectiveness = 0.8f,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 1,
            successRate = 1.0f
        )

        val highUseVaccine = VaccineEntry(
            id = "v2",
            problem = problem,
            solution = solution,
            effectiveness = 0.8f,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 100,
            successRate = 1.0f
        )

        // 高使用次数的疫苗效力应该更高
        assertTrue(highUseVaccine.calculatePotency() > lowUseVaccine.calculatePotency())
    }

    @Test
    fun `VaccineEntry isExpired works correctly`() {
        val problem = Problem(
            id = "p1",
            type = "inference",
            description = "Test problem",
            embedding = null,
            keywords = listOf("test"),
            complexity = ProblemComplexity.SIMPLE,
            category = "test"
        )

        val solution = Solution(
            id = "s1",
            approach = "Test solution",
            steps = emptyList(),
            resources = emptyList(),
            estimatedTimeMs = 1000,
            successIndicators = emptyList()
        )

        val freshVaccine = VaccineEntry(
            id = "v1",
            problem = problem,
            solution = solution,
            effectiveness = 0.9f,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 1,
            successRate = 1.0f
        )

        val unusedOldVaccine = VaccineEntry(
            id = "v2",
            problem = problem,
            solution = solution,
            effectiveness = 0.9f,
            createdAt = System.currentTimeMillis() - (31 * 24 * 60 * 60 * 1000), // 31 days ago
            lastUsedAt = System.currentTimeMillis() - (31 * 24 * 60 * 60 * 1000),
            useCount = 0, // Never used
            successRate = 1.0f
        )

        assertFalse(freshVaccine.isExpired())
        assertTrue(unusedOldVaccine.isExpired())
    }

    // ========== 问题定义测试 ==========

    @Test
    fun `Problem has correct properties`() {
        val problem = Problem(
            id = "p1",
            type = "translation",
            description = "Translate text from English to Chinese",
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            keywords = listOf("translation", "english", "chinese"),
            complexity = ProblemComplexity.MODERATE,
            category = "nlp",
            metadata = mapOf("source" to "user")
        )

        assertEquals("p1", problem.id)
        assertEquals("translation", problem.type)
        assertEquals(3, problem.keywords.size)
        assertEquals(ProblemComplexity.MODERATE, problem.complexity)
        assertEquals("nlp", problem.category)
        assertNotNull(problem.embedding)
    }

    @Test
    fun `Problem equality is based on id`() {
        val problem1 = Problem(
            id = "p1",
            type = "type1",
            description = "desc1",
            embedding = null,
            keywords = emptyList(),
            complexity = ProblemComplexity.SIMPLE,
            category = "cat1"
        )

        val problem2 = Problem(
            id = "p1",
            type = "type2",
            description = "desc2",
            embedding = null,
            keywords = listOf("different"),
            complexity = ProblemComplexity.COMPLEX,
            category = "cat2"
        )

        val problem3 = Problem(
            id = "p2",
            type = "type1",
            description = "desc1",
            embedding = null,
            keywords = emptyList(),
            complexity = ProblemComplexity.SIMPLE,
            category = "cat1"
        )

        assertEquals(problem1, problem2) // Same id
        assertNotEquals(problem1, problem3) // Different id
        assertEquals(problem1.hashCode(), problem2.hashCode())
    }

    // ========== 解决方案测试 ==========

    @Test
    fun `Solution has correct properties`() {
        val solution = Solution(
            id = "s1",
            approach = "Use neural machine translation",
            steps = listOf(
                SolutionStep(1, "Tokenize input", "Tokens", listOf("BPE")),
                SolutionStep(2, "Run model", "Logits", emptyList())
            ),
            resources = listOf("nmt_model", "tokenizer"),
            estimatedTimeMs = 5000,
            successIndicators = listOf("BLEU > 0.8"),
            metadata = mapOf("model" to "transformer")
        )

        assertEquals("s1", solution.id)
        assertEquals(2, solution.steps.size)
        assertEquals(2, solution.resources.size)
        assertEquals(5000L, solution.estimatedTimeMs)
        assertEquals(1, solution.successIndicators.size)
    }

    // ========== 进化事件测试 ==========

    @Test
    fun `EvolutionEvent types work correctly`() {
        val levelUp = EvolutionEvent.LevelUp(NodeLevel.CRAFTSMAN, System.currentTimeMillis())
        val unlocked = EvolutionEvent.CapabilityUnlocked("task_splitting", System.currentTimeMillis())
        val specialization = EvolutionEvent.SpecializationGained("translation", SpecializationTier.EXPERT, System.currentTimeMillis())
        val created = EvolutionEvent.VaccineCreated("v1", "inference", System.currentTimeMillis())
        val used = EvolutionEvent.VaccineUsed("v1", true, System.currentTimeMillis())

        assertTrue(levelUp is EvolutionEvent.LevelUp)
        assertEquals(NodeLevel.CRAFTSMAN, (levelUp as EvolutionEvent.LevelUp).newLevel)

        assertTrue(unlocked is EvolutionEvent.CapabilityUnlocked)
        assertEquals("task_splitting", (unlocked as EvolutionEvent.CapabilityUnlocked).capability)

        assertTrue(specialization is EvolutionEvent.SpecializationGained)
        assertEquals(SpecializationTier.EXPERT, (specialization as EvolutionEvent.SpecializationGained).tier)

        assertTrue(created is EvolutionEvent.VaccineCreated)
        assertTrue(used is EvolutionEvent.VaccineUsed)
    }

    // ========== 疫苗库统计测试 ==========

    @Test
    fun `VaccineLibraryStats has correct properties`() {
        val stats = VaccineLibraryStats(
            totalEntries = 100,
            effectiveEntries = 80,
            averagePotency = 0.75f,
            topCategories = listOf(
                CategoryCount("nlp", 50, 0.8f),
                CategoryCount("vision", 30, 0.7f)
            ),
            recentHits = 25,
            averageAge = 48,
            storageUsedMB = 10.5f
        )

        assertEquals(100, stats.totalEntries)
        assertEquals(80, stats.effectiveEntries)
        assertEquals(0.75f, stats.averagePotency, 0.001f)
        assertEquals(2, stats.topCategories.size)
        assertEquals(25, stats.recentHits)
        assertEquals(48L, stats.averageAge)
        assertEquals(10.5f, stats.storageUsedMB, 0.001f)
    }
}
