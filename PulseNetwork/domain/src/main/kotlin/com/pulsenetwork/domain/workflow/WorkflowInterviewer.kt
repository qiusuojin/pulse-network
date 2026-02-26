package com.pulsenetwork.domain.workflow

/**
 * 访谈式工作流 (Socratic Interview Workflow)
 *
 * 核心理念：
 * - 人不一定知道自己想要什么，需要通过问题引导
 * - 苏格拉底式追问，层层深入，帮助用户理清思路
 * - 最终产出结构化的 JSON 工作流定义
 *
 * 工作流特点：
 * - 不写代码，用聊天封装人类的隐性经验
 * - 工作流被加密封装，网络节点只能调用 API 服务
 * - 支持条件分支、循环、并行执行
 */
interface WorkflowInterviewer {

    /**
     * 开始新的工作流访谈
     * @param initialPrompt 用户的初始想法
     * @return 访谈会话
     */
    suspend fun startInterview(initialPrompt: String): InterviewSession

    /**
     * 继续访谈，回答问题
     * @param sessionId 会话ID
     * @param answer 用户回答
     * @return 下一个问题或完成信号
     */
    suspend fun answerQuestion(sessionId: String, answer: String): InterviewResponse

    /**
     * 获取当前访谈状态
     */
    suspend fun getInterviewState(sessionId: String): InterviewState

    /**
     * 取消访谈
     */
    suspend fun cancelInterview(sessionId: String)

    /**
     * 从访谈生成工作流
     * @param sessionId 完成的访谈会话
     * @return 生成的加密工作流
     */
    suspend fun generateWorkflow(sessionId: String): EncryptedWorkflow
}

/**
 * 访谈会话
 */
data class InterviewSession(
    val id: String,
    val createdAt: Long,
    val phase: InterviewPhase,
    val currentQuestion: Question,
    val history: List<QAPair>,
    val extractedInfo: ExtractedWorkflowInfo
)

/**
 * 访谈阶段（苏格拉底式追问流程）
 */
enum class InterviewPhase {
    PROBLEM_DISCOVERY,     // 发现问题："你想解决什么问题？"
    INPUT_CLARIFICATION,   // 澄清输入："你有什么材料？"
    OUTPUT_CLARIFICATION,  // 澄清输出："你想要什么结果？"
    PROCESS_DEEP_DIVE,     // 深入流程："具体怎么处理？"
    EDGE_CASES,           // 边界情况："如果...怎么办？"
    CONFIRMATION          // 确认总结："我理解对吗？"
}

/**
 * 问题
 */
data class Question(
    val id: String,
    val text: String,
    val type: QuestionType,
    val options: List<String>? = null,    // 选项（如果是选择题）
    val isRequired: Boolean = true,
    val followUpCondition: ((String) -> Boolean)? = null
)

enum class QuestionType {
    OPEN_ENDED,      // 开放式问题
    SINGLE_CHOICE,   // 单选
    MULTI_CHOICE,    // 多选
    YES_NO,          // 是/否
    SCALE,           // 1-10 评分
    FILE_INPUT       // 需要文件输入
}

/**
 * 问答对
 */
data class QAPair(
    val questionId: String,
    val question: String,
    val answer: String,
    val timestamp: Long,
    val insights: List<String> = emptyList()    // 从回答中提取的洞察
)

/**
 * 访谈响应
 */
sealed class InterviewResponse {
    data class NextQuestion(
        val question: Question,
        val progress: Float    // 0.0-1.0
    ) : InterviewResponse()

    data class NeedClarification(
        val originalQuestion: String,
        val clarificationRequest: String
    ) : InterviewResponse()

    data class InterviewComplete(
        val summary: String,
        val extractedInfo: ExtractedWorkflowInfo
    ) : InterviewResponse()
}

/**
 * 从访谈中提取的工作流信息
 */
data class ExtractedWorkflowInfo(
    val problemStatement: String,
    val inputs: List<WorkflowInput>,
    val outputs: List<WorkflowOutput>,
    val processSteps: List<ProcessStep>,
    val edgeCaseHandlers: List<EdgeCaseHandler>,
    val constraints: List<Constraint>
)

data class WorkflowInput(
    val name: String,
    val type: InputType,
    val description: String,
    val isRequired: Boolean = true,
    val defaultValue: Any? = null
)

data class WorkflowOutput(
    val name: String,
    val type: OutputType,
    val description: String
)

data class ProcessStep(
    val id: String,
    val name: String,
    val description: String,
    val promptTemplate: String,
    val dependencies: List<String> = emptyList(),    // 依赖的前置步骤ID
    val condition: String? = null,                   // 执行条件
    val loopConfig: LoopConfig? = null               // 循环配置
)

data class EdgeCaseHandler(
    val condition: String,
    val action: String,
    val fallbackPrompt: String? = null
)

data class Constraint(
    val type: ConstraintType,
    val value: String
)

enum class InputType { TEXT, FILE, IMAGE, AUDIO, JSON, NUMBER, BOOLEAN }
enum class OutputType { TEXT, FILE, IMAGE, JSON }
enum class ConstraintType { MAX_LENGTH, MAX_TIME, MAX_TOKENS, FORMAT, LANGUAGE }

data class LoopConfig(
    val iterateOver: String,           // 迭代变量来源
    val maxIterations: Int = 10,
    val breakCondition: String? = null
)

/**
 * 访谈状态
 */
data class InterviewState(
    val sessionId: String,
    val phase: InterviewPhase,
    val progress: Float,
    val questionsAsked: Int,
    val questionsRemaining: Int,
    val isComplete: Boolean
)
