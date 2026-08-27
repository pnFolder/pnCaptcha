package ru.privatenull.pncaptcha.update

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.message.MessageService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class UpdateChecker(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: CaptchaConfig,
    private val currentVersion: String,
    private val messages: MessageService
) {
    private val state = AtomicReference<UpdateState>(UpdateState.Unknown)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.updates.requestTimeoutSeconds))
        .build()

    fun start() {
        if (!config.updates.enabled || !config.updates.checkOnStartup) return
        proxy.scheduler.buildTask(plugin, Runnable { checkNow() })
            .delay(Duration.ofSeconds(config.updates.startupDelaySeconds))
            .schedule()
    }

    fun checkNow() {
        if (!config.updates.enabled) return

        val endpoint = "https://api.github.com/repos/${config.updates.repository}/releases/latest"
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(config.updates.requestTimeoutSeconds))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "pnCaptcha/$currentVersion")
            .GET()
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() !in 200..299) {
                    state.set(UpdateState.Failed("HTTP ${response.statusCode()}"))
                    if (config.updates.notifyConsole) {
                        logger.warn("pnCaptcha update check failed: GitHub returned HTTP {}", response.statusCode())
                    }
                    return@thenAccept
                }

                val body = response.body()
                val latest = TAG_REGEX.find(body)?.groupValues?.get(1)?.removePrefix("v")
                val url = URL_REGEX.find(body)?.groupValues?.get(1)
                if (latest.isNullOrBlank() || url.isNullOrBlank()) {
                    state.set(UpdateState.Failed("Invalid GitHub response"))
                    if (config.updates.notifyConsole) logger.warn("pnCaptcha update check failed: invalid GitHub response")
                    return@thenAccept
                }

                if (isNewer(latest, currentVersion)) {
                    val available = UpdateState.Available(latest, url)
                    state.set(available)
                    if (config.updates.notifyConsole) {
                        logger.warn("pnCaptcha {} is outdated. Latest version: {} — {}", currentVersion, latest, url)
                    }
                    proxy.allPlayers.forEach(::notifyPlayer)
                } else {
                    state.set(UpdateState.UpToDate(latest))
                    if (config.updates.notifyConsole && config.updates.announceUpToDate) {
                        logger.info("pnCaptcha is up to date ({}).", currentVersion)
                    }
                }
            }
            .exceptionally { throwable ->
                state.set(UpdateState.Failed(throwable.message ?: throwable.javaClass.simpleName))
                if (config.updates.notifyConsole) logger.warn("pnCaptcha update check failed", throwable)
                null
            }
    }

    fun notifyPlayer(player: Player) {
        if (!config.updates.enabled || !config.updates.notifyPlayers) return
        if (config.updates.notifyPermission.isNotBlank() && !player.hasPermission(config.updates.notifyPermission)) return
        val available = state.get() as? UpdateState.Available ?: return
        messages.send(
            player,
            config.messages.updateAvailable,
            mapOf(
                "current" to currentVersion,
                "latest" to available.version,
                "url" to available.url
            )
        )
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val left = versionParts(candidate)
        val right = versionParts(current)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

    private sealed interface UpdateState {
        data object Unknown : UpdateState
        data class UpToDate(val version: String) : UpdateState
        data class Available(val version: String, val url: String) : UpdateState
        data class Failed(val reason: String) : UpdateState
    }

    companion object {
        private val TAG_REGEX = Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        private val URL_REGEX = Regex("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+/releases/tag/[^\\\"]+)\\\"")
    }
}
