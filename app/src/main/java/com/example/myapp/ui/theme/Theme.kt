package com.example.myapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = DarkBackground,
    primaryContainer = TealAccentDark.copy(alpha = 0.2f),
    onPrimaryContainer = TealAccent,
    
    secondary = OrangeAccent,
    onSecondary = DarkBackground,
    secondaryContainer = OrangeAccent.copy(alpha = 0.2f),
    onSecondaryContainer = OrangeAccent,
    
    tertiary = GreenAccent,
    onTertiary = DarkBackground,
    tertiaryContainer = GreenAccent.copy(alpha = 0.2f),
    onTertiaryContainer = GreenAccent,
    
    error = Color(0xFFFF5252),
    onError = TextPrimary,
    errorContainer = Color(0xFFFF5252).copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFFF5252),
    
    background = DarkBackground,
    onBackground = TextPrimary,
    
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    outline = DarkSurfaceVariant,
    outlineVariant = DarkCard
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccentDark,
    secondary = OrangeAccentLight,
    tertiary = GreenAccentLight
)

@Composable
fun MyAppTheme(
    darkTheme: Boolean = true, // Force dark theme
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic colors to use our custom theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}