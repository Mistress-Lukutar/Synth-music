package com.synth.synthmusic.data.ai.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Encrypts and decrypts AI provider API keys using an AES/GCM key held in
 * AndroidKeyStore. Plaintext keys are never persisted anywhere; the generated
 * key is non-exportable so it never leaves the device.
 *
 * @param context used to access AndroidKeyStore.
 */
class ApiKeyCipher(
    @Suppress("unused") private val context: Context
) {

    private val aesGcm = AesGcmCipher()

    /**
     * Encrypts [plaintext] into a storable Base64 payload.
     *
     * @throws IllegalStateException if the AndroidKeyStore key cannot be created.
     */
    fun encrypt(plaintext: String): String = aesGcm.encrypt(getOrCreateKey(), plaintext)

    /**
     * Decrypts a payload produced by [encrypt].
     *
     * @throws IllegalArgumentException if the payload is malformed.
     * @throws javax.crypto.AEADBadTagException if the data was tampered with.
     */
    fun decrypt(payload: String): String = aesGcm.decrypt(getOrCreateKey(), payload)

    /**
     * Returns a masked preview of a key for UI display, e.g. `sk-…abcd`.
     * Never exposes more than the last 4 characters.
     */
    fun mask(plaintext: String): String {
        val cleaned = plaintext.trim()
        if (cleaned.isEmpty()) return ""
        val prefix = cleaned.takeWhile { it != '-' }.take(MAX_PREFIX_LENGTH)
        val suffix = cleaned.takeLast(MASKED_SUFFIX_LENGTH)
        return "$prefix…$suffix"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "synth_ai_keys"
        const val KEY_SIZE_BITS = 256
        const val MASKED_SUFFIX_LENGTH = 4
        const val MAX_PREFIX_LENGTH = 4
    }
}
