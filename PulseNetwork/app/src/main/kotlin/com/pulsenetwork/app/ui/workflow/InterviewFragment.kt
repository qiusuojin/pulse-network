package com.pulsenetwork.app.ui.workflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.pulsenetwork.app.R
import com.pulsenetwork.app.databinding.FragmentInterviewBinding
import com.pulsenetwork.domain.workflow.InterviewPhase
import com.pulsenetwork.domain.workflow.InterviewResponse
import com.pulsenetwork.domain.workflow.QuestionType
import dagger.hilt.android.AndroidEntryPoint

/**
 * 工作流访谈界面
 *
 * 苏格拉底式追问，帮助用户理清思路
 */
@AndroidEntryPoint
class InterviewFragment : Fragment() {

    private var _binding: FragmentInterviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InterviewViewModel by viewModels()
    private lateinit var adapter: InterviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInput()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = InterviewAdapter()
        binding.interviewList.adapter = adapter
    }

    private fun setupInput() {
        binding.submitBtn.setOnClickListener {
            val answer = binding.answerInput.text.toString().trim()
            if (answer.isNotEmpty()) {
                viewModel.submitAnswer(answer)
                binding.answerInput.text?.clear()
            }
        }

        // 选项点击
        binding.optionsChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                val answer = chip.text.toString()
                viewModel.submitAnswer(answer)
                group.clearCheck()
            }
        }
    }

    private fun observeState() {
        viewModel.interviewState.observe(viewLifecycleOwner) { state ->
            // 更新进度
            binding.progressText.text = "${state.questionsAsked + 1}/${state.questionsAsked + state.questionsRemaining + 1}"
            binding.progress.progress = (state.progress * 100).toInt()

            // 更新阶段
            binding.phaseText.text = getPhaseDisplayName(state.phase)

            // 更新消息列表
            adapter.submitList(state.messages)
            binding.interviewList.scrollToPosition(state.messages.size - 1)

            // 检查是否完成
            if (state.isComplete) {
                showCompletionDialog()
            }
        }

        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            // 显示选项（如果是选择题）
            if (question.type == QuestionType.SINGLE_CHOICE && !question.options.isNullOrEmpty()) {
                binding.optionsContainer.visibility = View.VISIBLE
                binding.optionsChipGroup.removeAllViews()

                question.options.forEach { option ->
                    val chip = Chip(requireContext()).apply {
                        text = option
                        isClickable = true
                        isCheckable = true
                    }
                    binding.optionsChipGroup.addView(chip)
                }
            } else {
                binding.optionsContainer.visibility = View.GONE
            }
        }
    }

    private fun getPhaseDisplayName(phase: InterviewPhase): String {
        return when (phase) {
            InterviewPhase.PROBLEM_DISCOVERY -> "发现问题"
            InterviewPhase.INPUT_CLARIFICATION -> "明确输入"
            InterviewPhase.OUTPUT_CLARIFICATION -> "明确输出"
            InterviewPhase.PROCESS_DEEP_DIVE -> "深入流程"
            InterviewPhase.EDGE_CASES -> "边界情况"
            InterviewPhase.CONFIRMATION -> "确认总结"
        }
    }

    private fun showCompletionDialog() {
        // 显示完成对话框，确认生成工作流
        // TODO: 使用 Navigation 导航到工作流预览页面
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
