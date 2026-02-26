package com.pulsenetwork.data.workflow

import com.pulsenetwork.domain.swarm.*
import com.pulsenetwork.domain.workflow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流执行引擎实现
 *
 * 支持多种执行模式：
 * - 顺序执行：按步骤顺序依次执行
 * - 并行执行：无依赖的步骤同时执行
 * - 自适应执行：根据网络负载动态调整
 */
@Singleton
class WorkflowExecutorImpl @Inject constructor(
    private val swarmNetwork: SwarmNetwork
) : WorkflowExecutor {

    // 活跃执行
    private val activeExecutions = ConcurrentHashMap<String, ActiveExecution>()

    // 执行进度流
    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<ExecutionProgress>>()

    // 步骤结果存储
    private val stepResults = ConcurrentHashMap<String, MutableMap<String, StepResult>>()

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun execute(workflow: ExecutableWorkflow): WorkflowExecutionResult {
        val workflowId = workflow.id
        val startTime = System.currentTimeMillis()

        // 初始化进度
        val progressFlow = MutableStateFlow(
            ExecutionProgress(
                workflowId = workflowId,
                status = ExecutionStatus.PENDING,
                currentStep = 0,
                totalSteps = workflow.steps.size,
                completedSteps = emptyList(),
                failedSteps = emptyList(),
                percentComplete = 0f,
                elapsedTimeMs = 0,
                estimatedRemainingMs = workflow.timeout,
                stepResults = emptyMap()
            )
        )
        progressFlows[workflowId] = progressFlow

        // 记录活跃执行
        activeExecutions[workflowId] = ActiveExecution(
            workflowId = workflowId,
            workflowName = workflow.name,
            startedAt = startTime,
            progress = progressFlow.value
        )

        // 初始化步骤结果存储
        stepResults[workflowId] = mutableMapOf()

        return try {
            when (workflow.executionMode) {
                ExecutionMode.SEQUENTIAL -> executeSequential(workflow, progressFlow)
                ExecutionMode.PARALLEL -> executeParallel(workflow, progressFlow)
                ExecutionMode.ADAPTIVE -> executeAdaptive(workflow, progressFlow)
            }
        } catch (e: CancellationException) {
            updateProgress(progressFlow) { it.copy(status = ExecutionStatus.CANCELLED) }
            WorkflowExecutionResult.Cancelled(
                workflowId = workflowId,
                cancelledAt = System.currentTimeMillis(),
                partialResults = stepResults[workflowId] ?: emptyMap()
            )
        } catch (e: Exception) {
            updateProgress(progressFlow) { it.copy(status = ExecutionStatus.FAILED) }
            WorkflowExecutionResult.Failure(
                workflowId = workflowId,
                error = e.message ?: "Unknown error",
                errorType = ErrorType.EXECUTION_FAILED,
                failedAtStep = progressFlow.value.currentStep.toString(),
                partialResults = stepResults[workflowId] ?: emptyMap()
            )
        } finally {
            activeExecutions.remove(workflowId)
        }
    }

    override fun executionProgressFlow(workflowId: String): Flow<ExecutionProgress> {
        return progressFlows.getOrPut(workflowId) {
            MutableStateFlow(
                ExecutionProgress(
                    workflowId = workflowId,
                    status = ExecutionStatus.PENDING,
                    currentStep = 0,
                    totalSteps = 0,
                    completedSteps = emptyList(),
                    failedSteps = emptyList(),
                    percentComplete = 0f,
                    elapsedTimeMs = 0,
                    estimatedRemainingMs = 0,
                    stepResults = emptyMap()
                )
            )
        }.asStateFlow()
    }

    override suspend fun cancel(workflowId: String): Boolean {
        val execution = activeExecutions[workflowId] ?: return false
        updateProgress(progressFlows[workflowId] ?: return false) {
            it.copy(status = ExecutionStatus.CANCELLED)
        }
        activeExecutions.remove(workflowId)
        return true
    }

    override fun getExecutionStatus(workflowId: String): ExecutionStatus? {
        return progressFlows[workflowId]?.value?.status
    }

    override fun getActiveExecutions(): List<ActiveExecution> {
        return activeExecutions.values.toList()
    }

    override suspend fun pause(workflowId: String): Boolean {
        val progressFlow = progressFlows[workflowId] ?: return false
        val current = progressFlow.value
        if (current.status != ExecutionStatus.RUNNING) return false

        updateProgress(progressFlow) { it.copy(status = ExecutionStatus.PAUSED) }
        return true
    }

    override suspend fun resume(workflowId: String): Boolean {
        val progressFlow = progressFlows[workflowId] ?: return false
        val current = progressFlow.value
        if (current.status != ExecutionStatus.PAUSED) return false

        updateProgress(progressFlow) { it.copy(status = ExecutionStatus.RUNNING) }
        return true
    }

    // ========== 私有方法 ==========

    private suspend fun executeSequential(
        workflow: ExecutableWorkflow,
        progressFlow: MutableStateFlow<ExecutionProgress>
    ): WorkflowExecutionResult {
        val startTime = System.currentTimeMillis()
        val results = mutableMapOf<String, StepResult>()
        val completedSteps = mutableListOf<String>()
        val failedSteps = mutableListOf<FailedStep>()
        var context = workflow.inputs.toMutableMap()

        updateProgress(progressFlow) { it.copy(status = ExecutionStatus.RUNNING) }

        // 按顺序排序步骤
        val sortedSteps = workflow.steps.sortedBy { it.order }

        for ((index, step) in sortedSteps.withIndex()) {
            // 检查是否被取消或暂停
            if (progressFlow.value.status == ExecutionStatus.CANCELLED) {
                break
            }

            while (progressFlow.value.status == ExecutionStatus.PAUSED) {
                delay(100)
            }

            // 更新当前步骤
            updateProgress(progressFlow) {
                it.copy(currentStep = index + 1)
            }

            // 执行步骤
            val stepResult = executeStep(step, context, workflow.retryPolicy)

            results[step.id] = stepResult
            stepResults[workflow.id]?.put(step.id, stepResult)

            if (stepResult.status == StepStatus.COMPLETED) {
                completedSteps.add(step.id)
                // 将步骤输出合并到上下文
                stepResult.output?.let { output ->
                    context.putAll(output)
                }
            } else if (stepResult.status == StepStatus.FAILED) {
                failedSteps.add(
                    FailedStep(
                        stepId = step.id,
                        error = stepResult.error ?: "Unknown error",
                        retryCount = step.retryCount,
                        lastAttemptAt = stepResult.timestamp
                    )
                )

                // 检查是否应该停止执行
                if (step.dependencies.isEmpty()) {
                    // 没有依赖的步骤失败，可以继续
                    continue
                }
                // 有依赖的步骤失败，停止执行
                break
            }

            // 更新进度
            val elapsed = System.currentTimeMillis() - startTime
            val percent = ((index + 1).toFloat() / sortedSteps.size) * 100
            val estimatedRemaining = if (index > 0) {
                (elapsed / (index + 1)) * (sortedSteps.size - index - 1)
            } else workflow.timeout

            updateProgress(progressFlow) {
                it.copy(
                    completedSteps = completedSteps.toList(),
                    failedSteps = failedSteps.toList(),
                    percentComplete = percent,
                    elapsedTimeMs = elapsed,
                    estimatedRemainingMs = estimatedRemaining,
                    stepResults = results.toMap()
                )
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        val finalStatus = progressFlow.value.status

        return when {
            failedSteps.isEmpty() && finalStatus != ExecutionStatus.CANCELLED -> {
                updateProgress(progressFlow) {
                    it.copy(status = ExecutionStatus.COMPLETED, percentComplete = 100f)
                }
                WorkflowExecutionResult.Success(
                    workflowId = workflow.id,
                    outputs = context.toMap(),
                    totalExecutionTimeMs = totalTime,
                    stepResults = results.toMap()
                )
            }
            completedSteps.isNotEmpty() -> {
                updateProgress(progressFlow) { it.copy(status = ExecutionStatus.COMPLETED) }
                WorkflowExecutionResult.PartialSuccess(
                    workflowId = workflow.id,
                    outputs = context.toMap(),
                    failedSteps = failedSteps,
                    totalExecutionTimeMs = totalTime
                )
            }
            else -> {
                updateProgress(progressFlow) { it.copy(status = ExecutionStatus.FAILED) }
                WorkflowExecutionResult.Failure(
                    workflowId = workflow.id,
                    error = "Execution failed at step ${failedSteps.firstOrNull()?.stepId}",
                    errorType = ErrorType.EXECUTION_FAILED,
                    failedAtStep = failedSteps.firstOrNull()?.stepId,
                    partialResults = results.toMap()
                )
            }
        }
    }

    private suspend fun executeParallel(
        workflow: ExecutableWorkflow,
        progressFlow: MutableStateFlow<ExecutionProgress>
    ): WorkflowExecutionResult {
        val startTime = System.currentTimeMillis()
        val results = ConcurrentHashMap<String, StepResult>()
        val context = ConcurrentHashMap<String, Any>().apply {
            putAll(workflow.inputs)
        }

        updateProgress(progressFlow) { it.copy(status = ExecutionStatus.RUNNING) }

        // 分组：独立步骤 vs 依赖步骤
        val (independent, dependent) = workflow.steps.partition { it.dependencies.isEmpty() }

        // 并行执行独立步骤
        val independentJobs = independent.map { step ->
            scope.async {
                step to executeStep(step, context.toMap(), workflow.retryPolicy)
            }
        }

        // 等待所有独立步骤完成
        independentJobs.awaitAll().forEach { (step, result) ->
            results[step.id] = result
            result.output?.let { context.putAll(it) }
        }

        // 顺序执行依赖步骤
        for (step in dependent.sortedBy { it.order }) {
            if (progressFlow.value.status == ExecutionStatus.CANCELLED) break

            val result = executeStep(step, context.toMap(), workflow.retryPolicy)
            results[step.id] = result
            result.output?.let { context.putAll(it) }
        }

        val totalTime = System.currentTimeMillis() - startTime
        val failedSteps = results.filter { it.value.status == StepStatus.FAILLED }
            .map { (id, result) ->
                FailedStep(id, result.error ?: "Unknown error", 0, result.timestamp)
            }

        return if (failedSteps.isEmpty()) {
            WorkflowExecutionResult.Success(
                workflowId = workflow.id,
                outputs = context.toMap(),
                totalExecutionTimeMs = totalTime,
                stepResults = results.toMap()
            )
        } else {
            WorkflowExecutionResult.PartialSuccess(
                workflowId = workflow.id,
                outputs = context.toMap(),
                failedSteps = failedSteps,
                totalExecutionTimeMs = totalTime
            )
        }
    }

    private suspend fun executeAdaptive(
        workflow: ExecutableWorkflow,
        progressFlow: MutableStateFlow<ExecutionProgress>
    ): WorkflowExecutionResult {
        // 自适应执行：根据网络状态决定执行策略
        val networkStats = swarmNetwork.getStats()
        val networkLoad = if (networkStats.connectedNodes > 0) {
            networkStats.connectedNodes.toFloat() / 10f  // 假设10个节点为满载
        } else 1f

        // 网络负载高时使用顺序执行，否则使用并行
        return if (networkLoad > 0.7f) {
            executeSequential(workflow, progressFlow)
        } else {
            executeParallel(workflow, progressFlow)
        }
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        context: Map<String, Any>,
        retryPolicy: RetryPolicy
    ): StepResult {
        val startTime = System.currentTimeMillis()
        var lastError: String? = null
        var attempts = 0

        repeat(retryPolicy.maxRetries + 1) {
            attempts++
            try {
                val result = when (step.type) {
                    StepType.LOCAL_INFERENCE -> executeLocalInference(step, context)
                    StepType.REMOTE_INFERENCE -> executeRemoteInference(step, context)
                    StepType.DATA_TRANSFORM -> executeDataTransform(step, context)
                    StepType.CONDITION_CHECK -> executeConditionCheck(step, context)
                    StepType.PARALLEL_BATCH -> executeParallelBatch(step, context)
                    StepType.AGGREGATION -> executeAggregation(step, context)
                    StepType.EXTERNAL_API -> executeExternalApi(step, context)
                    StepType.USER_INPUT -> executeUserInput(step, context)
                }

                return result.copy(
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                lastError = e.message
                if (attempts <= retryPolicy.maxRetries) {
                    val delay = if (retryPolicy.exponentialBackoff) {
                        retryPolicy.retryDelayMs * (1L shl (attempts - 1))
                    } else {
                        retryPolicy.retryDelayMs
                    }
                    delay(delay)
                }
            }
        }

        return StepResult(
            stepId = step.id,
            status = StepStatus.FAILED,
            output = null,
            error = lastError ?: "Unknown error after $attempts attempts",
            executionTimeMs = System.currentTimeMillis() - startTime,
            executedBy = null
        )
    }

    private suspend fun executeLocalInference(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.LocalInference

        // 构建prompt
        val prompt = buildPrompt(config.promptTemplate, context)

        // TODO: 实际调用本地模型推理
        // 这里返回模拟结果
        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = mapOf(
                "response" to "[Local inference result for: ${prompt.take(50)}...]",
                "model" to config.modelType
            ),
            error = null,
            executionTimeMs = 0,
            executedBy = "local"
        )
    }

    private suspend fun executeRemoteInference(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.RemoteInference
        val prompt = buildPrompt(config.promptTemplate, context)

        // 选择合适的节点
        val nodes = swarmNetwork.getDiscoveredNodes()
            .filter { it.connectionState == ConnectionState.CONNECTED }

        val selectedNode = if (config.preferredNodes.isNotEmpty()) {
            nodes.find { it.id in config.preferredNodes }
        } else {
            nodes.firstOrNull()
        }

        if (selectedNode == null) {
            return StepResult(
                stepId = step.id,
                status = StepStatus.FAILED,
                output = null,
                error = "No available nodes for remote inference",
                executionTimeMs = 0,
                executedBy = null
            )
        }

        // 发送推理请求
        val message = SwarmMessage(
            id = UUID.randomUUID().toString(),
            type = MessageType.TASK_REQUEST,
            senderId = "local",
            recipientId = selectedNode.id,
            timestamp = System.currentTimeMillis(),
            payload = MessagePayload.TaskRequest(
                taskId = step.id,
                taskType = "inference",
                input = mapOf("prompt" to prompt, "model" to config.modelType),
                requirements = TaskRequirements(
                    minMemoryMB = 1024,
                    requiresNPU = false,
                    estimatedTimeMs = step.timeout
                ),
                timeout = step.timeout
            ),
            priority = MessagePriority.NORMAL
        )

        val result = swarmNetwork.sendToNode(selectedNode.id, message)

        return when (result) {
            is SendMessageResult.Success -> StepResult(
                stepId = step.id,
                status = StepStatus.COMPLETED,
                output = mapOf("response" to "Remote inference sent"),
                error = null,
                executionTimeMs = 0,
                executedBy = selectedNode.id
            )
            is SendMessageResult.Failure -> StepResult(
                stepId = step.id,
                status = StepStatus.FAILED,
                output = null,
                error = result.error,
                executionTimeMs = 0,
                executedBy = selectedNode.id
            )
        }
    }

    private fun executeDataTransform(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.DataTransform
        val output = mutableMapOf<String, Any>()

        config.inputMapping.forEach { (inputKey, outputKey) ->
            context[inputKey]?.let { value ->
                output[outputKey] = value
            }
        }

        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = output,
            error = null,
            executionTimeMs = 0,
            executedBy = "local"
        )
    }

    private fun executeConditionCheck(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.ConditionCheck

        // 简单的条件评估（实际实现需要更复杂的表达式引擎）
        val result = evaluateCondition(config.condition, context)

        val nextStep = if (result) config.trueBranch else config.falseBranch

        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = mapOf(
                "conditionResult" to result,
                "nextStep" to (nextStep ?: "end")
            ),
            error = null,
            executionTimeMs = 0,
            executedBy = "local"
        )
    }

    private suspend fun executeParallelBatch(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.ParallelBatch

        // TODO: 实现真正的并行批处理
        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = mapOf("batchResult" to "processed ${config.batchSize} items"),
            error = null,
            executionTimeMs = 0,
            executedBy = "local"
        )
    }

    private fun executeAggregation(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        val config = step.config as StepConfig.Aggregation

        val values = config.inputKeys.mapNotNull { context[it] }
        val aggregated = when (config.aggregationType) {
            AggregationType.CONCAT -> values.joinToString("\n")
            AggregationType.FIRST_VALID -> values.firstOrNull() ?: ""
            else -> values.toString()
        }

        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = mapOf("aggregated" to aggregated),
            error = null,
            executionTimeMs = 0,
            executedBy = "local"
        )
    }

    private suspend fun executeExternalApi(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        // TODO: 实现外部API调用
        return StepResult(
            stepId = step.id,
            status = StepStatus.COMPLETED,
            output = mapOf("apiResponse" to "mock response"),
            error = null,
            executionTimeMs = 0,
            executedBy = "external"
        )
    }

    private suspend fun executeUserInput(
        step: WorkflowStep,
        context: Map<String, Any>
    ): StepResult {
        // 用户输入需要暂停等待，这里返回等待状态
        // 实际实现中需要与UI层交互
        return StepResult(
            stepId = step.id,
            status = StepStatus.PENDING,
            output = null,
            error = "Waiting for user input",
            executionTimeMs = 0,
            executedBy = "user"
        )
    }

    private fun buildPrompt(template: String, context: Map<String, Any>): String {
        var prompt = template
        context.forEach { (key, value) ->
            prompt = prompt.replace("{{${key}}}", value.toString())
        }
        return prompt
    }

    private fun evaluateCondition(condition: String, context: Map<String, Any>): Boolean {
        // 简单的条件评估
        // 支持: key == value, key != value, key > value, key < value
        val patterns = listOf(
            "(\\w+)\\s*==\\s*(.+)".toRegex(),
            "(\\w+)\\s*!=\\s*(.+)".toRegex(),
            "(\\w+)\\s*>\\s*(.+)".toRegex(),
            "(\\w+)\\s*<\\s*(.+)".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(condition) ?: continue
            val key = match.groupValues[1]
            val expectedValue = match.groupValues[2].trim().removeSurrounding("\"", "\"")
            val actualValue = context[key]?.toString() ?: continue

            return when {
                condition.contains("==") -> actualValue == expectedValue
                condition.contains("!=") -> actualValue != expectedValue
                condition.contains(">") -> (actualValue.toDoubleOrNull() ?: 0.0) > (expectedValue.toDoubleOrNull() ?: 0.0)
                condition.contains("<") -> (actualValue.toDoubleOrNull() ?: 0.0) < (expectedValue.toDoubleOrNull() ?: 0.0)
                else -> false
            }
        }

        return false
    }

    private inline fun updateProgress(
        progressFlow: MutableStateFlow<ExecutionProgress>,
        update: (ExecutionProgress) -> ExecutionProgress
    ) {
        progressFlow.update { current ->
            update(current).also { updated ->
                // 更新活跃执行记录
                activeExecutions[updated.workflowId]?.let { execution ->
                    activeExecutions[execution.workflowId] = execution.copy(progress = updated)
                }
            }
        }
    }
}
