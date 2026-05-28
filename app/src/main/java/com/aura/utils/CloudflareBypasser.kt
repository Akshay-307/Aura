package com.aura.utils

import android.content.Intent
import com.aura.AuraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CloudflareState(val userAgent: String, val cookies: String)

object CloudflareBypasser {
    @Volatile var _currentState: CloudflareState? = null
    @Volatile var isBypassing = false
    @Volatile var bypassSuccess = false

    suspend fun bypass(url: String): CloudflareState = withContext(Dispatchers.IO) {
        if (isBypassing) {
            // Wait if already bypassing
            while (isBypassing) { delay(500) }
            return@withContext _currentState ?: CloudflareState("", "")
        }
        
        isBypassing = true
        bypassSuccess = false
        
        val context = AuraApp.getInstance()
        val intent = Intent(context, CloudflareBypassActivity::class.java).apply {
            putExtra("url", url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        
        withContext(Dispatchers.Main) {
            context.startActivity(intent)
        }
        
        // Wait until activity finishes and sets bypassSuccess
        var waitTime = 0
        while (isBypassing && waitTime < 60000) { // 60s timeout
            delay(500)
            waitTime += 500
        }
        
        isBypassing = false
        return@withContext _currentState ?: CloudflareState("", "")
    }
    
    fun getCurrentState(): CloudflareState? = _currentState
}

