/*
 * 文件职责：整合配色与字体，向外暴露布布表情包转换器的主题框架。
 * 主要内容：BubuTheme（Composabe 主题包装器）。
 * 依赖：依赖本包下的 Color 与 Type 文件。
 */
package me.marukon.webp2gif.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = DarkOnSecondary,
  background = DarkBackground,
  surface = DarkSurface,
  onBackground = DarkOnBackground,
  onSurface = DarkOnSurface,
  tertiary = DarkTertiary
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
  primary = LightPrimary,
  onPrimary = LightOnPrimary,
  primaryContainer = LightPrimaryContainer,
  onPrimaryContainer = LightOnPrimaryContainer,
  secondary = LightSecondary,
  onSecondary = LightOnSecondary,
  background = LightBackground,
  surface = LightSurface,
  onBackground = LightOnBackground,
  onSurface = LightOnSurface,
  tertiary = LightTertiary
)

@Composable
fun BubuTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // 是否启用 Android 12 的动态设备配色取色，设为 false 以全面保持布布软粉奶油主题的经典一致美感
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
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
