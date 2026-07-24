package dev.scuttle.inventory.ui.appupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scuttle.inventory.data.appupdate.AppUpdateRepository
import dev.scuttle.inventory.data.appupdate.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        private var dismissedOptional = false

        fun refresh() {
            viewModelScope.launch {
                _status.value = repository.check()
            }
        }

        fun dismissOptional() {
            dismissedOptional = true
        }

        val isDialogVisible: Boolean
            get() =
                when (status.value) {
                    UpdateStatus.None -> false
                    is UpdateStatus.Optional -> !dismissedOptional
                    is UpdateStatus.Breaking -> true
                }
    }
