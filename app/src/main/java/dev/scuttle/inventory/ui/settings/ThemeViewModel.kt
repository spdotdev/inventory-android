package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.settings.ThemeModeStore
import dev.scuttle.inventory.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Holds the active theme mode. Activity-scoped, so MainActivity (which applies the
 * theme) and SettingsScreen (which changes it) share the same instance.
 */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val store: ThemeModeStore,
    ) : ViewModel() {
        private val _mode = MutableStateFlow(store.get())
        val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

        fun setMode(mode: ThemeMode) {
            store.set(mode)
            _mode.value = mode
        }
    }
