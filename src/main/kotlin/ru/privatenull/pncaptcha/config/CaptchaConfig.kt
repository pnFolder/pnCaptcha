package ru.privatenull.pncaptcha.config

import java.time.Duration

/**
 * Public configuration model. Everything that changes the visual scene or the
 * verification flow is represented here and loaded from one config.yml file.
 */
data class CaptchaConfig(
    val general: GeneralConfig = GeneralConfig(),
    val security: SecurityConfig = SecurityConfig(),
    val limbo: LimboConfig = LimboConfig(),
    val player: PlayerConfig = PlayerConfig(),
    val scene: SceneConfig = SceneConfig(),
    val font: FontConfig = FontConfig(),
    val geometry: GeometryConfig = GeometryConfig(),
    val randomness: RandomnessConfig = RandomnessConfig(),
    val palette: PaletteConfig = PaletteConfig(),
    val noise: NoiseConfig = NoiseConfig(),
    val messages: MessageConfig = MessageConfig()
) {
    val targetServer: String get() = general.targetServer
    val captchaLength: Int get() = general.captchaLength
    val maxAttempts: Int get() = general.maxAttempts
    val timeout: Duration get() = Duration.ofSeconds(general.timeoutSeconds)
    val verifiedCacheTtl: Duration get() = Duration.ofMinutes(general.verifiedCacheMinutes)

    val maxJoinsPerWindow: Int get() = security.maxJoinsPerWindow
    val joinWindow: Duration get() = Duration.ofSeconds(security.joinWindowSeconds)
    val maxActiveCaptchas: Int get() = security.maxActiveCaptchas

    val creativeMode: Boolean get() = player.gameMode.equals("creative", ignoreCase = true)
    val lockPlayerPosition: Boolean get() = player.lockPosition
    val limboViewDistance: Int get() = limbo.viewDistance
    val limboSimulationDistance: Int get() = limbo.simulationDistance

    val captchaDistanceBlocks: Double get() = scene.distanceBlocks
    val captchaAngleDegrees: Double get() = scene.rotationYawDegrees
    val captchaCenterHeightBlocks: Double get() = scene.centerHeightBlocks
    val cameraPitchOffsetDegrees: Double get() = player.camera.pitchOffsetDegrees

    val glyphScaleX: Int get() = geometry.pixelWidth
    val glyphScaleY: Int get() = geometry.pixelHeight
    val glyphDepth: Int get() = geometry.depthBlocks
    val glyphGapBlocks: Int get() = geometry.letterGapBlocks
    val glyphJitterYBlocks: Int get() = randomness.character.verticalJitterBlocks
    val glyphJitterDepthBlocks: Int get() = randomness.character.depthJitterBlocks

    val glyphMaterials: List<String> get() = palette.front.materials.map { it.block }
    val glyphSideMaterials: List<String> get() = palette.side.materials.map { it.block }
    val noiseBlocks: Int get() = if (noise.enabled) noise.count else 0
    val noiseMaterial: String get() = noise.materials.firstOrNull()?.block ?: "minecraft:gray_stained_glass"

    init {
        require(general.targetServer.isNotBlank()) { "general.target-server must not be blank" }
        require(general.captchaLength in 3..12) { "general.captcha-length must be between 3 and 12" }
        require(general.maxAttempts in 1..10) { "general.max-attempts must be between 1 and 10" }
        require(general.timeoutSeconds in 5..300) { "general.timeout-seconds must be between 5 and 300" }
        require(general.verifiedCacheMinutes in 1..43_200) { "general.verified-cache-minutes must be between 1 and 43200" }

        require(security.maxJoinsPerWindow in 1..10_000) { "security.max-joins-per-window must be between 1 and 10000" }
        require(security.joinWindowSeconds in 1..3600) { "security.join-window-seconds must be between 1 and 3600" }
        require(security.maxActiveCaptchas in 1..10_000) { "security.max-active-captchas must be between 1 and 10000" }

        require(limbo.viewDistance in 2..32) { "limbo.view-distance must be between 2 and 32" }
        require(limbo.simulationDistance in 2..32) { "limbo.simulation-distance must be between 2 and 32" }
        require(limbo.simulationDistance <= limbo.viewDistance) { "limbo.simulation-distance must not exceed view-distance" }

        require(player.gameMode.lowercase() in setOf("creative", "adventure", "survival", "spectator")) {
            "player.game-mode must be creative, adventure, survival, or spectator"
        }
        require(player.lockRadiusBlocks in 0.1..64.0) { "player.lock-radius-blocks must be between 0.1 and 64" }

        require(scene.distanceBlocks in 4.0..128.0) { "scene.distance-blocks must be between 4 and 128" }
        require(scene.centerHeightBlocks in -32.0..64.0) { "scene.center-height-blocks must be between -32 and 64" }
        require(scene.forwardYawDegrees in -180.0..180.0) { "scene.forward-yaw-degrees must be between -180 and 180" }
        require(scene.rotationYawDegrees in -180.0..180.0) { "scene.rotation.yaw-degrees must be between -180 and 180" }
        require(scene.rotationPitchDegrees in -65.0..65.0) { "scene.rotation.pitch-degrees must be between -65 and 65" }
        require(scene.rotationRollDegrees in -65.0..65.0) { "scene.rotation.roll-degrees must be between -65 and 65" }

        require(font.alphabet.isNotBlank()) { "font.alphabet must not be blank" }
        require(font.preset.lowercase() in setOf("classic-5x7", "custom")) {
            "font.preset must be classic-5x7 or custom"
        }

        require(geometry.pixelWidth in 1..6) { "geometry.pixel-width must be between 1 and 6" }
        require(geometry.pixelHeight in 1..6) { "geometry.pixel-height must be between 1 and 6" }
        require(geometry.depthBlocks in 1..16) { "geometry.depth-blocks must be between 1 and 16" }
        require(geometry.letterGapBlocks in 0..16) { "geometry.letter-gap-blocks must be between 0 and 16" }

        require(randomness.character.horizontalJitterBlocks in 0..8)
        require(randomness.character.verticalJitterBlocks in 0..8)
        require(randomness.character.depthJitterBlocks in 0..8)
        require(randomness.scene.distanceJitterBlocks in 0.0..32.0)
        require(randomness.scene.heightJitterBlocks in 0.0..32.0)
        require(randomness.scene.forwardYawJitterDegrees in 0.0..90.0)
        require(randomness.scene.rotationYawJitterDegrees in 0.0..90.0)
        require(randomness.scene.rotationPitchJitterDegrees in 0.0..45.0)
        require(randomness.scene.rotationRollJitterDegrees in 0.0..45.0)

        palette.front.requireValid("palette.front")
        palette.side.requireValid("palette.side")
        palette.back.requireValid("palette.back")
        if (noise.enabled) {
            require(noise.count in 0..4096) { "noise.count must be between 0 and 4096" }
            require(noise.depthMinBlocks >= 0) { "noise.depth-min-blocks must be >= 0" }
            require(noise.depthMaxBlocks >= noise.depthMinBlocks) { "noise.depth-max-blocks must be >= depth-min-blocks" }
            require(noise.materials.isNotEmpty()) { "noise.materials must not be empty when noise is enabled" }
            require(noise.materials.all { it.weight > 0 && it.block.isNotBlank() }) { "noise.materials entries must have a block and positive weight" }
        }
    }
}

