package dev.scuttle.inventory.ui.settings

import androidx.lifecycle.ViewModel
import dev.scuttle.inventory.data.settings.AppLanguage
import dev.scuttle.inventory.data.settings.LanguageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val store: LanguageStore,
) : ViewModel() {

    private val _language = MutableStateFlow(store.get())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        store.set(language)
        _language.value = language
    }
}
