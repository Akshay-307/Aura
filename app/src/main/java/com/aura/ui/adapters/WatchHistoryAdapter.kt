package com.aura.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aura.data.local.WatchHistoryEntity
import com.aura.databinding.ItemWatchHistoryBinding
import com.aura.utils.loadImage

class WatchHistoryAdapter(
    private val onClick: (WatchHistoryEntity) -> Unit
) : ListAdapter<WatchHistoryEntity, WatchHistoryAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemWatchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchHistoryEntity) {
            binding.tvHistoryTitle.text = item.title
            binding.ivHistoryPoster.loadImage(item.posterUrl)
            binding.progressBar.progress = item.progressPercent
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<WatchHistoryEntity>() {
        override fun areItemsTheSame(old: WatchHistoryEntity, new: WatchHistoryEntity) = old.id == new.id
        override fun areContentsTheSame(old: WatchHistoryEntity, new: WatchHistoryEntity) = old == new
    }
}