data class GeneralConfig(
    val targetServer: String = "lobby",
    val captchaLength: Int = 5,
    val maxAttempts: Int = 3,
    val timeoutSeconds: Long = 30,
    val verifiedCacheMinutes: Long = 720
)

data class SecurityConfig(
    val maxJoinsPerWindow: Int = 6,
    val joinWindowSeconds: Long = 10,
    val maxActiveCaptchas: Int = 128
)

data class LimboConfig(
    val viewDistance: Int = 10,
    val simulationDistance: Int = 8,
    val reducedDebugInfo: Boolean = false,
    val pedestalEnabled: Boolean = true,
    val pedestalBlock: String = "minecraft:deepslate_tiles"
)

data class PlayerConfig(
    val gameMode: String = "creative",
    val lockPosition: Boolean = false,
    val lockRadiusBlocks: Double = 1.5,
    val spawn: SpawnConfig = SpawnConfig(),
    val camera: CameraConfig = CameraConfig()
)

data class SpawnConfig(
    val x: Double = 0.5,
    val y: Double = 65.0,
    val z: Double = 0.5
)

data class CameraConfig(
    val autoAim: Boolean = true,
    val yawDegrees: Double = 0.0,
    val pitchDegrees: Double = 0.0,
    val yawOffsetDegrees: Double = 0.0,
    val pitchOffsetDegrees: Double = 0.0
)

