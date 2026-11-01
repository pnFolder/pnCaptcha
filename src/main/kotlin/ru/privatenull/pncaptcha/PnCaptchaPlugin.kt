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
import com.velocitypowered.api.scheduler.ScheduledTask
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import org.pnfolder.pnlibrary.velocity.bootstrap.PnLibraryBootstrap
import org.slf4j.Logger
import ru.privatenull.pncaptcha.log.Log
import ru.privatenull.pncaptcha.log.Slf4jSink
import ru.privatenull.pncaptcha.action.ActionService
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.config.CaptchaConfigLoader
import ru.privatenull.pncaptcha.limbo.CaptchaLimboEnvironment
import ru.privatenull.pncaptcha.integration.PnLibraryRuntime
import ru.privatenull.pncaptcha.manager.CaptchaManager
import ru.privatenull.pncaptcha.message.MessageService
import ru.privatenull.pncaptcha.routing.ServerRouter
import ru.privatenull.pncaptcha.security.IpJoinRateLimiter
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.nio.file.Files
import java.nio.file.Path

@Plugin(
    id = "pncaptcha",
    name = "pnCaptcha",
    version = PnCaptchaPlugin.VERSION,
    description = "Fully configurable LimboAPI 3D voxel CAPTCHA and smart routing for Velocity",
    url = "https://github.com/pnFolder/pnCaptcha",
    authors = ["PnFolder"],
    dependencies = [Dependency(id = "limboapi"), Dependency(id = "pnlibrary", optional = true)]
)
class PnCaptchaPlugin @Inject constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    @Volatile private var manager: CaptchaManager? = null
    private var environment: CaptchaLimboEnvironment? = null
    private var pnRuntime: PnLibraryRuntime? = null
    private var verificationCachePurgeTask: ScheduledTask? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        Log.fallback = Slf4jSink(logger)
        val ready = try {
            PnLibraryBootstrap.ensureInstalled(proxy, this, PnLibraryRuntime.MINIMUM_VERSION)
        } catch (failure: Throwable) {
            Log.error("Не удалось автоматически подготовить pnLibrary. pnCaptcha не будет запущен.", failure)
            false
        }
        if (!ready) {
            Log.error("pnLibrary не готова (если она только что скачана, перезапустите прокси). pnCaptcha не запущен.")
            return
        }
        val runtime = try {
            PnLibraryRuntime.connect(this, logger, dataDirectory)
        } catch (failure: Throwable) {
            Log.error("pnLibrary не удалось подключить к pnCaptcha. pnCaptcha не будет запущен.", failure)
            return
        }
        val config = CaptchaConfigLoader.load(dataDirectory)
        Files.deleteIfExists(dataDirectory.resolve("config.properties"))

        val font = CaptchaFont.resolve(config.font)
        val factory = resolveLimboFactory()
        val limboEnvironment = CaptchaLimboEnvironment(factory, config)
        val messageService = MessageService(config)
        val serverRouter = ServerRouter(proxy, config.routing)
        val actionService = ActionService(this, proxy, config, messageService, serverRouter)
        val sessionManager = CaptchaSessionManager()
        val verificationCache = VerificationCache(config.verifiedCacheTtl)
        verificationCachePurgeTask = proxy.scheduler.buildTask(this, Runnable {
            verificationCache.purgeExpired()
        }).repeat(java.time.Duration.ofMinutes(1)).schedule()
        val rateLimiter = IpJoinRateLimiter(config.maxJoinsPerWindow, config.joinWindow)

        environment = limboEnvironment
        manager = CaptchaManager(
            plugin = this,
            proxy = proxy,
            config = config,
            environment = limboEnvironment,
            generator = CaptchaGenerator(font.alphabet),
            sessions = sessionManager,
            cache = verificationCache,
            rateLimiter = rateLimiter,
            messages = messageService,
            router = serverRouter,
            actions = actionService
        )

        try {
            runtime.register(config.metrics.enabled, VERSION)
            pnRuntime = runtime
        } catch (failure: Throwable) {
            Log.error("Не удалось зарегистрировать pnCaptcha в pnLibrary. Капча работает без неё.", failure)
            runCatching { runtime.close() }
        }

        Log.info("pnCaptcha $VERSION initialized. Config: plugins/pncaptcha/config.yml")
        Log.info(
            "Routing: strategy=${config.routing.strategy}, configured servers=${serverRouter.configuredServerCount()}, " +
                "network max=${config.routing.networkMaxPlayers}, reserve=${config.routing.networkReserveSlots}"
        )
        Log.info(
            "3D: distance=${config.scene.distanceBlocks}, " +
                "yaw/pitch/roll=${config.scene.rotationYawDegrees}/${config.scene.rotationPitchDegrees}/${config.scene.rotationRollDegrees}, " +
                "voxel=${config.geometry.pixelWidth}x${config.geometry.pixelHeight}x${config.geometry.depthBlocks}, " +
                "front/back=${config.geometry.frontThicknessBlocks}/${config.geometry.backThicknessBlocks}, " +
                "font=${config.font.preset} ${font.width}x${font.height}, " +
                "Limbo view/sim=${config.limbo.viewDistance}/${config.limbo.simulationDistance}"
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
        val closing = pnRuntime
        pnRuntime = null
        runCatching { closing?.close() }
            .onFailure { Log.warning("Не удалось корректно закрыть контекст pnLibrary", it) }
        verificationCachePurgeTask?.cancel()
        verificationCachePurgeTask = null
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
        const val VERSION = "1.2.4"
    }
}
