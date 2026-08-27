package ru.privatenull.pncaptcha.session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CaptchaSessionManager {
    private val sessions = ConcurrentHashMap<UUID, CaptchaSession>()

    fun create(session: CaptchaSession): CaptchaSession {
        sessions[session.playerId] = session
        return session
    }

    operator fun get(playerId: UUID): CaptchaSession? = sessions[playerId]

    fun remove(playerId: UUID): CaptchaSession? = sessions.remove(playerId)

    fun contains(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun clear() = sessions.clear()

    fun size(): Int = sessions.size
}
