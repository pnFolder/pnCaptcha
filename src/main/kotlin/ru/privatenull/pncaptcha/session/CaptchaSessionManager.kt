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

    fun getBySessionId(playerId: UUID, sessionId: UUID): CaptchaSession? =
        sessions[playerId]?.takeIf { it.id == sessionId }

    fun remove(playerId: UUID): CaptchaSession? = sessions.remove(playerId)

    fun remove(playerId: UUID, sessionId: UUID): CaptchaSession? {
        var removed: CaptchaSession? = null
        sessions.computeIfPresent(playerId) { _, current ->
            if (current.id == sessionId) {
                removed = current
                null
            } else {
                current
            }
        }
        return removed
    }

    fun contains(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun clear() = sessions.clear()

    fun size(): Int = sessions.size
}
