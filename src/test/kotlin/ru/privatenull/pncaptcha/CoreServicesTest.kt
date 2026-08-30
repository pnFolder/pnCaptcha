package ru.privatenull.pncaptcha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pncaptcha.cache.VerificationCache
import ru.privatenull.pncaptcha.captcha.CaptchaFont
import ru.privatenull.pncaptcha.captcha.CaptchaGenerator
import ru.privatenull.pncaptcha.captcha.CaptchaLayout
import ru.privatenull.pncaptcha.captcha.CaptchaScene
import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.config.CaptchaConfigLoader
import ru.privatenull.pncaptcha.config.FillConfig
import ru.privatenull.pncaptcha.config.FontConfig
import ru.privatenull.pncaptcha.config.GeometryConfig
import ru.privatenull.pncaptcha.config.NoiseConfig
import ru.privatenull.pncaptcha.config.RandomnessConfig
import ru.privatenull.pncaptcha.config.SceneConfig
import ru.privatenull.pncaptcha.session.CaptchaSession
import ru.privatenull.pncaptcha.session.CaptchaSessionManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Random
import java.util.UUID

class CoreServicesTest {

    @Test
    fun `generator emits configured font alphabet`() {
        val config = CaptchaConfig()
        val font = CaptchaFont.resolve(config.font)
        val generator = CaptchaGenerator(font.alphabet)

        repeat(100) {
            val code = generator.generate(5)
            assertEquals(5, code.length)
            assertTrue(code.all { it in font.alphabet })
            assertFalse(code.any { it in "01ILO" })
        }
    }

    @Test
    fun `ornate font exposes detailed nine by twelve glyphs`() {
        val resolved = CaptchaFont.resolve(FontConfig(preset = "ornate-9x12"))
        assertEquals(9, resolved.width)
        assertEquals(12, resolved.height)
        assertTrue(resolved.pattern('B').sumOf { row -> row.count { it == '1' } } > 20)
        assertTrue(resolved.pattern('9').sumOf { row -> row.count { it == '1' } } > 20)
    }

    @Test
    fun `custom font can replace built in font`() {
        val custom = FontConfig(
            preset = "custom",
            alphabet = "AB",
            customGlyphs = mapOf(
                'A' to listOf("010", "101", "111", "101", "101"),
                'B' to listOf("110", "101", "110", "101", "110")
            )
        )
        val resolved = CaptchaFont.resolve(custom)
        assertEquals(3, resolved.width)
        assertEquals(5, resolved.height)
        assertEquals("AB", resolved.alphabet)
    }

    @Test
    fun `rotated layout has real depth pitch and roll`() {
        val config = CaptchaConfig(
            scene = SceneConfig(
                distanceBlocks = 30.0,
                rotationYawDegrees = 28.0,
                rotationPitchDegrees = 8.0,
                rotationRollDegrees = 5.0
            ),
            geometry = GeometryConfig(fill = FillConfig(mode = "solid")),
            randomness = RandomnessConfig(enabled = false),
            noise = NoiseConfig(enabled = false)
        )
        val random = Random(42)
        val font = CaptchaFont.resolve(config.font)
        val scene = CaptchaScene.resolve(config, random)
        val frame = CaptchaLayout().build("A2B3C", config, font, scene, random)

        assertTrue(frame.isNotEmpty())
        assertEquals(frame.keys.size, frame.size)
        assertTrue(frame.keys.map { it.x }.distinct().size > 10)
        assertTrue(frame.keys.map { it.y }.distinct().size > 10)
        assertTrue(frame.keys.map { it.z }.distinct().size > 3)

        val bounds = CaptchaScene.chunkBounds(config, scene, font)
        assertTrue(bounds.maxZ >= bounds.minZ)
        assertTrue(CaptchaScene.recommendedViewDistance(config, bounds) >= config.limbo.viewDistance)
    }

    @Test
    fun `porous fill removes voxels while preserving readable structure`() {
        val base = CaptchaConfig(
            geometry = GeometryConfig(
                depthBlocks = 4,
                fill = FillConfig(mode = "solid")
            ),
            randomness = RandomnessConfig(enabled = false),
            noise = NoiseConfig(enabled = false)
        )
        val porous = base.copy(
            geometry = base.geometry.copy(
                fill = FillConfig(
                    mode = "porous",
                    density = 0.65,
                    preserveConnectivity = true,
                    protectEndpoints = true,
                    outlinePreservePercent = 25.0,
                    minRetainedPixels = 10
                )
            )
        )
        val font = CaptchaFont.resolve(base.font)
        val scene = CaptchaScene.resolve(base, Random(7))
        val solidFrame = CaptchaLayout().build("B39", base, font, scene, Random(7))
        val porousFrame = CaptchaLayout().build("B39", porous, font, scene, Random(7))

        assertTrue(porousFrame.isNotEmpty())
        assertTrue(porousFrame.size < solidFrame.size)
        assertTrue(porousFrame.size > solidFrame.size / 3)
    }

    @Test
    fun `default yaml includes documented mosaic and bossbar settings`(@TempDir tempDir: Path) {
        val config = CaptchaConfigLoader.load(tempDir)
        val text = Files.readString(tempDir.resolve("config.yml"))

        assertTrue(tempDir.resolve("config.yml").toFile().isFile)
        assertEquals(42.0, config.scene.distanceBlocks)
        assertEquals(8.0, config.scene.rotationYawDegrees)
        assertEquals("ornate-9x12", config.font.preset)
        assertEquals(1, config.geometry.pixelWidth)
        assertEquals(4, config.geometry.depthBlocks)
        assertEquals("porous", config.geometry.fill.mode)
        assertEquals("clustered", config.palette.mode)
        assertTrue(config.palette.front.materials.isNotEmpty())
        assertTrue(config.palette.outline.enabled)
        assertTrue(text.contains("FILL — ПЛОТНЫЕ ИЛИ КОНТРОЛИРУЕМО"))
        assertTrue(text.contains("cluster-size-min"))
        assertTrue(text.contains("БЫСТРЫЙ ВОЗВРАТ"))

        val startBossBar = config.actions.triggers["challenge-start"].orEmpty()
            .first { it.type == "bossbar" }
        assertEquals("animate", startBossBar.bossBarOperation)
        assertEquals("captcha-timer", startBossBar.bossBarId)
        assertEquals(1.0, startBossBar.bossBarStartProgress)
        assertEquals(0.0, startBossBar.bossBarEndProgress)

        val wrongBossBar = config.actions.triggers["wrong-answer"].orEmpty()
            .first { it.type == "bossbar" }
        assertEquals("add-progress", wrongBossBar.bossBarOperation)
        assertTrue(wrongBossBar.bossBarProgressDelta < 0.0)
    }

    @Test
    fun `old config is backed up when config version changes`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("config.yml"), "config-version: 2\n")
        CaptchaConfigLoader.load(tempDir)
        assertTrue(tempDir.resolve("config.pre-1.1.0.yml.bak").toFile().isFile)
        assertTrue(Files.readString(tempDir.resolve("config.yml")).contains("config-version: 3"))
    }

    @Test
    fun `session manager replaces stale challenge for same player`() {
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

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
