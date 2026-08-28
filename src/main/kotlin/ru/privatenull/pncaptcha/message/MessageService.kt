package ru.privatenull.pncaptcha.message

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import ru.privatenull.pncaptcha.config.CaptchaConfig

class MessageService(
    private val config: CaptchaConfig,
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
) {
    /**
     * Renders MiniMessage regardless of messages.enabled.
     * Actions use this method so disabling lifecycle chat messages does not disable
     * titles, actionbars, bossbars or disconnect text configured through Actions.
     */
    fun render(lines: List<String>, placeholders: Map<String, Any?> = emptyMap()): Component {
        if (lines.isEmpty()) return Component.empty()

        val rendered = lines.map { line ->
            var text = line
            placeholders.forEach { (key, value) ->
                text = text.replace("{$key}", value?.toString().orEmpty())
            }
            miniMessage.deserialize(text)
        }

        var result = Component.empty()
        rendered.forEachIndexed { index, component ->
            if (index > 0) result = result.append(Component.newline())
            result = result.append(component)
        }
        return result
    }

    fun component(lines: List<String>, placeholders: Map<String, Any?> = emptyMap()): Component {
        if (!config.messages.enabled || lines.isEmpty()) return Component.empty()
        return render(lines, placeholders)
    }

    fun send(player: Player, lines: List<String>, placeholders: Map<String, Any?> = emptyMap()) {
        if (!config.messages.enabled || lines.isEmpty()) return
        player.sendMessage(render(lines, placeholders))
    }
}
