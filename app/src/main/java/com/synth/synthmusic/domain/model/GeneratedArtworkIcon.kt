package com.synth.synthmusic.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Standard icon choices that can be drawn on a procedurally generated cover.
 *
 * Each entry exposes a Material [ImageVector] so the picker UI uses the same
 * icons as the rest of the application instead of custom generated shapes.
 */
enum class GeneratedArtworkIcon(
    val imageVector: ImageVector
) {
    MusicNote(Icons.Default.MusicNote),
    PlayArrow(Icons.Default.PlayArrow),
    Pause(Icons.Default.Pause),
    Stop(Icons.Default.Stop),
    Star(Icons.Default.Star),
    Favorite(Icons.Default.Favorite),
    Circle(Icons.Default.Circle),
    Square(Icons.Default.CropSquare),
    Diamond(Icons.Default.Diamond),
    Headphones(Icons.Default.Headphones),
    Bolt(Icons.Default.Bolt),
    Album(Icons.Default.Album),
    Mic(Icons.Default.Mic),
    Audiotrack(Icons.Default.Audiotrack),
    QueueMusic(Icons.AutoMirrored.Default.QueueMusic),
    PlaylistPlay(Icons.AutoMirrored.Default.PlaylistPlay),
    GraphicEq(Icons.Default.GraphicEq),
    Lightbulb(Icons.Default.Lightbulb),
    Radio(Icons.Default.Radio),
    Speaker(Icons.Default.Speaker)
}
