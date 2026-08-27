package ru.privatenull.pncaptcha.limbo

import com.velocitypowered.api.proxy.Player
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualBlock
import net.elytrium.limboapi.api.player.GameMode
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates one tiny in-memory Limbo world per active CAPTCHA session.
 *
 * This replaces the old PacketEvents fake-block overlay. Every glyph voxel is
 * written into LimboAPI's VirtualWorld before the player joins, so the normal
 * chunk packets contain the CAPTCHA themselves. Late chunk delivery can no
 * longer erase the challenge and unloaded-chunk block-change packets are no
 * longer part of the design.
 *
 * These are not Minecraft world folders and nothing is saved to disk. A world
 * exists only for the lifetime of one verification session and is disposed as
 * soon as that session ends.
 */
class CaptchaLimboEnvironment(
    private val factory: LimboFactory,
    private val config: CaptchaConfig,
    private val layout: CaptchaLayout = CaptchaLayout()
) : AutoCloseable {
    private val activeLimbos = ConcurrentHashMap<UUID, Limbo>()

    val spawnX: Double = CaptchaScene.SPAWN_X
    val spawnY: Double = CaptchaScene.SPAWN_Y
    val spawnZ: Double = CaptchaScene.SPAWN_Z
    val spawnYaw: Float = CaptchaScene.spawnYaw(config)
    val spawnPitch: Float = CaptchaScene.spawnPitch(config)
    val gameMode: GameMode = if (config.creativeMode) GameMode.CREATIVE else GameMode.ADVENTURE

    fun spawn(
        sessionId: UUID,
        answer: String,
        player: Player,
        handler: LimboSessionHandler
    ): ChallengeInfo {
        dispose(sessionId)

        val world = factory.createVirtualWorld(
            Dimension.OVERWORLD,
            spawnX,
            spawnY,
            spawnZ,
            spawnYaw,
            spawnPitch
        )

        val pedestal = factory.createSimpleBlock("minecraft:deepslate_tiles")
        world.setBlock(
            CaptchaScene.PEDESTAL_X,
            CaptchaScene.PEDESTAL_Y,
            CaptchaScene.PEDESTAL_Z,
            pedestal
        )

        val frame = layout.build(answer, config)
        val blockCache = HashMap<String, VirtualBlock>()
        frame.forEach { (position, materialId) ->
            val block = blockCache.getOrPut(materialId) {
                factory.createSimpleBlock(materialId)
            }
            world.setBlock(position.x, position.y, position.z, block)
        }

        // Ensure every chunk exists before Limbo prepares its initial and
        // delayed chunk packets. Because the blocks live in VirtualWorld, a
        // chunk arriving later still contains the correct CAPTCHA blocks.
        val bounds = CaptchaScene.chunkBounds(config)
        for (chunkX in bounds.minX..bounds.maxX) {
            for (chunkZ in bounds.minZ..bounds.maxZ) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(15)
        world.fillBlockLight(15)

        val limbo = factory.createLimbo(world)
            .setName("pnCaptcha-${sessionId.toString().take(8)}")
            .setGameMode(gameMode)
            .setReadTimeout(config.timeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 5_000)
            .setViewDistance(config.limboViewDistance)
            .setSimulationDistance(config.limboSimulationDistance)
            .setReducedDebugInfo(false)
            .setShouldRespawn(true)
            .setShouldRejoin(true)

        activeLimbos[sessionId] = limbo

        try {
            limbo.spawnPlayer(player, handler)
        } catch (throwable: Throwable) {
            activeLimbos.remove(sessionId, limbo)
            limbo.dispose()
            throw throwable
        }

        return ChallengeInfo(
            blockCount = frame.size,
            chunkBounds = bounds
        )
    }

    fun dispose(sessionId: UUID) {
        activeLimbos.remove(sessionId)?.dispose()
    }

    fun activeCount(): Int = activeLimbos.size

    override fun close() {
        activeLimbos.values.forEach(Limbo::dispose)
        activeLimbos.clear()
    }

    data class ChallengeInfo(
        val blockCount: Int,
        val chunkBounds: CaptchaScene.ChunkBounds
    )
}
