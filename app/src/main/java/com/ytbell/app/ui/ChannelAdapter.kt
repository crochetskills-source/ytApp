package com.ytbell.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ytbell.app.data.YouTubeChannel
import com.ytbell.app.databinding.ItemChannelBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ChannelAdapter(
    private val context: Context,
    private var channels: MutableList<YouTubeChannel>,
    private val onToggleEnabled: (YouTubeChannel, Boolean) -> Unit,
    private val onDelete: (YouTubeChannel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private val avatarColors = intArrayOf(
        Color.parseColor("#E50914"),
        Color.parseColor("#3498DB"),
        Color.parseColor("#9B59B6"),
        Color.parseColor("#1ABC9C"),
        Color.parseColor("#E67E22"),
        Color.parseColor("#F39C12"),
        Color.parseColor("#E91E63")
    )

    fun updateList(newChannels: List<YouTubeChannel>) {
        channels.clear()
        channels.addAll(newChannels)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: YouTubeChannel) {
            binding.tvChannelTitle.text = channel.title
            binding.tvChannelHandle.text = channel.handleOrUrl

            // Avatar initial & background color
            val initial = channel.title.firstOrNull()?.uppercaseChar()?.toString() ?: "Y"
            binding.tvAvatar.text = initial
            val colorIndex = abs(channel.id.hashCode()) % avatarColors.size
            binding.tvAvatar.backgroundTintList = ColorStateList.valueOf(avatarColors[colorIndex])

            // Switch state
            binding.switchChannelEnabled.setOnCheckedChangeListener(null)
            binding.switchChannelEnabled.isChecked = channel.isEnabled
            binding.switchChannelEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(channel, isChecked)
                updateStatusBadge(channel, isChecked)
            }

            updateStatusBadge(channel, channel.isEnabled)

            // Latest video
            if (channel.lastVideoTitle.isNullOrEmpty()) {
                binding.tvLatestVideoTitle.text = "Checking feed for latest videos..."
                binding.btnOpenVideo.visibility = View.GONE
            } else {
                binding.tvLatestVideoTitle.text = channel.lastVideoTitle
                binding.btnOpenVideo.visibility = View.VISIBLE
                binding.btnOpenVideo.setOnClickListener {
                    val url = if (channel.lastVideoId != null) {
                        "https://www.youtube.com/watch?v=${channel.lastVideoId}"
                    } else {
                        channel.getChannelUrl()
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }

            // Last checked time
            if (channel.lastCheckedTime > 0) {
                val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                binding.tvLastChecked.text = "Checked: ${sdf.format(Date(channel.lastCheckedTime))}"
            } else {
                binding.tvLastChecked.text = "Pending first check"
            }

            // Delete click
            binding.btnDeleteChannel.setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Remove Channel")
                    .setMessage("Stop tracking \"${channel.title}\"?")
                    .setPositiveButton("Remove") { _, _ ->
                        onDelete(channel)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            // Click entire item opens channel URL
            binding.root.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.getChannelUrl()))
                context.startActivity(intent)
            }
        }

        private fun updateStatusBadge(channel: YouTubeChannel, isEnabled: Boolean) {
            if (isEnabled) {
                binding.tvStatusBadge.text = "● Monitoring"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#00C853"))
                binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2000C853"))
            } else {
                binding.tvStatusBadge.text = "○ Paused"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#9E9EA8"))
                binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#209E9EA8"))
            }
        }
    }
}
