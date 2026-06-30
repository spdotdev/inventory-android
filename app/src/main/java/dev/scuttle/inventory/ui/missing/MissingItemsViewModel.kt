package dev.scuttle.inventory.ui.missing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissingItem(
    val productName: String,
    val shelfName: String,
    val locationName: String,
    val householdId: Long,
    val locationId: Long,
)

data class MissingItemsUiState(
    val loading: Boolean = false,
    val items: List<MissingItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MissingItemsViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val locationRepository: LocationRepository,
    private val shelfRepository: ShelfRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MissingItemsUiState())
    val state: StateFlow<MissingItemsUiState> = _state.asStateFlow()

    init {
        if (householdRepository.getCached() != null) {
            loadFromCache()
            refreshSilent()
        } else {
            refresh()
        }
    }

    private fun loadFromCache() {
        val households = householdRepository.getCached() ?: return
        val missing = mutableListOf<MissingItem>()
        for (household in households) {
            val locations = locationRepository.getCached(household.id) ?: continue
            for (location in locations) {
                val shelves = shelfRepository.getCached(household.id, location.id) ?: continue
                for (shelf in shelves) {
                    val products = productRepository.getCached(household.id, shelf.id) ?: continue
                    for (product in products) {
                        if (product.is_mandatory == true && product.quantity == 0) {
                            missing += MissingItem(
                                productName = product.name,
                                shelfName = shelf.name,
                                locationName = location.name,
                                householdId = household.id,
                                locationId = location.id,
                            )
                        }
                    }
                }
            }
        }
        _state.update { it.copy(items = missing.sortedWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))) }
    }

    private fun refreshSilent() {
        viewModelScope.launch {
            runCatching {
                val missing = mutableListOf<MissingItem>()
                val households = householdRepository.list()
                for (household in households) {
                    val locations = locationRepository.list(household.id)
                    for (location in locations) {
                        val shelves = shelfRepository.list(household.id, location.id)
                        for (shelf in shelves) {
                            val products = productRepository.list(household.id, shelf.id)
                            for (product in products) {
                                if (product.is_mandatory == true && product.quantity == 0) {
                                    missing += MissingItem(
                                        productName = product.name,
                                        shelfName = shelf.name,
                                        locationName = location.name,
                                        householdId = household.id,
                                        locationId = location.id,
                                    )
                                }
                            }
                        }
                    }
                }
                missing.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))
                missing
            }.onSuccess { items -> _state.update { it.copy(items = items) } }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                val missing = mutableListOf<MissingItem>()
                val households = householdRepository.list()
                for (household in households) {
                    val locations = locationRepository.list(household.id)
                    for (location in locations) {
                        val shelves = shelfRepository.list(household.id, location.id)
                        for (shelf in shelves) {
                            val products = productRepository.list(household.id, shelf.id)
                            for (product in products) {
                                if (product.is_mandatory == true && product.quantity == 0) {
                                    missing += MissingItem(
                                        productName = product.name,
                                        shelfName = shelf.name,
                                        locationName = location.name,
                                        householdId = household.id,
                                        locationId = location.id,
                                    )
                                }
                            }
                        }
                    }
                }
                missing.sortWith(compareBy({ it.locationName }, { it.shelfName }, { it.productName }))
                missing
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { items -> state.copy(loading = false, items = items) },
                    onFailure = { error -> state.copy(loading = false, error = error.message ?: "Something went wrong.") },
                )
            }
        }
    }
}
