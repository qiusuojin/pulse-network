package com.pulsenetwork.data.workflow

import com.pulsenetwork.domain.workflow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 访谈式工作流服务实现
 *
 * 苏格拉底式追问，帮助用户理清思路
 * 最终产出结构化的加密工作流
 */
@Singleton
class WorkflowInterviewerImpl @Inject constructor(
    // 后续注入 LLM 服务
) : WorkflowInterviewer {

    // 活跃的访谈会话
    private val sessions = mutableMapOf<String, InterviewSession>()

    // 问题模板库
    private val questionTemplates = QuestionTemplateLibrary()

    override suspend fun startInterview(initialPrompt: String): InterviewSession {
        val sessionId = UUID.randomUUID().toString()

        // 分析初始输入，确定起点
        val initialPhase = determineInitialPhase(initialPrompt)
        val firstQuestion = questionTemplates.getFirstQuestion(initialPhase)

        val session = InterviewSession(
            id = sessionId,
            createdAt = System.currentTimeMillis(),
            phase = initialPhase,
            currentQuestion = firstQuestion,
            history = emptyList(),
            extractedInfo = ExtractedWorkflowInfo(
                problemStatement = initialPrompt,
                inputs = emptyList(),
                outputs = emptyList(),
                processSteps = emptyList(),
                edgeCaseHandlers = emptyList(),
                constraints = emptyList()
            )
        )

        sessions[sessionId] = session
        return session
    }

    override suspend fun answerQuestion(sessionId: String, answer: String): InterviewResponse {
        val session = sessions[sessionId]
            ?: return InterviewResponse.NeedClarification("", "会话不存在")

        // 记录回答
        val qaPair = QAPair(
            questionId = session.currentQuestion.id,
            question = session.currentQuestion.text,
            answer = answer,
            timestamp = System.currentTimeMillis(),
            insights = extractInsights(answer, session.phase)
        )

        val updatedHistory = session.history + qaPair

        // 更新提取的信息
        val updatedExtractedInfo = updateExtractedInfo(
            session.extractedInfo,
            session.phase,
            answer,
            qaPair.insights
        )

        // 确定下一阶段
        val nextPhase = determineNextPhase(session.phase, answer, updatedExtractedInfo)

        // 检查是否完成
        if (nextPhase == null || shouldCompleteInterview(updatedExtractedInfo)) {
            // 访谈完成
            val completedSession = session.copy(
                history = updatedHistory,
                extractedInfo = updatedExtractedInfo,
                phase = InterviewPhase.CONFIRMATION
            )
            sessions[sessionId] = completedSession

            return InterviewResponse.InterviewComplete(
                summary = generateSummary(updatedExtractedInfo),
                extractedInfo = updatedExtractedInfo
            )
        }

        // 获取下一个问题
        val nextQuestion = questionTemplates.getNextQuestion(
            nextPhase,
            updatedHistory,
            updatedExtractedInfo
        )

        // 更新会话
        val updatedSession = session.copy(
            phase = nextPhase,
            currentQuestion = nextQuestion,
            history = updatedHistory,
            extractedInfo = updatedExtractedInfo
        )
        sessions[sessionId] = updatedSession

        val progress = calculateProgress(nextPhase, updatedHistory.size)

        return InterviewResponse.NextQuestion(
            question = nextQuestion,
            progress = progress
        )
    }

    override suspend fun getInterviewState(sessionId: String): InterviewState {
        val session = sessions[sessionId]
            ?: return InterviewState(
                sessionId = sessionId,
                phase = InterviewPhase.PROBLEM_DISCOVERY,
                progress = 0f,
                questionsAsked = 0,
                questionsRemaining = 10,
                isComplete = false
            )

        return InterviewState(
            sessionId = sessionId,
            phase = session.phase,
            progress = calculateProgress(session.phase, session.history.size),
            questionsAsked = session.history.size,
            questionsRemaining = estimateRemainingQuestions(session),
            isComplete = session.phase == InterviewPhase.CONFIRMATION
        )
    }

    override suspend fun cancelInterview(sessionId: String) {
        sessions.remove(sessionId)
    }

    override suspend fun generateWorkflow(sessionId: String): EncryptedWorkflow {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("会话不存在: $sessionId")

        if (session.phase != InterviewPhase.CONFIRMATION) {
            throw IllegalStateException("访谈尚未完成")
        }

        // 生成工作流步骤
        val steps = generateProcessSteps(session.extractedInfo)

        // 创建工作流定义
        val workflowJson = createWorkflowJson(session.extractedInfo, steps)

        // 加密（简化实现）
        val encryptedCore = encryptWorkflow(workflowJson)

        return EncryptedWorkflow(
            id = UUID.randomUUID().toString(),
            name = session.extractedInfo.problemStatement.take(50),
            description = generateSummary(session.extractedInfo),
            creatorId = "local",
            createdAt = System.currentTimeMillis(),
            encryptedCore = encryptedCore,
            encryptionLevel = EncryptionLevel.PRIVATE,
            publicInterface = createPublicInterface(session.extractedInfo),
            metadata = WorkflowMetadata(
                version = "1.0",
                tags = extractTags(session.extractedInfo),
                category = categorizeWorkflow(session.extractedInfo)
            )
        )
    }

    // ========== 私有方法 ==========

    private fun determineInitialPhase(prompt: String): InterviewPhase {
        // 分析提示词，确定从哪个阶段开始
        val lowerPrompt = prompt.lowercase()

        return when {
            lowerPrompt.contains("我想") || lowerPrompt.contains("需要") -> InterviewPhase.PROBLEM_DISCOVERY
            lowerPrompt.contains("输入") || lowerPrompt.contains("数据") -> InterviewPhase.INPUT_CLARIFICATION
            lowerPrompt.contains("输出") || lowerPrompt.contains("结果") -> InterviewPhase.OUTPUT_CLARIFICATION
            else -> InterviewPhase.PROBLEM_DISCOVERY
        }
    }

    private fun determineNextPhase(
        currentPhase: InterviewPhase,
        answer: String,
        extractedInfo: ExtractedWorkflowInfo
    ): InterviewPhase? {
        return when (currentPhase) {
            InterviewPhase.PROBLEM_DISCOVERY -> {
                if (extractedInfo.inputs.isEmpty()) InterviewPhase.INPUT_CLARIFICATION
                else if (extractedInfo.outputs.isEmpty()) InterviewPhase.OUTPUT_CLARIFICATION
                else InterviewPhase.PROCESS_DEEP_DIVE
            }
            InterviewPhase.INPUT_CLARIFICATION -> {
                if (extractedInfo.outputs.isEmpty()) InterviewPhase.OUTPUT_CLARIFICATION
                else InterviewPhase.PROCESS_DEEP_DIVE
            }
            InterviewPhase.OUTPUT_CLARIFICATION -> InterviewPhase.PROCESS_DEEP_DIVE
            InterviewPhase.PROCESS_DEEP_DIVE -> InterviewPhase.EDGE_CASES
            InterviewPhase.EDGE_CASES -> InterviewPhase.CONFIRMATION
            InterviewPhase.CONFIRMATION -> null
        }
    }

    private fun shouldCompleteInterview(extractedInfo: ExtractedWorkflowInfo): Boolean {
        // 检查是否有足够的信息生成工作流
        return extractedInfo.problemStatement.isNotEmpty() &&
                extractedInfo.inputs.isNotEmpty() &&
                extractedInfo.outputs.isNotEmpty() &&
                extractedInfo.processSteps.isNotEmpty()
    }

    private fun extractInsights(answer: String, phase: InterviewPhase): List<String> {
        val insights = mutableListOf<String>()

        // 简单的关键词提取
        when (phase) {
            InterviewPhase.PROBLEM_DISCOVERY -> {
                if (answer.contains("自动化")) insights.add("需要自动化")
                if (answer.contains("效率")) insights.add("关注效率")
                if (answer.contains("批量")) insights.add("批量处理")
            }
            InterviewPhase.INPUT_CLARIFICATION -> {
                if (answer.contains("文件")) insights.add("文件输入")
                if (answer.contains("文本")) insights.add("文本输入")
                if (answer.contains("API")) insights.add("API输入")
            }
            else -> {}
        }

        return insights
    }

    private fun updateExtractedInfo(
        current: ExtractedWorkflowInfo,
        phase: InterviewPhase,
        answer: String,
        insights: List<String>
    ): ExtractedWorkflowInfo {
        return when (phase) {
            InterviewPhase.PROBLEM_DISCOVERY -> {
                current.copy(
                    problemStatement = if (current.problemStatement.isEmpty()) answer else current.problemStatement
                )
            }
            InterviewPhase.INPUT_CLARIFICATION -> {
                val newInputs = parseInputs(answer, insights)
                current.copy(inputs = current.inputs + newInputs)
            }
            InterviewPhase.OUTPUT_CLARIFICATION -> {
                val newOutputs = parseOutputs(answer)
                current.copy(outputs = current.outputs + newOutputs)
            }
            InterviewPhase.PROCESS_DEEP_DIVE -> {
                val newSteps = parseProcessSteps(answer)
                current.copy(processSteps = current.processSteps + newSteps)
            }
            InterviewPhase.EDGE_CASES -> {
                val newHandlers = parseEdgeCases(answer)
                current.copy(edgeCaseHandlers = current.edgeCaseHandlers + newHandlers)
            }
            InterviewPhase.CONFIRMATION -> current
        }
    }

    private fun parseInputs(answer: String, insights: List<String>): List<WorkflowInput> {
        val inputs = mutableListOf<WorkflowInput>()

        if (insights.contains("文件输入")) {
            inputs.add(WorkflowInput("file", InputType.FILE, "输入文件", true))
        }
        if (insights.contains("文本输入")) {
            inputs.add(WorkflowInput("text", InputType.TEXT, "输入文本", true))
        }

        if (inputs.isEmpty()) {
            inputs.add(WorkflowInput("input", InputType.TEXT, "输入内容", true))
        }

        return inputs
    }

    private fun parseOutputs(answer: String): List<WorkflowOutput> {
        return listOf(
            WorkflowOutput("result", OutputType.TEXT, "处理结果")
        )
    }

    private fun parseProcessSteps(answer: String): List<ProcessStep> {
        // 按数字或换行分割步骤
        val steps = answer.split(Regex("\\d+\\.|\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return steps.mapIndexed { index, stepText ->
            ProcessStep(
                id = "step_$index",
                name = "步骤 ${index + 1}",
                description = stepText,
                promptTemplate = stepText,
                dependencies = if (index > 0) listOf("step_${index - 1}") else emptyList()
            )
        }
    }

    private fun parseEdgeCases(answer: String): List<EdgeCaseHandler> {
        return listOf(
            EdgeCaseHandler(
                condition = "输入为空",
                action = "返回错误提示"
            )
        )
    }

    private fun calculateProgress(phase: InterviewPhase, questionsAsked: Int): Float {
        val phases = InterviewPhase.values()
        val phaseIndex = phases.indexOf(phase)
        val baseProgress = phaseIndex.toFloat() / phases.size
        val questionBonus = (questionsAsked % 3) * 0.05f

        return (baseProgress + questionBonus).coerceIn(0f, 1f)
    }

    private fun estimateRemainingQuestions(session: InterviewSession): Int {
        val phasesLeft = InterviewPhase.values().size - InterviewPhase.values().indexOf(session.phase) - 1
        return phasesLeft * 3
    }

    private fun generateSummary(extractedInfo: ExtractedWorkflowInfo): String {
        return """
            |工作流: ${extractedInfo.problemStatement}
            |输入: ${extractedInfo.inputs.joinToString(", ") { it.name }}
            |输出: ${extractedInfo.outputs.joinToString(", ") { it.name }}
            |步骤数: ${extractedInfo.processSteps.size}
        """.trimMargin()
    }

    private fun generateProcessSteps(extractedInfo: ExtractedWorkflowInfo): List<ProcessStep> {
        return if (extractedInfo.processSteps.isEmpty()) {
            listOf(
                ProcessStep(
                    id = "main",
                    name = "主处理",
                    description = extractedInfo.problemStatement,
                    promptTemplate = "处理输入: \${input}"
                )
            )
        } else {
            extractedInfo.processSteps
        }
    }

    private fun createWorkflowJson(
        extractedInfo: ExtractedWorkflowInfo,
        steps: List<ProcessStep>
    ): ByteArray {
        val json = """
            {
                "problem": "${extractedInfo.problemStatement}",
                "inputs": ${extractedInfo.inputs.map { it.name }},
                "outputs": ${extractedInfo.outputs.map { it.name }},
                "steps": ${steps.map { it.name }}
            }
        """.trimIndent()
        return json.toByteArray()
    }

    private fun encryptWorkflow(data: ByteArray): ByteArray {
        // 简化实现，实际应使用加密服务
        return data
    }

    private fun createPublicInterface(extractedInfo: ExtractedWorkflowInfo): WorkflowInterface {
        return WorkflowInterface(
            inputs = extractedInfo.inputs.map {
                InputSpec(it.name, it.type.name, it.isRequired, it.description)
            },
            outputs = extractedInfo.outputs.map {
                OutputSpec(it.name, it.type.name, it.description)
            },
            estimatedCost = CostEstimate(
                minTokens = 100,
                maxTokens = 1000,
                estimatedTimeMs = 5000,
                requiredMemoryMB = 512
            ),
            executionConstraints = ExecutionConstraints()
        )
    }

    private fun extractTags(extractedInfo: ExtractedWorkflowInfo): List<String> {
        val tags = mutableListOf<String>()
        extractedInfo.problemStatement.lowercase().let { text ->
            if (text.contains("翻译")) tags.add("翻译")
            if (text.contains("总结")) tags.add("总结")
            if (text.contains("分析")) tags.add("分析")
            if (text.contains("生成")) tags.add("生成")
        }
        return tags.ifEmpty { listOf("通用") }
    }

    private fun categorizeWorkflow(extractedInfo: ExtractedWorkflowInfo): String {
        val tags = extractTags(extractedInfo)
        return tags.firstOrNull() ?: "其他"
    }
}

/**
 * 问题模板库
 */
class QuestionTemplateLibrary {

    private val templates = mapOf<InterviewPhase, List<QuestionTemplate>>(
        InterviewPhase.PROBLEM_DISCOVERY to listOf(
            QuestionTemplate(
                "你想解决什么问题？",
                QuestionType.OPEN_ENDED,
                "描述你希望实现的目标"
            ),
            QuestionTemplate(
                "这个问题目前是怎么解决的？",
                QuestionType.OPEN_ENDED,
                "描述现有的解决方案"
            ),
            QuestionTemplate(
                "你希望自动化到什么程度？",
                QuestionType.SINGLE_CHOICE,
                listOf("完全自动", "半自动辅助", "提供建议")
            )
        ),
        InterviewPhase.INPUT_CLARIFICATION to listOf(
            QuestionTemplate(
                "你有什么材料作为输入？",
                QuestionType.OPEN_ENDED,
                "描述输入数据的格式和来源"
            ),
            QuestionTemplate(
                "输入数据的规模大概是多少？",
                QuestionType.SINGLE_CHOICE,
                listOf("单条数据", "小批量(<100)", "大批量(>100)")
            )
        ),
        InterviewPhase.OUTPUT_CLARIFICATION to listOf(
            QuestionTemplate(
                "你希望得到什么样的输出？",
                QuestionType.OPEN_ENDED,
                "描述期望的输出格式"
            ),
            QuestionTemplate(
                "输出的质量要求是什么？",
                QuestionType.SINGLE_CHOICE,
                listOf("快速即可", "需要准确", "必须精确")
            )
        ),
        InterviewPhase.PROCESS_DEEP_DIVE to listOf(
            QuestionTemplate(
                "请描述处理的步骤，每行一个步骤",
                QuestionType.OPEN_ENDED,
                "例如：1. 读取文件 2. 分析内容 3. 生成报告"
            )
        ),
        InterviewPhase.EDGE_CASES to listOf(
            QuestionTemplate(
                "如果输入数据有问题，应该怎么处理？",
                QuestionType.OPEN_ENDED,
                "描述异常处理策略"
            )
        ),
        InterviewPhase.CONFIRMATION to listOf(
            QuestionTemplate(
                "以上理解正确吗？需要修改吗？",
                QuestionType.YES_NO,
                null
            )
        )
    )

    fun getFirstQuestion(phase: InterviewPhase): Question {
        val phaseTemplates = templates[phase] ?: return Question(
            id = "default",
            text = "请描述你想要实现的功能",
            type = QuestionType.OPEN_ENDED,
            isRequired = true
        )

        val template = phaseTemplates.first()
        return Question(
            id = "${phase.name}_0",
            text = template.text,
            type = template.type,
            options = template.options,
            isRequired = true
        )
    }

    fun getNextQuestion(
        phase: InterviewPhase,
        history: List<QAPair>,
        extractedInfo: ExtractedWorkflowInfo
    ): Question {
        val phaseTemplates = templates[phase] ?: return getFirstQuestion(phase)

        // 找到当前阶段已问过的问题数量
        val askedInPhase = history.count { qa ->
            phaseTemplates.any { it.text == qa.question }
        }

        val templateIndex = askedInPhase.coerceIn(0, phaseTemplates.size - 1)
        val template = phaseTemplates[templateIndex]

        return Question(
            id = "${phase.name}_$templateIndex",
            text = template.text,
            type = template.type,
            options = template.options,
            isRequired = true
        )
    }
}

data class QuestionTemplate(
    val text: String,
    val type: QuestionType,
    val hint: String?,
    val options: List<String>? = null
)
