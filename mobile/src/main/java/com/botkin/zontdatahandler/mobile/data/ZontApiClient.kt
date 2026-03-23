package com.botkin.zontdatahandler.mobile.data

import com.botkin.zontdatahandler.shared.ZontSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class ZontApiClient {
    suspend fun fetchAvailableDevices(
        client: String,
        token: String,
    ): AvailableDevicesResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = postDevices(
                ZontSettings(
                    client = client,
                    token = token,
                ),
            )
            parseAvailableDevices(body)
        }.fold(
            onSuccess = { AvailableDevicesResult.Success(it) },
            onFailure = { AvailableDevicesResult.Failure(it.message ?: "Unknown devices error") },
        )
    }

    suspend fun fetchSnapshot(
        settings: ZontSettings,
        previousSnapshot: ZontSnapshot?,
    ): ApiRefreshResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = postDevices(settings)
            parseSnapshot(body = body, settings = settings, previousSnapshot = previousSnapshot)
        }.fold(
            onSuccess = { ApiRefreshResult.Success(it) },
            onFailure = { ApiRefreshResult.Failure(it.message ?: "Unknown refresh error") },
        )
    }

    suspend fun fetchAuthToken(
        client: String,
        login: String,
        password: String,
    ): AuthTokenResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = postAuthToken(
                client = client,
                login = login,
                password = password,
            )
            parseAuthToken(body)
        }.fold(
            onSuccess = { AuthTokenResult.Success(it) },
            onFailure = { AuthTokenResult.Failure(it.message ?: "Unknown auth error") },
        )
    }

    private fun postDevices(settings: ZontSettings): String {
        val connection = (URL(DEVICES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-ZONT-Client", settings.client)
            setRequestProperty("X-ZONT-Token", settings.token)
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write("""{"load_io":true}""")
            }
            val responseCode = connection.responseCode
            val responseBody = connection.readBody()
            if (responseCode !in 200..299) {
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw IllegalStateException(
                        "ZONT rejected the current token (HTTP 403). Request a new token or enter another X-ZONT-Token.",
                    )
                }
                throw IllegalStateException("ZONT HTTP $responseCode: ${responseBody.take(300)}")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun postAuthToken(
        client: String,
        login: String,
        password: String,
    ): String {
        val connection = (URL(AUTH_TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-ZONT-Client", client)
            setRequestProperty("Authorization", buildBasicAuthorization(login, password))
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write("""{"client_name":"$APP_CLIENT_NAME"}""")
            }
            val responseCode = connection.responseCode
            val responseBody = connection.readBody()
            if (responseCode !in 200..299) {
                throw IllegalStateException("ZONT auth HTTP $responseCode: ${responseBody.take(300)}")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAuthToken(body: String): String {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) {
            throw IllegalStateException(readApiError(root))
        }

        return root.optString("token").trim().ifBlank {
            throw IllegalStateException("ZONT auth response does not contain token")
        }
    }

    private fun parseAvailableDevices(body: String): List<AvailableDevice> {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) {
            throw IllegalStateException(readApiError(root))
        }

        val devices = root.optJSONArray("devices")
            ?: throw IllegalStateException("ZONT response does not contain devices[]")

        return buildList {
            for (index in 0 until devices.length()) {
                val device = devices.optJSONObject(index) ?: continue
                val deviceId = device.opt("device_id")?.toString()
                    ?: device.opt("id")?.toString()
                    ?: continue
                add(
                    AvailableDevice(
                        deviceId = deviceId,
                        name = device.optString("name").ifBlank { null },
                    ),
                )
            }
        }.distinctBy { it.deviceId }
    }

    private fun parseSnapshot(
        body: String,
        settings: ZontSettings,
        previousSnapshot: ZontSnapshot?,
    ): ZontSnapshot {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) {
            throw IllegalStateException(readApiError(root))
        }

        val devices = root.optJSONArray("devices")
            ?: throw IllegalStateException("ZONT response does not contain devices[]")
        val device = devices.findDevice(settings.deviceId)
            ?: throw IllegalStateException("device_id ${settings.deviceId} was not found in devices[]")

        val z3kState = device.optJSONObject("io")?.optJSONObject("z3k-state")
        if (z3kState != null) {
            return parseZ3kSnapshot(
                device = device,
                z3kState = z3kState,
                settings = settings,
                previousSnapshot = previousSnapshot,
            )
        }

        val thermometers = device.optJSONArray("thermometers") ?: JSONArray()
        val lastBoilerState = device.optJSONObject("io")?.optJSONObject("last-boiler-state")
        val boilerOt = lastBoilerState?.optJSONObject("ot")

        val roomSource = findThermometer(
            thermometers = thermometers,
            function = "control",
            preferredZone = settings.zone,
            fallbackToAnyZone = true,
        )
        val coolantSensorSource = findThermometer(
            thermometers = thermometers,
            function = "heat",
            preferredZone = settings.zone,
            fallbackToAnyZone = false,
        )

        val rawRoomSetpoint = lastBoilerState.optDoubleOrNull("target_temp")
        val rawTarget = rawRoomSetpoint
        val rawBurner = boilerOt.optDoubleOrNull("rml")
        val rawCoolant = boilerOt.optDoubleOrNull("bt") ?: coolantSensorSource?.value
        val rawRoom = roomSource?.value
        val updatedAtEpochSeconds = lastBoilerState.optLongOrNull("time")
            ?: roomSource?.valueTimeEpochSeconds
            ?: coolantSensorSource?.valueTimeEpochSeconds
            ?: currentEpochSeconds()

        val (roomTemperature, roomMissingStreak) = mergeMetric(
            currentValue = rawRoom,
            previousValue = previousSnapshot?.roomTemperature,
            previousMissingStreak = previousSnapshot?.roomTemperatureMissingStreak ?: 0,
        )
        val (roomSetpointTemperature, roomSetpointMissingStreak) = mergeMetric(
            currentValue = rawRoomSetpoint,
            previousValue = previousSnapshot?.roomSetpointTemperature,
            previousMissingStreak = previousSnapshot?.roomSetpointTemperatureMissingStreak ?: 0,
        )
        val (burnerModulation, burnerMissingStreak) = mergeMetric(
            currentValue = rawBurner,
            previousValue = previousSnapshot?.burnerModulation,
            previousMissingStreak = previousSnapshot?.burnerModulationMissingStreak ?: 0,
        )
        val (targetTemperature, targetMissingStreak) = mergeMetric(
            currentValue = rawTarget,
            previousValue = previousSnapshot?.targetTemperature,
            previousMissingStreak = previousSnapshot?.targetTemperatureMissingStreak ?: 0,
        )
        val (coolantTemperature, coolantMissingStreak) = mergeMetric(
            currentValue = rawCoolant,
            previousValue = previousSnapshot?.coolantTemperature,
            previousMissingStreak = previousSnapshot?.coolantTemperatureMissingStreak ?: 0,
        )

        return ZontSnapshot(
            roomTemperature = roomTemperature,
            roomSetpointTemperature = roomSetpointTemperature,
            burnerModulation = burnerModulation,
            targetTemperature = targetTemperature,
            coolantTemperature = coolantTemperature,
            deviceId = device.opt("device_id")?.toString() ?: settings.deviceId,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            roomTemperatureMissingStreak = roomMissingStreak,
            roomSetpointTemperatureMissingStreak = roomSetpointMissingStreak,
            burnerModulationMissingStreak = burnerMissingStreak,
            targetTemperatureMissingStreak = targetMissingStreak,
            coolantTemperatureMissingStreak = coolantMissingStreak,
            isStale = false,
            errorMessage = null,
            sourceSummary = buildSourceSummary(
                roomSource = roomSource,
                hasRoomSetpoint = rawRoomSetpoint != null,
                coolantSource = if (boilerOt.optDoubleOrNull("bt") != null) "io.last-boiler-state.ot.bt" else coolantSensorSource?.summary,
                hasTarget = rawTarget != null,
                hasBurner = rawBurner != null,
            ),
        )
    }

    private fun parseZ3kSnapshot(
        device: JSONObject,
        z3kState: JSONObject,
        settings: ZontSettings,
        previousSnapshot: ZontSnapshot?,
    ): ZontSnapshot {
        val z3kConfig = device.optJSONObject("z3k_config")
        val heatingCircuits = z3kConfig?.optJSONArray("heating_circuits") ?: JSONArray()
        val boilerAdapters = z3kConfig?.optJSONArray("boiler_adapters") ?: JSONArray()

        val primaryHeatingCircuit = findPrimaryHeatingCircuit(heatingCircuits)
        val heatingCircuitId = primaryHeatingCircuit?.opt("id")?.toString()
        val roomSensorId = primaryHeatingCircuit?.opt("air_temp_sensor")?.toString()
        val boilerAdapterId = primaryHeatingCircuit?.opt("water_temp_sensor")?.toString()
            ?: boilerAdapters.optJSONObject(0)?.opt("id")?.toString()

        val heatingCircuitState = z3kState.optJSONObjectById(heatingCircuitId)
        val roomSensorState = z3kState.optJSONObjectById(roomSensorId)
        val boilerAdapterState = z3kState.optJSONObjectById(boilerAdapterId)
        val boilerOt = boilerAdapterState?.optJSONObject("ot")

        val rawRoomSetpoint = heatingCircuitState.optDoubleOrNull("target_temp")
        val rawTarget = heatingCircuitState.optDoubleOrNull("setpoint_temp")
        val rawRoom = roomSensorState.optDoubleOrNull("curr_temp")
        val rawBurner = boilerOt.optDoubleOrNull("rml")
        val rawCoolant = boilerOt.optDoubleOrNull("bt")
        val updatedAtEpochSeconds = device.optLongOrNull("last_receive_time") ?: currentEpochSeconds()

        val (roomTemperature, roomMissingStreak) = mergeMetric(
            currentValue = rawRoom,
            previousValue = previousSnapshot?.roomTemperature,
            previousMissingStreak = previousSnapshot?.roomTemperatureMissingStreak ?: 0,
        )
        val (roomSetpointTemperature, roomSetpointMissingStreak) = mergeMetric(
            currentValue = rawRoomSetpoint,
            previousValue = previousSnapshot?.roomSetpointTemperature,
            previousMissingStreak = previousSnapshot?.roomSetpointTemperatureMissingStreak ?: 0,
        )
        val (burnerModulation, burnerMissingStreak) = mergeMetric(
            currentValue = rawBurner,
            previousValue = previousSnapshot?.burnerModulation,
            previousMissingStreak = previousSnapshot?.burnerModulationMissingStreak ?: 0,
        )
        val (targetTemperature, targetMissingStreak) = mergeMetric(
            currentValue = rawTarget,
            previousValue = previousSnapshot?.targetTemperature,
            previousMissingStreak = previousSnapshot?.targetTemperatureMissingStreak ?: 0,
        )
        val (coolantTemperature, coolantMissingStreak) = mergeMetric(
            currentValue = rawCoolant,
            previousValue = previousSnapshot?.coolantTemperature,
            previousMissingStreak = previousSnapshot?.coolantTemperatureMissingStreak ?: 0,
        )

        return ZontSnapshot(
            roomTemperature = roomTemperature,
            roomSetpointTemperature = roomSetpointTemperature,
            burnerModulation = burnerModulation,
            targetTemperature = targetTemperature,
            coolantTemperature = coolantTemperature,
            deviceId = device.opt("device_id")?.toString()
                ?: device.opt("id")?.toString()
                ?: settings.deviceId,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            roomTemperatureMissingStreak = roomMissingStreak,
            roomSetpointTemperatureMissingStreak = roomSetpointMissingStreak,
            burnerModulationMissingStreak = burnerMissingStreak,
            targetTemperatureMissingStreak = targetMissingStreak,
            coolantTemperatureMissingStreak = coolantMissingStreak,
            isStale = false,
            errorMessage = null,
            sourceSummary = listOf(
                "room=z3k-state[$roomSensorId].curr_temp",
                "roomSetpoint=z3k-state[$heatingCircuitId].target_temp",
                "burner=z3k-state[$boilerAdapterId].ot.rml",
                "target=z3k-state[$heatingCircuitId].setpoint_temp",
                "coolant=z3k-state[$boilerAdapterId].ot.bt",
            ).joinToString(", "),
        )
    }

    private fun readApiError(root: JSONObject): String {
        val errorUi = root.opt("error_ui")
        return when (errorUi) {
            is JSONArray -> buildList {
                for (index in 0 until errorUi.length()) {
                    add(errorUi.optString(index))
                }
            }.filter { it.isNotBlank() }.joinToString("; ")
            is String -> errorUi
            else -> root.optString("error").ifBlank { "ZONT API returned ok=false" }
        }
    }

    private fun buildSourceSummary(
        roomSource: ThermometerMatch?,
        hasRoomSetpoint: Boolean,
        coolantSource: String?,
        hasTarget: Boolean,
        hasBurner: Boolean,
    ): String {
        return listOf(
            "room=${roomSource?.summary ?: "missing"}",
            "roomSetpoint=${if (hasRoomSetpoint) "io.last-boiler-state.target_temp" else "missing"}",
            "burner=${if (hasBurner) "io.last-boiler-state.ot.rml" else "missing"}",
            "target=${if (hasTarget) "io.last-boiler-state.target_temp" else "missing"}",
            "coolant=${coolantSource ?: "missing"}",
        ).joinToString(", ")
    }

    private fun mergeMetric(
        currentValue: Double?,
        previousValue: Double?,
        previousMissingStreak: Int,
    ): Pair<Double?, Int> {
        if (currentValue != null) {
            return currentValue to 0
        }
        val nextMissingStreak = (previousMissingStreak + 1).coerceAtMost(99)
        return if (previousValue != null && previousMissingStreak < 1) {
            previousValue to nextMissingStreak
        } else {
            null to nextMissingStreak
        }
    }

    private fun JSONArray.findDevice(deviceId: String): JSONObject? {
        for (index in 0 until length()) {
            val candidate = optJSONObject(index) ?: continue
            if (candidate.opt("device_id")?.toString() == deviceId || candidate.opt("id")?.toString() == deviceId) {
                return candidate
            }
        }
        return null
    }

    private fun findPrimaryHeatingCircuit(heatingCircuits: JSONArray): JSONObject? {
        var fallback: JSONObject? = null
        for (index in 0 until heatingCircuits.length()) {
            val candidate = heatingCircuits.optJSONObject(index) ?: continue
            if (fallback == null) {
                fallback = candidate
            }
            if (!candidate.isNull("air_temp_sensor")) {
                return candidate
            }
        }
        return fallback
    }

    private fun findThermometer(
        thermometers: JSONArray,
        function: String,
        preferredZone: Int,
        fallbackToAnyZone: Boolean,
    ): ThermometerMatch? {
        val preferred = mutableListOf<ThermometerMatch>()
        val fallback = mutableListOf<ThermometerMatch>()

        for (index in 0 until thermometers.length()) {
            val thermometer = thermometers.optJSONObject(index) ?: continue
            if (!thermometer.optBoolean("is_assigned_to_slot", false)) {
                continue
            }
            val value = thermometer.optDoubleOrNull("last_value") ?: continue
            val valueTime = thermometer.optLongOrNull("last_value_time")
            val name = thermometer.optString("name").ifBlank { "sensor-$index" }
            val functions = thermometer.optJSONArray("functions") ?: JSONArray()
            for (functionIndex in 0 until functions.length()) {
                val functionObject = functions.optJSONObject(functionIndex) ?: continue
                if (functionObject.optString("f") != function) {
                    continue
                }
                val zone = functionObject.optInt("zone", -1)
                val match = ThermometerMatch(
                    value = value,
                    valueTimeEpochSeconds = valueTime,
                    summary = "thermometer:$name($function, zone=$zone)",
                )
                if (zone == preferredZone) {
                    preferred += match
                } else {
                    fallback += match
                }
            }
        }

        return preferred.firstOrNull() ?: if (fallbackToAnyZone) fallback.firstOrNull() else null
    }

    private fun HttpURLConnection.readBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
    }

    private fun buildBasicAuthorization(login: String, password: String): String {
        val credentials = "$login:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    private fun JSONObject?.optLongOrNull(name: String): Long? {
        if (this == null || isNull(name) || !has(name)) {
            return null
        }
        return runCatching { getLong(name) }.getOrNull()
            ?: runCatching { getString(name).toLong() }.getOrNull()
    }

    private fun JSONObject?.optDoubleOrNull(name: String): Double? {
        if (this == null || isNull(name) || !has(name)) {
            return null
        }
        return runCatching { getDouble(name) }.getOrNull()
            ?: runCatching { getString(name).toDouble() }.getOrNull()
    }

    private fun JSONObject.optJSONObjectById(id: String?): JSONObject? {
        if (id.isNullOrBlank()) {
            return null
        }
        return optJSONObject(id)
    }

    private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

    private data class ThermometerMatch(
        val value: Double,
        val valueTimeEpochSeconds: Long?,
        val summary: String,
    )

    companion object {
        private const val APP_CLIENT_NAME = "ZONT Data Handler"
        private const val AUTH_TOKEN_URL = "https://my.zont.online/api/get_authtoken"
        private const val DEVICES_URL = "https://my.zont.online/api/devices"
    }
}

sealed interface ApiRefreshResult {
    data class Success(val snapshot: ZontSnapshot) : ApiRefreshResult
    data class Failure(val message: String) : ApiRefreshResult
}

sealed interface AuthTokenResult {
    data class Success(val token: String) : AuthTokenResult
    data class Failure(val message: String) : AuthTokenResult
}

sealed interface AvailableDevicesResult {
    data class Success(val devices: List<AvailableDevice>) : AvailableDevicesResult
    data class Failure(val message: String) : AvailableDevicesResult
}

data class AvailableDevice(
    val deviceId: String,
    val name: String?,
)
