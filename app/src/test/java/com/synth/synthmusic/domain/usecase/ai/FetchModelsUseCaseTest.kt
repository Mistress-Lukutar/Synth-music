package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiSettings
import com.synth.synthmusic.domain.model.guessModelCapabilities
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests for [FetchModelsUseCase.parseModels] per protocol.
 */
class FetchModelsUseCaseTest {

    private val useCase = FetchModelsUseCase()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses openai model list`() {
        val body = """
            {"data":[{"id":"gpt-4o"},{"id":"gpt-3.5-turbo"}]}
        """.trimIndent()
        val models = useCase.parseModels(body, AiProtocol.OPENAI_COMPATIBLE, json)
        assertEquals(2, models.size)
        assertEquals("gpt-4o", models[0].modelId)
        assertEquals(true, models[0].supportsVision)
    }

    @Test
    fun `parses anthropic model list with display names`() {
        val body = """
            {"data":[{"id":"claude-4-sonnet","display_name":"Claude 4 Sonnet"}]}
        """.trimIndent()
        val models = useCase.parseModels(body, AiProtocol.ANTHROPIC, json)
        assertEquals(1, models.size)
        assertEquals("Claude 4 Sonnet", models[0].displayName)
        assertEquals("claude-4-sonnet", models[0].modelId)
    }

    @Test
    fun `filters gemini models without generateContent`() {
        val body = """
            {"models":[
                {"name":"models/gemini-2.5-pro","displayName":"Gemini 2.5 Pro",
                 "supportedGenerationMethods":["generateContent","countTokens"]},
                {"name":"models/embedding-001","displayName":"Embedding",
                 "supportedGenerationMethods":["embedContent"]}
            ]}
        """.trimIndent()
        val models = useCase.parseModels(body, AiProtocol.GOOGLE_GEMINI, json)
        assertEquals(1, models.size)
        assertEquals("gemini-2.5-pro", models[0].modelId)
    }

    @Test
    fun `capability heuristic is conservative for unknown models`() {
        val (tools, vision) = guessModelCapabilities("totally-unknown-model")
        assertTrue(!vision)
        assertTrue(!tools)
    }

    @Test
    fun `default capability grants match plan`() {
        val settings = AiSettings()
        assertEquals(true, settings.isCapabilityEnabled(AiCapability.READ_LIBRARY))
        assertEquals(true, settings.isCapabilityEnabled(AiCapability.WRITE_METADATA))
        assertEquals(false, settings.isCapabilityEnabled(AiCapability.MANAGE_FILES))
        assertEquals(false, settings.isCapabilityEnabled(AiCapability.INTERNET))
    }

    @Test
    fun `model domain defaults use providerId zero before upsert`() {
        val body = """{"data":[{"id":"gpt-x"}]}"""
        val models = useCase.parseModels(body, AiProtocol.OPENAI_COMPATIBLE, json)
        assertEquals(0L, models[0].providerId)
        assertEquals(false, models[0].isPinned)
        assertTrue(models[0] is AiModel)
    }
}
