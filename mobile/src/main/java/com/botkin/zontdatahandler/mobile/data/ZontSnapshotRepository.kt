package com.botkin.zontdatahandler.mobile.data

import com.botkin.zontdatahandler.mobile.work.AutoRefreshScheduler
import com.botkin.zontdatahandler.shared.ZontSnapshot
import kotlinx.coroutines.flow.Flow

class ZontSnapshotRepository(
    private val preferencesStore: ZontPreferencesStore,
    private val apiClient: ZontApiClient,
    private val dataLayerSync: ZontDataLayerSync,
    private val autoRefreshScheduler: AutoRefreshScheduler,
) {
    val storedStateFlow: Flow<StoredMobileState> = preferencesStore.stateFlow

    suspend fun saveSettings(settings: ZontSettings) {
        val sanitized = settings.sanitized()
        preferencesStore.saveSettings(sanitized)
        autoRefreshScheduler.scheduleFromSettings(sanitized)
    }

    suspend fun obtainToken(
        settings: ZontSettings,
        login: String,
        password: String,
    ): AuthOutcome {
        val sanitizedSettings = settings.sanitized()
        val sanitizedLogin = login.trim()
        val sanitizedPassword = password.trim()
        val effectiveClient = sanitizedSettings.client.ifBlank { sanitizedLogin }

        if (effectiveClient.isBlank()) {
            return AuthOutcome.Failure("Fill in X-ZONT-Client or login before requesting a token.")
        }
        if (sanitizedLogin.isBlank()) {
            return AuthOutcome.Failure("Fill in login before requesting a token.")
        }
        if (sanitizedPassword.isBlank()) {
            return AuthOutcome.Failure("Fill in password before requesting a token.")
        }

        return when (val result = apiClient.fetchAuthToken(effectiveClient, sanitizedLogin, sanitizedPassword)) {
            is AuthTokenResult.Success -> {
                val deviceSelection = when (
                    val devicesResult = apiClient.fetchAvailableDevices(
                        client = effectiveClient,
                        token = result.token,
                    )
                ) {
                    is AvailableDevicesResult.Success -> selectDeviceId(
                        requestedDeviceId = sanitizedSettings.deviceId,
                        devices = devicesResult.devices,
                    )

                    is AvailableDevicesResult.Failure -> DeviceSelection(
                        deviceId = sanitizedSettings.deviceId,
                        message = "Token saved, but device list could not be loaded: ${devicesResult.message}",
                    )
                }

                val updatedSettings = sanitizedSettings.copy(
                    client = effectiveClient,
                    token = result.token,
                    deviceId = deviceSelection.deviceId,
                ).sanitized()
                saveSettings(updatedSettings)
                AuthOutcome.Success(
                    token = updatedSettings.token,
                    client = updatedSettings.client,
                    deviceId = updatedSettings.deviceId,
                    message = deviceSelection.message,
                )
            }

            is AuthTokenResult.Failure -> AuthOutcome.Failure(result.message)
        }
    }

    suspend fun refreshNow(trigger: RefreshTrigger = RefreshTrigger.MANUAL): RefreshOutcome {
        val state = preferencesStore.readState()
        val settings = state.settings.sanitized()

        if (!settings.isReadyForRefresh) {
            val message = "Fill in X-ZONT-Client, X-ZONT-Token and device_id before refresh."
            preferencesStore.saveFailure(
                previousSnapshot = state.snapshot,
                settings = settings,
                errorMessage = message,
                autoRefreshPaused = false,
            )
            autoRefreshScheduler.cancel()
            return RefreshOutcome.Failure(message)
        }

        return when (val result = apiClient.fetchSnapshot(settings, state.snapshot)) {
            is ApiRefreshResult.Success -> handleRefreshSuccess(
                snapshot = result.snapshot,
                settings = settings,
                trigger = trigger,
            )

            is ApiRefreshResult.Failure -> handleRefreshFailure(
                previousSnapshot = state.snapshot,
                settings = settings,
                trigger = trigger,
                failure = result,
            )
        }
    }

    suspend fun refreshFromWorker(): RefreshOutcome {
        return refreshNow(RefreshTrigger.AUTO)
    }

    private suspend fun handleRefreshSuccess(
        snapshot: ZontSnapshot,
        settings: ZontSettings,
        trigger: RefreshTrigger,
    ): RefreshOutcome {
        val nowEpochSeconds = System.currentTimeMillis() / 1_000L
        val cleanSnapshot = snapshot.copy(isStale = false, errorMessage = null)
        val snapshotForPhone = runCatching {
            dataLayerSync.pushSnapshot(
                snapshot = cleanSnapshot,
                urgent = dataLayerSyncShouldBeUrgent(trigger),
            )
            cleanSnapshot
        }.getOrElse { syncError ->
            cleanSnapshot.copy(errorMessage = "Phone refresh succeeded, but watch sync failed: ${syncError.message}")
        }

        preferencesStore.saveSnapshot(
            snapshot = snapshotForPhone,
            lastSuccessfulRefreshEpochSeconds = nowEpochSeconds,
        )
        applyScheduleMode(scheduleModeAfterSuccess(trigger), settings)
        return RefreshOutcome.Success(snapshotForPhone)
    }

    private suspend fun handleRefreshFailure(
        previousSnapshot: ZontSnapshot?,
        settings: ZontSettings,
        trigger: RefreshTrigger,
        failure: ApiRefreshResult.Failure,
    ): RefreshOutcome {
        val policy = failurePolicy(trigger, failure.kind)
        val persistedMessage = if (policy.autoRefreshPaused) {
            "${failure.message} Auto-refresh is paused until settings are updated or a manual refresh succeeds."
        } else {
            failure.message
        }

        preferencesStore.saveFailure(
            previousSnapshot = previousSnapshot,
            settings = settings,
            errorMessage = persistedMessage,
            autoRefreshPaused = policy.autoRefreshPaused,
        )
        applyScheduleMode(policy.scheduleMode, settings)
        return RefreshOutcome.Failure(
            message = persistedMessage,
            shouldRetry = policy.shouldRetryWorker,
        )
    }

    private fun applyScheduleMode(
        mode: AutoRefreshScheduleMode,
        settings: ZontSettings,
    ) {
        when (mode) {
            AutoRefreshScheduleMode.NONE -> Unit
            AutoRefreshScheduleMode.RESET_DELAY -> autoRefreshScheduler.scheduleFromSettings(settings)
            AutoRefreshScheduleMode.APPEND_AFTER_CURRENT -> autoRefreshScheduler.scheduleNextAfterWorker(settings)
            AutoRefreshScheduleMode.CANCEL -> autoRefreshScheduler.cancel()
        }
    }

    private fun selectDeviceId(
        requestedDeviceId: String,
        devices: List<AvailableDevice>,
    ): DeviceSelection {
        val sanitizedRequestedId = requestedDeviceId.trim()
        val matchingRequestedDevice = devices.firstOrNull { it.deviceId == sanitizedRequestedId }
        if (matchingRequestedDevice != null) {
            return DeviceSelection(
                deviceId = matchingRequestedDevice.deviceId,
                message = "Token received. Saved existing device_id ${matchingRequestedDevice.deviceId}.",
            )
        }

        val selectedDevice = devices.firstOrNull()
        if (selectedDevice == null) {
            return DeviceSelection(
                deviceId = "",
                message = "Token saved, but devices[] is empty. Fill in device_id manually.",
            )
        }

        val selectedNameSuffix = selectedDevice.name?.let { " ($it)" }.orEmpty()
        val message = if (devices.size == 1) {
            "Token received. device_id ${selectedDevice.deviceId}$selectedNameSuffix was filled automatically."
        } else {
            "Token received. device_id ${selectedDevice.deviceId}$selectedNameSuffix was picked from ${devices.size} devices; change it manually if needed."
        }
        return DeviceSelection(
            deviceId = selectedDevice.deviceId,
            message = message,
        )
    }

    private data class DeviceSelection(
        val deviceId: String,
        val message: String,
    )
}

sealed interface RefreshOutcome {
    data class Success(val snapshot: ZontSnapshot) : RefreshOutcome
    data class Failure(
        val message: String,
        val shouldRetry: Boolean = false,
    ) : RefreshOutcome
}

sealed interface AuthOutcome {
    data class Success(
        val token: String,
        val client: String,
        val deviceId: String,
        val message: String,
    ) : AuthOutcome

    data class Failure(val message: String) : AuthOutcome
}
