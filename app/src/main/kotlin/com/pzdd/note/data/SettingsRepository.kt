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

/**
 * 排序方向：0 = 降序（最新修改在前），1 = 升序（最早修改在前）
 */
enum class SortOrder(val value: Int, val label: String) {
    DESCENDING(0, "最新修改在前"),
    ASCENDING(1, "最早修改在前");

    companion object {
        fun fromValue(v: Int): SortOrder = entries.firstOrNull { it.value == v } ?: DESCENDING
    }
}

/**
 * 视图模式：0 = 列表视图，1 = 网格视图
 */
enum class ViewMode(val value: Int, val label: String) {
    LIST(0, "列表视图"),
    GRID(1, "网格视图");

    companion object {
        fun fromValue(v: Int): ViewMode = entries.firstOrNull { it.value == v } ?: LIST
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColorKey: String = "purple",
    val floatingBottomBar: Boolean = true,
    val liquidGlassBottomBar: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val viewMode: ViewMode = ViewMode.LIST
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pznote_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromValue(prefs.getInt(KEY_THEME_MODE, 0)),
        themeColorKey = prefs.getString(KEY_THEME_COLOR, "purple") ?: "purple",
        floatingBottomBar = prefs.getBoolean(KEY_FLOATING_BOTTOM_BAR, true),
        liquidGlassBottomBar = prefs.getBoolean(KEY_LIQUID_GLASS, true),
        sortOrder = SortOrder.fromValue(prefs.getInt(KEY_SORT_ORDER, 0)),
        viewMode = ViewMode.fromValue(prefs.getInt(KEY_VIEW_MODE, 0))
    )

    fun save(settings: AppSettings) {
        prefs.edit().apply {
            putInt(KEY_THEME_MODE, settings.themeMode.value)
            putString(KEY_THEME_COLOR, settings.themeColorKey)
            putBoolean(KEY_FLOATING_BOTTOM_BAR, settings.floatingBottomBar)
            putBoolean(KEY_LIQUID_GLASS, settings.liquidGlassBottomBar)
            putInt(KEY_SORT_ORDER, settings.sortOrder.value)
            putInt(KEY_VIEW_MODE, settings.viewMode.value)
        }.apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_VIEW_MODE = "view_mode"
    }
}