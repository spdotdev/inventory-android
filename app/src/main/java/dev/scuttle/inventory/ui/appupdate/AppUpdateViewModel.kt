package dev.scuttle.inventory.ui.appupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.appupdate.AppUpdateRepository
import dev.scuttle.inventory.data.appupdate.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel
    @Inject
    constructor(
        private val repository: AppUpdateRepository,
    ) : ViewModel() {
        private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.None)
        val status: StateFlow<UpdateStatus> = _status.asStateFlow()

        // Tracks the version_code of the Optional release the user last dismissed, not a
        // single "any optional dismissed ever" flag - so a newer Optional release (a
        // different version_code) isn't wrongly suppressed by an earlier dismissal.
        private val dismissedOptionalVersionCode = MutableStateFlow<Int?>(null)

        val isDialogVisible: StateFlow<Boolean> =
            combine(_status, dismissedOptionalVersionCode) { status, dismissedVersionCode ->
                when (status) {
                    UpdateStatus.None -> false
                    is UpdateStatus.Optional -> status.release.versionCode != dismissedVersionCode
                    is UpdateStatus.Breaking -> true
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        fun refresh() {
            viewModelScope.launch {
                _status.value = repository.check()
            }
        }

        fun dismissOptional() {
            val current = _status.value
            if (current is UpdateStatus.Optional) {
                dismissedOptionalVersionCode.value = current.release.versionCode
            }
        }
    }
