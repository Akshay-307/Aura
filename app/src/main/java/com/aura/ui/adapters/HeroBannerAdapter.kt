package com.aura.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aura.data.model.SearchResult
import com.aura.databinding.ItemHeroBannerBinding
import com.aura.utils.loadImage

class HeroBannerAdapter(
    private val onPlayClick: (SearchResult) -> Unit,
    private val onInfoClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, HeroBannerAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResult) {
            binding.tvHeroTitle.text = item.title
            binding.tvHeroGenre.text = item.genre
            binding.ivHeroPoster.loadImage(item.posterUrl)

            // Year pill
            if (item.year.isNotBlank()) {
                binding.tvHeroYear.text = item.year
                binding.tvHeroYear.visibility = android.view.View.VISIBLE
            } else {
                binding.tvHeroYear.visibility = android.view.View.GONE
            }

            binding.btnHeroPlay.setOnClickListener { onPlayClick(item) }
            binding.btnHeroInfo.setOnClickListener { onInfoClick(item) }
            binding.root.setOnClickListener { onInfoClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHeroBannerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(old: SearchResult, new: SearchResult) = old.id == new.id
        override fun areContentsTheSame(old: SearchResult, new: SearchResult) = old == new
    }
}

