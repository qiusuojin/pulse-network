package com.pulsenetwork.app.ui.network

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.pulsenetwork.app.R
import com.pulsenetwork.app.databinding.FragmentNetworkBinding
import com.pulsenetwork.domain.swarm.ConnectionState
import dagger.hilt.android.AndroidEntryPoint

/**
 * 网络状态界面
 */
@AndroidEntryPoint
class NetworkFragment : Fragment() {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NetworkViewModel by viewModels()
    private lateinit var nodeAdapter: NodeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeState()
    }

    private fun setupRecyclerView() {
        nodeAdapter = NodeAdapter { node ->
            // 点击节点，尝试连接
            viewModel.connectToNode(node.id)
        }
        binding.nodesList.adapter = nodeAdapter
    }

    private fun setupFab() {
        binding.fabToggle.setOnClickListener {
            if (viewModel.isRunning.value == true) {
                viewModel.stopNetwork()
            } else {
                viewModel.startNetwork()
            }
        }
    }

    private fun observeState() {
        viewModel.isRunning.observe(viewLifecycleOwner) { isRunning ->
            binding.fabToggle.setImageResource(
                if (isRunning) R.drawable.ic_network else R.drawable.ic_network
            )
            updateStatusBadge(isRunning)
        }

        viewModel.nodes.observe(viewLifecycleOwner) { nodes ->
            nodeAdapter.submitList(nodes)
            binding.emptyState.visibility = if (nodes.isEmpty()) View.VISIBLE else View.GONE

            // 更新统计
            binding.peerCount.text = nodes.size.toString()
            binding.connectedCount.text = nodes.count {
                it.connectionState == ConnectionState.CONNECTED
            }.toString()
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.cacheHits.text = stats.cacheHits.toString()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatusBadge(isRunning: Boolean) {
        if (isRunning) {
            binding.statusBadge.text = "运行中"
            binding.statusBadge.setBackgroundResource(R.drawable.bg_status_online)
        } else {
            binding.statusBadge.text = "离线"
            binding.statusBadge.setBackgroundResource(R.drawable.bg_status_offline)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
