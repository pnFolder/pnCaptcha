package ru.privatenull.pncaptcha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Random
import java.util.UUID

class CoreServicesTest {

    @Test
    fun `generator only emits supported unambiguous glyphs`() {
        val generator = CaptchaGenerator()

        repeat(100) {
            val code = generator.generate(5)
            assertEquals(5, code.length)
            assertTrue(code.all(CaptchaFont::supports))
            assertFalse(code.any { it in "01ILO" })
        }
    }

    @Test
    fun `font has fixed five by seven glyphs`() {
        CaptchaGenerator.DEFAULT_ALPHABET.forEach { char ->
            val pattern = CaptchaFont.pattern(char)
            assertEquals(CaptchaFont.HEIGHT, pattern.size)
            assertTrue(pattern.all { it.length == CaptchaFont.WIDTH })
        }
    }

    @Test
    fun `layout creates deterministic non-overlapping frame`() {
        val layout = CaptchaLayout(random = Random(42))
        val frame = layout.build(
            answer = "A2B3C",
            glyphMaterials = listOf("minecraft:white_concrete"),
            noiseMaterial = "minecraft:gray_stained_glass",
            noiseCount = 10
        )

        assertTrue(frame.isNotEmpty())
        assertEquals(frame.keys.size, frame.size)
        assertTrue(frame.keys.all { it.z == CaptchaLayout.DEFAULT_PLANE_Z })
    }

    @Test
    fun `session manager replaces stale challenge for the same player`() {
        val manager = CaptchaSessionManager()
        val playerId = UUID.randomUUID()
        val first = manager.create(CaptchaSession(playerId = playerId, answer = "AAAAA"))
        val second = manager.create(CaptchaSession(playerId = playerId, answer = "BBBBB"))

        assertNotEquals(first.id, second.id)
        assertEquals(second, manager[playerId])
        assertEquals(null, manager.getBySessionId(playerId, first.id))
        assertEquals(second, manager.getBySessionId(playerId, second.id))
    }

    @Test
    fun `verification cache expires uuid and ip pair`() {
        val clock = MutableClock(Instant.parse("2026-08-27T18:00:00Z"))
        val cache = VerificationCache(Duration.ofMinutes(10), clock)
        val uuid = UUID.randomUUID()

        cache.markVerified(uuid, "127.0.0.1")
        assertTrue(cache.isVerified(uuid, "127.0.0.1"))
        assertFalse(cache.isVerified(uuid, "127.0.0.2"))

        clock.now = clock.now.plus(Duration.ofMinutes(11))
        assertFalse(cache.isVerified(uuid, "127.0.0.1"))
    }

    private class MutableClock(
        var now: Instant
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
