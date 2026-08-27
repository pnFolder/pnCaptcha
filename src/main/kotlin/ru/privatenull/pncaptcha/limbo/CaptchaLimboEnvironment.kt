package ru.privatenull.pncaptcha.limbo

import com.velocitypowered.api.proxy.Player
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualBlock
import net.elytrium.limboapi.api.player.GameMode
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/** One tiny in-memory Limbo world per active CAPTCHA session. */
class CaptchaLimboEnvironment(
    private val factory: LimboFactory,
    private val config: CaptchaConfig,
    private val layout: CaptchaLayout = CaptchaLayout()
) : AutoCloseable {
    private val activeLimbos = ConcurrentHashMap<UUID, SessionWorld>()
    private val font: CaptchaFont.ResolvedFont = CaptchaFont.resolve(config.font)

    val gameMode: GameMode = when (config.player.gameMode.lowercase()) {
        "survival" -> GameMode.SURVIVAL
        "adventure" -> GameMode.ADVENTURE
        "spectator" -> GameMode.SPECTATOR
        else -> GameMode.CREATIVE
    }

    fun spawn(
        sessionId: UUID,
        answer: String,
        player: Player,
        handler: LimboSessionHandler
    ): ChallengeInfo {
        dispose(sessionId)

        val random = sessionRandom()
        val scene = CaptchaScene.resolve(config, random)
        val frame = layout.build(answer, config, font, scene, random)
        val bounds = CaptchaScene.chunkBounds(config, scene, font)
        val camera = scene.camera

        val world = factory.createVirtualWorld(
            Dimension.OVERWORLD,
            camera.x,
            camera.y,
            camera.z,
            camera.yaw,
            camera.pitch
        )

        if (config.limbo.pedestalEnabled) {
            val pedestal = factory.createSimpleBlock(config.limbo.pedestalBlock)
            world.setBlock(
                floor(camera.x).toInt(),
                floor(camera.y).toInt() - 1,
                floor(camera.z).toInt(),
                pedestal
            )
        }

        val blockCache = HashMap<String, VirtualBlock>()
        frame.forEach { (position, materialId) ->
            val block = blockCache.getOrPut(materialId) {
                factory.createSimpleBlock(materialId)
            }
            world.setBlock(position.x, position.y, position.z, block)
        }

        for (chunkX in bounds.minX..bounds.maxX) {
            for (chunkZ in bounds.minZ..bounds.maxZ) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(15)
        world.fillBlockLight(15)

        val viewDistance = CaptchaScene.recommendedViewDistance(config, bounds)
        val simulationDistance = minOf(config.limbo.simulationDistance, viewDistance)

        val limbo = factory.createLimbo(world)
            .setName("pnCaptcha-${sessionId.toString().take(8)}")
            .setGameMode(gameMode)
            .setReadTimeout(config.timeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 5_000)
            .setViewDistance(viewDistance)
            .setSimulationDistance(simulationDistance)
            .setReducedDebugInfo(config.limbo.reducedDebugInfo)
            .setShouldRespawn(true)
            .setShouldRejoin(true)

        val sessionWorld = SessionWorld(limbo, scene, bounds, frame.size)
        activeLimbos[sessionId] = sessionWorld

        try {
            limbo.spawnPlayer(player, handler)
        } catch (throwable: Throwable) {
            activeLimbos.remove(sessionId, sessionWorld)
            limbo.dispose()
            throw throwable
        }

        return ChallengeInfo(
            blockCount = frame.size,
            chunkBounds = bounds,
            scene = scene,
            viewDistance = viewDistance,
            simulationDistance = simulationDistance
        )
    }

    fun camera(sessionId: UUID): CaptchaScene.CameraPose? = activeLimbos[sessionId]?.scene?.camera

    fun dispose(sessionId: UUID) {
        activeLimbos.remove(sessionId)?.limbo?.dispose()
    }

    fun activeCount(): Int = activeLimbos.size

    fun resolvedFont(): CaptchaFont.ResolvedFont = font

    override fun close() {
        activeLimbos.values.forEach { it.limbo.dispose() }
        activeLimbos.clear()
    }

    private fun sessionRandom(): Random {
        if (!config.randomness.enabled) return Random(0L)
        return if (config.randomness.seedMode.equals("fixed", ignoreCase = true)) {
            Random(config.randomness.fixedSeed)
        } else {
            SecureRandom()
        }
    }

    private data class SessionWorld(
        val limbo: Limbo,
        val scene: CaptchaScene.ResolvedScene,
        val bounds: CaptchaScene.ChunkBounds,
        val blockCount: Int
    )

    data class ChallengeInfo(
        val blockCount: Int,
        val chunkBounds: CaptchaScene.ChunkBounds,
        val scene: CaptchaScene.ResolvedScene,
        val viewDistance: Int,
        val simulationDistance: Int
    )
}
