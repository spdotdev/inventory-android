package dev.scuttle.inventory.ui.home

import androidx.lifecycle.ViewModel
import dev.scuttle.inventory.data.settings.FavoritesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AllStoragesUiState(
    val favoriteLocationIds: Set<Long> = emptySet(),
)

@HiltViewModel
class AllStoragesViewModel @Inject constructor(
    private val favoritesStore: FavoritesStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AllStoragesUiState(favoritesStore.getFavoriteLocations()))
    val state: StateFlow<AllStoragesUiState> = _state.asStateFlow()

    fun toggleFavorite(id: Long) {
        favoritesStore.toggleFavoriteLocation(id)
        _state.update { it.copy(favoriteLocationIds = favoritesStore.getFavoriteLocations()) }
    }
}
