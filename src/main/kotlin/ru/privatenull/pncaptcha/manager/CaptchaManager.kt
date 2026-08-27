package ru.privatenull.pncaptcha.manager

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.LimboPlayer
import org.slf4j.Logger
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.limbo.CaptchaSessionHandler
import ru.privatenull.pncaptcha.message.MessageService
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import ru.privatenull.pncaptcha.session.VerificationState
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

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
    private val messages: MessageService
) {
    private val timeoutTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    private val lastRecoveryAt = ConcurrentHashMap<UUID, Long>()

    fun register(event: LoginLimboRegisterEvent) {
        val player = event.player
        if (cache.isVerified(player)) return

        if (!rateLimiter.allow(player.remoteAddress.address)) {
            event.addOnJoinCallback(Runnable { player.disconnect(messages.component(config.messages.rateLimited)) })
            return
        }

        if (environment.activeCount() >= config.maxActiveCaptchas) {
            event.addOnJoinCallback(Runnable { player.disconnect(messages.component(config.messages.busy)) })
            return
        }

        event.addOnJoinCallback(Runnable { begin(player) })
    }

    private fun begin(player: Player) {
        sessions.remove(player.uniqueId)?.let { stale ->
            timeoutTasks.remove(player.uniqueId)?.cancel()
            environment.dispose(stale.id)
        }
        lastRecoveryAt.remove(player.uniqueId)

        val session = sessions.create(CaptchaSession(playerId = player.uniqueId, answer = generator.generate(config.captchaLength)))

        try {
            val info = environment.spawn(
                sessionId = session.id,
                answer = session.answer,
                player = player,
                handler = CaptchaSessionHandler(this, player.uniqueId, session.id)
            )
            logger.info(
                "CAPTCHA {} for {}: {} blocks, chunks X {}..{}, Z {}..{}, view/sim={}/{}, yaw/pitch/roll={}/{}/{}",
                session.id.toString().take(8), player.username, info.blockCount,
                info.chunkBounds.minX, info.chunkBounds.maxX, info.chunkBounds.minZ, info.chunkBounds.maxZ,
                info.viewDistance, info.simulationDistance,
                "%.2f".format(info.scene.rotationYawDegrees),
                "%.2f".format(info.scene.rotationPitchDegrees),
                "%.2f".format(info.scene.rotationRollDegrees)
            )
        } catch (throwable: Throwable) {
            sessions.remove(player.uniqueId, session.id)
            environment.dispose(session.id)
            logger.error("Failed to build/spawn CAPTCHA Limbo for {}", player.username, throwable)
            player.disconnect(messages.component(config.messages.unavailable))
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

        limboPlayer.setGameMode(environment.gameMode)
        limboPlayer.sendAbilities()

        // LimboAPI's disableFalling() forcibly puts the client into a flying
        // state. Recovery is intended to catch a real fall, so gravity must be
        // enabled whenever recovery is enabled. If recovery is disabled we keep
        // the old floating behaviour for servers that want a fixed void scene.
        if (config.player.recovery.enabled) {
            limboPlayer.enableFalling()
        } else {
            limboPlayer.disableFalling()
        }

        teleportToCamera(sessionId, limboPlayer, null, null)

        val becameWaiting = synchronized(session) {
            if (session.state == VerificationState.CAPTCHA_LOADING) {
                session.state = VerificationState.CAPTCHA_WAITING
                true
            } else false
        }

        if (becameWaiting) {
            messages.send(
                limboPlayer.proxyPlayer,
                config.messages.prompt,
                mapOf("max" to config.maxAttempts, "timeout" to config.general.timeoutSeconds)
            )
        }
    }

    fun submit(playerId: UUID, sessionId: UUID, input: String) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val limboPlayer = session.limboPlayer ?: return
        val proxyPlayer = limboPlayer.proxyPlayer

        var passed = false
        var exhausted = false
        synchronized(session) {
            if (session.state != VerificationState.CAPTCHA_WAITING) return
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
            proxyPlayer.disconnect(messages.component(config.messages.tooManyAttempts))
            cleanup(session, delayedDispose = true)
            return
        }

        messages.send(
            proxyPlayer,
            config.messages.wrong,
            mapOf("attempt" to session.attempts, "max" to config.maxAttempts)
        )
    }

    private fun complete(session: CaptchaSession, proxyPlayer: Player, limboPlayer: LimboPlayer) {
        cache.markVerified(proxyPlayer)
        val target = proxy.getServer(config.targetServer)
        if (target.isEmpty) {
            logger.error("Configured target server '{}' does not exist", config.targetServer)
            proxyPlayer.disconnect(messages.component(config.messages.targetMissing))
            cleanup(session, delayedDispose = true)
            return
        }

        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        lastRecoveryAt.remove(session.playerId)
        session.limboPlayer = null

        messages.send(proxyPlayer, config.messages.passed)
        limboPlayer.disconnect(target.get())
        scheduleDispose(session.id)
    }

    fun onMove(
        playerId: UUID,
        sessionId: UUID,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float? = null,
        pitch: Float? = null
    ) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val limboPlayer = session.limboPlayer ?: return
        val camera = environment.camera(sessionId) ?: return

        val dx = x - camera.x
        val dy = y - camera.y
        val dz = z - camera.z
        val distanceSquared = dx * dx + dy * dy + dz * dz

        val reason = when {
            config.player.lockPosition && distanceSquared > config.player.lockRadiusBlocks * config.player.lockRadiusBlocks ->
                "позиция ограничена настройкой lock-position"
            config.player.recovery.enabled && config.player.recovery.belowSpawnBlocks > 0.0 &&
                y < camera.y - config.player.recovery.belowSpawnBlocks -> "ты упал ниже допустимой высоты"
            config.player.recovery.enabled && config.player.recovery.aboveSpawnBlocks > 0.0 &&
                y > camera.y + config.player.recovery.aboveSpawnBlocks -> "ты поднялся слишком высоко"
            config.player.recovery.enabled && config.player.recovery.maxHorizontalDistanceBlocks > 0.0 &&
                sqrt(dx * dx + dz * dz) > config.player.recovery.maxHorizontalDistanceBlocks ->
                "ты слишком далеко отошёл от точки проверки"
            else -> null
        } ?: return

        val now = System.currentTimeMillis()
        val previous = lastRecoveryAt[playerId] ?: 0L
        if (now - previous < config.player.recovery.cooldownMillis) return
        lastRecoveryAt[playerId] = now

        teleportToCamera(sessionId, limboPlayer, yaw, pitch)
        if (config.player.recovery.sendMessage) {
            messages.send(limboPlayer.proxyPlayer, config.messages.recovered, mapOf("reason" to reason))
        }
    }

    fun onDisconnect(playerId: UUID, sessionId: UUID) {
        val session = sessions.remove(playerId, sessionId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        lastRecoveryAt.remove(playerId)
        session.limboPlayer = null
        environment.dispose(session.id)
    }

    fun onVelocityDisconnect(playerId: UUID) {
        val session = sessions.remove(playerId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        lastRecoveryAt.remove(playerId)
        session.limboPlayer = null
        environment.dispose(session.id)
    }

    private fun timeout(playerId: UUID, sessionId: UUID) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        val shouldKick = synchronized(session) {
            if (session.state == VerificationState.VERIFIED || session.state == VerificationState.FAILED) false
            else {
                session.state = VerificationState.FAILED
                true
            }
        }
        if (!shouldKick) return

        session.limboPlayer?.proxyPlayer?.disconnect(messages.component(config.messages.timeout))
        cleanup(session, delayedDispose = true)
    }

    private fun cleanup(session: CaptchaSession, delayedDispose: Boolean) {
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        lastRecoveryAt.remove(session.playerId)
        session.limboPlayer = null
        if (delayedDispose) scheduleDispose(session.id) else environment.dispose(session.id)
    }

    private fun scheduleDispose(sessionId: UUID) {
        proxy.scheduler.buildTask(plugin, Runnable { environment.dispose(sessionId) })
            .delay(DISPOSE_DELAY)
            .schedule()
    }

    private fun teleportToCamera(sessionId: UUID, limboPlayer: LimboPlayer, currentYaw: Float?, currentPitch: Float?) {
        val camera = environment.camera(sessionId) ?: return
        val preserve = config.player.recovery.preserveCurrentLook && currentYaw != null && currentPitch != null
        limboPlayer.teleport(
            camera.x, camera.y, camera.z,
            if (preserve) currentYaw!! else camera.yaw,
            if (preserve) currentPitch!! else camera.pitch
        )
    }

    fun shutdown() {
        timeoutTasks.values.forEach(ScheduledTask::cancel)
        timeoutTasks.clear()
        lastRecoveryAt.clear()
        sessions.clear()
    }

    companion object {
        private val DISPOSE_DELAY: Duration = Duration.ofSeconds(2)
    }
}
