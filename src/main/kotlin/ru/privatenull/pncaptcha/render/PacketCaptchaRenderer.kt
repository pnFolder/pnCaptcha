package ru.privatenull.pncaptcha.render

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange
import com.velocitypowered.api.proxy.Player
import ru.privatenull.pncaptcha.captcha.BlockPos
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends client-only fake blocks. Changes are grouped by 16x16x16 chunk section
 * and sent through MULTI_BLOCK_CHANGE instead of thousands of individual block
 * packets. This is both cheaper and much less likely to lose a large frame.
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
            sideMaterials = config.glyphSideMaterials,
            noiseMaterial = config.noiseMaterial,
            noiseCount = config.noiseBlocks,
            scaleX = config.glyphScaleX,
            scaleY = config.glyphScaleY,
            depth = config.glyphDepth
        )

        val air = WrappedBlockState.getByString(clientVersion, "minecraft:air")
        val changes = LinkedHashMap<BlockPos, WrappedBlockState>()

        previous.asSequence()
            .filterNot(next::containsKey)
            .forEach { changes[it] = air }

        next.forEach { (position, material) ->
            changes[position] = WrappedBlockState.getByString(clientVersion, material)
        }

        sendBatched(player, changes)
        renderedPositions[player.uniqueId] = next.keys.toSet()
        user.flushPackets()
        return true
    }

    fun clear(player: Player) {
        val previous = renderedPositions.remove(player.uniqueId) ?: return
        val playerManager = PacketEvents.getAPI().getPlayerManager()
        val user = playerManager.getUser(player) ?: return
        val clientVersion = playerManager.getClientVersion(player)
        val air = WrappedBlockState.getByString(clientVersion, "minecraft:air")

        val changes = previous.associateWith { air }
        sendBatched(player, changes)
        user.flushPackets()
    }

    fun forget(playerId: UUID) {
        renderedPositions.remove(playerId)
    }

    private fun sendBatched(player: Player, changes: Map<BlockPos, WrappedBlockState>) {
        if (changes.isEmpty()) return

        val playerManager = PacketEvents.getAPI().getPlayerManager()
        changes.entries
            .groupBy { SectionPos.from(it.key) }
            .forEach { (section, entries) ->
                val encoded = entries.map { (position, state) ->
                    WrapperPlayServerMultiBlockChange.EncodedBlock(
                        state,
                        position.x,
                        position.y,
                        position.z
                    )
                }.toTypedArray()

                playerManager.sendPacket(
                    player,
                    WrapperPlayServerMultiBlockChange(
                        Vector3i(section.x, section.y, section.z),
                        null,
                        encoded
                    )
                )
            }
    }

    private data class SectionPos(val x: Int, val y: Int, val z: Int) {
        companion object {
            fun from(position: BlockPos): SectionPos = SectionPos(
                x = position.x shr 4,
                y = position.y shr 4,
                z = position.z shr 4
            )
        }
    }
}
