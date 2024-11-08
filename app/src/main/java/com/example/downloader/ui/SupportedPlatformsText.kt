// ui/SupportedPlatformsText.kt
package com.example.downloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SupportedPlatformsText() {
    Text(
        text = "Plateformes supportées : YouTube, Dailymotion",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
