package com.aura.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Premium Animation helper that houses state-of-the-art visual micro-interactions
 * and staggered RecyclerView transitions for an incredibly premium user experience.
 */
object AnimationHelper {

    /**
     * Reusable spring scale touch animation for button/card clicks.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applySpringClickEffect(view: View, onClick: (() -> Unit)? = null) {
        if (onClick != null) {
            view.setOnClickListener { onClick.invoke() }
        }
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(3.0f))
                        .start()
                }
            }
            false
        }
    }

    /**
     * Staggered slide up and fade-in entry animation for lists/grids.
     */
    fun animateSlideUp(view: View, position: Int) {
        view.translationY = 180f
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(minOf(position * 35L, 350L)) // Cap maximum start delay to prevent late-loading items from lagging
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }
}

