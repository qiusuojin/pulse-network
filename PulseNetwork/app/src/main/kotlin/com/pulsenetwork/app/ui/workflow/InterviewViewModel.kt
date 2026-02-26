package com.pulsenetwork.app.ui.workflow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.domain.workflow.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 访谈界面 ViewModel
 */
@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val interviewer: WorkflowInterviewer
) : ViewModel() {

    private val _interviewState = MutableLiveData<InterviewUiState>()
    val interviewState: LiveData<InterviewUiState> = _interviewState

    private val _currentQuestion = MutableLiveData<Question>()
    val currentQuestion: LiveData<Question> = _currentQuestion

    private var sessionId: String? = null
    private val messages = mutableListOf<InterviewMessage>()

    fun startInterview(initialPrompt: String) {
        viewModelScope.launch {
            val session = interviewer.startInterview(initialPrompt)
            sessionId = session.id

            _currentQuestion.value = session.currentQuestion
            messages.add(InterviewMessage(
                text = session.currentQuestion.text,
                isSystem = true
            ))

            updateState(session)
        }
    }

    fun submitAnswer(answer: String) {
        val id = sessionId ?: return

        // 添加用户消息
        messages.add(InterviewMessage(
            text = answer,
            isSystem = false
        ))

        viewModelScope.launch {
            when (val response = interviewer.answerQuestion(id, answer)) {
                is InterviewResponse.NextQuestion -> {
                    _currentQuestion.value = response.question
                    messages.add(InterviewMessage(
                        text = response.question.text,
                        isSystem = true
                    ))

                    _interviewState.value = _interviewState.value?.copy(
                        messages = messages.toList(),
                        progress = response.progress
                    ) ?: InterviewUiState(
                        messages = messages.toList(),
                        progress = response.progress,
                        isComplete = false
                    )
                }
                is InterviewResponse.InterviewComplete -> {
                    // 访谈完成
                    messages.add(InterviewMessage(
                        text = "✅ 访谈完成！\n\n${response.summary}",
                        isSystem = true
                    ))

                    _interviewState.value = InterviewUiState(
                        messages = messages.toList(),
                        progress = 1f,
                        isComplete = true
                    )

                    // 生成工作流
                    generateWorkflow()
                }
                is InterviewResponse.NeedClarification -> {
                    messages.add(InterviewMessage(
                        text = response.clarificationRequest,
                        isSystem = true
                    ))
                    _interviewState.value = _interviewState.value?.copy(
                        messages = messages.toList()
                    )
                }
            }
        }
    }

    private fun generateWorkflow() {
        val id = sessionId ?: return

        viewModelScope.launch {
            try {
                val workflow = interviewer.generateWorkflow(id)
                // TODO: 导航到工作流预览页面
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }

    private fun updateState(session: InterviewSession) {
        val state = interviewer.getInterviewState(session.id)

        _interviewState.value = InterviewUiState(
            messages = messages.toList(),
            progress = state.progress,
            phase = state.phase,
            questionsAsked = state.questionsAsked,
            questionsRemaining = state.questionsRemaining,
            isComplete = state.isComplete
        )
    }
}

/**
 * 访谈 UI 状态
 */
data class InterviewUiState(
    val messages: List<InterviewMessage>,
    val progress: Float,
    val phase: InterviewPhase = InterviewPhase.PROBLEM_DISCOVERY,
    val questionsAsked: Int = 0,
    val questionsRemaining: Int = 0,
    val isComplete: Boolean
)

/**
 * 访谈消息
 */
data class InterviewMessage(
    val text: String,
    val isSystem: Boolean
)
