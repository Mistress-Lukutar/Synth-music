package com.synth.synthmusic.domain.model

/**
 * Avatar options for assistant personas (preset Material icons rendered in
 * the accent color wheel).
 */
enum class AiAvatarIcon {
    MUSIC_NOTE,
    BRUSH,
    TAG,
    BOLT,
    CLOUD,
    BOOK,
    STAR,
    HEART,
    WRENCH,
    PERSON,
    SMART_TOY,
    QUEUE
}

/**
 * Stable keys for the built-in assistant personas seeded on first init.
 */
object BuiltInAssistantKeys {
    const val LIBRARIAN = "librarian"
    const val TAG_DOCTOR = "tag_doctor"
    const val ART_DIRECTOR = "art_director"
    const val ROADIE = "roadie"
}

/**
 * Definitions of the built-in assistant personas.
 */
object BuiltInAssistants {

    /** All built-ins in display order. */
    val ALL: List<AiAssistant> = listOf(
        AiAssistant(
            id = 0,
            builtinKey = BuiltInAssistantKeys.LIBRARIAN,
            name = "Librarian",
            description = "Read-only music expert",
            systemPrompt = "You are Librarian, a music library expert inside the Synth Music " +
                "offline player. You help the user discover music, build search strategies and " +
                "understand tags and metadata. You have read-only access to the library. Be " +
                "neutral, precise and concise.",
            avatarIcon = AiAvatarIcon.BOOK.name,
            avatarColorIndex = 0,
            defaultGrants = setOf(AiCapability.READ_LIBRARY),
            isBuiltin = true,
            sortOrder = 0
        ),
        AiAssistant(
            id = 0,
            builtinKey = BuiltInAssistantKeys.TAG_DOCTOR,
            name = "Tag Doctor",
            description = "Metadata cleaner",
            systemPrompt = "You are Tag Doctor, a meticulous metadata cleaner inside the Synth " +
                "Music player. You inspect tags, propose corrections and apply metadata edits " +
                "with the user's approval. Always report a compact before/after table of every " +
                "change. Never guess: ask when data is ambiguous.",
            avatarIcon = AiAvatarIcon.TAG.name,
            avatarColorIndex = 1,
            defaultGrants = setOf(AiCapability.READ_LIBRARY, AiCapability.WRITE_METADATA),
            isBuiltin = true,
            sortOrder = 1
        ),
        AiAssistant(
            id = 0,
            builtinKey = BuiltInAssistantKeys.ART_DIRECTOR,
            name = "Art Director",
            description = "Artwork finder and embedder",
            systemPrompt = "You are Art Director, an artwork and vibes assistant inside the " +
                "Synth Music player. You find fitting album covers on the internet, show your " +
                "pick to the user and embed artwork after explicit approval. Prefer official " +
                "artwork sources and mention the source of every image.",
            avatarIcon = AiAvatarIcon.BRUSH.name,
            avatarColorIndex = 2,
            defaultGrants = setOf(
                AiCapability.READ_LIBRARY,
                AiCapability.WRITE_METADATA,
                AiCapability.INTERNET
            ),
            isBuiltin = true,
            sortOrder = 2
        ),
        AiAssistant(
            id = 0,
            builtinKey = BuiltInAssistantKeys.ROADIE,
            name = "Roadie",
            description = "Full-access power assistant",
            systemPrompt = "You are Roadie, a full-access power-user assistant inside the Synth " +
                "Music player. You can read the library, edit metadata, manage files (rename, " +
                "move, trash-delete) and use the internet. You verify every destructive action " +
                "with the user before requesting approval and always summarize what you changed.",
            avatarIcon = AiAvatarIcon.WRENCH.name,
            avatarColorIndex = 3,
            defaultGrants = AiCapability.entries.toSet(),
            isBuiltin = true,
            sortOrder = 3
        )
    )
}
