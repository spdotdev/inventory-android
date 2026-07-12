package dev.scuttle.inventory

import dev.scuttle.inventory.data.settings.AppLanguage
import dev.scuttle.inventory.data.settings.LanguageStore
import dev.scuttle.inventory.ui.settings.LanguageViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageViewModelTest {
    private class FakeLanguageStore(
        initial: AppLanguage = AppLanguage.EN,
    ) : LanguageStore {
        var stored = initial

        override fun get() = stored

        override fun set(language: AppLanguage) {
            stored = language
        }
    }

    @Test
    fun initial_language_comes_from_store() {
        val vm = LanguageViewModel(FakeLanguageStore(AppLanguage.NL))
        assertEquals(AppLanguage.NL, vm.language.value)
    }

    @Test
    fun set_language_updates_state_and_persists() {
        val store = FakeLanguageStore(AppLanguage.EN)
        val vm = LanguageViewModel(store)
        vm.setLanguage(AppLanguage.NL)
        assertEquals(AppLanguage.NL, vm.language.value)
        assertEquals(AppLanguage.NL, store.stored)
    }

    @Test
    fun set_language_back_to_same_value_is_stable() {
        val store = FakeLanguageStore(AppLanguage.EN)
        val vm = LanguageViewModel(store)
        vm.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, vm.language.value)
    }
}
