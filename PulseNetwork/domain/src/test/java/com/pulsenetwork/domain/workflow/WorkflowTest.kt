package com.pulsenetwork.domain.workflow

import org.junit.Assert.*
import org.junit.Test

/**
 * Workflow 模块测试
 */
class WorkflowTest {

    @Test
    fun `InterviewPhase has all expected values`() {
        val phases = InterviewPhase.values()
        assertEquals(6, phases.size)
        assertEquals(InterviewPhase.PROBLEM_DISCOVERY, phases[0])
        assertEquals(InterviewPhase.INPUT_CLARIFICATION, phases[1])
        assertEquals(InterviewPhase.OUTPUT_CLARIFICATION, phases[2])
        assertEquals(InterviewPhase.PROCESS_DEEP_DIVE, phases[3])
        assertEquals(InterviewPhase.EDGE_CASES, phases[4])
        assertEquals(InterviewPhase.CONFIRMATION, phases[5])
    }

    @Test
    fun `QuestionType has all expected values`() {
        val types = QuestionType.values()
        assertTrue(types.contains(QuestionType.OPEN_ENDED))
        assertTrue(types.contains(QuestionType.SINGLE_CHOICE))
        assertTrue(types.contains(QuestionType.MULTI_CHOICE))
        assertTrue(types.contains(QuestionType.YES_NO))
        assertTrue(types.contains(QuestionType.SCALE))
        assertTrue(types.contains(QuestionType.FILE_INPUT))
    }

    @Test
    fun `EncryptionLevel has correct hierarchy`() {
        val levels = EncryptionLevel.values()
        assertEquals(4, levels.size)
        assertEquals(EncryptionLevel.PUBLIC, levels[0])
        assertEquals(EncryptionLevel.PRIVATE, levels[1])
        assertEquals(EncryptionLevel.CONFIDENTIAL, levels[2])
        assertEquals(EncryptionLevel.TOP_SECRET, levels[3])
    }

    @Test
    fun `ExtractedWorkflowInfo can be created with default values`() {
        val info = ExtractedWorkflowInfo(
            problemStatement = "测试问题",
            inputs = emptyList(),
            outputs = emptyList(),
            processSteps = emptyList(),
            edgeCaseHandlers = emptyList(),
            constraints = emptyList()
        )

        assertEquals("测试问题", info.problemStatement)
        assertTrue(info.inputs.isEmpty())
        assertTrue(info.outputs.isEmpty())
    }

    @Test
    fun `WorkflowInput has correct properties`() {
        val input = WorkflowInput(
            name = "text_input",
            type = InputType.TEXT,
            description = "用户输入的文本",
            isRequired = true,
            defaultValue = null
        )

        assertEquals("text_input", input.name)
        assertEquals(InputType.TEXT, input.type)
        assertTrue(input.isRequired)
    }

    @Test
    fun `ProcessStep can have dependencies`() {
        val step1 = ProcessStep(
            id = "step1",
            name = "步骤1",
            description = "第一步",
            promptTemplate = "处理第一步",
            dependencies = emptyList()
        )

        val step2 = ProcessStep(
            id = "step2",
            name = "步骤2",
            description = "第二步",
            promptTemplate = "处理第二步",
            dependencies = listOf("step1")
        )

        assertTrue(step1.dependencies.isEmpty())
        assertEquals(1, step2.dependencies.size)
        assertEquals("step1", step2.dependencies[0])
    }

    @Test
    fun `EncryptedWorkflow has correct structure`() {
        val workflow = EncryptedWorkflow(
            id = "wf1",
            name = "测试工作流",
            description = "这是一个测试工作流",
            creatorId = "user1",
            createdAt = System.currentTimeMillis(),
            encryptedCore = ByteArray(10),
            encryptionLevel = EncryptionLevel.PRIVATE,
            publicInterface = WorkflowInterface(
                inputs = listOf(InputSpec("input", "TEXT", true, "输入")),
                outputs = listOf(OutputSpec("output", "TEXT", "输出")),
                estimatedCost = CostEstimate(100, 500, 5000, 512),
                executionConstraints = ExecutionConstraints()
            ),
            metadata = WorkflowMetadata(
                version = "1.0",
                tags = listOf("测试"),
                category = "通用"
            )
        )

        assertEquals("wf1", workflow.id)
        assertEquals(EncryptionLevel.PRIVATE, workflow.encryptionLevel)
        assertEquals("1.0", workflow.metadata.version)
    }

    @Test
    fun `InterviewSession can track progress`() {
        val session = InterviewSession(
            id = "session1",
            createdAt = System.currentTimeMillis(),
            phase = InterviewPhase.PROCESS_DEEP_DIVE,
            currentQuestion = Question(
                id = "q1",
                text = "测试问题?",
                type = QuestionType.OPEN_ENDED,
                isRequired = true
            ),
            history = listOf(
                QAPair("q0", "之前的问题?", "用户的回答", System.currentTimeMillis())
            ),
            extractedInfo = ExtractedWorkflowInfo(
                problemStatement = "测试",
                inputs = listOf(WorkflowInput("text", InputType.TEXT, "输入")),
                outputs = emptyList(),
                processSteps = emptyList(),
                edgeCaseHandlers = emptyList(),
                constraints = emptyList()
            )
        )

        assertEquals("session1", session.id)
        assertEquals(InterviewPhase.PROCESS_DEEP_DIVE, session.phase)
        assertEquals(1, session.history.size)
        assertEquals(1, session.extractedInfo.inputs.size)
    }
}
