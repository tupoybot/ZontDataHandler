package com.botkin.zontdatahandler.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.botkin.zontdatahandler.mobile.data.AuthOutcome
import com.botkin.zontdatahandler.mobile.data.RefreshOutcome
import com.botkin.zontdatahandler.mobile.data.StoredMobileState
import com.botkin.zontdatahandler.mobile.data.ZontSettings
import com.botkin.zontdatahandler.mobile.data.ZontSnapshotRepository
import com.botkin.zontdatahandler.shared.ZontSnapshot
import com.botkin.zontdatahandler.shared.withComputedStaleness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZontViewModel(
    private val repository: ZontSnapshotRepository,
) : ViewModel() {
    private val storedState = repository.storedStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoredMobileState())

    private val formState = MutableStateFlow(ZontFormState())
    private val isDirty = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val isAuthorizing = MutableStateFlow(false)
    private val authMessage = MutableStateFlow<AuthStatusMessage?>(null)

    init {
        viewModelScope.launch {
            storedState.collect { state ->
                if (!isDirty.value) {
                    formState.value = ZontFormState.fromSettings(state.settings)
                }
            }
        }
    }

    val uiState: StateFlow<ZontUiState> = combine(
        storedState,
        formState,
        combine(isDirty, isRefreshing, isAuthorizing) { dirty, refreshing, authorizing ->
            Triple(dirty, refreshing, authorizing)
        },
        authMessage,
    ) { stored, form, flags, message ->
        val (dirty, refreshing, authorizing) = flags
        val nowEpochSeconds = System.currentTimeMillis() / 1_000L
        ZontUiState(
            formState = form,
            snapshot = stored.snapshot?.withComputedStaleness(nowEpochSeconds),
            lastSuccessfulRefreshEpochSeconds = stored.lastSuccessfulRefreshEpochSeconds,
            isRefreshing = refreshing,
            isAuthorizing = authorizing,
            hasUnsavedChanges = dirty,
            authMessage = message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ZontUiState(),
    )

    fun onClientChanged(value: String) = updateForm { copy(client = value) }

    fun onLoginChanged(value: String) = updateForm { copy(login = value) }

    fun onPasswordChanged(value: String) = updateForm { copy(password = value) }

    fun onTokenChanged(value: String) = updateForm { copy(token = value) }

    fun onDeviceIdChanged(value: String) = updateForm { copy(deviceId = value) }

    fun onZoneChanged(value: String) = updateForm { copy(zone = value) }

    fun onRefreshIntervalChanged(value: String) = updateForm { copy(refreshIntervalMinutes = value) }

    fun saveSettings() {
        viewModelScope.launch {
            authMessage.value = null
            repository.saveSettings(currentFormSettings())
            isDirty.value = false
        }
    }

    fun requestToken() {
        viewModelScope.launch {
            val login = formState.value.login.ifBlank { formState.value.client }.trim()
            val password = formState.value.password
            authMessage.value = null
            isAuthorizing.value = true

            when (val result = repository.obtainToken(currentFormSettings(), login, password)) {
                is AuthOutcome.Success -> {
                    formState.update { current ->
                        current.copy(
                            client = result.client,
                            token = result.token,
                            deviceId = result.deviceId,
                            login = "",
                            password = "",
                        )
                    }
                    isDirty.value = false
                    authMessage.value = AuthStatusMessage(
                        text = result.message,
                        isError = false,
                    )
                }

                is AuthOutcome.Failure -> {
                    formState.update { current -> current.copy(password = "") }
                    authMessage.value = AuthStatusMessage(
                        text = result.message,
                        isError = true,
                    )
                }
            }

            isAuthorizing.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            authMessage.value = null
            isRefreshing.value = true
            repository.saveSettings(currentFormSettings())
            isDirty.value = false
            when (val result = repository.refreshNow()) {
                is RefreshOutcome.Success -> Unit
                is RefreshOutcome.Failure -> Unit
            }
            isRefreshing.value = false
        }
    }

    private fun updateForm(transform: ZontFormState.() -> ZontFormState) {
        formState.update(transform)
        isDirty.value = true
        authMessage.value = null
    }

    private fun currentFormSettings(): ZontSettings {
        return formState.value.toSettings(storedState.value.settings)
    }

    class Factory(
        private val repository: ZontSnapshotRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ZontViewModel(repository) as T
        }
    }
}

data class ZontUiState(
    val formState: ZontFormState = ZontFormState(),
    val snapshot: ZontSnapshot? = null,
    val lastSuccessfulRefreshEpochSeconds: Long? = null,
    val isRefreshing: Boolean = false,
    val isAuthorizing: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val authMessage: AuthStatusMessage? = null,
)

data class ZontFormState(
    val client: String = "",
    val login: String = "",
    val password: String = "",
    val token: String = "",
    val deviceId: String = "",
    val zone: String = "1",
    val refreshIntervalMinutes: String = ZontSnapshot.DEFAULT_REFRESH_INTERVAL_MINUTES.toString(),
) {
    fun toSettings(previousSettings: ZontSettings? = null): ZontSettings {
        val fallbackSettings = previousSettings ?: ZontSettings()
        return ZontSettings(
            client = client,
            token = token,
            deviceId = deviceId,
            zone = zone.toIntOrNull() ?: fallbackSettings.zone,
            refreshIntervalMinutes = refreshIntervalMinutes.toIntOrNull()
                ?: fallbackSettings.refreshIntervalMinutes,
        ).sanitized()
    }

    companion object {
        fun fromSettings(settings: ZontSettings): ZontFormState {
            return ZontFormState(
                client = settings.client,
                token = settings.token,
                deviceId = settings.deviceId,
                zone = settings.zone.toString(),
                refreshIntervalMinutes = settings.refreshIntervalMinutes.toString(),
            )
        }
    }
}

data class AuthStatusMessage(
    val text: String,
    val isError: Boolean,
)
