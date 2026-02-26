package com.pulsenetwork.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter

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
        observeState()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messageList.adapter = messageAdapter

        // 滚动到新消息时隐藏空状态
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

        binding.voiceButton.setOnClickListener {
            // TODO: 实现语音输入
        }
    }

    private fun observeState() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages)
            updateEmptyState()

            // 滚动到底部
            if (messages.isNotEmpty()) {
                binding.messageList.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.networkStatus.observe(viewLifecycleOwner) { status ->
            binding.statusText.text = status.displayText
            binding.peerCount.text = status.peerCountText
            binding.peerCount.visibility = if (status.peerCount > 0) View.VISIBLE else View.GONE
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
