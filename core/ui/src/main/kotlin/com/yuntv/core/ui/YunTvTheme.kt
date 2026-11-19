package com.yuntv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val YunTvColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF071A33),
    secondary = Color(0xFF81C995),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE8EAED),
)

@Composable
fun YunTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YunTvColors,
        content = content,
    )
}
