package ru.privatenull.pncaptcha.limbo

import com.velocitypowered.api.proxy.Player
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.player.GameMode
import ru.privatenull.pncaptcha.config.CaptchaConfig

/**
 * One shared in-memory Limbo world for every CAPTCHA session.
 *
 * Only the floor and background wall exist in the virtual world. CAPTCHA glyphs
 * are sent as per-player PacketEvents block updates and are never written here.
 */
class CaptchaLimboEnvironment(
    private val factory: LimboFactory,
    config: CaptchaConfig
) : AutoCloseable {
    val limbo: Limbo

    init {
        val world = factory.createVirtualWorld(
            Dimension.OVERWORLD,
            SPAWN_X,
            SPAWN_Y,
            SPAWN_Z,
            SPAWN_YAW,
            SPAWN_PITCH
        )

        val floor = factory.createSimpleBlock("minecraft:deepslate_tiles")
        val wall = factory.createSimpleBlock("minecraft:black_concrete")

        for (x in -32..32) {
            for (z in -4..15) {
                world.setBlock(x, 64, z, floor)
            }
        }

        for (x in -30..30) {
            for (y in 65..77) {
                world.setBlock(x, y, 14, wall)
            }
        }

        // Ensure the chunks around the visual area exist even though the glyph plane itself is air.
        for (chunkX in -2..2) {
            for (chunkZ in -1..1) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(15)
        world.fillBlockLight(15)

        limbo = factory.createLimbo(world)
            .setName("pnCaptcha")
            .setGameMode(GameMode.ADVENTURE)
            .setReadTimeout(config.timeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 5_000)
            .setViewDistance(3)
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

    companion object {
        const val SPAWN_X: Double = 0.5
        const val SPAWN_Y: Double = 65.0
        const val SPAWN_Z: Double = 0.5
        const val SPAWN_YAW: Float = 0.0f
        const val SPAWN_PITCH: Float = -10.0f
    }
}
