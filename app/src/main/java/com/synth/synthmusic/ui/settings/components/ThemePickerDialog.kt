package com.synth.synthmusic.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.ui.theme.BluePrimary
import com.synth.synthmusic.ui.theme.GreenPrimary
import com.synth.synthmusic.ui.theme.OrangePrimary
import com.synth.synthmusic.ui.theme.PurplePrimary
import com.synth.synthmusic.ui.theme.RedPrimary
import com.synth.synthmusic.ui.theme.YellowPrimary

/**
 * Full-screen dialog for selecting theme mode and accent color.
 *
 * @param currentTheme currently selected [ThemeMode].
 * @param currentAccent currently selected [AccentColor].
 * @param onThemeSelected callback when a theme mode is chosen.
 * @param onAccentSelected callback when an accent color is chosen.
 * @param onDismiss called when the dialog should be dismissed.
 */
@Composable
fun ThemePickerDialog(
    currentTheme: ThemeMode,
    currentAccent: AccentColor,
    onThemeSelected: (ThemeMode) -> Unit,
    onAccentSelected: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip(
                        icon = Icons.Default.LightMode,
                        label = "Light",
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onThemeSelected(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeChip(
                        icon = Icons.Default.DarkMode,
                        label = "Dark",
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onThemeSelected(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeChip(
                        icon = Icons.Default.BrightnessAuto,
                        label = "System",
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { onThemeSelected(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AccentColorCircle(
                            color = YellowPrimary,
                            selected = currentAccent == AccentColor.YELLOW,
                            onClick = { onAccentSelected(AccentColor.YELLOW) }
                        )
                        AccentColorCircle(
                            color = GreenPrimary,
                            selected = currentAccent == AccentColor.GREEN,
                            onClick = { onAccentSelected(AccentColor.GREEN) }
                        )
                        AccentColorCircle(
                            color = BluePrimary,
                            selected = currentAccent == AccentColor.BLUE,
                            onClick = { onAccentSelected(AccentColor.BLUE) }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AccentColorCircle(
                            color = RedPrimary,
                            selected = currentAccent == AccentColor.RED,
                            onClick = { onAccentSelected(AccentColor.RED) }
                        )
                        AccentColorCircle(
                            color = PurplePrimary,
                            selected = currentAccent == AccentColor.PURPLE,
                            onClick = { onAccentSelected(AccentColor.PURPLE) }
                        )
                        AccentColorCircle(
                            color = OrangePrimary,
                            selected = currentAccent == AccentColor.ORANGE,
                            onClick = { onAccentSelected(AccentColor.ORANGE) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AccentColorCircle(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
