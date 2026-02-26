package com.pulsenetwork.app.ui.workflow

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pulsenetwork.app.databinding.ItemInterviewMessageBinding

/**
 * 访谈消息适配器
 */
class InterviewAdapter : ListAdapter<InterviewMessage, InterviewAdapter.MessageViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemInterviewMessageBinding.inflate(
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
        private val binding: ItemInterviewMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: InterviewMessage) {
            binding.messageText.text = message.text

            // 系统消息和用户消息样式不同
            if (message.isSystem) {
                binding.messageText.setBackgroundResource(com.pulsenetwork.app.R.drawable.bg_bubble_ai)
                binding.messageText.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
            } else {
                binding.messageText.setBackgroundResource(com.pulsenetwork.app.R.drawable.bg_bubble_user)
                binding.messageText.textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_END
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<InterviewMessage>() {
        override fun areItemsTheSame(oldItem: InterviewMessage, newItem: InterviewMessage): Boolean {
            return oldItem.text == newItem.text && oldItem.isSystem == newItem.isSystem
        }

        override fun areContentsTheSame(oldItem: InterviewMessage, newItem: InterviewMessage): Boolean {
            return oldItem == newItem
        }
    }
}
