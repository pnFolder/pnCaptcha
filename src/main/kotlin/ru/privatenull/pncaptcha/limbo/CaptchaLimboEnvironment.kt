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
 * The real virtual world contains exactly one pedestal block under the player.
 * CAPTCHA glyphs and decorative noise are client-only PacketEvents overlays.
 *
 * The spawn is deliberately offset to the left of the CAPTCHA centre. The
 * player therefore looks at the extruded voxel text at an oblique angle and
 * sees its real Z-depth, similar to an isometric 3D sculpture rather than a
 * flat bitmap wall.
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

        val pedestal = factory.createSimpleBlock("minecraft:deepslate_tiles")
        world.setBlock(PEDESTAL_X, 64, PEDESTAL_Z, pedestal)

        // Pre-create empty chunks that contain the distant 3D overlay. Fake
        // block changes are most reliable after the client has the chunks.
        for (chunkX in -3..3) {
            for (chunkZ in -1..4) {
                world.getChunkOrNew(chunkX, chunkZ)
            }
        }

        world.fillSkyLight(15)
        world.fillBlockLight(15)

        limbo = factory.createLimbo(world)
            .setName("pnCaptcha")
            .setGameMode(GameMode.ADVENTURE)
            .setReadTimeout(config.timeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + 5_000)
            .setViewDistance(6)
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
        private const val PEDESTAL_X: Int = -5
        private const val PEDESTAL_Z: Int = 0

        const val SPAWN_X: Double = -4.5
        const val SPAWN_Y: Double = 65.0
        const val SPAWN_Z: Double = 0.5

        // Target is approximately (10, 73, 42): ~19 degrees off-axis.
        const val SPAWN_YAW: Float = -19.0f
        const val SPAWN_PITCH: Float = -9.0f
    }
}
