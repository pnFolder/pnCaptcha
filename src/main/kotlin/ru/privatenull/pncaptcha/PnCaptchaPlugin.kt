package ru.privatenull.pncaptcha

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
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
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfigLoader
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.manager.CaptchaManager
import ru.privatenull.pncaptcha.message.MessageService
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import ru.privatenull.pncaptcha.update.UpdateChecker
import java.nio.file.Files
import java.nio.file.Path

@Plugin(
    id = "pncaptcha",
    name = "pnCaptcha",
    version = PnCaptchaPlugin.VERSION,
    description = "Fully configurable LimboAPI 3D voxel CAPTCHA for Velocity",
    url = "https://github.com/pnFolder/pnCaptcha",
    authors = ["PnFolder"],
    dependencies = [Dependency(id = "limboapi")]
)
class PnCaptchaPlugin @Inject constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    @Volatile private var manager: CaptchaManager? = null
    private var environment: CaptchaLimboEnvironment? = null
    private var updateChecker: UpdateChecker? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val config = CaptchaConfigLoader.load(dataDirectory)
        Files.deleteIfExists(dataDirectory.resolve("config.properties"))

        val font = CaptchaFont.resolve(config.font)
        val factory = resolveLimboFactory()
        val limboEnvironment = CaptchaLimboEnvironment(factory, config)
        val messageService = MessageService(config)
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
            generator = CaptchaGenerator(font.alphabet),
            sessions = sessionManager,
            cache = verificationCache,
            rateLimiter = rateLimiter,
            messages = messageService
        )

        updateChecker = UpdateChecker(this, proxy, logger, config, VERSION, messageService).also { it.start() }

        logger.info("pnCaptcha {} initialized. Config: plugins/pncaptcha/config.yml", VERSION)
        logger.info(
            "3D: distance={}, yaw/pitch/roll={}/{}/{}, voxel={}x{}x{}, front/back={}/{}, font={} {}x{}, Limbo view/sim={}/{}",
            config.scene.distanceBlocks,
            config.scene.rotationYawDegrees,
            config.scene.rotationPitchDegrees,
            config.scene.rotationRollDegrees,
            config.geometry.pixelWidth,
            config.geometry.pixelHeight,
            config.geometry.depthBlocks,
            config.geometry.frontThicknessBlocks,
            config.geometry.backThicknessBlocks,
            config.font.preset,
            font.width,
            font.height,
            config.limbo.viewDistance,
            config.limbo.simulationDistance
        )
    }

    @Subscribe
    fun onLoginLimboRegister(event: LoginLimboRegisterEvent) {
        manager?.register(event)
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        updateChecker?.notifyPlayer(event.player)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        manager?.onVelocityDisconnect(event.player.uniqueId)
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        manager?.shutdown()
        manager = null
        updateChecker = null
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

    companion object {
        const val VERSION = "0.5.0"
    }
}
