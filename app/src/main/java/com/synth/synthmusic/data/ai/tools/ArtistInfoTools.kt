package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Result of resolving an artist name to a domain [Artist].
 */
private sealed interface ArtistResolution {
    data class Found(val artist: Artist) : ArtistResolution
    object NotFound : ArtistResolution
    data class Ambiguous(val candidates: List<Artist>) : ArtistResolution
}

/**
 * Resolves an artist by name: exact case-insensitive match first, then
 * substring match. Returns [ArtistResolution.Found], [NotFound] or
 * [Ambiguous] when multiple substring matches exist.
 */
private suspend fun resolveArtist(
    artistRepository: ArtistRepository,
    name: String
): ArtistResolution {
    val all = artistRepository.observeAllArtists().first()

    // Exact case-insensitive match
    val exact = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    if (exact != null) return ArtistResolution.Found(exact)

    // Substring matches
    val matches = all.filter { it.name.contains(name, ignoreCase = true) }
    return when {
        matches.isEmpty() -> ArtistResolution.NotFound
        matches.size == 1 -> ArtistResolution.Found(matches.single())
        else -> ArtistResolution.Ambiguous(matches)
    }
}

/**
 * `get_artist_info` — reads the stored Markdown bio/info for an artist
 * together with track and album counts.
 */
class GetArtistInfoTool(
    private val artistRepository: ArtistRepository
) : AiTool {

    override val name = "get_artist_info"
    override val description =
        "Get the stored Markdown info/bio for a music artist in the library, " +
            "including track and album counts. Returns the current text (or null " +
            "if none exists). Call this BEFORE set_artist_bio to avoid overwriting " +
            "existing info."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("artist_name", buildJsonObject {
                put("type", "string")
                put("description", "Exact or partial artist name.")
            })
        }
        putJsonArray("required") { add(JsonPrimitive("artist_name")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override fun summarize(args: JsonObject): String {
        val artistName = args["artist_name"]?.jsonPrimitive?.content ?: "?"
        return "Get info for artist '$artistName'"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val name = args["artist_name"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing required parameter: artist_name", isError = true)

        return when (val resolution = resolveArtist(artistRepository, name)) {
            is ArtistResolution.NotFound -> {
                ToolOutcome("No artist matching '$name' found in the library.", isError = true)
            }
            is ArtistResolution.Ambiguous -> {
                val candidates = resolution.candidates.joinToString(", ") { it.name }
                ToolOutcome(
                    "Ambiguous artist name '$name'. Did you mean: $candidates?",
                    isError = true
                )
            }
            is ArtistResolution.Found -> {
                val artist = resolution.artist
                ToolOutcome(
                    buildJsonObject {
                        put("name", artist.name)
                        put("songCount", artist.songCount)
                        put("albumCount", artist.albumCount)
                        put("hasInfo", artist.bio != null)
                        put("info", artist.bio?.let { JsonPrimitive(it) } ?: JsonNull)
                    }.toString()
                )
            }
        }
    }
}

/**
 * `set_artist_bio` — creates or updates the Markdown bio shown on an
 * artist's page. Supports append mode for small additions.
 */
class SetArtistBioTool(
    private val artistRepository: ArtistRepository
) : AiTool {

    override val name = "set_artist_bio"
    override val description =
        "Create or update the Markdown info/bio shown on an artist's page. " +
            "Markdown is supported (headings, bold/italic, bullet lists, links). " +
            "Guidelines: call get_artist_info first to see existing text; use append=true " +
            "for small additions instead of rewriting; do not invent discography facts you " +
            "are not sure about; keep the bio under 10000 characters; write in the language " +
            "the user is chatting in."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("artist_name", buildJsonObject {
                put("type", "string")
                put("description", "Artist name (exact or partial).")
            })
            put("bio", buildJsonObject {
                put("type", "string")
                put("description", "Markdown-formatted bio text.")
            })
            put("append", buildJsonObject {
                put("type", "boolean")
                put("description", "If true, append to the existing bio instead of replacing it.")
            })
        }
        putJsonArray("required") {
            add(JsonPrimitive("artist_name"))
            add(JsonPrimitive("bio"))
        }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val artistName = args["artist_name"]?.jsonPrimitive?.content ?: "?"
        val append = args["append"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return "Set info for artist '$artistName'" + if (append) " (append)" else ""
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val name = args["artist_name"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing required parameter: artist_name", isError = true)
        val bio = args["bio"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing required parameter: bio", isError = true)

        // Resolve artist
        when (val resolution = resolveArtist(artistRepository, name)) {
            is ArtistResolution.NotFound -> {
                return ToolOutcome("No artist matching '$name' found in the library.", isError = true)
            }
            is ArtistResolution.Ambiguous -> {
                val candidates = resolution.candidates.joinToString(", ") { it.name }
                return ToolOutcome(
                    "Ambiguous artist name '$name'. Did you mean: $candidates?",
                    isError = true
                )
            }
            is ArtistResolution.Found -> {
                val artist = resolution.artist

                // Validate bio content
                if (bio.isBlank()) {
                    return ToolOutcome("bio must not be empty", isError = true)
                }
                if (bio.length > MAX_BIO_LENGTH) {
                    return ToolOutcome(
                        "bio exceeds the $MAX_BIO_LENGTH-character limit",
                        isError = true
                    )
                }

                // Determine new bio value
                val append = args["append"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val existingBio = artistRepository.observeAllArtists().first()
                    .find { it.id == artist.id }?.bio
                val newValue = if (append && existingBio != null) {
                    existingBio + "\n\n" + bio
                } else {
                    bio
                }

                // Persist
                artistRepository.updateArtistBio(artist.id, newValue)

                return ToolOutcome(
                    buildJsonObject {
                        put("name", artist.name)
                        put("mode", if (append && existingBio != null) "appended" else "replaced")
                        put("lengthChars", newValue.length)
                    }.toString()
                )
            }
        }
    }

    private companion object {
        const val MAX_BIO_LENGTH = 10_000
    }
}
