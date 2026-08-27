package ru.privatenull.pncaptcha.config

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

object CaptchaConfigLoader {
    private const val FILE_NAME = "config.properties"

    fun load(dataDirectory: Path): CaptchaConfig {
        Files.createDirectories(dataDirectory)
        val path = dataDirectory.resolve(FILE_NAME)

        if (Files.notExists(path)) {
            writeDefaults(path)
        }

        val properties = Properties()
        Files.newInputStream(path).use(properties::load)

        val legacySideMaterial = properties.string("glyph-side-material", "")
        val sideMaterials = properties.string(
            "glyph-side-materials",
            if (legacySideMaterial.isNotEmpty()) legacySideMaterial else DEFAULT_SIDE_MATERIALS
        ).split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

        return CaptchaConfig(
            targetServer = properties.string("target-server", "lobby"),
            captchaLength = properties.int("captcha-length", 5),
            maxAttempts = properties.int("max-attempts", 3),
            timeout = Duration.ofSeconds(properties.long("timeout-seconds", 30)),
            verifiedCacheTtl = Duration.ofMinutes(properties.long("verified-cache-minutes", 720)),
            noiseBlocks = properties.int("noise-blocks", 8),
            maxJoinsPerWindow = properties.int("max-joins-per-window", 6),
            joinWindow = Duration.ofSeconds(properties.long("join-window-seconds", 10)),

            captchaDistanceBlocks = properties.double("captcha-distance-blocks", 30.0),
            captchaAngleDegrees = properties.double("captcha-angle-degrees", 28.0),
            captchaCenterHeightBlocks = properties.double("captcha-center-height-blocks", 8.0),
            cameraPitchOffsetDegrees = properties.double("camera-pitch-offset-degrees", 0.0),

            glyphScaleX = properties.int("glyph-scale-x", 2),
            glyphScaleY = properties.int("glyph-scale-y", 2),
            glyphDepth = properties.int("glyph-depth", 3),
            glyphGapBlocks = properties.int("glyph-gap-blocks", 2),
            glyphJitterYBlocks = properties.int("glyph-jitter-y-blocks", 1),
            glyphJitterDepthBlocks = properties.int("glyph-jitter-depth-blocks", 1),
            glyphMaterials = properties.string("glyph-materials", DEFAULT_GLYPH_MATERIALS)
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty),
            glyphSideMaterials = sideMaterials.ifEmpty { DEFAULT_SIDE_MATERIALS.split(',') },
            noiseMaterial = properties.string("noise-material", "minecraft:gray_stained_glass")
        )
    }

    private fun writeDefaults(path: Path) {
        val properties = Properties().apply {
            setProperty("target-server", "lobby")
            setProperty("captcha-length", "5")
            setProperty("max-attempts", "3")
            setProperty("timeout-seconds", "30")
            setProperty("verified-cache-minutes", "720")
            setProperty("noise-blocks", "8")
            setProperty("max-joins-per-window", "6")
            setProperty("join-window-seconds", "10")

            // Placement/perspective.
            setProperty("captcha-distance-blocks", "30.0")
            setProperty("captcha-angle-degrees", "28.0")
            setProperty("captcha-center-height-blocks", "8.0")
            setProperty("camera-pitch-offset-degrees", "0.0")

            // Mass and spacing.
            setProperty("glyph-scale-x", "2")
            setProperty("glyph-scale-y", "2")
            setProperty("glyph-depth", "3")
            setProperty("glyph-gap-blocks", "2")
            setProperty("glyph-jitter-y-blocks", "1")
            setProperty("glyph-jitter-depth-blocks", "1")
            setProperty("glyph-materials", DEFAULT_GLYPH_MATERIALS)
            setProperty("glyph-side-materials", DEFAULT_SIDE_MATERIALS)
            setProperty("noise-material", "minecraft:gray_stained_glass")
        }

        Files.newOutputStream(path).use { output ->
            properties.store(output, "pnCaptcha configuration")
        }
    }

    private fun Properties.string(key: String, default: String): String =
        getProperty(key, default).trim()

    private fun Properties.int(key: String, default: Int): Int =
        getProperty(key)?.trim()?.toIntOrNull() ?: default

    private fun Properties.long(key: String, default: Long): Long =
        getProperty(key)?.trim()?.toLongOrNull() ?: default

    private fun Properties.double(key: String, default: Double): Double =
        getProperty(key)?.trim()?.toDoubleOrNull() ?: default

    private const val DEFAULT_GLYPH_MATERIALS =
        "minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:gray_concrete,minecraft:cyan_terracotta,minecraft:light_blue_terracotta"

    private const val DEFAULT_SIDE_MATERIALS =
        "minecraft:deepslate_tiles,minecraft:deepslate_bricks,minecraft:blackstone,minecraft:polished_blackstone"
}
