package com.droid.googlebookfeaturechecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.droid.googlebookfeaturechecker.model.ThemeMode

/**
 * Calm Material 3 Expressive teal palette inspired by googlebook.studio (Fika Labs / HearOn Link).
 */
private val TealLight = lightColorScheme(
  primary = Color(0xFF00696B),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFB2EEEE),
  onPrimaryContainer = Color(0xFF002021),
  secondary = Color(0xFF4A6362),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD5E6E3),
  onSecondaryContainer = Color(0xFF102220),
  tertiary = Color(0xFF9C4230),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFDBD2),
  onTertiaryContainer = Color(0xFF3B0900),
  error = Color(0xFF9C4230),
  onError = Color.White,
  errorContainer = Color(0xFFFFDBD2),
  onErrorContainer = Color(0xFF3B0900),
  background = Color(0xFFF5F9F8),
  onBackground = Color(0xFF161D1C),
  surface = Color(0xFFF5F9F8),
  onSurface = Color(0xFF161D1C),
  surfaceVariant = Color(0xFFDAE5E2),
  onSurfaceVariant = Color(0xFF3E4947),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFEFF5F4),
  surfaceContainer = Color(0xFFE8F0EE),
  surfaceContainerHigh = Color(0xFFE2EAE8),
  surfaceContainerHighest = Color(0xFFDCE4E2),
  outline = Color(0xFF6F7977),
  outlineVariant = Color(0xFFC4D0CD)
)

private val TealDark = darkColorScheme(
  primary = Color(0xFF6FD6D7),
  onPrimary = Color(0xFF003738),
  primaryContainer = Color(0xFF004F51),
  onPrimaryContainer = Color(0xFFB2EEEE),
  secondary = Color(0xFFB0CCC9),
  onSecondary = Color(0xFF1B3533),
  secondaryContainer = Color(0xFF2E4644),
  onSecondaryContainer = Color(0xFFCDE8E5),
  tertiary = Color(0xFFFFB4A2),
  onTertiary = Color(0xFF5C1F12),
  tertiaryContainer = Color(0xFF7E2B1B),
  onTertiaryContainer = Color(0xFFFFDBD2),
  error = Color(0xFFFFB4A2),
  onError = Color(0xFF5C1F12),
  errorContainer = Color(0xFF7E2B1B),
  onErrorContainer = Color(0xFFFFDBD2),
  background = Color(0xFF0F1514),
  onBackground = Color(0xFFDDE4E3),
  surface = Color(0xFF0F1514),
  onSurface = Color(0xFFDDE4E3),
  surfaceVariant = Color(0xFF3F4947),
  onSurfaceVariant = Color(0xFFBAC9C6),
  surfaceContainerLowest = Color(0xFF1C2625),
  surfaceContainerLow = Color(0xFF151D1C),
  surfaceContainer = Color(0xFF161E1D),
  surfaceContainerHigh = Color(0xFF223030),
  surfaceContainerHighest = Color(0xFF2B3634),
  outline = Color(0xFF899392),
  outlineVariant = Color(0xFF3A4745)
)

@Immutable
data class StudioColors(
  val card: Color,
  val chrome: Color,
  val track: Color,
  val warnContainer: Color,
  val onWarnContainer: Color,
  val metGreen: Color,
  val onMetGreen: Color,
  val metGreenContainer: Color,
  val onMetGreenContainer: Color
)

val LocalStudioColors = staticCompositionLocalOf {
  StudioColors(
    card = Color.White,
    chrome = Color(0xFFE8F0EE),
    track = Color(0xFFDCE4E2),
    warnContainer = Color(0xFFFFDBD2),
    onWarnContainer = Color(0xFF3B0900),
    metGreen = Color(0xFF1E8E3E),
    onMetGreen = Color.White,
    metGreenContainer = Color(0xFFCEEAD6),
    onMetGreenContainer = Color(0xFF0D652D)
  )
}

private fun studioTypography(): Typography {
  val b = Typography()
  return b.copy(
    headlineSmall = b.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(700)),
    titleLarge = b.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(680)),
    titleMedium = b.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(620)),
    titleSmall = b.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(650)),
    bodyLarge = b.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = b.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = b.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = b.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(620)),
    labelMedium = b.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(600))
  )
}

@Composable
fun GooglebookFeatureCheckerTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val useDarkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  val c = if (useDarkTheme) TealDark else TealLight
  val studio = StudioColors(
    card = c.surfaceContainerLowest,
    chrome = c.surfaceContainer,
    track = c.surfaceContainerHighest,
    warnContainer = c.tertiaryContainer,
    onWarnContainer = c.onTertiaryContainer,
    metGreen = if (useDarkTheme) Color(0xFF34A853) else Color(0xFF1E8E3E),
    onMetGreen = if (useDarkTheme) Color(0xFF072711) else Color.White,
    metGreenContainer = if (useDarkTheme) Color(0xFF135327) else Color(0xFFCEEAD6),
    onMetGreenContainer = if (useDarkTheme) Color(0xFFA8DAB5) else Color(0xFF0D652D)
  )

  CompositionLocalProvider(LocalStudioColors provides studio) {
    MaterialTheme(
      colorScheme = c,
      typography = studioTypography(),
      content = content
    )
  }
}
