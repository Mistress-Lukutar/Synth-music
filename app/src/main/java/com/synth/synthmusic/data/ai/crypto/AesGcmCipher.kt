package com.synth.synthmusic.data.ai.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure-Kotlin AES/GCM cipher core used to protect API keys at rest.
 *
 * The ciphertext layout is `IV (12 bytes) || ciphertext+tag`, Base64-encoded.
 * Splitting the algorithm from [ApiKeyCipher] keeps it unit-testable on the JVM
 * (AndroidKeyStore is only available on a device).
 */
class AesGcmCipher {

    /**
     * Encrypts [plaintext] with [key] and returns a Base64 string of `IV || ciphertext`.
     *
     * @throws javax.crypto.AEADBadTagException never during encryption, but decryption
     * of tampered data does; see [decrypt].
     */
    fun encrypt(key: SecretKey, plaintext: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /**
     * Decrypts a Base64 `IV || ciphertext` payload produced by [encrypt].
     *
     * @throws IllegalArgumentException if the payload is malformed.
     * @throws javax.crypto.AEADBadTagException if the data was tampered with or
     * encrypted with a different key (GCM tag verification fails).
     */
    fun decrypt(key: SecretKey, payload: String): String {
        val decoded = runCatching {
            java.util.Base64.getDecoder().decode(payload)
        }.getOrElse { throw IllegalArgumentException("Malformed encrypted payload", it) }
        if (decoded.size <= IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Encrypted payload too short")
        }
        val iv = decoded.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = decoded.copyOfRange(IV_LENGTH_BYTES, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
