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
import ru.privatenull.pncaptcha.render.PacketCaptchaRenderer
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.nio.file.Path

@Plugin(
    id = "pncaptcha",
    name = "pnCaptcha",
    version = "0.2.6",
    description = "Configurable angled 3D packet CAPTCHA in a shared Velocity Limbo",
    url = "https://github.com/pnFolder/pnCaptcha",
    authors = ["PnFolder"],
    dependencies = [
        Dependency(id = "limboapi"),
        Dependency(id = "packetevents")
    ]
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
        val renderer = PacketCaptchaRenderer(config)

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
            rateLimiter = rateLimiter,
            renderer = renderer
        )

        logger.info(
            "pnCaptcha {} initialized on Velocity {} (target={}, timeout={}s, attempts={})",
            "0.2.6",
            proxy.version.version,
            config.targetServer,
            config.timeout.seconds,
            config.maxAttempts
        )
        logger.info(
            "3D scene: distance={} blocks, angle={}°, height={}, face={}x{}, depth={}, gap={}, creative={}, lock-position={}",
            config.captchaDistanceBlocks,
            config.captchaAngleDegrees,
            config.captchaCenterHeightBlocks,
            config.glyphScaleX,
            config.glyphScaleY,
            config.glyphDepth,
            config.glyphGapBlocks,
            config.creativeMode,
            config.lockPlayerPosition
        )

        val bounds = CaptchaScene.chunkBounds(config)
        logger.info(
            "CAPTCHA chunk bounds: X {}..{}, Z {}..{} (Limbo view-distance={})",
            bounds.minX,
            bounds.maxX,
            bounds.minZ,
            bounds.maxZ,
            CaptchaScene.recommendedViewDistance(config)
        )

        // LimboAPI's default CHUNK_RADIUS_SEND_ON_SPAWN=2 means the spawn chunk
        // plus directly adjacent chunks: relative chunk coordinates -1..1.
        if (bounds.minX < -1 || bounds.maxX > 1 || bounds.minZ < -1 || bounds.maxZ > 1) {
            logger.warn(
                "Configured CAPTCHA reaches beyond chunks sent immediately by LimboAPI's default " +
                    "chunk-radius-send-on-spawn=2. If parts are missing, increase it to 3 or reduce " +
                    "captcha-distance/angle/size. The pnCaptcha 0.2.6 defaults stay inside -1..1."
            )
        }
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
