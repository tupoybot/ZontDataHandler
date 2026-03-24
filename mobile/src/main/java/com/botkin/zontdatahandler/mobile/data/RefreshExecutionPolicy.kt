package com.botkin.zontdatahandler.mobile.data

enum class RefreshTrigger {
    MANUAL,
    AUTO,
}

enum class RefreshFailureKind {
    TRANSIENT,
    PERMANENT,
}

enum class AutoRefreshScheduleMode {
    NONE,
    RESET_DELAY,
    APPEND_AFTER_CURRENT,
    CANCEL,
}

data class RefreshFailurePolicy(
    val shouldRetryWorker: Boolean,
    val autoRefreshPaused: Boolean,
    val scheduleMode: AutoRefreshScheduleMode,
)

fun scheduleModeAfterSuccess(trigger: RefreshTrigger): AutoRefreshScheduleMode {
    return when (trigger) {
        RefreshTrigger.MANUAL -> AutoRefreshScheduleMode.RESET_DELAY
        RefreshTrigger.AUTO -> AutoRefreshScheduleMode.APPEND_AFTER_CURRENT
    }
}

fun failurePolicy(
    trigger: RefreshTrigger,
    kind: RefreshFailureKind,
): RefreshFailurePolicy {
    return when (kind) {
        RefreshFailureKind.TRANSIENT -> when (trigger) {
            RefreshTrigger.MANUAL -> RefreshFailurePolicy(
                shouldRetryWorker = false,
                autoRefreshPaused = false,
                scheduleMode = AutoRefreshScheduleMode.RESET_DELAY,
            )

            RefreshTrigger.AUTO -> RefreshFailurePolicy(
                shouldRetryWorker = true,
                autoRefreshPaused = false,
                scheduleMode = AutoRefreshScheduleMode.NONE,
            )
        }

        RefreshFailureKind.PERMANENT -> RefreshFailurePolicy(
            shouldRetryWorker = false,
            autoRefreshPaused = true,
            scheduleMode = AutoRefreshScheduleMode.CANCEL,
        )
    }
}

fun dataLayerSyncShouldBeUrgent(trigger: RefreshTrigger): Boolean {
    return trigger == RefreshTrigger.MANUAL
}
