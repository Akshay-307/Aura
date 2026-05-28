package com.aura.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aura.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateLogo()

        lifecycleScope.launch {
            delay(2000)
            startActivity(Intent(this@SplashActivity, AdWallActivity::class.java))
            finish()
        }
    }

    private fun animateLogo() {
        binding.logoText.alpha = 0f
        binding.logoSubtitle.alpha = 0f

        val textFade = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(binding.logoText, View.ALPHA, 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(binding.logoSubtitle, View.ALPHA, 0f, 1f).apply { duration = 400 }
            )
        }

        textFade.start()
    }
}

