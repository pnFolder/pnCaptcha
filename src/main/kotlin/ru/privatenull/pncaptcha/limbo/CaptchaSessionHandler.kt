package ru.privatenull.pncaptcha.limbo

import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.player.LimboPlayer
import ru.privatenull.pncaptcha.manager.CaptchaManager
import java.util.UUID

class CaptchaSessionHandler(
    private val manager: CaptchaManager,
    private val playerId: UUID,
    private val sessionId: UUID
) : LimboSessionHandler {

    override fun onSpawn(server: Limbo, player: LimboPlayer) {
        manager.onSpawn(playerId, sessionId, player)
    }

    override fun onChat(chat: String) {
        manager.submit(playerId, sessionId, chat)
    }

    override fun onMove(posX: Double, posY: Double, posZ: Double) {
        manager.onMove(playerId, sessionId, posX, posY, posZ)
    }

    override fun onMove(posX: Double, posY: Double, posZ: Double, yaw: Float, pitch: Float) {
        manager.onMove(playerId, sessionId, posX, posY, posZ, yaw, pitch)
    }

    override fun onDisconnect() {
        manager.onDisconnect(playerId, sessionId)
    }
}
