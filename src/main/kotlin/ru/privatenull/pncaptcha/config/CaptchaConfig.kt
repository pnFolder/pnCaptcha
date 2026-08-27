package ru.privatenull.pncaptcha.config

import java.time.Duration

data class CaptchaConfig(
    val targetServer: String = "lobby",
    val captchaLength: Int = 5,
    val maxAttempts: Int = 3,
    val timeout: Duration = Duration.ofSeconds(30),
    val verifiedCacheTtl: Duration = Duration.ofHours(12),
    val noiseBlocks: Int = 8,
    val maxJoinsPerWindow: Int = 6,
    val joinWindow: Duration = Duration.ofSeconds(10),
    val glyphScaleX: Int = 1,
    val glyphScaleY: Int = 2,
    val glyphDepth: Int = 6,
    val glyphMaterials: List<String> = listOf(
        "minecraft:polished_deepslate",
        "minecraft:deepslate_bricks",
        "minecraft:gray_concrete",
        "minecraft:cyan_terracotta",
        "minecraft:light_blue_terracotta"
    ),
    val glyphSideMaterials: List<String> = listOf(
        "minecraft:deepslate_tiles",
        "minecraft:deepslate_bricks",
        "minecraft:blackstone",
        "minecraft:polished_blackstone"
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
        require(glyphScaleX in 1..3) { "glyphScaleX must be between 1 and 3" }
        require(glyphScaleY in 1..3) { "glyphScaleY must be between 1 and 3" }
        require(glyphDepth in 1..8) { "glyphDepth must be between 1 and 8" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
        require(glyphSideMaterials.isNotEmpty()) { "glyphSideMaterials must not be empty" }
    }
}
