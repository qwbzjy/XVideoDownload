package com.example.xvideodownload

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF1D9BF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EDFF),
    onPrimaryContainer = Color(0xFF003258),
    secondary = Color(0xFF536471),
    background = Color.White,
    surface = Color.White,
    error = Color(0xFFBA1A1A),
)

@Composable
fun XVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
