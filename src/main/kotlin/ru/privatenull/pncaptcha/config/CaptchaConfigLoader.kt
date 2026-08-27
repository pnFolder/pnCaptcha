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

        return CaptchaConfig(
            targetServer = properties.string("target-server", "lobby"),
            captchaLength = properties.int("captcha-length", 5),
            maxAttempts = properties.int("max-attempts", 3),
            timeout = Duration.ofSeconds(properties.long("timeout-seconds", 30)),
            verifiedCacheTtl = Duration.ofMinutes(properties.long("verified-cache-minutes", 720)),
            noiseBlocks = properties.int("noise-blocks", 22),
            maxJoinsPerWindow = properties.int("max-joins-per-window", 6),
            joinWindow = Duration.ofSeconds(properties.long("join-window-seconds", 10)),
            glyphMaterials = properties.string("glyph-materials", DEFAULT_GLYPH_MATERIALS)
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty),
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
            setProperty("noise-blocks", "22")
            setProperty("max-joins-per-window", "6")
            setProperty("join-window-seconds", "10")
            setProperty("glyph-materials", DEFAULT_GLYPH_MATERIALS)
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

    private const val DEFAULT_GLYPH_MATERIALS =
        "minecraft:white_concrete,minecraft:light_gray_concrete,minecraft:cyan_concrete,minecraft:blue_concrete,minecraft:purple_concrete"
}
