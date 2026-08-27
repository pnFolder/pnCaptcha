package ru.privatenull.pncaptcha

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import org.slf4j.Logger
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfigLoader
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.manager.CaptchaManager
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.nio.file.Path

@Plugin(
    id = "pncaptcha",
    name = "pnCaptcha",
    version = "0.3.0",
    description = "Per-session LimboAPI 3D voxel CAPTCHA for Velocity",
    url = "https://github.com/pnFolder/pnCaptcha",
    authors = ["PnFolder"],
    dependencies = [Dependency(id = "limboapi")]
)
class PnCaptchaPlugin @Inject constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    @Volatile
    private var manager: CaptchaManager? = null
    private var environment: CaptchaLimboEnvironment? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val config = CaptchaConfigLoader.load(dataDirectory)
        val factory = resolveLimboFactory()
        val limboEnvironment = CaptchaLimboEnvironment(factory, config)
        val sessionManager = CaptchaSessionManager()
        val verificationCache = VerificationCache(config.verifiedCacheTtl)
        val rateLimiter = IpJoinRateLimiter(config.maxJoinsPerWindow, config.joinWindow)

        environment = limboEnvironment
        manager = CaptchaManager(
            plugin = this,
            proxy = proxy,
            logger = logger,
            config = config,
            environment = limboEnvironment,
            generator = CaptchaGenerator(),
            sessions = sessionManager,
            cache = verificationCache,
            rateLimiter = rateLimiter
        )

        logger.info(
            "pnCaptcha {} initialized on Velocity {} (target={}, timeout={}s, attempts={})",
            "0.3.0",
            proxy.version.version,
            config.targetServer,
            config.timeout.seconds,
            config.maxAttempts
        )
        logger.info(
            "Renderer=Limbo VirtualWorld; PacketEvents is no longer required. " +
                "distance={} blocks, angle={}°, face={}x{}, depth={}, view={}, simulation={}, creative={}",
            config.captchaDistanceBlocks,
            config.captchaAngleDegrees,
            config.glyphScaleX,
            config.glyphScaleY,
            config.glyphDepth,
            config.limboViewDistance,
            config.limboSimulationDistance,
            config.creativeMode
        )

        val bounds = CaptchaScene.chunkBounds(config)
        logger.info(
            "Configured CAPTCHA volume spans chunks X {}..{}, Z {}..{}. " +
                "These chunks are created before each session Limbo is prepared.",
            bounds.minX,
            bounds.maxX,
            bounds.minZ,
            bounds.maxZ
        )
    }

    @Subscribe
    fun onLoginLimboRegister(event: LoginLimboRegisterEvent) {
        manager?.register(event)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        manager?.onVelocityDisconnect(event.player.uniqueId)
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        manager?.shutdown()
        manager = null
        environment?.close()
        environment = null
    }

    private fun resolveLimboFactory(): LimboFactory {
        val instance = proxy.pluginManager
            .getPlugin("limboapi")
            .flatMap { it.instance }
            .orElseThrow { IllegalStateException("LimboAPI plugin is required") }

        return instance as? LimboFactory
            ?: throw IllegalStateException("Installed LimboAPI does not expose LimboFactory")
    }
}
