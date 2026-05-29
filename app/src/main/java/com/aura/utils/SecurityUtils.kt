package com.aura.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

object SecurityUtils {

    // Base64 encoded expected SHA-256 signature of the release key (debug key in this case)
    private const val EXPECTED_SIGNATURE_B64 =
        "MzA6MDQ6MTE6NEY6N0M6RDA6NjU6RTE6NkU6RDE6REQ6MzQ6NTQ6NzM6Nzc6MEI6OTU6NDg6Q0Q6MTI6MUE6MDQ6ODk6MzU6OUU6NTI6NTk6MTM6NzA6NzI6MEM6RjA="

    /**
     * Verifies that the package signature matches the official signing certificate.
     * Prevents tampered, modified, or recompiled versions of the app from running.
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun verifySignature(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            if (signatures == null || signatures.isEmpty()) {
                return false
            }

            // Decode expected SHA-256 fingerprint from Base64
            val expectedSignature = String(Base64.decode(EXPECTED_SIGNATURE_B64, Base64.DEFAULT)).trim()

            for (sig in signatures) {
                val rawCert = sig.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val publicKey = md.digest(rawCert)
                
                // Convert to uppercase colon-separated hex format
                val hexString = publicKey.joinToString(":") { 
                    String.format("%02X", it) 
                }

                if (hexString.equals(expectedSignature, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
