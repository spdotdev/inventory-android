package dev.scuttle.inventory

import dev.scuttle.inventory.data.settings.ThemeModeStore
import dev.scuttle.inventory.ui.settings.ThemeViewModel
import dev.scuttle.inventory.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelTest {
    private class FakeThemeModeStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemeModeStore {
        var stored: ThemeMode = initial

        override fun get(): ThemeMode = stored

        override fun set(mode: ThemeMode) {
            stored = mode
        }
    }

    @Test
    fun initial_mode_comes_from_the_store() {
        val viewModel = ThemeViewModel(FakeThemeModeStore(ThemeMode.DARK))

        assertEquals(ThemeMode.DARK, viewModel.mode.value)
    }

    @Test
    fun set_mode_updates_state_and_persists() {
        val store = FakeThemeModeStore(ThemeMode.SYSTEM)
        val viewModel = ThemeViewModel(store)

        viewModel.setMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.mode.value)
        assertEquals(ThemeMode.LIGHT, store.stored)
    }
}
