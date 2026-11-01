package ru.privatenull.pncaptcha.manager

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pncaptcha.integration.later
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.LimboPlayer
import ru.privatenull.pncaptcha.log.Log
import ru.privatenull.pncaptcha.action.ActionService
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.limbo.CaptchaSessionHandler
import ru.privatenull.pncaptcha.message.MessageService
import ru.privatenull.pncaptcha.routing.ServerRouter
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import ru.privatenull.pncaptcha.session.VerificationState
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class CaptchaManager(
    private val tasks: TaskScope,
    private val cooldowns: CooldownService,
    private val proxy: ProxyServer,
    private val config: CaptchaConfig,
    private val environment: CaptchaLimboEnvironment,
    private val generator: CaptchaGenerator,
    private val sessions: CaptchaSessionManager,
    private val cache: VerificationCache,
    private val rateLimiter: IpJoinRateLimiter,
    private val messages: MessageService,
    private val router: ServerRouter,
    private val actions: ActionService
) {
    private val timeoutTasks = ConcurrentHashMap<UUID, TaskHandle>()
    private val disposeTasks = ConcurrentHashMap<UUID, TaskHandle>()
    private val beginLock = Any()

    fun register(event: LoginLimboRegisterEvent) {
        val player = event.player

        if (config.security.bypassPermission.isNotBlank() && player.hasPermission(config.security.bypassPermission)) return
        if (cache.isVerified(player)) return

        if (router.networkIsFull(player)) {
            event.addOnJoinCallback(Runnable {
                val terminal = actions.fire("network-full", ActionService.Context(player = player))
                if (!terminal) player.disconnect(messages.component(config.messages.networkFull))
            })
            return
        }

        if (!rateLimiter.allow(player.remoteAddress.address)) {
            event.addOnJoinCallback(Runnable {
                val terminal = actions.fire("rate-limited", ActionService.Context(player = player))
                if (!terminal) player.disconnect(messages.component(config.messages.rateLimited))
            })
            return
        }

        if (environment.activeCount() >= config.maxActiveCaptchas) {
            event.addOnJoinCallback(Runnable {
                val terminal = actions.fire("busy", ActionService.Context(player = player))
                if (!terminal) player.disconnect(messages.component(config.messages.busy))
            })
            return
        }

        event.addOnJoinCallback(Runnable { begin(player) })
    }

    private fun begin(player: Player) {
        synchronized(beginLock) {
            // The check in register() is only an early rejection. Recheck while holding
            // a lock so concurrent join callbacks cannot create more worlds than allowed.
            if (environment.activeCount() >= config.maxActiveCaptchas) {
                val terminal = actions.fire("busy", ActionService.Context(player = player))
                if (!terminal) player.disconnect(messages.component(config.messages.busy))
                return
            }

            sessions.remove(player.uniqueId)?.let { stale ->
                timeoutTasks.remove(player.uniqueId)?.cancel()
                actions.cleanup(player.uniqueId)
                environment.dispose(stale.id)
            }
            cooldowns.reset(player.uniqueId, RECOVERY_COOLDOWN)

            val session = sessions.create(CaptchaSession(playerId = player.uniqueId, answer = generator.generate(config.captchaLength)))

            try {
                val info = environment.spawn(
                    sessionId = session.id,
                    answer = session.answer,
                    player = player,
                    handler = CaptchaSessionHandler(this, player.uniqueId, session.id)
                )
                Log.info(
                    "CAPTCHA ${session.id.toString().take(8)} for ${player.username}: ${info.blockCount} blocks, " +
                        "chunks X ${info.chunkBounds.minX}..${info.chunkBounds.maxX}, " +
                        "Z ${info.chunkBounds.minZ}..${info.chunkBounds.maxZ}, " +
                        "view/sim=${info.viewDistance}/${info.simulationDistance}, " +
                        "yaw/pitch/roll=${"%.2f".format(info.scene.rotationYawDegrees)}/" +
                        "${"%.2f".format(info.scene.rotationPitchDegrees)}/" +
                        "${"%.2f".format(info.scene.rotationRollDegrees)}"
                )
            } catch (throwable: Throwable) {
                sessions.remove(player.uniqueId, session.id)
                actions.cleanup(player.uniqueId)
                environment.dispose(session.id)
                Log.error("Failed to build/spawn CAPTCHA Limbo for ${player.username}", throwable)
                val terminal = actions.fire(
                    "unavailable",
                    ActionService.Context(player = player, sessionId = session.id, captcha = session.answer)
                )
                if (!terminal) player.disconnect(messages.component(config.messages.unavailable))
                return
            }

            timeoutTasks[player.uniqueId] = tasks.later("pncaptcha-timeout", config.timeout, Runnable { timeout(player.uniqueId, session.id) })
        }
    }

    fun onSpawn(playerId: UUID, sessionId: UUID, limboPlayer: LimboPlayer) {
        val session = sessions.getBySessionId(playerId, sessionId) ?: return
        session.limboPlayer = limboPlayer

        limboPlayer.setGameMode(environment.gameMode)
        limboPlayer.sendAbilities()
        limboPlayer.setWorldTime(config.limbo.worldTimeTicks)

        if (config.player.recovery.enabled || config.limbo.fallingEnabled) limboPlayer.enableFalling()
        else limboPlayer.disableFalling()

        teleportToCamera(sessionId, limboPlayer, null, null)

        val becameWaiting = synchronized(session) {
            if (session.state == VerificationState.CAPTCHA_LOADING) {
                session.state = VerificationState.CAPTCHA_WAITING
                true
            } else false
        }

        if (becameWaiting) {
            val placeholders = mapOf("max" to config.maxAttempts, "timeout" to config.general.timeoutSeconds)
            messages.send(limboPlayer.proxyPlayer, config.messages.prompt, placeholders)
            val terminal = actions.fire("challenge-start", context(session, limboPlayer, placeholders = placeholders))
            if (terminal) cleanup(session, delayedDispose = true)
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
            if (matches(session.answer, input)) {
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
            val terminal = actions.fire("exhausted", context(session, limboPlayer))
            if (!terminal) proxyPlayer.disconnect(messages.component(config.messages.tooManyAttempts))
            cleanup(session, delayedDispose = true)
            return
        }

        val placeholders = mapOf(
            "attempt" to session.attempts,
            "max" to config.maxAttempts,
            "remaining" to (config.maxAttempts - session.attempts).coerceAtLeast(0)
        )
        messages.send(proxyPlayer, config.messages.wrong, placeholders)
        val terminal = actions.fire("wrong-answer", context(session, limboPlayer, placeholders = placeholders))
        if (terminal) cleanup(session, delayedDispose = true)
    }

    private fun complete(session: CaptchaSession, proxyPlayer: Player, limboPlayer: LimboPlayer) {
        val target = router.select()
        if (target == null) {
            Log.error("No configured routing server is available")
            val terminal = actions.fire("route-unavailable", context(session, limboPlayer))
            if (!terminal) proxyPlayer.disconnect(messages.component(config.messages.routeUnavailable))
            cleanup(session, delayedDispose = true)
            return
        }

        val serverName = target.serverInfo.name
        val placeholders = mapOf("server" to serverName)
        messages.send(proxyPlayer, config.messages.passed, placeholders)
        val terminal = actions.fire("passed", context(session, limboPlayer, server = serverName, placeholders = placeholders))

        cache.markVerified(proxyPlayer)
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        cooldowns.reset(session.playerId, RECOVERY_COOLDOWN)
        actions.cleanup(session.playerId)
        session.limboPlayer = null

        if (!terminal) limboPlayer.disconnect(target)
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

        if (!cooldowns.acquire(playerId, RECOVERY_COOLDOWN, Duration.ofMillis(config.player.recovery.cooldownMillis)).allowed) return

        teleportToCamera(sessionId, limboPlayer, yaw, pitch)
        val placeholders = mapOf("reason" to reason)
        if (config.player.recovery.sendMessage) messages.send(limboPlayer.proxyPlayer, config.messages.recovered, placeholders)
        val terminal = actions.fire("recovery", context(session, limboPlayer, reason = reason, placeholders = placeholders))
        if (terminal) cleanup(session, delayedDispose = true)
    }

    fun onDisconnect(playerId: UUID, sessionId: UUID) {
        val session = sessions.remove(playerId, sessionId) ?: return
        timeoutTasks.remove(playerId)?.cancel()
        cooldowns.reset(playerId, RECOVERY_COOLDOWN)
        actions.cleanup(playerId)
        session.limboPlayer = null
        environment.dispose(session.id)
    }

    fun onVelocityDisconnect(playerId: UUID) {
        val session = sessions.remove(playerId)
        timeoutTasks.remove(playerId)?.cancel()
        cooldowns.reset(playerId, RECOVERY_COOLDOWN)
        actions.cleanup(playerId)
        if (session != null) {
            session.limboPlayer = null
            environment.dispose(session.id)
        }
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

        val limboPlayer = session.limboPlayer
        val proxyPlayer = limboPlayer?.proxyPlayer
        if (proxyPlayer != null) {
            val terminal = actions.fire("timeout", context(session, limboPlayer))
            if (!terminal) proxyPlayer.disconnect(messages.component(config.messages.timeout))
        }
        cleanup(session, delayedDispose = true)
    }

    private fun matches(expected: String, rawInput: String): Boolean {
        if (rawInput.length > config.general.input.maxLength) return false
        return normalize(expected) == normalize(rawInput)
    }

    private fun normalize(value: String): String {
        var result = value
        if (config.general.input.trim) result = result.trim()
        if (config.general.input.removeSpaces) result = result.filterNot(Char::isWhitespace)
        if (!config.general.input.caseSensitive) result = result.uppercase()
        return result
    }

    private fun context(
        session: CaptchaSession,
        limboPlayer: LimboPlayer,
        reason: String? = null,
        server: String? = null,
        placeholders: Map<String, Any?> = emptyMap()
    ): ActionService.Context = ActionService.Context(
        player = limboPlayer.proxyPlayer,
        limboPlayer = limboPlayer,
        sessionId = session.id,
        captcha = session.answer,
        attempt = session.attempts,
        maxAttempts = config.maxAttempts,
        reason = reason,
        server = server,
        placeholders = placeholders
    )

    private fun cleanup(session: CaptchaSession, delayedDispose: Boolean) {
        sessions.remove(session.playerId, session.id)
        timeoutTasks.remove(session.playerId)?.cancel()
        cooldowns.reset(session.playerId, RECOVERY_COOLDOWN)
        actions.cleanup(session.playerId)
        session.limboPlayer = null
        if (delayedDispose) scheduleDispose(session.id) else environment.dispose(session.id)
    }

    private fun scheduleDispose(sessionId: UUID) {
        disposeTasks.remove(sessionId)?.cancel()
        val task = tasks.later("pncaptcha-dispose", DISPOSE_DELAY, Runnable {
            disposeTasks.remove(sessionId)
            environment.dispose(sessionId)
        })
        disposeTasks[sessionId] = task
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
        timeoutTasks.values.forEach(TaskHandle::cancel)
        timeoutTasks.clear()
        disposeTasks.values.forEach(TaskHandle::cancel)
        disposeTasks.clear()
        cooldowns.clear()
        actions.shutdown()
        sessions.clear()
    }

    companion object {
        private val DISPOSE_DELAY: Duration = Duration.ofSeconds(2)
        private const val RECOVERY_COOLDOWN = "recovery"
    }
}
