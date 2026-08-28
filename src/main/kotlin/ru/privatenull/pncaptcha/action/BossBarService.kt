package ru.privatenull.pncaptcha.action

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import net.kyori.adventure.bossbar.BossBar
import ru.privatenull.pncaptcha.config.ActionDefinition
import ru.privatenull.pncaptcha.message.MessageService
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

class BossBarService(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val messages: MessageService
) {
    private val bars = ConcurrentHashMap<BarKey, ActiveBar>()

    fun execute(action: ActionDefinition, player: Player, placeholders: Map<String, Any?>) {
        val id = render(action.bossBarId, placeholders).ifBlank { "captcha" }
        val key = BarKey(player.uniqueId, id)
        val operation = action.bossBarOperation.lowercase()

        when (operation) {
            "show" -> showOrUpdate(key, player, action, placeholders, show = true)
            "update" -> showOrUpdate(key, player, action, placeholders, show = false)
            "animate" -> animate(key, player, action, placeholders)
            "set-progress" -> setProgress(key, player, action, placeholders)
            "add-progress" -> addProgress(key, player, action, placeholders)
            "pause" -> pause(key)
            "resume" -> resume(key)
            "hide" -> hide(key)
            "remove" -> remove(key)
        }
    }

    fun cleanup(playerId: UUID) {
        bars.keys.filter { it.playerId == playerId }.forEach(::remove)
    }

    fun shutdown() {
        bars.keys.toList().forEach(::remove)
    }

    private fun showOrUpdate(
        key: BarKey,
        player: Player,
        action: ActionDefinition,
        placeholders: Map<String, Any?>,
        show: Boolean
    ) {
        val state = getOrCreate(key, player, action, placeholders)
        synchronized(state) {
            applyAppearance(state, action, placeholders)
            action.bossBarProgress?.let { state.bar.progress(clamp(it)) }
            if (show && !state.visible) {
                player.showBossBar(state.bar)
                state.visible = true
            }
        }
    }

    private fun animate(
        key: BarKey,
        player: Player,
        action: ActionDefinition,
        placeholders: Map<String, Any?>
    ) {
        val state = getOrCreate(key, player, action, placeholders)
        synchronized(state) {
            state.task?.cancel()
            state.task = null
            applyAppearance(state, action, placeholders)

            val start = clamp(action.bossBarStartProgress ?: action.bossBarProgress ?: state.bar.progress().toDouble())
            val end = clamp(action.bossBarEndProgress ?: 0.0)
            val duration = resolveDuration(action, placeholders)
            val interval = action.bossBarUpdateIntervalMillis.coerceIn(50L, 5_000L)

            state.bar.progress(start)
            if (!state.visible) {
                player.showBossBar(state.bar)
                state.visible = true
            }

            val animation = Animation(
                startedAtMillis = System.currentTimeMillis(),
                durationMillis = duration,
                startProgress = start,
                endProgress = end,
                adjustment = 0f,
                pausedAtMillis = null,
                textTemplate = action.text,
                basePlaceholders = placeholders,
                removeOnFinish = action.bossBarRemoveOnFinish,
                hideOnFinish = action.bossBarHideOnFinish
            )
            state.animation = animation

            if (duration <= 0L) {
                state.bar.progress(end)
                updateAnimatedName(state, animation, 1.0, end)
                finishAnimation(key, state, animation)
                return
            }

            state.task = proxy.scheduler
                .buildTask(plugin, Runnable { tick(key, state) })
                .repeat(Duration.ofMillis(interval))
                .schedule()
        }
    }

    private fun setProgress(
        key: BarKey,
        player: Player,
        action: ActionDefinition,
        placeholders: Map<String, Any?>
    ) {
        val state = getOrCreate(key, player, action, placeholders)
        val target = clamp(action.bossBarProgress ?: 1.0)
        synchronized(state) {
            applyAppearance(state, action, placeholders)
            val animation = state.animation
            if (animation != null) {
                val current = currentProgress(animation, System.currentTimeMillis())
                animation.adjustment += target - current
            }
            state.bar.progress(target)
            if (!state.visible) {
                player.showBossBar(state.bar)
                state.visible = true
            }
        }
    }

    private fun addProgress(
        key: BarKey,
        player: Player,
        action: ActionDefinition,
        placeholders: Map<String, Any?>
    ) {
        val state = getOrCreate(key, player, action, placeholders)
        synchronized(state) {
            applyAppearance(state, action, placeholders)
            val delta = action.bossBarProgressDelta.toFloat()
            state.animation?.let { it.adjustment += delta }
            state.bar.progress(clamp(state.bar.progress() + delta))
            if (!state.visible) {
                player.showBossBar(state.bar)
                state.visible = true
            }
        }
    }

    private fun pause(key: BarKey) {
        val state = bars[key] ?: return
        synchronized(state) {
            val animation = state.animation ?: return
            if (animation.pausedAtMillis == null) {
                animation.pausedAtMillis = System.currentTimeMillis()
            }
        }
    }

    private fun resume(key: BarKey) {
        val state = bars[key] ?: return
        synchronized(state) {
            val animation = state.animation ?: return
            val pausedAt = animation.pausedAtMillis ?: return
            animation.startedAtMillis += System.currentTimeMillis() - pausedAt
            animation.pausedAtMillis = null
        }
    }

    private fun hide(key: BarKey) {
        val state = bars[key] ?: return
        synchronized(state) {
            if (state.visible) {
                state.player.hideBossBar(state.bar)
                state.visible = false
            }
        }
    }

    private fun remove(key: BarKey) {
        val state = bars.remove(key) ?: return
        synchronized(state) {
            state.task?.cancel()
            state.task = null
            state.animation = null
            if (state.visible) {
                state.player.hideBossBar(state.bar)
                state.visible = false
            }
        }
    }

    private fun tick(key: BarKey, state: ActiveBar) {
        synchronized(state) {
            if (bars[key] !== state) return
            val animation = state.animation ?: return
            if (animation.pausedAtMillis != null) return

            val now = System.currentTimeMillis()
            val elapsed = (now - animation.startedAtMillis).coerceAtLeast(0L)
            val fraction = (elapsed.toDouble() / animation.durationMillis.toDouble()).coerceIn(0.0, 1.0)
            val progress = currentProgress(animation, now)

            state.bar.progress(progress)
            updateAnimatedName(state, animation, fraction, progress)

            if (fraction >= 1.0) {
                finishAnimation(key, state, animation)
            }
        }
    }

    private fun finishAnimation(key: BarKey, state: ActiveBar, animation: Animation) {
        state.task?.cancel()
        state.task = null
        state.animation = null

        when {
            animation.removeOnFinish -> remove(key)
            animation.hideOnFinish -> {
                if (state.visible) {
                    state.player.hideBossBar(state.bar)
                    state.visible = false
                }
            }
        }
    }

    private fun currentProgress(animation: Animation, now: Long): Float {
        val effectiveNow = animation.pausedAtMillis ?: now
        val elapsed = (effectiveNow - animation.startedAtMillis).coerceAtLeast(0L)
        val fraction = if (animation.durationMillis <= 0L) 1.0
        else (elapsed.toDouble() / animation.durationMillis.toDouble()).coerceIn(0.0, 1.0)
        val base = animation.startProgress + ((animation.endProgress - animation.startProgress) * fraction.toFloat())
        return clamp(base + animation.adjustment)
    }

    private fun updateAnimatedName(
        state: ActiveBar,
        animation: Animation,
        fraction: Double,
        progress: Float
    ) {
        if (animation.textTemplate.isBlank()) return
        val remainingMillis = ((1.0 - fraction) * animation.durationMillis).toLong().coerceAtLeast(0L)
        val dynamic = animation.basePlaceholders + mapOf(
            "bossbar_progress" to "%.3f".format(progress),
            "bossbar_percent" to (progress * 100f).toInt().coerceIn(0, 100),
            "bossbar_seconds" to ceil(remainingMillis / 1000.0).toLong(),
            "bossbar_millis" to remainingMillis
        )
        state.bar.name(messages.render(listOf(animation.textTemplate), dynamic))
    }

    private fun getOrCreate(
        key: BarKey,
        player: Player,
        action: ActionDefinition,
        placeholders: Map<String, Any?>
    ): ActiveBar = bars.computeIfAbsent(key) {
        val text = action.text.ifBlank { "<aqua><bold>pnCaptcha</bold></aqua>" }
        val bar = BossBar.bossBar(
            messages.render(listOf(text), placeholders),
            clamp(action.bossBarProgress ?: action.bossBarStartProgress ?: 1.0),
            parseColor(action.bossBarColor),
            parseOverlay(action.bossBarOverlay)
        )
        player.showBossBar(bar)
        ActiveBar(player = player, bar = bar, visible = true)
    }

    private fun applyAppearance(
        state: ActiveBar,
        action: ActionDefinition,
        placeholders: Map<String, Any?>
    ) {
        if (action.text.isNotBlank()) {
            state.bar.name(messages.render(listOf(action.text), placeholders))
        }
        state.bar.color(parseColor(action.bossBarColor))
        state.bar.overlay(parseOverlay(action.bossBarOverlay))
    }

    private fun resolveDuration(action: ActionDefinition, placeholders: Map<String, Any?>): Long {
        if (action.bossBarDurationMillis > 0L) return action.bossBarDurationMillis
        val timeoutSeconds = (placeholders["timeout"] as? Number)?.toLong()
            ?: placeholders["timeout"]?.toString()?.toLongOrNull()
        return timeoutSeconds?.times(1000L) ?: 30_000L
    }

    private fun parseColor(raw: String): BossBar.Color = runCatching {
        BossBar.Color.valueOf(raw.trim().uppercase().replace('-', '_'))
    }.getOrDefault(BossBar.Color.BLUE)

    private fun parseOverlay(raw: String): BossBar.Overlay = runCatching {
        BossBar.Overlay.valueOf(raw.trim().uppercase().replace('-', '_'))
    }.getOrDefault(BossBar.Overlay.PROGRESS)

    private fun render(text: String, placeholders: Map<String, Any?>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value?.toString().orEmpty())
        }
        return result
    }

    private fun clamp(value: Double): Float = value.coerceIn(0.0, 1.0).toFloat()
    private fun clamp(value: Float): Float = value.coerceIn(0f, 1f)

    private data class BarKey(val playerId: UUID, val id: String)

    private data class ActiveBar(
        val player: Player,
        val bar: BossBar,
        var visible: Boolean,
        var task: ScheduledTask? = null,
        var animation: Animation? = null
    )

    private data class Animation(
        var startedAtMillis: Long,
        val durationMillis: Long,
        val startProgress: Float,
        val endProgress: Float,
        var adjustment: Float,
        var pausedAtMillis: Long?,
        val textTemplate: String,
        val basePlaceholders: Map<String, Any?>,
        val removeOnFinish: Boolean,
        val hideOnFinish: Boolean
    )
}
