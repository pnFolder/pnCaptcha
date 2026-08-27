package ru.privatenull.pncaptcha

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.session.CaptchaSessionManager

@Plugin(
    id = "pncaptcha",
    name = "pnCaptcha",
    version = "0.1.0-SNAPSHOT",
    description = "Block-based CAPTCHA verification for Velocity proxies",
    authors = ["PnFolder"]
)
class PnCaptchaPlugin @Inject constructor(
    private val proxy: ProxyServer,
    private val logger: Logger
) {
    private val sessionManager = CaptchaSessionManager()
    private val captchaGenerator = CaptchaGenerator()

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("pnCaptcha initialized on Velocity {}", proxy.version.version)
        logger.info("Core services ready: sessions={}, alphabet={}", sessionManager.size(), CaptchaGenerator.DEFAULT_ALPHABET.length)
    }
}
