package ru.privatenull.pncaptcha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.util.UUID

class CoreServicesTest {
    @Test
    fun `generator produces requested length from safe alphabet`() {
        val generator = CaptchaGenerator()
        val value = generator.generate(12)

        assertEquals(12, value.length)
        assertTrue(value.all { it in CaptchaGenerator.DEFAULT_ALPHABET })
    }

    @Test
    fun `session matches trimmed answer ignoring case`() {
        val session = CaptchaSession(UUID.randomUUID(), "K7M4P")

        assertTrue(session.matches("  k7m4p  "))
        assertFalse(session.matches("K7M4X"))
    }

    @Test
    fun `session manager stores and removes sessions`() {
        val manager = CaptchaSessionManager()
        val session = CaptchaSession(UUID.randomUUID(), "AB234")

        manager.create(session)
        assertTrue(manager.contains(session.playerId))
        assertEquals(session, manager[session.playerId])

        manager.remove(session.playerId)
        assertFalse(manager.contains(session.playerId))
    }
}
