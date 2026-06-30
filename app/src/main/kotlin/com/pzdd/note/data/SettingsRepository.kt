package com.pzdd.note.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 主题模式：0 = 跟随系统，1 = 亮色，2 = 暗色
 */
enum class ThemeMode(val value: Int, val label: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "亮色模式"),
    DARK(2, "暗色模式");

    companion object {
        fun fromValue(v: Int): ThemeMode = entries.firstOrNull { it.value == v } ?: SYSTEM
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColorKey: String = "purple",
    val floatingBottomBar: Boolean = true,
    val liquidGlassBottomBar: Boolean = true
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pznote_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromValue(prefs.getInt(KEY_THEME_MODE, 0)),
        themeColorKey = prefs.getString(KEY_THEME_COLOR, "purple") ?: "purple",
        floatingBottomBar = prefs.getBoolean(KEY_FLOATING_BOTTOM_BAR, true),
        liquidGlassBottomBar = prefs.getBoolean(KEY_LIQUID_GLASS, true)
    )

    fun save(settings: AppSettings) {
        prefs.edit().apply {
            putInt(KEY_THEME_MODE, settings.themeMode.value)
            putString(KEY_THEME_COLOR, settings.themeColorKey)
            putBoolean(KEY_FLOATING_BOTTOM_BAR, settings.floatingBottomBar)
            putBoolean(KEY_LIQUID_GLASS, settings.liquidGlassBottomBar)
        }.apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
    }
}