package dev.scuttle.inventory.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HouseholdWithLocations
import dev.scuttle.inventory.data.settings.FavoritesStore
import dev.scuttle.inventory.data.settings.HouseholdViewStore
import dev.scuttle.inventory.ui.common.orderByPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AllStoragesUiState(
    val favoriteLocationIds: Set<Long> = emptySet(),
    // Which household groups are currently collapsed on the storage list. Purely
    // a presentation toggle -- see AllStoragesViewModel.toggleCollapsed's doc for
    // why it must never touch anything else (favorites, edit mode, delete flow).
    val collapsedHouseholdIds: Set<Long> = emptySet(),
)

@HiltViewModel
class AllStoragesViewModel
    @Inject
    constructor(
        private val favoritesStore: FavoritesStore,
        private val householdViewStore: HouseholdViewStore,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                AllStoragesUiState(
                    favoriteLocationIds = favoritesStore.getFavoriteLocations(),
                    collapsedHouseholdIds = householdViewStore.collapsed(),
                ),
            )
        val state: StateFlow<AllStoragesUiState> = _state.asStateFlow()

        fun toggleFavorite(id: Long) {
            favoritesStore.toggleFavoriteLocation(id)
            _state.update { it.copy(favoriteLocationIds = favoritesStore.getFavoriteLocations()) }
        }

        /**
         * Collapse/expand ONE household group. Deliberately touches nothing but
         * [HouseholdViewStore]'s own collapsed-id set: no favorite, no edit-mode
         * state, and no delete-flow state (that lives entirely in DrawerViewModel,
         * a separate class) is ever read or written here. AllStoragesScreen has no
         * persisted multi-select either -- a delete is a single, immediate tap
         * that opens a confirmation dialog on the spot -- so there is no "pending
         * selection" this toggle could ever hide. Keeping the two concerns
         * structurally separate is what makes that true by construction rather
         * than by convention.
         */
        fun toggleCollapsed(id: Long) {
            householdViewStore.toggleCollapsed(id)
            _state.update { it.copy(collapsedHouseholdIds = householdViewStore.collapsed()) }
        }

        /**
         * Household groups in the user's device-local drag order (D8): manual
         * position wins, name is the tie-break for households nobody has
         * reordered yet -- the same [orderByPosition] rule Task 2 pinned for
         * locations/shelves, applied here to household groups themselves.
         */
        fun orderedEntries(entries: List<HouseholdWithLocations>): List<HouseholdWithLocations> {
            val order = householdViewStore.order()
            return orderByPosition(entries, { order.indexOf(it.id).takeIf { idx -> idx >= 0 } }, { it.name })
        }
    }
