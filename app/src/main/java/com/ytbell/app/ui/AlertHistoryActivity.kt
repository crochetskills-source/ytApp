package com.ytbell.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.databinding.ActivityAlertHistoryBinding

class AlertHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertHistoryBinding
    private lateinit var repository: ChannelRepository
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ChannelRepository.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        loadHistory()
    }

    private fun setupToolbar() {
        binding.toolbarHistory.setNavigationOnClickListener {
            finish()
        }

        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Clear all alert history records?")
                .setPositiveButton("Clear") { _, _ ->
                    repository.clearHistory()
                    loadHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(this, mutableListOf())
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        val history = repository.getHistory()
        if (history.isEmpty()) {
            binding.layoutEmptyHistory.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
            binding.btnClearHistory.visibility = View.GONE
        } else {
            binding.layoutEmptyHistory.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
            binding.btnClearHistory.visibility = View.VISIBLE
            adapter.updateList(history)
        }
    }
}
