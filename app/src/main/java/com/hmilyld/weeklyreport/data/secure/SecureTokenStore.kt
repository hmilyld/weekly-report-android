package com.hmilyld.weeklyreport.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The JWT is encrypted with an AES-GCM key that never leaves the Android
 * Keystore. The password itself is never written to disk; it is used once for
 * the login request and then forgotten.
 */
class SecureTokenStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("wr_secure_session", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + encrypted.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(encrypted, 0, it, iv.size, encrypted.size)
        }
        prefs.edit { putString(KEY_PACKED, Base64.encodeToString(packed, Base64.NO_WRAP)) }
    }

    fun readToken(): String? {
        val packedRaw = prefs.getString(KEY_PACKED, null) ?: return null
        return runCatching {
            val packed = Base64.decode(packedRaw, Base64.NO_WRAP)
            if (packed.size <= IV_SIZE) return null
            val iv = packed.copyOfRange(0, IV_SIZE)
            val body = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrElse {
            // A key can be invalidated by a lock-screen change; the session is
            // unusable then, and the honest recovery is a fresh login.
            clear()
            null
        }
    }

    fun hasToken(): Boolean = prefs.contains(KEY_PACKED)

    fun clear() {
        prefs.edit { remove(KEY_PACKED) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "weekly_report_session_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val KEY_PACKED = "packed_token"
    }
}
