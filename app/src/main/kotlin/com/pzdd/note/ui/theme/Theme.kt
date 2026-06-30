package com.pzdd.note.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pzdd.note.data.ThemeMode

private fun buildLightScheme(c: ThemeColorScheme) = lightColorScheme(
    primary = c.lightPrimary,
    secondary = c.lightSecondary,
    tertiary = c.lightTertiary
)

private fun buildDarkScheme(c: ThemeColorScheme) = darkColorScheme(
    primary = c.darkPrimary,
    secondary = c.darkSecondary,
    tertiary = c.darkTertiary
)

@Composable
fun ComposeEmptyActivityTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColorKey: String = "purple",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        themeColorKey == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val c = findThemeColor(themeColorKey)
            if (darkTheme) buildDarkScheme(c) else buildLightScheme(c)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}