package ru.privatenull.pncaptcha.manager

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.GameMode
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.limbo.CaptchaSessionHandler
import ru.privatenull.pncaptcha.render.PacketCaptchaRenderer
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import ru.privatenull.pncaptcha.session.VerificationState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CaptchaManager(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: CaptchaConfig,
    private val environment: CaptchaLimboEnvironment,
    private val generator: CaptchaGenerator,
    private val sessions: CaptchaSessionManager,
    private val cache: VerificationCache,
    private val rateLimiter: IpJoinRateLimiter,
    private val renderer: PacketCaptchaRenderer
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

        event.addOnJoinCallback(Runnable { begin(player) })
    }

    private fun begin(player: Player) {
        sessions.remove(player.uniqueId)?.let {
            timeoutTasks.remove(player.uniqueId)?.cancel()
            renderer.forget(player.uniqueId)
        }

        val session = sessions.create(
            CaptchaSession(
                playerId = player.uniqueId,
                answer = generator.generate(config.captchaLength)
            )
        )

        try {
            environment.spawn(
                player,
                CaptchaSessionHandler(this, player.uniqueId, session.id)
            )
        } catch (throwable: Throwable) {
            sessions.remove(player.uniqueId, session.id)
            logger.error("Failed to move {} into CAPTCHA Limbo", player.username, throwable)
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
        limboPlayer.setGameMode(GameMode.ADVENTURE)
        limboPlayer.sendAbilities()
        limboPlayer.teleport(
            CaptchaLimboEnvironment.SPAWN_X,
            CaptchaLimboEnvironment.SPAWN_Y,
            CaptchaLimboEnvironment.SPAWN_Z,
            CaptchaLimboEnvironment.SPAWN_YAW,
            CaptchaLimboEnvironment.SPAWN_PITCH
        )
        scheduleRender(playerId, sessionId, retriesLeft = 8, delayMillis = 150)
    }

    private fun scheduleRender(
        playerId: UUID,
        sessionId: UUID,
        retriesLeft: Int,
        delayMillis: Long
    ) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val limboPlayer = session.limboPlayer ?: return

        limboPlayer.scheduledExecutor.schedule({
            val current = sessions.getBySessionId(playerId, sessionId) ?: return@schedule
            val proxyPlayer = current.limboPlayer?.proxyPlayer ?: return@schedule

            try {
                if (renderer.render(proxyPlayer, current.answer)) {
                    val becameWaiting = synchronized(current) {
                        if (current.state == VerificationState.CAPTCHA_LOADING) {
                            current.state = VerificationState.CAPTCHA_WAITING
                            true
                        } else {
                            false
                        }
                    }
                    if (becameWaiting) {
                        sendPrompt(current)
                    }
                } else if (retriesLeft > 1) {
                    scheduleRender(playerId, sessionId, retriesLeft - 1, 150)
                } else {
                    failInternal(current, "PacketEvents user was not ready after render retries")
                }
            } catch (throwable: Throwable) {
                logger.error("Failed to render CAPTCHA for {}", proxyPlayer.username, throwable)
                failInternal(current, "CAPTCHA rendering failed")
            }
        }, delayMillis, TimeUnit.MILLISECONDS)
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
                } else {
                    session.answer = generator.generate(config.captchaLength)
                    session.state = VerificationState.CAPTCHA_LOADING
                }
            }
        }

        if (passed) {
            complete(session, proxyPlayer, limboPlayer)
            return
        }

        if (exhausted) {
            cleanup(session)
            proxyPlayer.disconnect(message("Too many wrong CAPTCHA attempts.", NamedTextColor.RED))
            return
        }

        proxyPlayer.sendMessage(
            message(
                "Wrong code. A new CAPTCHA was generated (${session.attempts}/${config.maxAttempts}).",
                NamedTextColor.RED
            )
        )
        scheduleRender(playerId, sessionId, retriesLeft = 4, delayMillis = 0)
    }

    private fun complete(session: CaptchaSession, proxyPlayer: Player, limboPlayer: LimboPlayer) {
        cache.markVerified(proxyPlayer)

        val target = proxy.getServer(config.targetServer)
        if (target.isEmpty) {
            cleanup(session)
            logger.error("Configured target server '{}' does not exist", config.targetServer)
            proxyPlayer.disconnect(message("Target server is not configured correctly.", NamedTextColor.RED))
            return
        }

        renderer.clear(proxyPlayer)
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        session.limboPlayer = null

        proxyPlayer.sendMessage(message("Verification passed.", NamedTextColor.GREEN))
        limboPlayer.disconnect(target.get())
    }

    fun enforcePosition(playerId: UUID, sessionId: UUID, x: Double, y: Double, z: Double) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val dx = x - CaptchaLimboEnvironment.SPAWN_X
        val dy = y - CaptchaLimboEnvironment.SPAWN_Y
        val dz = z - CaptchaLimboEnvironment.SPAWN_Z

        if (dx * dx + dy * dy + dz * dz > MAX_MOVE_DISTANCE_SQUARED) {
            session.limboPlayer?.teleport(
                CaptchaLimboEnvironment.SPAWN_X,
                CaptchaLimboEnvironment.SPAWN_Y,
                CaptchaLimboEnvironment.SPAWN_Z,
                CaptchaLimboEnvironment.SPAWN_YAW,
                CaptchaLimboEnvironment.SPAWN_PITCH
            )
        }
    }

    fun onDisconnect(playerId: UUID, sessionId: UUID) {
        val session = sessions.remove(playerId, sessionId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        renderer.forget(playerId)
        session.limboPlayer = null
    }

    fun onVelocityDisconnect(playerId: UUID) {
        val session = sessions.remove(playerId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        renderer.forget(playerId)
        session.limboPlayer = null
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
        cleanup(session)
        proxyPlayer?.disconnect(message("CAPTCHA timed out.", NamedTextColor.RED))
    }

    private fun failInternal(session: CaptchaSession, reason: String) {
        val player = session.limboPlayer?.proxyPlayer
        logger.warn("CAPTCHA session {} failed: {}", session.id, reason)
        session.state = VerificationState.FAILED
        cleanup(session)
        player?.disconnect(message("CAPTCHA service is temporarily unavailable.", NamedTextColor.RED))
    }

    private fun cleanup(session: CaptchaSession) {
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        session.limboPlayer?.proxyPlayer?.let(renderer::clear)
        session.limboPlayer = null
    }

    private fun sendPrompt(session: CaptchaSession) {
        val player = session.limboPlayer?.proxyPlayer ?: return
        player.sendMessage(message("Type the block code you see in chat.", NamedTextColor.YELLOW))
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
    }
}
