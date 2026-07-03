package dev.scuttle.inventory.ui.missing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.MissingItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MissingItemsUiState(
    val loading: Boolean = false,
    val items: List<MissingItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MissingItemsViewModel @Inject constructor(
    private val store: HierarchyStore,
) : ViewModel() {

    val state: StateFlow<MissingItemsUiState> = store.state.map { s ->
        MissingItemsUiState(loading = s.loading, items = s.missingItems, error = s.error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MissingItemsUiState())

    fun refresh() = store.refresh()
}
