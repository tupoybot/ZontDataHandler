package com.botkin.zontdatahandler.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshExecutionPolicyTest {
    @Test
    fun `manual success resets auto refresh delay and keeps urgent watch sync`() {
        assertEquals(
            AutoRefreshScheduleMode.RESET_DELAY,
            scheduleModeAfterSuccess(RefreshTrigger.MANUAL),
        )
        assertTrue(dataLayerSyncShouldBeUrgent(RefreshTrigger.MANUAL))
    }

    @Test
    fun `auto success appends next work instead of replacing the running worker`() {
        assertEquals(
            AutoRefreshScheduleMode.APPEND_AFTER_CURRENT,
            scheduleModeAfterSuccess(RefreshTrigger.AUTO),
        )
        assertFalse(dataLayerSyncShouldBeUrgent(RefreshTrigger.AUTO))
    }

    @Test
    fun `manual transient failure keeps auto refresh enabled and resets delay`() {
        val policy = failurePolicy(
            trigger = RefreshTrigger.MANUAL,
            kind = RefreshFailureKind.TRANSIENT,
        )

        assertFalse(policy.shouldRetryWorker)
        assertFalse(policy.autoRefreshPaused)
        assertEquals(AutoRefreshScheduleMode.RESET_DELAY, policy.scheduleMode)
    }

    @Test
    fun `auto transient failure retries instead of scheduling duplicate future work`() {
        val policy = failurePolicy(
            trigger = RefreshTrigger.AUTO,
            kind = RefreshFailureKind.TRANSIENT,
        )

        assertTrue(policy.shouldRetryWorker)
        assertFalse(policy.autoRefreshPaused)
        assertEquals(AutoRefreshScheduleMode.NONE, policy.scheduleMode)
    }

    @Test
    fun `permanent failure pauses auto refresh and cancels pending work`() {
        val manualPolicy = failurePolicy(
            trigger = RefreshTrigger.MANUAL,
            kind = RefreshFailureKind.PERMANENT,
        )
        val autoPolicy = failurePolicy(
            trigger = RefreshTrigger.AUTO,
            kind = RefreshFailureKind.PERMANENT,
        )

        assertFalse(manualPolicy.shouldRetryWorker)
        assertTrue(manualPolicy.autoRefreshPaused)
        assertEquals(AutoRefreshScheduleMode.CANCEL, manualPolicy.scheduleMode)

        assertFalse(autoPolicy.shouldRetryWorker)
        assertTrue(autoPolicy.autoRefreshPaused)
        assertEquals(AutoRefreshScheduleMode.CANCEL, autoPolicy.scheduleMode)
    }
}
