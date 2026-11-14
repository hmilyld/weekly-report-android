package com.hmilyld.weeklyreport.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hmilyld.weeklyreport.R

object BiometricUi {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(
            context,
        ).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onFailure(context.getString(R.string.biometric_unavailable))
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    val message = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> context.getString(R.string.biometric_canceled)
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                        -> context.getString(R.string.biometric_locked_out)
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            context.getString(R.string.biometric_not_enrolled)
                        else -> errString.toString()
                    }
                    onFailure(message)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(context.getString(R.string.common_cancel))
            .build()
        prompt.authenticate(info)
    }

    private fun Context.findActivity(): FragmentActivity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return current as? FragmentActivity
    }
}
