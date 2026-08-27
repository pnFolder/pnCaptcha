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
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig
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
    fun `angled layout physically recedes across z`() {
        val layout = CaptchaLayout(random = Random(42))
        val config = CaptchaConfig(
            noiseBlocks = 0,
            captchaDistanceBlocks = 30.0,
            captchaAngleDegrees = 28.0,
            glyphScaleX = 2,
            glyphScaleY = 2,
            glyphDepth = 3,
            glyphGapBlocks = 2,
            glyphJitterYBlocks = 0,
            glyphJitterDepthBlocks = 0,
            glyphMaterials = listOf("minecraft:gray_concrete"),
            glyphSideMaterials = listOf("minecraft:deepslate_tiles")
        )

        val frame = layout.build("A2B3C", config)
        val front = frame.filterValues { it == "minecraft:gray_concrete" }.keys

        assertTrue(frame.isNotEmpty())
        assertEquals(frame.keys.size, frame.size)
        assertTrue(front.map { it.z }.distinct().size > 8)
        assertTrue(front.minOf { it.z } < 25)
        assertTrue(front.maxOf { it.z } > 36)

        // The configured front centre is about 30 blocks away and the whole
        // default scene remains within a practical Limbo view distance.
        val bounds = CaptchaScene.chunkBounds(config)
        assertTrue(bounds.minX >= -3)
        assertTrue(bounds.maxX <= 3)
        assertTrue(bounds.minZ >= 0)
        assertTrue(bounds.maxZ <= 3)
    }

    @Test
    fun `zero angle keeps the bright face on one z plane`() {
        val layout = CaptchaLayout(random = Random(7))
        val config = CaptchaConfig(
            noiseBlocks = 0,
            captchaAngleDegrees = 0.0,
            glyphScaleX = 2,
            glyphScaleY = 2,
            glyphDepth = 3,
            glyphJitterYBlocks = 0,
            glyphJitterDepthBlocks = 0,
            glyphMaterials = listOf("minecraft:gray_concrete"),
            glyphSideMaterials = listOf("minecraft:deepslate_tiles")
        )

        val front = layout.build("AB3", config)
            .filterValues { it == "minecraft:gray_concrete" }
            .keys

        assertEquals(1, front.map { it.z }.distinct().size)
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
