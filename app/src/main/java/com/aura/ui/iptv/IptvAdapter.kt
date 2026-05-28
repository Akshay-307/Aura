package com.aura.ui.iptv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aura.R
import com.aura.data.model.IptvChannel
import com.aura.databinding.ItemIptvChannelBinding
import com.aura.utils.loadImage

/**
 * ListAdapter that displays a list of live TV channels in a grid.
 * Supports a long-press callback to toggle favourites.
 */
class IptvAdapter(
    private val onClick: (IptvChannel) -> Unit,
    private val onFavouriteToggle: (IptvChannel) -> Unit,
    private val isFavourite: (String) -> Boolean
) : ListAdapter<IptvChannel, IptvAdapter.ViewHolder>(DiffCallback) {

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

    inner class ViewHolder(private val binding: ItemIptvChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: IptvChannel) {
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

            // Show favourite star
            val fav = isFavourite(item.id)
            binding.ivFavourite.setImageResource(
                if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            val tint = if (fav)
                android.graphics.Color.parseColor("#FFD700")
            else
                android.graphics.Color.parseColor("#66FFFFFF")
            binding.ivFavourite.setColorFilter(tint)
            binding.ivFavourite.visibility = android.view.View.VISIBLE

            binding.ivFavourite.setOnClickListener {
                onFavouriteToggle(item)
            }

            // Apply premium bouncy click physics
            com.aura.utils.AnimationHelper.applySpringClickEffect(binding.root) {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIptvChannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        com.aura.utils.AnimationHelper.animateSlideUp(holder.itemView, position)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<IptvChannel>() {
        override fun areItemsTheSame(old: IptvChannel, new: IptvChannel) = old.id == new.id
        override fun areContentsTheSame(old: IptvChannel, new: IptvChannel) = old == new
    }
}

