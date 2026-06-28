package dev.scuttle.inventory.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.settings.DefaultHouseholdStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawerUiState(
    val households: List<HouseholdDto> = emptyList(),
    val defaultHouseholdId: Long? = null,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val defaultHouseholdStore: DefaultHouseholdStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DrawerUiState())
    val state: StateFlow<DrawerUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                _state.update {
                    it.copy(
                        households = householdRepository.list(),
                        defaultHouseholdId = defaultHouseholdStore.get(),
                    )
                }
            }
        }
    }

    fun setDefault(householdId: Long) {
        defaultHouseholdStore.set(householdId)
        _state.update { it.copy(defaultHouseholdId = householdId) }
    }

    fun clearDefault() {
        defaultHouseholdStore.clear()
        _state.update { it.copy(defaultHouseholdId = null) }
    }

    fun getDefault(): Long? = defaultHouseholdStore.get()
}
