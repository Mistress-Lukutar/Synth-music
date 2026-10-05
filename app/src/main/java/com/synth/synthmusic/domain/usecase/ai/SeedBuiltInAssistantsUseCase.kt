package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.BuiltInAssistants
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeds the built-in assistant personas idempotently: existing rows (matched
 * by stable `builtin_key`) are refreshed with the shipped definitions, so
 * prompt improvements roll out with app updates while user rows keep ids.
 */
class SeedBuiltInAssistantsUseCase(
    private val assistantRepository: AiAssistantRepository
) {

    /**
     * Upserts all built-ins. Safe to call on every app start.
     */
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        BuiltInAssistants.ALL.forEach { builtin ->
            val existing = builtin.builtinKey?.let {
                runCatching { findBuiltin(it) }.getOrNull()
            }
            if (existing != null) {
                assistantRepository.upsertAssistant(builtin.copy(id = existing.id))
            } else {
                assistantRepository.upsertAssistant(builtin)
            }
        }
    }

    private suspend fun findBuiltin(builtinKey: String) =
        assistantRepository.getAssistants().firstOrNull { it.builtinKey == builtinKey }
}
