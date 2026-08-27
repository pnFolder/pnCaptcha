package ru.privatenull.pncaptcha.limbo

import com.velocitypowered.api.proxy.Player
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.player.GameMode
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig

/**
 * One shared in-memory Limbo world for every CAPTCHA session.
 *
 * There is still exactly one physical pedestal block. The angled letters are
 * PacketEvents overlays. Spawn yaw/pitch are derived from the configured scene
 * so changing CAPTCHA distance or vertical placement keeps the camera aimed at
 * the centre automatically.
 */
class CaptchaLimboEnvironment(
    private val factory: LimboFactory,
    config: CaptchaConfig
) : AutoCloseable {
    val limbo: Limbo

    val spawnX: Double = CaptchaScene.SPAWN_X
    val spawnY: Double = CaptchaScene.SPAWN_Y
    val spawnZ: Double = CaptchaScene.SPAWN_Z
    val spawnYaw: Float = CaptchaScene.spawnYaw(config)
    val spawnPitch: Float = CaptchaScene.spawnPitch(config)

    init {
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

        // Pre-create every chunk the configured volume may touch. LimboAPI's
        // own spawn-chunk radius still controls exactly when distant chunks are
        // delivered, while the renderer re-applies its overlay after delivery.
        val bounds = CaptchaScene.chunkBounds(config)
        for (chunkX in bounds.minX..bounds.maxX) {
            for (chunkZ in bounds.minZ..bounds.maxZ) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(15)
        world.fillBlockLight(15)

        limbo = factory.createLimbo(world)
            .setName("pnCaptcha")
            .setGameMode(GameMode.CREATIVE)
            .setReadTimeout(config.timeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 5_000)
            .setViewDistance(CaptchaScene.recommendedViewDistance(config))
            .setSimulationDistance(2)
            .setReducedDebugInfo(true)
            .setShouldRespawn(true)
            .setShouldRejoin(true)
    }

    fun spawn(player: Player, handler: LimboSessionHandler) {
        limbo.spawnPlayer(player, handler)
    }

    override fun close() {
        limbo.dispose()
    }
}
