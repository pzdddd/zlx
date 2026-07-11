package com.pzdd.note.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ===== 主题色方案 =====
// 每个主题色提供亮色/暗色两套 primary/secondary/tertiary

data class ThemeColorScheme(
    val key: String,
    val name: String,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color
)

val ThemeColorOptions: List<ThemeColorScheme> = listOf(
    ThemeColorScheme(
        key = "purple",
        name = "经典紫",
        lightPrimary = Color(0xFF6650A4),
        lightSecondary = Color(0xFF625B71),
        lightTertiary = Color(0xFF7D5260),
        darkPrimary = Color(0xFFD0BCFF),
        darkSecondary = Color(0xFFCCC2DC),
        darkTertiary = Color(0xFFEFB8C8)
    ),
    ThemeColorScheme(
        key = "blue",
        name = "海洋蓝",
        lightPrimary = Color(0xFF1976D2),
        lightSecondary = Color(0xFF4FC3F7),
        lightTertiary = Color(0xFF7E57C2),
        darkPrimary = Color(0xFF82B1FF),
        darkSecondary = Color(0xFF80D8FF),
        darkTertiary = Color(0xFFB39DDB)
    ),
    ThemeColorScheme(
        key = "green",
        name = "森林绿",
        lightPrimary = Color(0xFF2E7D32),
        lightSecondary = Color(0xFF66BB6A),
        lightTertiary = Color(0xFFAED581),
        darkPrimary = Color(0xFFA5D6A7),
        darkSecondary = Color(0xFF81C784),
        darkTertiary = Color(0xFFC5E1A5)
    ),
    ThemeColorScheme(
        key = "orange",
        name = "活力橙",
        lightPrimary = Color(0xFFEF6C00),
        lightSecondary = Color(0xFFFFB74D),
        lightTertiary = Color(0xFFFF8A65),
        darkPrimary = Color(0xFFFFB74D),
        darkSecondary = Color(0xFFFFCC80),
        darkTertiary = Color(0xFFFFAB91)
    ),
    ThemeColorScheme(
        key = "red",
        name = "热情红",
        lightPrimary = Color(0xFFC62828),
        lightSecondary = Color(0xFFEF5350),
        lightTertiary = Color(0xFFEC407A),
        darkPrimary = Color(0xFFEF9A9A),
        darkSecondary = Color(0xFFE57373),
        darkTertiary = Color(0xFFF48FB1)
    ),
    ThemeColorScheme(
        key = "teal",
        name = "青碧",
        lightPrimary = Color(0xFF00897B),
        lightSecondary = Color(0xFF26A69A),
        lightTertiary = Color(0xFF80CBC4),
        darkPrimary = Color(0xFF80CBC4),
        darkSecondary = Color(0xFF4DB6AC),
        darkTertiary = Color(0xFFB2DFDB)
    ),
    ThemeColorScheme(
        key = "pink",
        name = "樱花粉",
        lightPrimary = Color(0xFFD81B60),
        lightSecondary = Color(0xFFEC407A),
        lightTertiary = Color(0xFFF06292),
        darkPrimary = Color(0xFFF48FB1),
        darkSecondary = Color(0xFFF06292),
        darkTertiary = Color(0xFFFF8A80)
    ),
    ThemeColorScheme(
        key = "indigo",
        name = "靛青",
        lightPrimary = Color(0xFF3F51B5),
        lightSecondary = Color(0xFF5C6BC0),
        lightTertiary = Color(0xFF7986CB),
        darkPrimary = Color(0xFF9FA8DA),
        darkSecondary = Color(0xFF7986CB),
        darkTertiary = Color(0xFFB39DDB)
    )
)

fun findThemeColor(key: String): ThemeColorScheme =
    ThemeColorOptions.firstOrNull { it.key == key } ?: ThemeColorOptions.first()