data class SceneConfig(
    val distanceBlocks: Double = 30.0,
    val forwardYawDegrees: Double = 0.0,
    val lateralOffsetBlocks: Double = 0.0,
    val centerHeightBlocks: Double = 8.0,
    val rotationYawDegrees: Double = 28.0,
    val rotationPitchDegrees: Double = 0.0,
    val rotationRollDegrees: Double = 0.0
)

data class FontConfig(
    val preset: String = "classic-5x7",
    val alphabet: String = "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
    val customGlyphs: Map<Char, List<String>> = emptyMap()
)

data class GeometryConfig(
    val pixelWidth: Int = 2,
    val pixelHeight: Int = 2,
    val depthBlocks: Int = 3,
    val letterGapBlocks: Int = 2,
    val centerText: Boolean = true
)

data class RandomnessConfig(
    val enabled: Boolean = true,
    val seedMode: String = "random",
    val fixedSeed: Long = 1337L,
    val character: CharacterRandomnessConfig = CharacterRandomnessConfig(),
    val scene: SceneRandomnessConfig = SceneRandomnessConfig()
)

data class CharacterRandomnessConfig(
    val horizontalJitterBlocks: Int = 0,
    val verticalJitterBlocks: Int = 1,
    val depthJitterBlocks: Int = 1
)

data class SceneRandomnessConfig(
    val distanceJitterBlocks: Double = 0.0,
    val heightJitterBlocks: Double = 0.0,
    val forwardYawJitterDegrees: Double = 0.0,
    val rotationYawJitterDegrees: Double = 2.0,
    val rotationPitchJitterDegrees: Double = 0.0,
    val rotationRollJitterDegrees: Double = 0.0
)

data class PaletteConfig(
    val mode: String = "per-block",
    val front: MaterialGroup = MaterialGroup(
        materials = listOf(
            WeightedMaterial("minecraft:polished_deepslate", 4),
            WeightedMaterial("minecraft:deepslate_bricks", 3),
            WeightedMaterial("minecraft:gray_concrete", 2),
            WeightedMaterial("minecraft:cyan_terracotta", 1),
            WeightedMaterial("minecraft:light_blue_terracotta", 1)
        )
    ),
    val side: MaterialGroup = MaterialGroup(
        materials = listOf(
            WeightedMaterial("minecraft:deepslate_tiles", 4),
            WeightedMaterial("minecraft:deepslate_bricks", 3),
            WeightedMaterial("minecraft:blackstone", 2),
            WeightedMaterial("minecraft:polished_blackstone", 1)
        )
    ),
    val back: MaterialGroup = MaterialGroup(
        materials = listOf(
            WeightedMaterial("minecraft:blackstone", 3),
            WeightedMaterial("minecraft:deepslate_tiles", 2)
        )
    )
) {
    init {
        require(mode.lowercase() in setOf("per-block", "per-character", "solid")) {
            "palette.mode must be per-block, per-character, or solid"
        }
    }
}

data class MaterialGroup(
    val materials: List<WeightedMaterial>
) {
    fun requireValid(path: String) {
        require(materials.isNotEmpty()) { "$path.materials must not be empty" }
        require(materials.all { it.block.isNotBlank() && it.weight > 0 }) {
            "$path.materials entries must have a block and positive weight"
        }
    }
}

data class WeightedMaterial(
    val block: String,
    val weight: Int = 1
)

data class NoiseConfig(
    val enabled: Boolean = true,
    val count: Int = 8,
    val horizontalPaddingBlocks: Int = 4,
    val verticalPaddingBlocks: Int = 3,
    val depthMinBlocks: Int = 3,
    val depthMaxBlocks: Int = 7,
    val materials: List<WeightedMaterial> = listOf(
        WeightedMaterial("minecraft:gray_stained_glass", 4),
        WeightedMaterial("minecraft:light_blue_stained_glass", 1)
    )
)

data class MessageConfig(
    val prefix: String = "[pnCaptcha] ",
    val prompt: String = "Type the 3D block code you see in chat.",
    val wrong: String = "Wrong code. Try again ({attempt}/{max}).",
    val passed: String = "Verification passed.",
    val timeout: String = "CAPTCHA timed out.",
    val tooManyAttempts: String = "Too many wrong CAPTCHA attempts.",
    val busy: String = "CAPTCHA service is busy. Try again shortly.",
    val rateLimited: String = "Too many connection attempts. Try again in a few seconds.",
    val unavailable: String = "CAPTCHA service is temporarily unavailable.",
    val targetMissing: String = "Target server is not configured correctly."
)
