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

    // Scene placement. The whole front plane is physically rotated in X/Z.
    val captchaDistanceBlocks: Double = 30.0,
    val captchaAngleDegrees: Double = 28.0,
    val captchaCenterHeightBlocks: Double = 8.0,
    val cameraPitchOffsetDegrees: Double = 0.0,

    // Glyph mass/spacing. scaleX/scaleY are the visual "fatness" of one
    // logical font pixel, while glyphDepth is the real 3D extrusion depth.
    val glyphScaleX: Int = 2,
    val glyphScaleY: Int = 2,
    val glyphDepth: Int = 3,
    val glyphGapBlocks: Int = 2,
    val glyphJitterYBlocks: Int = 1,
    val glyphJitterDepthBlocks: Int = 1,

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

        require(captchaDistanceBlocks in 8.0..48.0) {
            "captchaDistanceBlocks must be between 8 and 48"
        }
        require(captchaAngleDegrees in -45.0..45.0) {
            "captchaAngleDegrees must be between -45 and 45"
        }
        require(captchaCenterHeightBlocks in 2.0..24.0) {
            "captchaCenterHeightBlocks must be between 2 and 24"
        }
        require(cameraPitchOffsetDegrees in -25.0..25.0) {
            "cameraPitchOffsetDegrees must be between -25 and 25"
        }

        require(glyphScaleX in 1..3) { "glyphScaleX must be between 1 and 3" }
        require(glyphScaleY in 1..3) { "glyphScaleY must be between 1 and 3" }
        require(glyphDepth in 1..8) { "glyphDepth must be between 1 and 8" }
        require(glyphGapBlocks in 0..8) { "glyphGapBlocks must be between 0 and 8" }
        require(glyphJitterYBlocks in 0..3) { "glyphJitterYBlocks must be between 0 and 3" }
        require(glyphJitterDepthBlocks in 0..3) { "glyphJitterDepthBlocks must be between 0 and 3" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
        require(glyphSideMaterials.isNotEmpty()) { "glyphSideMaterials must not be empty" }
    }
}
