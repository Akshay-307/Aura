package com.aura.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aura.R
import com.aura.data.local.IptvFavouriteEntity
import com.aura.databinding.ItemIptvFavouriteHorizontalBinding
import com.aura.utils.AnimationHelper

class IptvFavouriteHorizontalAdapter(
    private val onClick: (IptvFavouriteEntity) -> Unit,
    private val onFavouriteToggle: (IptvFavouriteEntity) -> Unit
) : ListAdapter<IptvFavouriteEntity, IptvFavouriteHorizontalAdapter.ViewHolder>(DiffCallback) {

    private fun getInitials(name: String): String {
        val words = name.trim().split(Regex("\\s+"))
        return if (words.size >= 2) {
            val first = words[0].firstOrNull()?.toString() ?: ""
            val second = words[1].firstOrNull()?.toString() ?: ""
            (first + second).uppercase()
        } else if (words.isNotEmpty()) {
            val word = words[0]
            if (word.length >= 2) {
                word.substring(0, 2).uppercase()
            } else {
                word.uppercase()
            }
        } else {
            "TV"
        }
    }

    inner class ViewHolder(private val binding: ItemIptvFavouriteHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: IptvFavouriteEntity) {
            binding.tvName.text = item.name
            binding.tvCategory.text = item.category

            val initials = getInitials(item.name)
            binding.tvLogoPlaceholder.text = initials

            if (item.logoUrl.isNullOrBlank()) {
                binding.ivLogo.visibility = android.view.View.GONE
                binding.tvLogoPlaceholder.visibility = android.view.View.VISIBLE
            } else {
                binding.ivLogo.visibility = android.view.View.VISIBLE
                binding.tvLogoPlaceholder.visibility = android.view.View.GONE

                com.bumptech.glide.Glide.with(binding.ivLogo.context)
                    .load(item.logoUrl)
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.ivLogo.visibility = android.view.View.GONE
                            binding.tvLogoPlaceholder.visibility = android.view.View.VISIBLE
                            return true
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }
                    })
                    .into(binding.ivLogo)
            }

            // Always filled star (since it's in the favourites list)
            binding.ivFavourite.setImageResource(R.drawable.ic_star_filled)
            binding.ivFavourite.setColorFilter(android.graphics.Color.parseColor("#FFD700"))
            binding.ivFavourite.visibility = android.view.View.VISIBLE

            binding.ivFavourite.setOnClickListener {
                onFavouriteToggle(item)
            }

            // Apply spring bouncy click effect
            AnimationHelper.applySpringClickEffect(binding.root) {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIptvFavouriteHorizontalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        AnimationHelper.animateSlideUp(holder.itemView, position)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<IptvFavouriteEntity>() {
        override fun areItemsTheSame(old: IptvFavouriteEntity, new: IptvFavouriteEntity) = old.id == new.id
        override fun areContentsTheSame(old: IptvFavouriteEntity, new: IptvFavouriteEntity) = old == new
    }
}

