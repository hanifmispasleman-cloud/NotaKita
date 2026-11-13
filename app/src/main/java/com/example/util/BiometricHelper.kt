package com.example.util

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class BiometricAvailability(
    val isAvailable: Boolean,
    val message: String
)

object BiometricHelper {

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(
                isAvailable = true,
                message = "Sidik jari / biometrik siap digunakan"
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability(
                isAvailable = false,
                message = "Sidik jari belum didaftarkan di pengaturan HP ini. Silakan gunakan PIN."
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability(
                isAvailable = false,
                message = "Perangkat ini tidak memiliki sensor sidik jari. Silakan gunakan PIN."
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability(
                isAvailable = false,
                message = "Sensor sidik jari sedang tidak tersedia. Silakan gunakan PIN."
            )
            else -> BiometricAvailability(
                isAvailable = false,
                message = "Autentikasi biometrik belum tersedia. Silakan gunakan PIN."
            )
        }
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Gunakan PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("Tidak dapat membuka dialog biometrik pada layar ini. Gunakan PIN.")
            return
        }

        val availability = checkAvailability(context)
        if (!availability.isAvailable) {
            onError(availability.message)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError("Verifikasi biometrik gagal: $errString")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Sidik jari tidak dikenali. Coba lagi atau gunakan PIN.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
