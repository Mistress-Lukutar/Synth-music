package com.synth.synthmusic.data.ai.customtools

import android.content.Context
import com.synth.synthmusic.data.ai.crypto.ApiKeyCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ToolSecrets(val values: Map<String, String> = emptyMap())

@Serializable
private data class SecretFile(val tools: Map<String, ToolSecrets> = emptyMap())

/** Read side custom tools use to resolve secrets at execution time. */
interface CustomSecrets {
    /** Decrypts one secret, or null when it was never provided. */
    suspend fun get(toolName: String, secretName: String): String?
}

/**
 * Encrypted-at-rest storage for custom-tool secrets (`{{secret:name}}` in
 * HTTP templates, `ctx.secret(name)` in scripts). Values are AES-GCM
 * encrypted with the app's AndroidKeyStore key ([ApiKeyCipher]) and never
 * leave the device; they live outside the shareable tool directory so
 * exporting a tool cannot leak credentials.
 */
class CustomToolSecretStore(
    context: Context,
    private val cipher: ApiKeyCipher,
    private val json: Json
) : CustomSecrets {

    private val file = File(File(context.filesDir, "custom_tools"), "secrets.json")

    /** Stores (or overwrites) one secret value. */
    suspend fun set(toolName: String, secretName: String, value: String): Unit =
        withContext(Dispatchers.IO) {
            update { current ->
                val forTool = current.tools[toolName] ?: ToolSecrets()
                val values = forTool.values.toMutableMap()
                if (value.isBlank()) values.remove(secretName) else values[secretName] = cipher.encrypt(value)
                current.copy(tools = current.tools + (toolName to ToolSecrets(values)))
            }
        }

    /** Decrypts one secret, or null when it was never provided. */
    override suspend fun get(toolName: String, secretName: String): String? = withContext(Dispatchers.IO) {
        val payload = read().tools[toolName]?.values?.get(secretName) ?: return@withContext null
        runCatching { cipher.decrypt(payload) }.getOrNull()
    }

    /** Names of secrets that currently have a stored value for [toolName]. */
    suspend fun storedNames(toolName: String): Set<String> = withContext(Dispatchers.IO) {
        read().tools[toolName]?.values?.keys.orEmpty()
    }

    /** Drops every secret of a deleted tool. */
    suspend fun removeAll(toolName: String): Unit = withContext(Dispatchers.IO) {
        update { current -> current.copy(tools = current.tools - toolName) }
    }

    private fun read(): SecretFile = runCatching {
        json.decodeFromString(SecretFile.serializer(), file.readText())
    }.getOrDefault(SecretFile())

    private fun update(transform: (SecretFile) -> SecretFile) {
        val updated = transform(read())
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(SecretFile.serializer(), updated))
    }
}
