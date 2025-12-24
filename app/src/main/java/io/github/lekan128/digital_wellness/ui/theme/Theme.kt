package io.github.lekan128.digital_wellness.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Soothing Colors
val TeaGreen = Color(0xFFD0F0C0)
val SoftBlue = Color(0xFFAEC6CF)
val MutedLavender = Color(0xFFE6E6FA)
val Charcoal = Color(0xFF36454F)
val OffWhite = Color(0xFFF8F8F8)
val DarkSlate = Color(0xFF2F4F4F)

private val DarkColorScheme = darkColorScheme(
    primary = TeaGreen,
    secondary = SoftBlue,
    tertiary = MutedLavender,
    background = DarkSlate,
    surface = DarkSlate,
    onPrimary = Charcoal,
    onSecondary = Charcoal,
    onTertiary = Charcoal,
    onBackground = OffWhite,
    onSurface = OffWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = DarkSlate,
    secondary = SoftBlue,
    tertiary = MutedLavender,
    background = OffWhite,
    surface = OffWhite,
    onPrimary = TeaGreen,
    onSecondary = Charcoal,
    onTertiary = Charcoal,
    onBackground = Charcoal,
    onSurface = Charcoal,
)

@Composable
fun DigitalWellnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
