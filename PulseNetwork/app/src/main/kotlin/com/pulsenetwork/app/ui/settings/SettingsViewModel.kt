package com.pulsenetwork.app.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsenetwork.core.native.LLMInference
import com.pulsenetwork.core.native.LLMState
import com.pulsenetwork.domain.governor.Governor
import com.pulsenetwork.domain.governor.GovernorStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置界面 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val governor: Governor,
    private val llmInference: LLMInference
) : ViewModel() {

    private val _governorStatus = MutableLiveData<GovernorStatus>()
    val governorStatus: LiveData<GovernorStatus> = _governorStatus

    private val _modelStatus = MutableLiveData<String>()
    val modelStatus: LiveData<String> = _modelStatus

    private val _networkTasksEnabled = MutableLiveData<Boolean>()
    val networkTasksEnabled: LiveData<Boolean> = _networkTasksEnabled

    init {
        _modelStatus.value = "未加载模型"
        _networkTasksEnabled.value = false

        startGovernorMonitoring()
        checkModelStatus()
    }

    private fun startGovernorMonitoring() {
        viewModelScope.launch {
            governor.startMonitoring { status ->
                _governorStatus.postValue(status)
            }
        }

        // 同时使用 Flow 观察
        viewModelScope.launch {
            governor.statusFlow().collectLatest { status ->
                _governorStatus.value = status
            }
        }
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            if (llmInference.isModelLoaded()) {
                val info = llmInference.getModelInfo()
                _modelStatus.value = "已加载: ${info?.name ?: "Unknown"}"
            } else {
                _modelStatus.value = "未加载模型"
            }
        }
    }

    fun setNetworkTasksEnabled(enabled: Boolean) {
        _networkTasksEnabled.value = enabled
        // TODO: 保存到 SharedPreferences
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            _modelStatus.value = "加载中..."
            val success = llmInference.loadModel(path)
            if (success) {
                val info = llmInference.getModelInfo()
                _modelStatus.value = "已加载: ${info?.name ?: "Unknown"}"
            } else {
                _modelStatus.value = "加载失败"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        governor.stopMonitoring()
    }
}
