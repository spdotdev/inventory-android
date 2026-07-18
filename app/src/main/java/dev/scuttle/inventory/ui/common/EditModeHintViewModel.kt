package dev.scuttle.inventory.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.settings.HINT_EDIT_MODE_PENCIL
import dev.scuttle.inventory.data.settings.HintsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GAP4-L9: small shared ViewModel backing [EditModeHintBanner] on every screen that shows
 * the edit-mode pencil — one [HintsStore] flag (device-scoped) covers all of them, so
 * dismissing the hint on one screen dismisses it everywhere, matching "shown once, ever".
 */
@HiltViewModel
class EditModeHintViewModel
    @Inject
    constructor(
        private val hintsStore: HintsStore,
    ) : ViewModel() {
        private val _visible = MutableStateFlow(!hintsStore.hasSeen(HINT_EDIT_MODE_PENCIL))
        val visible: StateFlow<Boolean> = _visible.asStateFlow()

        fun dismiss() {
            viewModelScope.launch {
                hintsStore.markSeen(HINT_EDIT_MODE_PENCIL)
                _visible.value = false
            }
        }
    }
