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
 * The virtual world contains exactly one physical pedestal block. The CAPTCHA
 * itself remains a per-client PacketEvents overlay.
 *
 * The camera sits left of the object and looks diagonally across its real Z
 * extrusion. The entire default CAPTCHA volume is intentionally kept in the
 * spawn chunk plus immediately adjacent chunks so the client has those chunks
 * before fake block updates are applied.
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

        // Only prepare nearby chunks. The default 3D CAPTCHA is deliberately
        // bounded to this area instead of relying on far chunks arriving later.
        for (chunkX in -2..1) {
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
            .setViewDistance(4)
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
        private const val PEDESTAL_X: Int = -7
        private const val PEDESTAL_Z: Int = 0

        const val SPAWN_X: Double = -6.5
        const val SPAWN_Y: Double = 65.0
        const val SPAWN_Z: Double = 0.5

        // Looks at approximately (0, 71.5, 16.5): strong oblique angle that
        // exposes the 6-block Z extrusion while keeping the face readable.
        const val SPAWN_YAW: Float = -26.0f
        const val SPAWN_PITCH: Float = -18.0f
    }
}
