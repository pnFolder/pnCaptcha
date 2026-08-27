package ru.privatenull.pncaptcha.config

import java.time.Duration

data class CaptchaConfig(
    val targetServer: String = "lobby",
    val captchaLength: Int = 5,
    val maxAttempts: Int = 3,
    val timeout: Duration = Duration.ofSeconds(30),
    val verifiedCacheTtl: Duration = Duration.ofHours(12),
    val noiseBlocks: Int = 22,
    val maxJoinsPerWindow: Int = 6,
    val joinWindow: Duration = Duration.ofSeconds(10),
    val glyphMaterials: List<String> = listOf(
        "minecraft:white_concrete",
        "minecraft:light_gray_concrete",
        "minecraft:cyan_concrete",
        "minecraft:blue_concrete",
        "minecraft:purple_concrete"
    ),
    val noiseMaterial: String = "minecraft:gray_stained_glass"
) {
    init {
        require(targetServer.isNotBlank()) { "targetServer must not be blank" }
        require(captchaLength in 3..8) { "captchaLength must be between 3 and 8" }
        require(maxAttempts in 1..10) { "maxAttempts must be between 1 and 10" }
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive" }
        require(!verifiedCacheTtl.isNegative && !verifiedCacheTtl.isZero) { "verifiedCacheTtl must be positive" }
        require(noiseBlocks in 0..128) { "noiseBlocks must be between 0 and 128" }
        require(maxJoinsPerWindow in 1..100) { "maxJoinsPerWindow must be between 1 and 100" }
        require(!joinWindow.isNegative && !joinWindow.isZero) { "joinWindow must be positive" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
    }
}
