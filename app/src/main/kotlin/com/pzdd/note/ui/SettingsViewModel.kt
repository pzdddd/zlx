package com.pzdd.note.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pzdd.note.data.AppSettings
import com.pzdd.note.data.SettingsRepository
import com.pzdd.note.data.SortOrder
import com.pzdd.note.data.ThemeMode
import com.pzdd.note.data.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    private val _settings = MutableStateFlow(repo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun persist() = repo.save(_settings.value)

    fun setThemeMode(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
        persist()
    }

    fun setThemeColorKey(key: String) {
        _settings.value = _settings.value.copy(themeColorKey = key)
        persist()
    }

    fun setFloatingBottomBar(enabled: Boolean) {
        _settings.value = _settings.value.copy(floatingBottomBar = enabled)
        persist()
    }

    fun setLiquidGlassBottomBar(enabled: Boolean) {
        _settings.value = _settings.value.copy(liquidGlassBottomBar = enabled)
        persist()
    }

    fun setSortOrder(order: SortOrder) {
        _settings.value = _settings.value.copy(sortOrder = order)
        persist()
    }

    fun setViewMode(mode: ViewMode) {
        _settings.value = _settings.value.copy(viewMode = mode)
        persist()
    }
}