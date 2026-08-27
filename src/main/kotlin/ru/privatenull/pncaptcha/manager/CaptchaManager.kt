package ru.privatenull.pncaptcha.manager

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.limbo.CaptchaSessionHandler
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import ru.privatenull.pncaptcha.session.VerificationState
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CaptchaManager(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: CaptchaConfig,
    private val environment: CaptchaLimboEnvironment,
    private val generator: CaptchaGenerator,
    private val sessions: CaptchaSessionManager,
    private val cache: VerificationCache,
    private val rateLimiter: IpJoinRateLimiter
) {
    private val timeoutTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    fun register(event: LoginLimboRegisterEvent) {
        val player = event.player

        if (cache.isVerified(player)) {
            return
        }

        if (!rateLimiter.allow(player.remoteAddress.address)) {
            event.addOnJoinCallback(Runnable {
                player.disconnect(message("Too many connection attempts. Try again in a few seconds.", NamedTextColor.RED))
            })
            return
        }

        if (environment.activeCount() >= config.maxActiveCaptchas) {
            event.addOnJoinCallback(Runnable {
                player.disconnect(message("CAPTCHA service is busy. Try again shortly.", NamedTextColor.RED))
            })
            return
        }

        event.addOnJoinCallback(Runnable { begin(player) })
    }

    private fun begin(player: Player) {
        sessions.remove(player.uniqueId)?.let { stale ->
            timeoutTasks.remove(player.uniqueId)?.cancel()
            environment.dispose(stale.id)
        }

        val session = sessions.create(
            CaptchaSession(
                playerId = player.uniqueId,
                answer = generator.generate(config.captchaLength)
            )
        )

        try {
            val info = environment.spawn(
                sessionId = session.id,
                answer = session.answer,
                player = player,
                handler = CaptchaSessionHandler(this, player.uniqueId, session.id)
            )
            logger.info(
                "CAPTCHA {} for {} prepared as {} real Limbo blocks in chunks X {}..{}, Z {}..{}",
                session.id.toString().take(8),
                player.username,
                info.blockCount,
                info.chunkBounds.minX,
                info.chunkBounds.maxX,
                info.chunkBounds.minZ,
                info.chunkBounds.maxZ
            )
        } catch (throwable: Throwable) {
            sessions.remove(player.uniqueId, session.id)
            environment.dispose(session.id)
            logger.error("Failed to build/spawn CAPTCHA Limbo for {}", player.username, throwable)
            player.disconnect(message("CAPTCHA service is temporarily unavailable.", NamedTextColor.RED))
            return
        }

        timeoutTasks[player.uniqueId] = proxy.scheduler
            .buildTask(plugin, Runnable { timeout(player.uniqueId, session.id) })
            .delay(config.timeout)
            .schedule()
    }

    fun onSpawn(playerId: UUID, sessionId: UUID, limboPlayer: LimboPlayer) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        session.limboPlayer = limboPlayer

        limboPlayer.disableFalling()
        limboPlayer.setGameMode(environment.gameMode)
        limboPlayer.sendAbilities()
        teleportToCamera(limboPlayer)

        val becameWaiting = synchronized(session) {
            if (session.state == VerificationState.CAPTCHA_LOADING) {
                session.state = VerificationState.CAPTCHA_WAITING
                true
            } else {
                false
            }
        }

        if (becameWaiting) {
            sendPrompt(session)
        }
    }

    fun submit(playerId: UUID, sessionId: UUID, input: String) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val limboPlayer = session.limboPlayer ?: return
        val proxyPlayer = limboPlayer.proxyPlayer

        var passed = false
        var exhausted = false

        synchronized(session) {
            if (session.state != VerificationState.CAPTCHA_WAITING) {
                return
            }

            if (session.matches(input)) {
                session.state = VerificationState.VERIFIED
                passed = true
            } else {
                session.attempts++
                if (session.attempts >= config.maxAttempts) {
                    session.state = VerificationState.FAILED
                    exhausted = true
                }
            }
        }

        if (passed) {
            complete(session, proxyPlayer, limboPlayer)
            return
        }

        if (exhausted) {
            proxyPlayer.disconnect(message("Too many wrong CAPTCHA attempts.", NamedTextColor.RED))
            cleanup(session, delayedDispose = true)
            return
        }

        // With real VirtualWorld blocks there is no packet overlay to redraw.
        // Keep the same visible code for the remaining attempts; this avoids a
        // world switch in the middle of a verification session and makes the
        // rendering path deterministic.
        proxyPlayer.sendMessage(
            message(
                "Wrong code. Try the same CAPTCHA again (${session.attempts}/${config.maxAttempts}).",
                NamedTextColor.RED
            )
        )
    }

    private fun complete(session: CaptchaSession, proxyPlayer: Player, limboPlayer: LimboPlayer) {
        cache.markVerified(proxyPlayer)

        val target = proxy.getServer(config.targetServer)
        if (target.isEmpty) {
            logger.error("Configured target server '{}' does not exist", config.targetServer)
            proxyPlayer.disconnect(message("Target server is not configured correctly.", NamedTextColor.RED))
            cleanup(session, delayedDispose = true)
            return
        }

        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        session.limboPlayer = null

        proxyPlayer.sendMessage(message("Verification passed.", NamedTextColor.GREEN))
        limboPlayer.disconnect(target.get())
        scheduleDispose(session.id)
    }

    fun enforcePosition(playerId: UUID, sessionId: UUID, x: Double, y: Double, z: Double) {
        if (!config.lockPlayerPosition) return

        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val dx = x - environment.spawnX
        val dy = y - environment.spawnY
        val dz = z - environment.spawnZ

        if (dx * dx + dy * dy + dz * dz > MAX_MOVE_DISTANCE_SQUARED) {
            session.limboPlayer?.let(::teleportToCamera)
        }
    }

    fun onDisconnect(playerId: UUID, sessionId: UUID) {
        val session = sessions.remove(playerId, sessionId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        session.limboPlayer = null
        environment.dispose(session.id)
    }

    fun onVelocityDisconnect(playerId: UUID) {
        val session = sessions.remove(playerId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        session.limboPlayer = null
        environment.dispose(session.id)
    }

    private fun timeout(playerId: UUID, sessionId: UUID) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val shouldKick = synchronized(session) {
            if (session.state == VerificationState.VERIFIED || session.state == VerificationState.FAILED) {
                false
            } else {
                session.state = VerificationState.FAILED
                true
            }
        }
        if (!shouldKick) return

        val proxyPlayer = session.limboPlayer?.proxyPlayer
        proxyPlayer?.disconnect(message("CAPTCHA timed out.", NamedTextColor.RED))
        cleanup(session, delayedDispose = true)
    }

    private fun cleanup(session: CaptchaSession, delayedDispose: Boolean) {
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        session.limboPlayer = null

        if (delayedDispose) {
            scheduleDispose(session.id)
        } else {
            environment.dispose(session.id)
        }
    }

    private fun scheduleDispose(sessionId: UUID) {
        proxy.scheduler
            .buildTask(plugin, Runnable { environment.dispose(sessionId) })
            .delay(DISPOSE_DELAY)
            .schedule()
    }

    private fun teleportToCamera(limboPlayer: LimboPlayer) {
        limboPlayer.teleport(
            environment.spawnX,
            environment.spawnY,
            environment.spawnZ,
            environment.spawnYaw,
            environment.spawnPitch
        )
    }

    private fun sendPrompt(session: CaptchaSession) {
        val player = session.limboPlayer?.proxyPlayer ?: return
        player.sendMessage(message("Type the 3D block code you see in chat.", NamedTextColor.YELLOW))
    }

    fun shutdown() {
        timeoutTasks.values.forEach(ScheduledTask::cancel)
        timeoutTasks.clear()
        sessions.clear()
    }

    private fun message(text: String, color: NamedTextColor): Component =
        Component.text("[pnCaptcha] ", NamedTextColor.GOLD)
            .append(Component.text(text, color))

    companion object {
        private const val MAX_MOVE_DISTANCE_SQUARED = 2.25
        private val DISPOSE_DELAY: Duration = Duration.ofSeconds(2)
    }
}
