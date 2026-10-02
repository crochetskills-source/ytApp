package com.ytbell.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.data.YouTubeChannel
import com.ytbell.app.data.YouTubeChannelResolver
import com.ytbell.app.databinding.DialogAddChannelBinding
import kotlinx.coroutines.launch

class AddChannelBottomSheetDialog(
    private val onChannelAdded: (YouTubeChannel) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddChannelBinding? = null
    private val binding get() = _binding!!

    private var resolvedChannel: YouTubeChannel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddChannelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Paste button
        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
                binding.etChannelInput.setText(pasteText)
                resolveAndPreview(pasteText)
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmAdd.setOnClickListener {
            val input = binding.etChannelInput.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                binding.tilChannelInput.error = "Please enter channel link or @handle"
                return@setOnClickListener
            }
            binding.tilChannelInput.error = null

            val currentResolved = resolvedChannel
            if (currentResolved != null) {
                commitAdd(currentResolved)
            } else {
                resolveAndAdd(input)
            }
        }
    }

    private fun resolveAndPreview(input: String) {
        if (input.isEmpty()) return
        binding.pbLoading.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.cardPreview.visibility = View.GONE
        binding.btnConfirmAdd.isEnabled = false

        lifecycleScope.launch {
            val result = YouTubeChannelResolver.resolveInput(input)
            binding.pbLoading.visibility = View.GONE
            binding.btnConfirmAdd.isEnabled = true

            if (result.success && result.channel != null) {
                resolvedChannel = result.channel
                binding.cardPreview.visibility = View.VISIBLE
                binding.tvPreviewTitle.text = result.channel.title
                val videoText = if (result.latestVideo != null) {
                    "Latest: ${result.latestVideo.title}"
                } else {
                    "No uploads found yet"
                }
                binding.tvPreviewVideo.text = videoText
                binding.btnConfirmAdd.text = "Add Channel"
            } else {
                resolvedChannel = null
                binding.tvError.text = result.errorMessage ?: "Failed to resolve channel."
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun resolveAndAdd(input: String) {
        binding.pbLoading.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.cardPreview.visibility = View.GONE
        binding.btnConfirmAdd.isEnabled = false

        lifecycleScope.launch {
            val result = YouTubeChannelResolver.resolveInput(input)
            binding.pbLoading.visibility = View.GONE
            binding.btnConfirmAdd.isEnabled = true

            if (result.success && result.channel != null) {
                commitAdd(result.channel)
            } else {
                binding.tvError.text = result.errorMessage ?: "Failed to resolve channel."
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun commitAdd(channel: YouTubeChannel) {
        val repo = ChannelRepository.getInstance(requireContext())
        val added = repo.addChannel(channel)
        if (added) {
            Toast.makeText(requireContext(), "Added \"${channel.title}\"", Toast.LENGTH_SHORT).show()
            onChannelAdded(channel)
            dismiss()
        } else {
            Toast.makeText(requireContext(), "Channel already exists in your list!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
