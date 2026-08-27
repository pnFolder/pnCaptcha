package ru.privatenull.pncaptcha.cache

import com.velocitypowered.api.proxy.Player
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VerificationCache(
    private val ttl: Duration,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Key(val uuid: UUID, val ip: String)

    private val entries = ConcurrentHashMap<Key, Instant>()

    fun isVerified(player: Player): Boolean = isVerified(
        player.uniqueId,
        player.remoteAddress.address.hostAddress
    )

    fun isVerified(uuid: UUID, ip: String): Boolean {
        val key = Key(uuid, ip)
        val expiresAt = entries[key] ?: return false
        if (!expiresAt.isAfter(clock.instant())) {
            entries.remove(key, expiresAt)
            return false
        }
        return true
    }

    fun markVerified(player: Player) = markVerified(
        player.uniqueId,
        player.remoteAddress.address.hostAddress
    )

    fun markVerified(uuid: UUID, ip: String) {
        entries[Key(uuid, ip)] = clock.instant().plus(ttl)
    }

    fun invalidate(player: Player) {
        entries.remove(Key(player.uniqueId, player.remoteAddress.address.hostAddress))
    }

    fun purgeExpired() {
        val now = clock.instant()
        entries.entries.removeIf { !it.value.isAfter(now) }
    }

    fun size(): Int = entries.size
}
