package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.GeneratedArtworkConfig
import com.synth.synthmusic.domain.model.GeneratedArtworkIcon
import com.synth.synthmusic.domain.usecase.GenerateArtworkUseCase
import com.synth.synthmusic.ui.components.ArtworkPresetColors
import com.synth.synthmusic.ui.components.ColorPickerDialog
import com.synth.synthmusic.ui.components.IconPickerDialog
import com.synth.synthmusic.ui.components.MenuDialog
import org.koin.compose.koinInject

/**
 * Target color fields that can be edited through the color picker.
 */
private enum class ColorPickerTarget(val title: String) {
    BackgroundStart("Background start"),
    BackgroundEnd("Background end"),
    IconColor("Icon color")
}

/**
 * Dialog for configuring and generating a procedural artwork image.
 *
 * The menu is intentionally compact: color and icon choices are moved into
 * separate picker dialogs so the main configuration screen always fits on
 * small devices.
 *
 * @param onGenerate Callback invoked with the final [GeneratedArtworkConfig].
 * @param onDismiss Dismiss callback.
 * @param generateArtworkUseCase Use case that renders the preview bitmap.
 */
@Composable
fun GenerateArtworkDialog(
    onGenerate: (GeneratedArtworkConfig) -> Unit,
    onDismiss: () -> Unit,
    generateArtworkUseCase: GenerateArtworkUseCase = koinInject()
) {
    var backgroundStart by remember { mutableStateOf(ArtworkPresetColors[0]) }
    var backgroundEnd by remember { mutableStateOf(ArtworkPresetColors[3]) }
    var iconColor by remember { mutableStateOf(Color.White) }
    var icon by remember { mutableStateOf(GeneratedArtworkIcon.MusicNote) }
    var offsetX by remember { mutableFloatStateOf(0.5f) }
    var offsetY by remember { mutableFloatStateOf(0.5f) }
    var iconScale by remember { mutableFloatStateOf(1.0f) }

    var activeColorPicker by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }

    val config = GeneratedArtworkConfig(
        backgroundStartColor = backgroundStart,
        backgroundEndColor = backgroundEnd,
        iconColor = iconColor,
        icon = icon,
        iconOffsetXPercent = offsetX,
        iconOffsetYPercent = offsetY,
        iconScale = iconScale
    )

    val previewPainter by produceState<BitmapPainter?>(initialValue = null, config) {
        val bytes = generateArtworkUseCase(config, PREVIEW_SIZE)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        value = BitmapPainter(bitmap.asImageBitmap())
    }

    activeColorPicker?.let { target ->
        ColorPickerDialog(
            title = target.title,
            selected = when (target) {
                ColorPickerTarget.BackgroundStart -> backgroundStart
                ColorPickerTarget.BackgroundEnd -> backgroundEnd
                ColorPickerTarget.IconColor -> iconColor
            },
            onSelected = { color ->
                when (target) {
                    ColorPickerTarget.BackgroundStart -> backgroundStart = color
                    ColorPickerTarget.BackgroundEnd -> backgroundEnd = color
                    ColorPickerTarget.IconColor -> iconColor = color
                }
                activeColorPicker = null
            },
            onDismiss = { activeColorPicker = null }
        )
    }

    if (showIconPicker) {
        IconPickerDialog(
            selected = icon,
            onSelected = {
                icon = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    MenuDialog(
        title = "Generate Artwork",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            previewPainter?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = "Generated artwork preview",
                    modifier = Modifier
                        .size(120.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                )
            } ?: Spacer(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OptionRow(
                label = "Background colors",
                onClick = null
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(
                        color = backgroundStart,
                        onClick = { activeColorPicker = ColorPickerTarget.BackgroundStart }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    ColorSwatch(
                        color = backgroundEnd,
                        onClick = { activeColorPicker = ColorPickerTarget.BackgroundEnd }
                    )
                }
            }

            OptionRow(
                label = "Icon color",
                onClick = { activeColorPicker = ColorPickerTarget.IconColor }
            ) {
                ColorSwatch(
                    color = iconColor,
                    onClick = { activeColorPicker = ColorPickerTarget.IconColor }
                )
            }

            OptionRow(
                label = "Icon",
                onClick = { showIconPicker = true }
            ) {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = icon.name,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel("Icon position X")
            Slider(
                value = offsetX,
                onValueChange = { offsetX = it },
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            SectionLabel("Icon position Y")
            Slider(
                value = offsetY,
                onValueChange = { offsetY = it },
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            SectionLabel("Icon scale")
            Slider(
                value = iconScale,
                onValueChange = { iconScale = it },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onGenerate(config)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Generate")
            }
        }
    }
}

/**
 * A single compact row inside the generate-artwork menu.
 *
 * @param label Text label displayed on the start of the row.
 * @param onClick Called when the row is tapped.
 * @param modifier Modifier for layout.
 * @param trailingContent Optional trailing composable (e.g. the current value).
 */
@Composable
private fun OptionRow(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

/**
 * Small circular preview of the selected color.
 *
 * @param color Color to display.
 * @param modifier Modifier for layout.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private const val PREVIEW_SIZE = 256
