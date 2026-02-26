package com.pulsenetwork.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.pulsenetwork.app.R
import com.pulsenetwork.app.databinding.FragmentDashboardBinding
import com.pulsenetwork.domain.evolution.NodeLevel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 仪表盘界面
 *
 * 展示网络状态、节点进化、关系网络、预测引擎的综合视图
 */
@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        // 网络 toggle 按钮
        binding.cardNetwork.btnToggleNetwork.setOnClickListener {
            viewModel.refresh()
        }

        // 查看网络详情
        binding.cardNetwork.btnViewNetwork.setOnClickListener {
            // 导航到网络页面
            // findNavController().navigate(R.id.action_dashboard_to_network)
        }

        // 刷新按钮
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun observeState() {
        // 整体仪表盘状态
        viewModel.dashboardState.observe(viewLifecycleOwner) { state ->
            updateOverallScore(state.overallScore)
            binding.statusMessage.text = state.statusMessage
        }

        // 网络状态
        viewModel.networkState.observe(viewLifecycleOwner) { state ->
            updateNetworkCard(state)
        }

        // 节点进化状态
        viewModel.evolutionState.observe(viewLifecycleOwner) { state ->
            updateEvolutionCard(state)
        }

        // 关系网络状态
        viewModel.relationState.observe(viewLifecycleOwner) { state ->
            updateRelationCard(state)
        }

        // 预测状态
        viewModel.predictionState.observe(viewLifecycleOwner) { state ->
            updatePredictionCard(state)
        }

        // 错误信息
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOverallScore(score: Float) {
        // 更新圆形进度指示器
        val percentage = (score * 100).toInt()
        binding.overallScoreProgress.progress = percentage
        binding.overallScoreText.text = "$percentage%"
    }

    private fun updateNetworkCard(state: NetworkDashboardState) {
        binding.cardNetwork.apply {
            // 状态指示
            statusBadge.text = if (state.isRunning) "在线" else "离线"
            statusBadge.setBackgroundResource(
                if (state.isRunning) R.drawable.bg_status_online
                else R.drawable.bg_status_offline
            )

            // 统计数据
            discoveredNodes.text = state.discoveredNodes.toString()
            connectedNodes.text = state.connectedNodes.toString()
            cacheHits.text = state.cacheHits.toString()

            // 健康度
            val health = if (state.discoveredNodes > 0) {
                (state.connectedNodes.toFloat() / state.discoveredNodes * 100).toInt()
            } else 0
            networkHealth.text = "$health%"
        }
    }

    private fun updateEvolutionCard(state: EvolutionDashboardState) {
        binding.cardEvolution.apply {
            // 等级显示
            levelName.text = state.currentLevel.displayName
            levelIcon.text = getLevelEmoji(state.currentLevel)

            // 经验值进度条
            experienceProgress.progress = state.levelProgress.toInt()
            experienceText.text = "${state.levelProgress.toInt()}%"

            // 专业化
            if (state.specializations.isNotEmpty()) {
                val topSpec = state.specializations.first()
                topSpecialization.text = "${topSpec.capability} (${getTierName(topSpec.tier)})"
            } else {
                topSpecialization.text = "暂无专业化"
            }

            // 疫苗库
            vaccineCount.text = state.vaccineCount.toString()
        }
    }

    private fun updateRelationCard(state: RelationDashboardState) {
        binding.cardRelation.apply {
            totalRelations.text = state.totalRelations.toString()
            trustedNodes.text = state.trustedNodes.toString()

            // 网络成熟度
            maturityProgress.progress = (state.networkMaturity * 100).toInt()
            maturityText.text = "${(state.networkMaturity * 100).toInt()}%"

            // 平均连接强度
            avgStrength.text = String.format("%.2f", state.averageStrength)
        }
    }

    private fun updatePredictionCard(state: PredictionDashboardState) {
        binding.cardPrediction.apply {
            accuracyText.text = "${(state.accuracy * 100).toInt()}%"
            explorationRatio.text = "${(state.explorationRatio * 100).toInt()}%"

            // 当前阶段
            phaseText.text = getPhaseName(state.currentPhase)

            // 预测需求列表
            if (state.topPredictedNeeds.isNotEmpty()) {
                predictionsList.text = state.topPredictedNeeds.joinToString("\n") {
                    "• ${it.type} (${(it.probability * 100).toInt()}%)"
                }
            } else {
                predictionsList.text = "暂无预测"
            }
        }
    }

    private fun getLevelEmoji(level: NodeLevel): String {
        return when (level) {
            NodeLevel.APPRENTICE -> "🎓"
            NodeLevel.CRAFTSMAN -> "🔧"
            NodeLevel.EXPERT -> "⚡"
            NodeLevel.MASTER -> "👑"
        }
    }

    private fun getTierName(tier: com.pulsenetwork.domain.evolution.SpecializationTier): String {
        return when (tier) {
            com.pulsenetwork.domain.evolution.SpecializationTier.NOVICE -> "新手"
            com.pulsenetwork.domain.evolution.SpecializationTier.COMPETENT -> "胜任"
            com.pulsenetwork.domain.evolution.SpecializationTier.PROFICIENT -> "熟练"
            com.pulsenetwork.domain.evolution.SpecializationTier.EXPERT -> "专家"
            com.pulsenetwork.domain.evolution.SpecializationTier.MASTER -> "大师"
        }
    }

    private fun getPhaseName(phase: com.pulsenetwork.domain.prediction.CriticalityPhase): String {
        return when (phase) {
            com.pulsenetwork.domain.prediction.CriticalityPhase.EXPLORATION -> "探索中"
            com.pulsenetwork.domain.prediction.CriticalityPhase.EXPLOITATION -> "利用中"
            com.pulsenetwork.domain.prediction.CriticalityPhase.BALANCED -> "平衡"
            com.pulsenetwork.domain.prediction.CriticalityPhase.CHAOTIC -> "混沌"
            com.pulsenetwork.domain.prediction.CriticalityPhase.ORDERED -> "有序"
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
