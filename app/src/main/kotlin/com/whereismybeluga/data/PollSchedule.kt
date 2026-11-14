package com.whereismybeluga.data

import kotlin.math.max

/**
 * adsb.lol allows 500 requests a day per IP address without a key. One a
 * minute gives over 8 hours of use; a Beluga covers about 15 km a minute.
 */
const val POLL_INTERVAL_MS = 60_000L

/** A manual refresh within this delay of the last request would not show anything new. */
const val MIN_MANUAL_REFRESH_MS = 30_000L

/** Wait used after a rate-limit answer that does not say how long to wait. */
const val DEFAULT_RATE_LIMIT_BACKOFF_MS = 10 * 60_000L

/**
 * Decides when the next adsb.lol request may go out. Times are milliseconds
 * from a monotonic clock (e.g. SystemClock.elapsedRealtime), never wall time.
 */
class PollSchedule(
    private val intervalMs: Long = POLL_INTERVAL_MS,
    private val minManualRefreshMs: Long = MIN_MANUAL_REFRESH_MS,
) {
    private var lastRequestAt: Long? = null

    /** No request at all before this time, set after a rate-limit answer. */
    var blockedUntil: Long? = null
        private set

    /** How long to wait before the next scheduled request; 0 if it may go now. */
    fun delayBeforeNextFetch(now: Long): Long {
        val nextRegular = lastRequestAt?.let { it + intervalMs } ?: now
        return max(0L, max(nextRegular, blockedUntil ?: now) - now)
    }

    /** True if a refresh asked by the user may send a request right away. */
    fun canRefreshNow(now: Long): Boolean {
        val blocked = blockedUntil?.let { now < it } ?: false
        val tooSoon = lastRequestAt?.let { now - it < minManualRefreshMs } ?: false
        return !blocked && !tooSoon
    }

    fun onRequest(now: Long) {
        lastRequestAt = now
    }

    /** Holds every request until adsb.lol's quota is back. Returns the time requests resume. */
    fun onRateLimited(now: Long, retryAfterSeconds: Long?): Long {
        val until = now + (retryAfterSeconds?.times(1_000) ?: DEFAULT_RATE_LIMIT_BACKOFF_MS)
        blockedUntil = until
        return until
    }
}
