package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.AiAvatarIcon
import com.synth.synthmusic.domain.model.AiAssistant

/**
 * Renders an assistant persona avatar: preset icon on a tinted circle.
 * Colors are derived from the Material theme palette, never hardcoded.
 *
 * @param assistant the persona to render.
 * @param size the avatar diameter.
 * @param modifier the modifier to be applied to the avatar.
 */
@Composable
fun AssistantAvatar(
    assistant: AiAssistant,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    AssistantAvatar(
        iconName = assistant.avatarIcon,
        colorIndex = assistant.avatarColorIndex,
        contentDescription = assistant.name,
        size = size,
        modifier = modifier
    )
}

/**
 * Renders an avatar from raw persona fields, tolerating deleted assistants.
 * A null [iconName] or [colorIndex] falls back to the neutral assistant look.
 *
 * @param iconName the [AiAvatarIcon] name of the persona.
 * @param colorIndex the persona tint index.
 * @param contentDescription accessibility label, null for decorative use.
 * @param size the avatar diameter.
 * @param modifier the modifier to be applied to the avatar.
 */
@Composable
fun AssistantAvatar(
    iconName: String?,
    colorIndex: Int?,
    contentDescription: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val tints = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    val containers = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    if (iconName == null || colorIndex == null) {
        Box(
            modifier = modifier
                .size(size)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = neutralAssistantIcon(),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
        return
    }
    val index = ((colorIndex % tints.size) + tints.size) % tints.size
    Box(
        modifier = modifier
            .size(size)
            .background(containers[index], CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = avatarIcon(iconName),
            contentDescription = contentDescription,
            tint = tints[index],
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Maps an [AiAvatarIcon] name (defensively) to an [ImageVector].
 */
fun avatarIcon(name: String): ImageVector = when (
    runCatching { AiAvatarIcon.valueOf(name) }.getOrDefault(AiAvatarIcon.SMART_TOY)
) {
    AiAvatarIcon.MUSIC_NOTE -> Icons.AutoMirrored.Filled.QueueMusic
    AiAvatarIcon.BRUSH -> Icons.Default.Brush
    AiAvatarIcon.TAG -> Icons.Default.Sell
    AiAvatarIcon.BOLT -> Icons.Default.Bolt
    AiAvatarIcon.CLOUD -> Icons.Default.Cloud
    AiAvatarIcon.BOOK -> Icons.Default.Book
    AiAvatarIcon.STAR -> Icons.Default.Star
    AiAvatarIcon.HEART -> Icons.Default.Favorite
    AiAvatarIcon.WRENCH -> Icons.Default.Build
    AiAvatarIcon.PERSON -> Icons.Default.Person
    AiAvatarIcon.SMART_TOY -> Icons.Default.SmartToy
    AiAvatarIcon.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
}

/**
 * Neutral assistant icon used when a chat has no persona.
 */
fun neutralAssistantIcon(): ImageVector = Icons.Default.AutoAwesome
