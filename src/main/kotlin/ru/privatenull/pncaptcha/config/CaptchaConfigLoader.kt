package ru.privatenull.pncaptcha.config

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

object CaptchaConfigLoader {
    private const val FILE_NAME = "config.properties"
    private const val CONFIG_VERSION = 6

    fun load(dataDirectory: Path): CaptchaConfig {
        Files.createDirectories(dataDirectory)
        val path = dataDirectory.resolve(FILE_NAME)

        if (Files.notExists(path)) {
            writeDefaults(path)
        }

        val properties = Properties()
        Files.newInputStream(path).use(properties::load)

        if (migrateAndFillDefaults(properties)) {
            store(path, properties)
        }

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
            maxActiveCaptchas = properties.int("max-active-captchas", 128),

            creativeMode = properties.bool("creative-mode", true),
            lockPlayerPosition = properties.bool("lock-player-position", false),

            limboViewDistance = properties.int("limbo-view-distance", 8),
            limboSimulationDistance = properties.int("limbo-simulation-distance", 6),

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

    private fun migrateAndFillDefaults(properties: Properties): Boolean {
        var changed = false
        val oldVersion = properties.int("config-version", 3)

        // 0.3.0 no longer depends on fake block packets. The exact stock 0.2.6
        // scene can therefore move back out to the requested large 30-block
        // presentation without losing the far side when chunks arrive later.
        if (oldVersion < CONFIG_VERSION) {
            if (properties.getProperty("captcha-distance-blocks")?.trim() == "20.0") {
                properties.setProperty("captcha-distance-blocks", "30.0")
                changed = true
            }
            if (properties.getProperty("captcha-angle-degrees")?.trim() == "24.0") {
                properties.setProperty("captcha-angle-degrees", "28.0")
                changed = true
            }
            if (properties.getProperty("glyph-scale-x")?.trim() == "1") {
                properties.setProperty("glyph-scale-x", "2")
                changed = true
            }
        }

        DEFAULT_PROPERTIES.forEach { (key, value) ->
            if (!properties.containsKey(key)) {
                properties.setProperty(key, value)
                changed = true
            }
        }

        if (properties.getProperty("config-version") != CONFIG_VERSION.toString()) {
            properties.setProperty("config-version", CONFIG_VERSION.toString())
            changed = true
        }

        return changed
    }

    private fun writeDefaults(path: Path) {
        val properties = Properties()
        DEFAULT_PROPERTIES.forEach(properties::setProperty)
        properties.setProperty("config-version", CONFIG_VERSION.toString())
        store(path, properties)
    }

    private fun store(path: Path, properties: Properties) {
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

    private fun Properties.bool(key: String, default: Boolean): Boolean =
        getProperty(key)?.trim()?.lowercase()?.let {
            when (it) {
                "true", "yes", "1", "on" -> true
                "false", "no", "0", "off" -> false
                else -> null
            }
        } ?: default

    private val DEFAULT_PROPERTIES = linkedMapOf(
        "target-server" to "lobby",
        "captcha-length" to "5",
        "max-attempts" to "3",
        "timeout-seconds" to "30",
        "verified-cache-minutes" to "720",
        "noise-blocks" to "8",
        "max-joins-per-window" to "6",
        "join-window-seconds" to "10",
        "max-active-captchas" to "128",

        "creative-mode" to "true",
        "lock-player-position" to "false",

        // Real Limbo chunk delivery settings used by the per-session world.
        "limbo-view-distance" to "8",
        "limbo-simulation-distance" to "6",

        // Placement/perspective.
        "captcha-distance-blocks" to "30.0",
        "captcha-angle-degrees" to "28.0",
        "captcha-center-height-blocks" to "8.0",
        "camera-pitch-offset-degrees" to "0.0",

        // Mass and spacing.
        "glyph-scale-x" to "2",
        "glyph-scale-y" to "2",
        "glyph-depth" to "3",
        "glyph-gap-blocks" to "2",
        "glyph-jitter-y-blocks" to "1",
        "glyph-jitter-depth-blocks" to "1",
        "glyph-materials" to DEFAULT_GLYPH_MATERIALS,
        "glyph-side-materials" to DEFAULT_SIDE_MATERIALS,
        "noise-material" to "minecraft:gray_stained_glass"
    )

    private const val DEFAULT_GLYPH_MATERIALS =
        "minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:gray_concrete,minecraft:cyan_terracotta,minecraft:light_blue_terracotta"

    private const val DEFAULT_SIDE_MATERIALS =
        "minecraft:deepslate_tiles,minecraft:deepslate_bricks,minecraft:blackstone,minecraft:polished_blackstone"
}
