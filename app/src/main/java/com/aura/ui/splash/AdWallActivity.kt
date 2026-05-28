package com.aura.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aura.databinding.ActivityAdWallBinding
import com.aura.ui.MainActivity
import com.aura.utils.Constants
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.aura.AuraApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdWallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdWallBinding
    private var timeoutJob: Job? = null
    private var hasProceeded = false
    private var adRetryCount = 0
    private lateinit var rewardedAd: StartAppAd
    private var isFallbackMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdWallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Block back button — user cannot skip during the initialization / ad loading phase
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing
            }
        })

        // Set initial status text
        binding.loadingStatusText.text = "Initializing secure tunnel…"

        // Start safety timeout (12 seconds maximum — enough for retries)
        startTimeoutTimer()

        // Fetch preference and initialize correct SDK
        initializeAppAds()
    }

    private fun initializeAppAds() {
        // Only keep Start.io ad provider as requested
        initializeStartIO()
    }

    // Start.io (StartApp) Integration
    private fun initializeStartIO() {
        try {
            // Disable automatic activity-change interstitials so we trigger manually
            StartAppAd.disableAutoInterstitial()

            // Configure test ads setting
            StartAppSDK.setTestAdsEnabled(Constants.STARTIO_TEST_MODE)

            // Initialize Start.io SDK
            StartAppSDK.init(this, Constants.STARTIO_APP_ID, false)

            Log.d("AdWallActivity", "Start.io SDK Initialization Complete")
            runOnUiThread {
                binding.loadingStatusText.text = "Fetching security challenge…"
                rewardedAd = StartAppAd(this)
                loadStartIOAd()
            }
        } catch (e: Exception) {
            Log.e("AdWallActivity", "Start.io Initialization Failed: ${e.message}")
            proceedToMainApp("Init failed: ${e.message}")
        }
    }

    private fun loadStartIOAd() {
        if (hasProceeded) return

        val mode = if (isFallbackMode) StartAppAd.AdMode.AUTOMATIC else StartAppAd.AdMode.VIDEO
        val modeStr = if (isFallbackMode) "Standard Interstitial" else "Video Interstitial"

        Log.d("AdWallActivity", "Loading Start.io $modeStr attempt ${adRetryCount + 1}/${Constants.MAX_AD_RETRY_COUNT}")

        rewardedAd.loadAd(mode, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                Log.d("AdWallActivity", "Start.io $modeStr Loaded successfully")
                adRetryCount = 0 // Reset on success
                runOnUiThread {
                    binding.loadingStatusText.text = "Starting verification…"
                    showStartIOAd()
                }
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                val errorMsg = ad?.errorMessage ?: "Unknown error"
                Log.e("AdWallActivity", "Start.io $modeStr Failed to Load: $errorMsg")

                if (!isFallbackMode) {
                    // Fall back to standard interstitial immediately on first failure
                    isFallbackMode = true
                    adRetryCount = 0 // Reset attempt counter for fallback
                    Log.d("AdWallActivity", "Falling back to Start.io Standard Interstitial...")
                    runOnUiThread {
                        Toast.makeText(this@AdWallActivity, "Video ad unavailable. Loading standard ad...", Toast.LENGTH_SHORT).show()
                        loadStartIOAd()
                    }
                } else {
                    adRetryCount++

                    runOnUiThread {
                        Toast.makeText(this@AdWallActivity, "Ad failed to load (${adRetryCount}/${Constants.MAX_AD_RETRY_COUNT}): $errorMsg", Toast.LENGTH_SHORT).show()
                    }

                    if (adRetryCount < Constants.MAX_AD_RETRY_COUNT && !hasProceeded) {
                        Log.d("AdWallActivity", "Retrying ad load in ${Constants.AD_RETRY_DELAY_MS}ms...")
                        lifecycleScope.launch {
                            delay(Constants.AD_RETRY_DELAY_MS)
                            runOnUiThread {
                                binding.loadingStatusText.text = "Retrying verification…"
                                loadStartIOAd()
                            }
                        }
                    } else {
                        Log.w("AdWallActivity", "All ad load retries exhausted, proceeding to app")
                        proceedToMainApp("Load failed after $adRetryCount retries")
                    }
                }
            }
        })
    }

    private fun showStartIOAd() {
        if (hasProceeded) return
        cancelTimeoutTimer()

        rewardedAd.showAd(object : AdDisplayListener {
            override fun adHidden(ad: Ad?) {
                Log.d("AdWallActivity", "Start.io Ad Hidden (Closed)")
                proceedToMainApp("Ad completed")
            }

            override fun adDisplayed(ad: Ad?) {
                Log.d("AdWallActivity", "Start.io Ad Displayed")
            }

            override fun adClicked(ad: Ad?) {
                Log.d("AdWallActivity", "Start.io Ad Clicked")
            }

            override fun adNotDisplayed(ad: Ad?) {
                Log.e("AdWallActivity", "Start.io Ad Not Displayed")
                proceedToMainApp("Ad not displayed")
            }
        })
    }

    // Timer & Navigation Logic
    private fun startTimeoutTimer() {
        timeoutJob = lifecycleScope.launch {
            delay(12000) // 12 seconds safety timeout (allows for retries)
            Log.w("AdWallActivity", "Ad loading timed out, proceeding to main app")
            proceedToMainApp("Timeout reached")
        }
    }

    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    @Synchronized
    private fun proceedToMainApp(reason: String) {
        if (hasProceeded) return
        hasProceeded = true
        cancelTimeoutTimer()

        Log.d("AdWallActivity", "Proceeding to main app: $reason")
        
        runOnUiThread {
            startActivity(Intent(this@AdWallActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        cancelTimeoutTimer()
        super.onDestroy()
    }
}
