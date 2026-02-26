package com.pulsenetwork.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pulsenetwork.app.databinding.ItemMessageBinding

/**
 * 消息列表适配器
 */
class MessageAdapter : ListAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.messageText.text = message.content

            // 根据发送者调整布局
            val params = binding.messageText.layoutParams as ViewGroup.MarginLayoutParams

            if (message.isUser) {
                // 用户消息靠右
                binding.messageText.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_END
                params.marginStart = 80
                params.marginEnd = 16
            } else {
                // AI 消息靠左
                binding.messageText.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
                params.marginStart = 16
                params.marginEnd = 80
            }

            binding.messageText.layoutParams = params

            // 显示加载动画
            if (message.isStreaming && message.content.isEmpty()) {
                // TODO: 显示打字指示器
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
