package ru.privatenull.pncaptcha.limbo

import com.velocitypowered.api.proxy.Player
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualBlock
import net.elytrium.limboapi.api.player.GameMode
import ru.privatenull.pncaptcha.captcha.BlockPos
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max

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

    fun spawn(sessionId: UUID, answer: String, player: Player, handler: LimboSessionHandler): ChallengeInfo {
        dispose(sessionId)

        val random = sessionRandom()
        val scene = CaptchaScene.resolve(config, random)
        val frame = layout.build(answer, config, font, scene, random)
        val camera = scene.camera

        val world = factory.createVirtualWorld(
            Dimension.OVERWORLD,
            camera.x,
            camera.y,
            camera.z,
            camera.yaw,
            camera.pitch
        )

        val blockCache = HashMap<String, VirtualBlock>()
        frame.forEach { (position, materialId) ->
            val block = blockCache.getOrPut(materialId) { factory.createSimpleBlock(materialId) }
            world.setBlock(position.x, position.y, position.z, block)
        }

        val pedestalPositions = placePedestal(world, blockCache, camera)
        val bounds = actualChunkBounds(frame.keys, pedestalPositions, camera)
        val paddedBounds = CaptchaScene.ChunkBounds(
            minX = bounds.minX - config.limbo.precreatePaddingChunks,
            maxX = bounds.maxX + config.limbo.precreatePaddingChunks,
            minZ = bounds.minZ - config.limbo.precreatePaddingChunks,
            maxZ = bounds.maxZ + config.limbo.precreatePaddingChunks
        )

        for (chunkX in paddedBounds.minX..paddedBounds.maxX) {
            for (chunkZ in paddedBounds.minZ..paddedBounds.maxZ) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(config.limbo.skyLightLevel)
        world.fillBlockLight(config.limbo.blockLightLevel)

        val autoView = if (config.limbo.autoExpandViewDistance) {
            CaptchaScene.recommendedViewDistance(config, paddedBounds)
        } else {
            config.limbo.viewDistance
        }
        val viewDistance = max(config.limbo.viewDistance, autoView).coerceAtMost(config.limbo.maxAutoViewDistance)
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

        val sessionWorld = SessionWorld(limbo, scene, paddedBounds, frame.size)
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
            chunkBounds = paddedBounds,
            scene = scene,
            viewDistance = viewDistance,
            simulationDistance = simulationDistance
        )
    }

    private fun placePedestal(
        world: net.elytrium.limboapi.api.chunk.VirtualWorld,
        blockCache: MutableMap<String, VirtualBlock>,
        camera: CaptchaScene.CameraPose
    ): Set<BlockPos> {
        val pedestal = config.limbo.pedestal
        if (!pedestal.enabled) return emptySet()

        val block = blockCache.getOrPut(pedestal.block) { factory.createSimpleBlock(pedestal.block) }
        val baseX = floor(camera.x).toInt()
        val baseY = floor(camera.y).toInt() + pedestal.yOffset
        val baseZ = floor(camera.z).toInt()
        val startX = baseX - (pedestal.sizeX - 1) / 2
        val startZ = baseZ - (pedestal.sizeZ - 1) / 2
        val positions = LinkedHashSet<BlockPos>()

        for (dx in 0 until pedestal.sizeX) {
            for (dz in 0 until pedestal.sizeZ) {
                val pos = BlockPos(startX + dx, baseY, startZ + dz)
                world.setBlock(pos.x, pos.y, pos.z, block)
                positions += pos
            }
        }
        return positions
    }

    private fun actualChunkBounds(
        frame: Set<BlockPos>,
        pedestal: Set<BlockPos>,
        camera: CaptchaScene.CameraPose
    ): CaptchaScene.ChunkBounds {
        val all = frame + pedestal
        if (all.isEmpty()) {
            val cx = floor(camera.x).toInt() shr 4
            val cz = floor(camera.z).toInt() shr 4
            return CaptchaScene.ChunkBounds(cx, cx, cz, cz)
        }
        return CaptchaScene.ChunkBounds(
            minX = all.minOf { it.x shr 4 },
            maxX = all.maxOf { it.x shr 4 },
            minZ = all.minOf { it.z shr 4 },
            maxZ = all.maxOf { it.z shr 4 }
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
