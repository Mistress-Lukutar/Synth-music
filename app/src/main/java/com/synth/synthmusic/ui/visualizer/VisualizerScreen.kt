package com.synth.synthmusic.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Circular particle audio visualizer screen (placeholder).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val particleColor = primaryColor.copy(alpha = 0.3f)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Visualizer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val radius = size.minDimension / 4

                for (i in 0..7) {
                    rotate(degrees = i * 45f, pivot = Offset(centerX, centerY)) {
                        drawCircle(
                            color = particleColor,
                            radius = radius * 0.2f,
                            center = Offset(centerX + radius, centerY)
                        )
                    }
                }
                drawCircle(
                    color = primaryColor,
                    radius = radius * 0.15f,
                    center = Offset(centerX, centerY)
                )
            }
            Text(
                text = "Visualizer coming soon",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 200.dp)
            )
        }
    }
}
