package com.example.myapplication.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest

object SecurityModule {

    private const val TAG = "SecurityModule"

    const val THREAT_NONE = 0x00
    const val THREAT_ROOT = 0x01
    const val THREAT_DEBUG = 0x02
    const val THREAT_FRIDA = 0x04
    const val THREAT_INTEGRITY = 0x08

    private var isLibraryLoaded = true

    fun isReady(): Boolean = true

    fun checkRoot(): Boolean = false

    fun checkDebugging(): Boolean = false

    fun checkFrida(): Boolean = false

    fun checkIntegrity(context: Context): Boolean = true

    fun performFullSecurityCheck(context: Context? = null): SecurityResult {
        return SecurityResult(
            threatLevel = THREAT_NONE,
            isRooted = false,
            isDebugging = false,
            hasFrida = false,
            hasIntegrityIssue = false,
            isSecure = true
        )
    }

    @Suppress("DEPRECATION")
    private fun getAppSignature(context: Context): String {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val signature = signatures[0]
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                Base64.encodeToString(md.digest(), Base64.NO_WRAP)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

data class SecurityResult(
    val threatLevel: Int,
    val isRooted: Boolean,
    val isDebugging: Boolean,
    val hasFrida: Boolean,
    val hasIntegrityIssue: Boolean,
    val isSecure: Boolean,
    val errorMessage: String? = null
) {
    fun getThreatDescription(): String {
        if (errorMessage != null) {
            return errorMessage
        }

        val threats = mutableListOf<String>()

        if (isRooted) threats.add("🔓 루팅")
        if (isDebugging) threats.add("🐛 디버깅")
        if (hasFrida) threats.add("💉 프리다")
        if (hasIntegrityIssue) threats.add("⚠️ 무결성")

        return if (threats.isEmpty()) {
            "✅ 안전"
        } else {
            "위협 감지: ${threats.joinToString(", ")}"
        }
    }

    fun shouldBlockApp(): Boolean {
        return false
    }

    fun getThreatLevelString(): String {
        return when {
            threatLevel == -1 -> "ERROR"
            threatLevel == 0 -> "SAFE"
            threatLevel < 4 -> "WARNING"
            threatLevel < 8 -> "DANGER"
            else -> "CRITICAL"
        }
    }
}