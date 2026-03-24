package com.botkin.zontdatahandler.mobile.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botkin.zontdatahandler.mobile.mobileAppContainer
import com.botkin.zontdatahandler.shared.MetricPresentation
import com.botkin.zontdatahandler.shared.SnapshotMetric
import com.botkin.zontdatahandler.shared.metricPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: ZontViewModel by viewModels {
        ZontViewModel.Factory(applicationContext.mobileAppContainer.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    uiState = uiState,
                    onClientChanged = viewModel::onClientChanged,
                    onLoginChanged = viewModel::onLoginChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onTokenChanged = viewModel::onTokenChanged,
                    onDeviceIdChanged = viewModel::onDeviceIdChanged,
                    onZoneChanged = viewModel::onZoneChanged,
                    onRefreshIntervalChanged = viewModel::onRefreshIntervalChanged,
                    onRequestToken = viewModel::requestToken,
                    onSaveSettings = viewModel::saveSettings,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    uiState: ZontUiState,
    onClientChanged: (String) -> Unit,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onDeviceIdChanged: (String) -> Unit,
    onZoneChanged: (String) -> Unit,
    onRefreshIntervalChanged: (String) -> Unit,
    onRequestToken: () -> Unit,
    onSaveSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ZONT -> Wear OS complications",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Phone build: ${installedBuildLabel(context)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Local secrets, manual refresh, scheduled auto-refresh and latest snapshot sync to watch.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (uiState.isAutoRefreshPaused) {
                Text(
                    text = "Auto-refresh is paused after a permanent refresh error. Save settings or run Refresh after fixing credentials or device selection.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SettingsCard(
                uiState = uiState,
                onClientChanged = onClientChanged,
                onLoginChanged = onLoginChanged,
                onPasswordChanged = onPasswordChanged,
                onTokenChanged = onTokenChanged,
                onDeviceIdChanged = onDeviceIdChanged,
                onZoneChanged = onZoneChanged,
                onRefreshIntervalChanged = onRefreshIntervalChanged,
                onRequestToken = onRequestToken,
                onSaveSettings = onSaveSettings,
                onRefresh = onRefresh,
            )

            SnapshotCard(uiState = uiState)
        }
    }
}

@Composable
private fun SettingsCard(
    uiState: ZontUiState,
    onClientChanged: (String) -> Unit,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onDeviceIdChanged: (String) -> Unit,
    onZoneChanged: (String) -> Unit,
    onRefreshIntervalChanged: (String) -> Unit,
    onRequestToken: () -> Unit,
    onSaveSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Recommended: request a token once with login and password. Manual X-ZONT-Token entry remains available as fallback.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = uiState.formState.client,
                onValueChange = onClientChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("X-ZONT-Client / contact") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = uiState.formState.login,
                onValueChange = onLoginChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Login (optional if same as X-ZONT-Client)") },
                supportingText = {
                    Text("Password is only used to request a token and is not stored on device.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = uiState.formState.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Button(
                onClick = onRequestToken,
                enabled = !uiState.isAuthorizing,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (uiState.isAuthorizing) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Get token")
                }
            }

            uiState.authMessage?.let { message ->
                Text(
                    text = message.text,
                    color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()
            OutlinedTextField(
                value = uiState.formState.token,
                onValueChange = onTokenChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("X-ZONT-Token") },
                supportingText = {
                    Text("You can still paste a token manually if the login flow stops working.")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = uiState.formState.deviceId,
                onValueChange = onDeviceIdChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("device_id") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Number,
                ),
            )
            OutlinedTextField(
                value = uiState.formState.zone,
                onValueChange = onZoneChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("zone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Number,
                ),
            )
            OutlinedTextField(
                value = uiState.formState.refreshIntervalMinutes,
                onValueChange = onRefreshIntervalChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Auto-refresh interval, minutes") },
                supportingText = { Text("1 min is allowed for manual testing.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number,
                ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onSaveSettings) {
                    Text(if (uiState.hasUnsavedChanges) "Save settings*" else "Save settings")
                }
                Button(
                    onClick = onRefresh,
                    enabled = !uiState.isRefreshing,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    uiState: ZontUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Last snapshot",
                style = MaterialTheme.typography.titleMedium,
            )

            val snapshot = uiState.snapshot
            if (snapshot == null) {
                Text("No snapshot yet. Save settings and tap Refresh.")
                return@Column
            }

            MetricRow(snapshot.metricPresentation(SnapshotMetric.ROOM_TEMPERATURE, currentEpochSeconds()))
            MetricRow(snapshot.metricPresentation(SnapshotMetric.ROOM_SETPOINT_TEMPERATURE, currentEpochSeconds()))
            MetricRow(snapshot.metricPresentation(SnapshotMetric.BURNER_MODULATION, currentEpochSeconds()))
            MetricRow(snapshot.metricPresentation(SnapshotMetric.TARGET_TEMPERATURE, currentEpochSeconds()))
            MetricRow(snapshot.metricPresentation(SnapshotMetric.COOLANT_TEMPERATURE, currentEpochSeconds()))

            Text("Device ID: ${snapshot.deviceId ?: "--"}")
            Text("Device update time: ${formatEpochSeconds(snapshot.updatedAtEpochSeconds)}")
            Text(
                "Last successful phone refresh: ${
                    uiState.lastSuccessfulRefreshEpochSeconds?.let(::formatEpochSeconds) ?: "--"
                }",
            )
            Text("Auto-refresh interval: ${snapshot.refreshIntervalMinutes} min")
            Text("Snapshot stale: ${if (snapshot.isStale) "yes" else "no"}")

            if (!snapshot.sourceSummary.isNullOrBlank()) {
                Text(
                    text = "Source summary: ${snapshot.sourceSummary}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!snapshot.errorMessage.isNullOrBlank()) {
                Text(
                    text = "Last error: ${snapshot.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(metric: MetricPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = metric.titleText)
        Text(text = metric.valueText)
    }
}

private fun formatEpochSeconds(epochSeconds: Long): String {
    if (epochSeconds <= 0L) {
        return "--"
    }
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(timestampFormatter)
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun installedBuildLabel(context: Context): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "--"
    val buildType = if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        "debug"
    } else {
        "release"
    }
    return "$versionName (${packageInfo.longVersionCode}) $buildType"
}
