package com.pulsenetwork.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.pulsenetwork.app.R
import com.pulsenetwork.app.databinding.FragmentChatBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * 聊天界面
 */
@AndroidEntryPoint
class ChatFragment : Fragment() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private var isRecording = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInput()
        setupVoiceButton()
        observeState()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messageList.adapter = messageAdapter

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateEmptyState()
            }
        })
    }

    private fun setupInput() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.messageInput.text?.clear()
            }
        }

        // 输入时自动调整
        binding.messageInput.setOnKeyListener { _, _, _ ->
            false
        }
    }

    private fun setupVoiceButton() {
        // 长按录音，松开发送
        binding.voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkRecordPermission()) {
                        startRecording()
                    } else {
                        requestRecordPermission()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(requireContext(), "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        isRecording = true
        viewModel.startRecording()
        binding.voiceButton.setImageResource(R.drawable.ic_mic_recording)
        binding.messageInput.hint = "正在录音..."
    }

    private fun stopRecording() {
        isRecording = false
        viewModel.stopRecording()
        binding.voiceButton.setImageResource(R.drawable.ic_mic)
        binding.messageInput.hint = getString(R.string.chat_input_hint)
    }

    private fun observeState() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages)
            updateEmptyState()

            if (messages.isNotEmpty()) {
                binding.messageList.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.networkStatus.observe(viewLifecycleOwner) { status ->
            binding.statusText.text = status.displayText
            binding.peerCount.text = status.peerCountText
            binding.peerCount.visibility = if (status.peerCount > 0) View.VISIBLE else View.GONE
        }

        viewModel.isGenerating.observe(viewLifecycleOwner) { isGenerating ->
            // 显示/隐藏打字指示器
            // TODO: 添加打字指示器动画
        }

        viewModel.recordingAmplitude.observe(viewLifecycleOwner) { amplitude ->
            // 更新录音音量指示器
            // amplitude 范围 0-1
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = messageAdapter.itemCount == 0
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.messageList.visibility = if (isEmpty) View.INVISIBLE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
