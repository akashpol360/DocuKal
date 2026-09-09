package com.docukal.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF21105F),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5F2EA),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFE4578A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E5),
    onTertiaryContainer = Color(0xFF3E001D),
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF1A1A20),
    surface = Color(0xFFF8F7FC),
    onSurface = Color(0xFF1A1A20),
    surfaceVariant = Color(0xFFE9E6F0),
    onSurfaceVariant = Color(0xFF5F5B66),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A8FF),
    onPrimary = Color(0xFF321B7A),
    primaryContainer = Color(0xFF4A3892),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF65DBC9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF82F8E5),
    tertiary = Color(0xFFFFABC9),
    onTertiary = Color(0xFF5A1235),
    tertiaryContainer = Color(0xFF7A294E),
    onTertiaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF0D0D13),
    onBackground = Color(0xFFE8E5ED),
    surface = Color(0xFF111118),
    onSurface = Color(0xFFE8E5ED),
    surfaceVariant = Color(0xFF302D38),
    onSurfaceVariant = Color(0xFFC9C4D0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun DocuKalTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
