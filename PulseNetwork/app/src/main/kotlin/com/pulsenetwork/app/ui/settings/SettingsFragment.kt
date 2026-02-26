package com.pulsenetwork.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.pulsenetwork.app.databinding.FragmentSettingsBinding
import com.pulsenetwork.domain.governor.ThermalState
import dagger.hilt.android.AndroidEntryPoint

/**
 * 设置界面
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        // 加载模型按钮
        binding.btnLoadModel.setOnClickListener {
            // TODO: 打开文件选择器选择模型
        }

        // 网络任务开关
        binding.switchNetworkTasks.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNetworkTasksEnabled(isChecked)
        }
    }

    private fun observeState() {
        // 总督状态
        viewModel.governorStatus.observe(viewLifecycleOwner) { status ->
            // 更新电池状态显示
            val batteryText = "${status.batteryLevel}%"
            val chargingText = if (status.isCharging) "充电中" else "未充电"
            val tempText = "${status.batteryTemperature}°C"

            // 更新网络任务可用性
            binding.switchNetworkTasks.isEnabled = status.canAcceptNetworkTasks
            if (!status.canAcceptNetworkTasks && binding.switchNetworkTasks.isChecked) {
                binding.switchNetworkTasks.isChecked = false
            }

            // 热状态警告
            if (status.thermalState == ThermalState.CRITICAL) {
                // 显示过热警告
            }
        }

        // 模型状态
        viewModel.modelStatus.observe(viewLifecycleOwner) { status ->
            binding.modelStatus.text = status
        }

        // 网络任务开关状态
        viewModel.networkTasksEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.switchNetworkTasks.isChecked = enabled
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
