package com.example.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.model.ThemeMode

private val FeatherDarkColorScheme = darkColorScheme(
  primary = FeatherTealDark,
  onPrimary = FeatherOnTealDark,
  primaryContainer = FeatherTealContainerDark,
  onPrimaryContainer = FeatherOnTealContainerDark,
  secondary = FeatherMintDark,
  background = FeatherBgDark,
  surface = FeatherSurfaceDark,
  surfaceVariant = FeatherSurfaceVariantDark,
  onBackground = FeatherOnSurfaceDark,
  onSurface = FeatherOnSurfaceDark,
  onSurfaceVariant = FeatherOnSurfaceVariantDark,
  outline = FeatherOutlineDark,
  error = FeatherDangerRed
)

private val FeatherLightColorScheme = lightColorScheme(
  primary = FeatherTealLight,
  onPrimary = FeatherOnTealLight,
  primaryContainer = FeatherTealContainerLight,
  onPrimaryContainer = FeatherOnTealContainerLight,
  secondary = FeatherMintLight,
  background = FeatherBgLight,
  surface = FeatherSurfaceLight,
  surfaceVariant = FeatherSurfaceVariantLight,
  onBackground = FeatherOnSurfaceLight,
  onSurface = FeatherOnSurfaceLight,
  onSurfaceVariant = FeatherOnSurfaceVariantLight,
  outline = FeatherOutlineLight,
  error = FeatherDangerRed
)

val LocalReduceMotion = staticCompositionLocalOf { false }

fun isReducedMotionEnabled(context: Context): Boolean {
  return try {
    val scale = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1.0f
    )
    scale == 0.0f
  } catch (_: Throwable) {
    false
  }
}

@Composable
fun FeatherTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
  }
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      try {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } catch (_: Throwable) {
        if (darkTheme) FeatherDarkColorScheme else FeatherLightColorScheme
      }
    }
    darkTheme -> FeatherDarkColorScheme
    else -> FeatherLightColorScheme
  }
  val reduceMotion = remember(context) { isReducedMotionEnabled(context) }

  CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = FeatherTypography,
      content = content
    )
  }
}
