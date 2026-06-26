package com.synth.synthmusic.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Parameters for generating an abstract artwork image.
 *
 * @property backgroundStartColor First key color for the gradient background.
 * @property backgroundEndColor Second key color for the gradient background.
 * @property iconColor Color used to draw the selected icon shape.
 * @property icon Selected icon shape.
 * @property iconOffsetXPercent Horizontal icon offset as a fraction of the available travel.
 *   0f places the icon's bounding box against the left edge, 1f against the right edge,
 *   and 0.5f centers it.
 * @property iconOffsetYPercent Vertical icon offset as a fraction of the available travel.
 *   0f places the icon's bounding box against the top edge, 1f against the bottom edge,
 *   and 0.5f centers it.
 * @property iconScale Icon scale multiplier, where 1.0f is the default size.
 */
data class GeneratedArtworkConfig(
    val backgroundStartColor: Color,
    val backgroundEndColor: Color,
    val iconColor: Color,
    val icon: GeneratedArtworkIcon,
    val iconOffsetXPercent: Float,
    val iconOffsetYPercent: Float,
    val iconScale: Float
)
