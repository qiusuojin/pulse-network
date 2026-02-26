package com.pulsenetwork.app.ui.network

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pulsenetwork.app.R
import com.pulsenetwork.app.databinding.ItemNodeBinding
import com.pulsenetwork.domain.swarm.ConnectionState
import com.pulsenetwork.domain.swarm.PeerNode

/**
 * 节点列表适配器
 */
class NodeAdapter(
    private val onNodeClick: (PeerNode) -> Unit
) : ListAdapter<PeerNode, NodeAdapter.NodeViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NodeViewHolder(binding, onNodeClick)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NodeViewHolder(
        private val binding: ItemNodeBinding,
        private val onNodeClick: (PeerNode) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: PeerNode) {
            binding.nodeName.text = node.deviceName
            binding.nodeAddress.text = "${node.address}:${node.port}"

            // 状态指示器
            val statusColor = when (node.connectionState) {
                ConnectionState.CONNECTED -> R.color.success
                ConnectionState.CONNECTING -> R.color.warning
                ConnectionState.DISCOVERED -> R.color.info
                else -> R.color.text_hint
            }
            binding.statusIndicator.setBackgroundResource(statusColor)

            // NPU 标签
            binding.npuBadge.visibility = if (node.capabilities.hasNPU) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            binding.root.setOnClickListener { onNodeClick(node) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PeerNode>() {
        override fun areItemsTheSame(oldItem: PeerNode, newItem: PeerNode): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PeerNode, newItem: PeerNode): Boolean {
            return oldItem == newItem
        }
    }
}
