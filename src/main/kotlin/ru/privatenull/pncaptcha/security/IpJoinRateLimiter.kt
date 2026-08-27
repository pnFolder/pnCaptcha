package ru.privatenull.pncaptcha.security

import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class IpJoinRateLimiter(
    private val maxJoins: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Bucket(
        val windowStartedAt: Instant,
        val count: Int
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun allow(address: InetAddress): Boolean {
        val key = address.hostAddress
        val now = clock.instant()
        var allowed = false

        buckets.compute(key) { _, current ->
            if (current == null || !current.windowStartedAt.plus(window).isAfter(now)) {
                allowed = true
                Bucket(now, 1)
            } else if (current.count < maxJoins) {
                allowed = true
                current.copy(count = current.count + 1)
            } else {
                current
            }
        }

        if (buckets.size > 4096) {
            purgeExpired(now)
        }
        return allowed
    }

    private fun purgeExpired(now: Instant) {
        buckets.entries.removeIf { !it.value.windowStartedAt.plus(window).isAfter(now) }
    }
}
