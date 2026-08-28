package ru.privatenull.pncaptcha.action

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.elytrium.limboapi.api.player.GameMode
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import org.slf4j.Logger
import ru.privatenull.pncaptcha.config.ActionDefinition
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.message.MessageService
import ru.privatenull.pncaptcha.routing.ServerRouter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class ActionService(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: CaptchaConfig,
    private val messages: MessageService,
    private val router: ServerRouter
) {
    fun fire(trigger: String, context: Context): Boolean {
        if (!config.actions.enabled) return false
        val actions = config.actions.triggers[trigger.lowercase()].orEmpty()
        var terminal = false

        for (action in actions) {
            if (!action.enabled) continue
            if (action.permission.isNotBlank() && !context.player.hasPermission(action.permission)) continue
            if (action.chancePercent < 100.0 &&
                ThreadLocalRandom.current().nextDouble(100.0) >= action.chancePercent
            ) continue

            if (action.delayMillis > 0L) {
                proxy.scheduler.buildTask(plugin, Runnable {
                    runCatching { execute(action, context) }
                        .onFailure { logger.warn("Action '{}' failed for trigger '{}'", action.type, trigger, it) }
                }).delay(Duration.ofMillis(action.delayMillis)).schedule()
            } else {
                terminal = runCatching { execute(action, context) }
                    .onFailure { logger.warn("Action '{}' failed for trigger '{}'", action.type, trigger, it) }
                    .getOrDefault(false) || terminal
            }

            if (action.stopAfter) break
            if (terminal) break
        }

        return terminal
    }

    private fun execute(action: ActionDefinition, context: Context): Boolean {
        val placeholders = basePlaceholders(context)

        return when (action.type.lowercase()) {
            "message" -> {
                val lines = if (action.lines.isNotEmpty()) action.lines else listOf(action.text)
                messages.send(context.player, lines.filter(String::isNotEmpty), placeholders)
                false
            }

            "actionbar" -> {
                if (action.text.isNotBlank()) {
                    context.player.sendActionBar(messages.component(listOf(action.text), placeholders))
                }
                false
            }

            "title" -> {
                val title = messages.component(listOf(action.title), placeholders)
                val subtitle = messages.component(listOf(action.subtitle), placeholders)
                context.player.showTitle(
                    Title.title(
                        title,
                        subtitle,
                        Title.Times.times(
                            Duration.ofMillis(action.fadeInMillis.coerceAtLeast(0)),
                            Duration.ofMillis(action.stayMillis.coerceAtLeast(0)),
                            Duration.ofMillis(action.fadeOutMillis.coerceAtLeast(0))
                        )
                    )
                )
                false
            }

            "sound" -> {
                val source = runCatching { Sound.Source.valueOf(action.source.uppercase()) }
                    .getOrDefault(Sound.Source.MASTER)
                context.player.playSound(
                    Sound.sound(
                        Key.key(action.sound),
                        source,
                        action.volume,
                        action.soundPitch
                    )
                )
                false
            }

            "command" -> {
                val command = render(action.command, placeholders).removePrefix("/")
                if (command.isNotBlank()) {
                    proxy.commandManager.executeAsync(proxy.consoleCommandSource, command)
                }
                false
            }

            "disconnect" -> {
                val lines = if (action.lines.isNotEmpty()) action.lines else listOf(action.text)
                context.player.disconnect(messages.component(lines.filter(String::isNotEmpty), placeholders))
                true
            }

            "connect" -> {
                val target = if (action.server.isBlank() || action.server == "@route") {
                    router.select()
                } else {
                    router.selectNamed(render(action.server, placeholders))
                } ?: return false

                context.limboPlayer?.disconnect(target)
                    ?: context.player.createConnectionRequest(target).fireAndForget()
                true
            }

            "teleport" -> {
                val limboPlayer = context.limboPlayer ?: return false
                val x = action.x ?: return false
                val y = action.y ?: return false
                val z = action.z ?: return false
                limboPlayer.teleport(
                    x, y, z,
                    action.teleportYaw ?: 0.0f,
                    action.teleportPitch ?: 0.0f
                )
                false
            }

            "gamemode" -> {
                val limboPlayer = context.limboPlayer ?: return false
                limboPlayer.setGameMode(
                    when (action.gameMode.lowercase()) {
                        "survival" -> GameMode.SURVIVAL
                        "creative" -> GameMode.CREATIVE
                        "spectator" -> GameMode.SPECTATOR
                        else -> GameMode.ADVENTURE
                    }
                )
                limboPlayer.sendAbilities()
                false
            }

            else -> false
        }
    }

    private fun basePlaceholders(context: Context): Map<String, Any?> = buildMap {
        put("player", context.player.username)
        put("uuid", context.player.uniqueId)
        put("ip", context.player.remoteAddress.address.hostAddress)
        put("online", proxy.playerCount)
        put("attempt", context.attempt)
        put("max", context.maxAttempts)
        put("reason", context.reason)
        put("server", context.server)
        put("captcha", context.captcha)
        context.placeholders.forEach { (key, value) -> put(key, value) }
    }

    private fun render(text: String, placeholders: Map<String, Any?>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value?.toString().orEmpty())
        }
        return result
    }

    data class Context(
        val player: Player,
        val limboPlayer: LimboPlayer? = null,
        val sessionId: UUID? = null,
        val captcha: String? = null,
        val attempt: Int = 0,
        val maxAttempts: Int = 0,
        val reason: String? = null,
        val server: String? = null,
        val placeholders: Map<String, Any?> = emptyMap()
    )
}
