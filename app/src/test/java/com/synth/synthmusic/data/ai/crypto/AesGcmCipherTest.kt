package com.synth.synthmusic.data.ai.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * JVM unit tests for [AesGcmCipher] roundtrip and tamper detection.
 */
class AesGcmCipherTest {

    private lateinit var cipher: AesGcmCipher
    private lateinit var key: SecretKey

    @Before
    fun setUp() {
        cipher = AesGcmCipher()
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        key = generator.generateKey()
    }

    @Test
    fun `roundtrip returns original plaintext`() {
        val plaintext = "sk-proj-abc123SECRET"
        val encrypted = cipher.encrypt(key, plaintext)

        assertEquals(plaintext, cipher.decrypt(key, encrypted))
    }

    @Test
    fun `encryption produces different ciphertext for same plaintext`() {
        val plaintext = "same-key-value"
        val first = cipher.encrypt(key, plaintext)
        val second = cipher.encrypt(key, plaintext)

        assertNotEquals(first, second)
        assertEquals(plaintext, cipher.decrypt(key, first))
        assertEquals(plaintext, cipher.decrypt(key, second))
    }

    @Test
    fun `tampered ciphertext throws AEADBadTagException not crash`() {
        val encrypted = cipher.encrypt(key, "secret")
        val decoded = java.util.Base64.getDecoder().decode(encrypted)
        decoded[decoded.size - 1] = (decoded[decoded.size - 1].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(decoded)

        val exception = runCatching { cipher.decrypt(key, tampered) }.exceptionOrNull()
        assertTrue(exception is AEADBadTagException)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed base64 payload throws IllegalArgumentException`() {
        cipher.decrypt(key, "!!!not-base64!!!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated payload throws IllegalArgumentException`() {
        cipher.decrypt(key, java.util.Base64.getEncoder().encodeToString(ByteArray(4)))
    }

    @Test
    fun `decrypt with wrong key throws AEADBadTagException`() {
        val encrypted = cipher.encrypt(key, "secret")
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val otherKey = generator.generateKey()

        val exception = runCatching { cipher.decrypt(otherKey, encrypted) }.exceptionOrNull()
        assertTrue(exception is AEADBadTagException)
    }
}
