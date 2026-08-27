package ru.privatenull.pncaptcha.render

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange
import com.velocitypowered.api.proxy.Player
import ru.privatenull.pncaptcha.captcha.BlockPos
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends fake block updates directly to one client.
 *
 * The underlying Limbo world stays unchanged, so each player can see a different
 * CAPTCHA while every connection shares the same in-memory Limbo instance.
 */
class PacketCaptchaRenderer(
    private val config: CaptchaConfig,
    private val layout: CaptchaLayout = CaptchaLayout()
) {
    private val renderedPositions = ConcurrentHashMap<UUID, Set<BlockPos>>()

    fun render(player: Player, answer: String): Boolean {
        val playerManager = PacketEvents.getAPI().getPlayerManager()
        val user = playerManager.getUser(player) ?: return false
        val clientVersion = playerManager.getClientVersion(player)

        val previous = renderedPositions[player.uniqueId].orEmpty()
        val next = layout.build(
            answer = answer,
            glyphMaterials = config.glyphMaterials,
            sideMaterial = config.glyphSideMaterial,
            noiseMaterial = config.noiseMaterial,
            noiseCount = config.noiseBlocks,
            scale = config.glyphScale,
            depth = config.glyphDepth
        )

        val air = WrappedBlockState.getByString(clientVersion, "minecraft:air")
        previous.asSequence()
            .filterNot(next::containsKey)
            .forEach { position ->
                playerManager.sendPacket(
                    player,
                    WrapperPlayServerBlockChange(position.toVector(), air)
                )
            }

        next.forEach { (position, material) ->
            val state = WrappedBlockState.getByString(clientVersion, material)
            playerManager.sendPacket(
                player,
                WrapperPlayServerBlockChange(position.toVector(), state)
            )
        }

        renderedPositions[player.uniqueId] = next.keys.toSet()
        user.flushPackets()
        return true
    }

    fun clear(player: Player) {
        val previous = renderedPositions.remove(player.uniqueId) ?: return
        val playerManager = PacketEvents.getAPI().getPlayerManager()
        if (playerManager.getUser(player) == null) return
        val clientVersion = playerManager.getClientVersion(player)
        val air = WrappedBlockState.getByString(clientVersion, "minecraft:air")

        previous.forEach { position ->
            playerManager.sendPacket(
                player,
                WrapperPlayServerBlockChange(position.toVector(), air)
            )
        }
        playerManager.getUser(player)?.flushPackets()
    }

    fun forget(playerId: UUID) {
        renderedPositions.remove(playerId)
    }

    private fun BlockPos.toVector(): Vector3i = Vector3i(x, y, z)
}
