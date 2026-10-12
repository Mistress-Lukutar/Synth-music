package com.synth.synthmusic.domain.usecase.ai

/**
 * Read-only access to user-imported custom tools. Implemented by the data
 * layer; the tool dispatcher consults it next to the built-in registry so
 * imported tools are advertised, gated and audited exactly like built-ins.
 */
interface CustomToolProvider {

    /**
     * All currently enabled, successfully loaded custom tools. Called on
     * every chat turn — implementations must return a cached snapshot.
     */
    suspend fun activeTools(): List<AiTool>

    /** One custom tool by its manifest name, or null when not enabled/loaded. */
    suspend fun toolByName(name: String): AiTool?
}
