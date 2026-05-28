package com.aura.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aura.R
import com.aura.data.model.SearchResult
import com.aura.databinding.ItemContentCardBinding
import com.aura.databinding.ItemContentCardGridBinding
import com.aura.utils.loadImage

class ContentCardAdapter(
    private val isGridLayout: Boolean = false,
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, ContentCardAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        val view: View,
        val tvTitle: TextView,
        val tvYear: TextView,
        val tvRating: TextView,
        val ivPoster: ImageView
    ) : RecyclerView.ViewHolder(view) {

        fun bind(item: SearchResult) {
            tvTitle.text = item.title
            tvYear.text = item.year
            ivPoster.loadImage(item.posterUrl)

            if (item.rating.isNotBlank()) {
                tvRating.text = "⭐ ${item.rating}"
            } else {
                tvRating.text = ""
            }

            // Apply premium bouncy click physics
            com.aura.utils.AnimationHelper.applySpringClickEffect(view) {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (isGridLayout) {
            val binding = ItemContentCardGridBinding.inflate(inflater, parent, false)
            ViewHolder(
                view = binding.root,
                tvTitle = binding.tvTitle,
                tvYear = binding.tvYear,
                tvRating = binding.tvRating,
                ivPoster = binding.ivPoster
            )
        } else {
            val binding = ItemContentCardBinding.inflate(inflater, parent, false)
            ViewHolder(
                view = binding.root,
                tvTitle = binding.tvTitle,
                tvYear = binding.tvYear,
                tvRating = binding.tvRating,
                ivPoster = binding.ivPoster
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        com.aura.utils.AnimationHelper.animateSlideUp(holder.itemView, position)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(old: SearchResult, new: SearchResult) = old.id == new.id
        override fun areContentsTheSame(old: SearchResult, new: SearchResult) = old == new
    }
}

