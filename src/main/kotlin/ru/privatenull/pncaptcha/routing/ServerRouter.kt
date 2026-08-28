package ru.privatenull.pncaptcha.routing

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import ru.privatenull.pncaptcha.config.RouteServerConfig
import ru.privatenull.pncaptcha.config.RoutingConfig
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

class ServerRouter(
    private val proxy: ProxyServer,
    private val config: RoutingConfig
) {
    private val roundRobin = AtomicInteger()

    fun networkIsFull(player: Player): Boolean {
        val limit = config.networkMaxPlayers
        if (limit <= 0) return false
        if (config.fullBypassPermission.isNotBlank() && player.hasPermission(config.fullBypassPermission)) return false

        val usableSlots = (limit - config.networkReserveSlots).coerceAtLeast(1)
        return proxy.playerCount >= usableSlots
    }

    fun select(): RegisteredServer? {
        val candidates = configuredCandidates()
        if (candidates.isNotEmpty()) return selectFrom(candidates)

        if (!config.fallbackToAnyRegistered) return null
        val registered = proxy.allServers.toList()
        if (registered.isEmpty()) return null
        return registered.minByOrNull { it.playersConnected.size }
    }

    fun selectNamed(name: String): RegisteredServer? {
        val registered = proxy.getServer(name).orElse(null) ?: return null
        val entry = config.servers.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (entry != null && !canAccept(entry, registered)) return null
        return registered
    }

    fun configuredServerCount(): Int = config.servers.count { it.enabled }

    private fun configuredCandidates(): List<Candidate> = config.servers.mapNotNull { entry ->
        if (!entry.enabled) return@mapNotNull null
        val server = proxy.getServer(entry.name).orElse(null) ?: return@mapNotNull null
        if (!canAccept(entry, server)) return@mapNotNull null
        Candidate(entry, server)
    }

    private fun canAccept(entry: RouteServerConfig, server: RegisteredServer): Boolean {
        if (entry.maxPlayers <= 0) return true
        val usableSlots = (entry.maxPlayers - entry.reserveSlots).coerceAtLeast(1)
        return server.playersConnected.size < usableSlots
    }

    private fun selectFrom(candidates: List<Candidate>): RegisteredServer = when (config.strategy.lowercase()) {
        "priority" -> candidates
            .sortedWith(compareBy<Candidate> { it.config.priority }.thenBy { it.server.playersConnected.size })
            .first()
            .server

        "random" -> candidates[ThreadLocalRandom.current().nextInt(candidates.size)].server

        "weighted-random" -> weighted(candidates).server

        "round-robin" -> {
            val ordered = candidates.sortedWith(compareBy<Candidate> { it.config.priority }.thenBy { it.config.name })
            ordered[Math.floorMod(roundRobin.getAndIncrement(), ordered.size)].server
        }

        "first-available" -> candidates
            .sortedWith(compareBy<Candidate> { it.config.priority }.thenBy { config.servers.indexOf(it.config) })
            .first()
            .server

        else -> candidates
            .sortedWith(compareBy<Candidate> { it.server.playersConnected.size }.thenBy { it.config.priority })
            .first()
            .server
    }

    private fun weighted(candidates: List<Candidate>): Candidate {
        val total = candidates.sumOf { it.config.weight.coerceAtLeast(1) }
        var cursor = ThreadLocalRandom.current().nextInt(total)
        for (candidate in candidates) {
            cursor -= candidate.config.weight.coerceAtLeast(1)
            if (cursor < 0) return candidate
        }
        return candidates.last()
    }

    private data class Candidate(
        val config: RouteServerConfig,
        val server: RegisteredServer
    )
}
