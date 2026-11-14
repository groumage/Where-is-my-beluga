package com.whereismybeluga

import com.whereismybeluga.data.DEFAULT_RATE_LIMIT_BACKOFF_MS
import com.whereismybeluga.data.PollSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollScheduleTest {
    private val schedule = PollSchedule(intervalMs = 120_000, minManualRefreshMs = 30_000)

    @Test
    fun firstRequestGoesAtOnce() {
        assertEquals(0, schedule.delayBeforeNextFetch(now = 5_000))
        assertTrue(schedule.canRefreshNow(now = 5_000))
    }

    @Test
    fun nextRequestWaitsForTheIntervalEvenAfterARestart() {
        schedule.onRequest(now = 10_000)
        // Coming back to the app 50 s later must not send a request early.
        assertEquals(70_000, schedule.delayBeforeNextFetch(now = 60_000))
        assertEquals(0, schedule.delayBeforeNextFetch(now = 200_000))
    }

    @Test
    fun manualRefreshIsSkippedRightAfterARequest() {
        schedule.onRequest(now = 10_000)
        assertFalse(schedule.canRefreshNow(now = 39_999))
        assertTrue(schedule.canRefreshNow(now = 40_000))
    }

    @Test
    fun rateLimitHoldsEverythingForTheAskedTime() {
        schedule.onRequest(now = 0)
        assertEquals(3_600_000, schedule.onRateLimited(now = 0, retryAfterSeconds = 3_600))
        assertEquals(3_600_000, schedule.delayBeforeNextFetch(now = 0))
        assertFalse(schedule.canRefreshNow(now = 3_599_999))
        assertTrue(schedule.canRefreshNow(now = 3_600_000))
    }

    @Test
    fun rateLimitWithoutDelayUsesTheDefaultBackoff() {
        assertEquals(1_000 + DEFAULT_RATE_LIMIT_BACKOFF_MS, schedule.onRateLimited(now = 1_000, retryAfterSeconds = null))
    }
}
