package com.ytbell.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ytbell.app.data.AlertHistoryItem
import com.ytbell.app.databinding.ItemAlertHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val context: Context,
    private var items: MutableList<AlertHistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun updateList(newItems: List<AlertHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemAlertHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemAlertHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlertHistoryItem) {
            binding.tvHistoryChannelTitle.text = item.channelTitle
            binding.tvHistoryVideoTitle.text = item.videoTitle

            val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
            binding.tvHistoryTime.text = sdf.format(Date(item.timestamp))

            binding.btnHistoryWatch.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.videoUrl))
                context.startActivity(intent)
            }

            binding.root.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.videoUrl))
                context.startActivity(intent)
            }
        }
    }
}
