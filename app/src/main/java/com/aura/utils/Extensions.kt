package com.aura.utils

import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.aura.R

// View visibility helpers
fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
fun View.isVisible(): Boolean = visibility == View.VISIBLE

// Image loading with Glide
fun ImageView.loadImage(url: String?, placeholder: Int = R.drawable.ic_image_placeholder) {
    Glide.with(context)
        .load(url)
        .apply(RequestOptions().placeholder(placeholder).error(placeholder))
        .transition(DrawableTransitionOptions.withCrossFade(300))
        .into(this)
}

fun ImageView.loadImageBlurred(url: String?) {
    Glide.with(context)
        .load(url)
        .apply(
            RequestOptions()
                .override(200, 300)
                .centerCrop()
        )
        .transition(DrawableTransitionOptions.withCrossFade(300))
        .into(this)
}

// Toast helpers
fun Fragment.showToast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

// Time formatting
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

