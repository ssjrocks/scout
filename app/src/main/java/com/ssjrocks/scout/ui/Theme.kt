package com.ssjrocks.scout.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.ssjrocks.scout.R
import com.ssjrocks.scout.data.Settings

/** Google Sans Flex (OFL, from Google Fonts). One variable file; the weight axis follows FontWeight. */
val GoogleSans = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    Font(R.font.google_sans_flex, FontWeight.Bold),
)

private val GoogleLight = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF00639B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E7FF), onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF146C2E), tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color.White, onBackground = Color(0xFF1F1F1F),
    surface = Color.White, onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1), onSurfaceVariant = Color(0xFF444746),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F4F9), surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFF747775), outlineVariant = Color(0xFFC4C7C5),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC),
)

private val GoogleDark = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7FCFFF), onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77), onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C), tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF1F1F1F), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1F1F1F), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746), onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF282A2C), surfaceContainerHigh = Color(0xFF333537),
    surfaceContainerHighest = Color(0xFF3D3F42),
    outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
    error = Color(0xFFF2B8B5), errorContainer = Color(0xFF8C1D18),
)

private val GoogleBlack = GoogleDark.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF161616), surfaceContainerHigh = Color(0xFF202124), surfaceContainerHighest = Color(0xFF2A2B2E),
)

private fun TextStyle.g() = copy(fontFamily = GoogleSans)

private val GoogleType = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.g(), displayMedium = t.displayMedium.g(), displaySmall = t.displaySmall.g(),
        headlineLarge = t.headlineLarge.g(), headlineMedium = t.headlineMedium.g(), headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(), titleMedium = t.titleMedium.g().copy(fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.g(), bodyLarge = t.bodyLarge.g(), bodyMedium = t.bodyMedium.g(), bodySmall = t.bodySmall.g(),
        labelLarge = t.labelLarge.g(), labelMedium = t.labelMedium.g(), labelSmall = t.labelSmall.g(),
    )
}

@Composable
fun isDark(s: Settings): Boolean = when (s.theme) {
    "light" -> false
    "dark", "black" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun ScoutTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = isDark(s)
    val ctx = LocalContext.current
    var scheme: ColorScheme = when {
        s.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> GoogleDark
        else -> GoogleLight
    }
    if (dark && s.theme == "black") {
        scheme = if (s.dynamicColor) scheme.copy(background = Color.Black, surface = Color.Black) else GoogleBlack
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * s.textScale)) {
        MaterialTheme(colorScheme = scheme, typography = GoogleType, content = content)
    }
}
