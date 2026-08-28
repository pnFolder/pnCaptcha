package ru.privatenull.pncaptcha.config

import java.time.Duration

data class CaptchaConfig(
    val general: GeneralConfig = GeneralConfig(),
    val routing: RoutingConfig = RoutingConfig(),
    val security: SecurityConfig = SecurityConfig(),
    val metrics: MetricsConfig = MetricsConfig(),
    val updates: UpdateConfig = UpdateConfig(),
    val limbo: LimboConfig = LimboConfig(),
    val player: PlayerConfig = PlayerConfig(),
    val scene: SceneConfig = SceneConfig(),
    val font: FontConfig = FontConfig(),
    val geometry: GeometryConfig = GeometryConfig(),
    val randomness: RandomnessConfig = RandomnessConfig(),
    val palette: PaletteConfig = PaletteConfig(),
    val noise: NoiseConfig = NoiseConfig(),
    val actions: ActionsConfig = ActionsConfig(),
    val messages: MessageConfig = MessageConfig()
) {
    val captchaLength: Int get() = general.captchaLength
    val maxAttempts: Int get() = general.maxAttempts
    val timeout: Duration get() = Duration.ofSeconds(general.timeoutSeconds)
    val verifiedCacheTtl: Duration get() = Duration.ofMinutes(general.verifiedCacheMinutes)
    val maxJoinsPerWindow: Int get() = security.maxJoinsPerWindow
    val joinWindow: Duration get() = Duration.ofSeconds(security.joinWindowSeconds)
    val maxActiveCaptchas: Int get() = security.maxActiveCaptchas

    init {
        require(general.captchaLength in 3..12)
        require(general.maxAttempts in 1..20)
        require(general.timeoutSeconds in 5L..600L)
        require(general.verifiedCacheMinutes in 1L..43_200L)
        require(general.input.maxLength in 3..256)

        require(routing.strategy.lowercase() in setOf(
            "priority", "least-players", "random", "weighted-random", "round-robin", "first-available"
        ))
        require(routing.networkMaxPlayers >= 0)
        require(routing.networkReserveSlots >= 0)
        require(routing.networkMaxPlayers == 0 || routing.networkReserveSlots < routing.networkMaxPlayers)
        require(routing.servers.isNotEmpty()) { "routing.servers must contain at least one server" }
        require(routing.servers.map { it.name.lowercase() }.distinct().size == routing.servers.size) {
            "routing.servers names must be unique"
        }
        routing.servers.forEach { server ->
            require(server.name.isNotBlank())
            require(server.priority in -100_000..100_000)
            require(server.weight in 1..100_000)
            require(server.maxPlayers >= 0)
            require(server.reserveSlots >= 0)
            require(server.maxPlayers == 0 || server.reserveSlots < server.maxPlayers)
        }

        require(security.maxJoinsPerWindow in 1..10_000)
        require(security.joinWindowSeconds in 1L..3600L)
        require(security.maxActiveCaptchas in 1..10_000)

        require(updates.repository.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) {
            "updates.repository must be owner/repository"
        }
        require(updates.requestTimeoutSeconds in 1L..30L)
        require(updates.startupDelaySeconds in 0L..120L)

        require(limbo.viewDistance in 2..32)
        require(limbo.simulationDistance in 2..32)
        require(limbo.maxAutoViewDistance in 2..32)
        require(limbo.precreatePaddingChunks in 0..8)
        require(limbo.skyLightLevel in 0..15)
        require(limbo.blockLightLevel in 0..15)
        require(limbo.worldTimeTicks in 0L..24_000L)
        require(limbo.pedestal.sizeX in 1..16)
        require(limbo.pedestal.sizeZ in 1..16)
        require(limbo.pedestal.block.isNotBlank())

        require(player.gameMode.lowercase() in setOf("creative", "adventure", "survival", "spectator"))
        require(player.lockRadiusBlocks in 0.1..128.0)
        require(player.recovery.belowSpawnBlocks in 0.0..256.0)
        require(player.recovery.aboveSpawnBlocks in 0.0..256.0)
        require(player.recovery.maxHorizontalDistanceBlocks in 0.0..512.0)
        require(player.recovery.cooldownMillis in 0L..60_000L)

        require(scene.distanceBlocks in 4.0..256.0)
        require(scene.centerHeightBlocks in -64.0..128.0)
        require(scene.forwardYawDegrees in -180.0..180.0)
        require(scene.rotationYawDegrees in -180.0..180.0)
        require(scene.rotationPitchDegrees in -89.0..89.0)
        require(scene.rotationRollDegrees in -89.0..89.0)

        require(font.alphabet.isNotBlank())
        require(font.preset.lowercase() in setOf("classic-5x7", "custom"))

        require(geometry.pixelWidth in 1..8)
        require(geometry.pixelHeight in 1..8)
        require(geometry.depthBlocks in 1..24)
        require(geometry.frontThicknessBlocks in 1..geometry.depthBlocks)
        require(geometry.backThicknessBlocks in 0..geometry.depthBlocks)
        require(geometry.frontThicknessBlocks + geometry.backThicknessBlocks <= geometry.depthBlocks)
        require(geometry.letterGapBlocks in 0..24)
        require(geometry.letterRiseBlocks in -16.0..16.0)
        require(geometry.letterDepthStepBlocks in -16.0..16.0)

        require(randomness.seedMode.lowercase() in setOf("random", "fixed"))
        require(randomness.character.horizontalJitterBlocks in 0..12)
        require(randomness.character.verticalJitterBlocks in 0..12)
        require(randomness.character.depthJitterBlocks in 0..12)
        require(randomness.character.depthVariationBlocks in 0..12)
        require(randomness.character.rotationYawJitterDegrees in 0.0..45.0)
        require(randomness.character.rotationPitchJitterDegrees in 0.0..45.0)
        require(randomness.character.rotationRollJitterDegrees in 0.0..45.0)
        require(randomness.scene.distanceJitterBlocks in 0.0..64.0)
        require(randomness.scene.heightJitterBlocks in 0.0..64.0)
        require(randomness.scene.lateralJitterBlocks in 0.0..64.0)
        require(randomness.scene.forwardYawJitterDegrees in 0.0..90.0)
        require(randomness.scene.rotationYawJitterDegrees in 0.0..90.0)
        require(randomness.scene.rotationPitchJitterDegrees in 0.0..45.0)
        require(randomness.scene.rotationRollJitterDegrees in 0.0..45.0)

        require(palette.mode.lowercase() in setOf("per-block", "per-character", "solid"))
        palette.front.requireValid("palette.front")
        palette.side.requireValid("palette.side")
        palette.back.requireValid("palette.back")
        require(palette.accent.chancePercent in 0.0..100.0)
        if (palette.accent.enabled) palette.accent.group.requireValid("palette.accent")

        require(noise.count in 0..8192)
        require(noise.horizontalPaddingBlocks in 0..64)
        require(noise.verticalPaddingBlocks in 0..64)
        require(noise.depthMinBlocks >= 0)
        require(noise.depthMaxBlocks >= noise.depthMinBlocks)
        require(noise.clusterSizeMin in 1..32)
        require(noise.clusterSizeMax in noise.clusterSizeMin..64)
        if (noise.enabled) require(noise.materials.isNotEmpty())

        actions.triggers.forEach { (trigger, entries) ->
            require(trigger.isNotBlank())
            entries.forEach { action ->
                require(action.type.lowercase() in setOf(
                    "message", "actionbar", "title", "sound", "command", "disconnect", "connect",
                    "teleport", "gamemode"
                )) { "Unsupported action type '${action.type}' in trigger '$trigger'" }
                require(action.delayMillis in 0L..300_000L)
                require(action.chancePercent in 0.0..100.0)
                require(action.volume in 0.0f..16.0f)
                require(action.soundPitch in 0.0f..2.0f)
            }
        }
    }
}

data class GeneralConfig(
    val captchaLength: Int = 5,
    val maxAttempts: Int = 3,
    val timeoutSeconds: Long = 30,
    val verifiedCacheMinutes: Long = 720,
    val input: InputConfig = InputConfig()
)

data class InputConfig(
    val caseSensitive: Boolean = false,
    val trim: Boolean = true,
    val removeSpaces: Boolean = true,
    val maxLength: Int = 32
)

data class RoutingConfig(
    val strategy: String = "least-players",
    val networkMaxPlayers: Int = 0,
    val networkReserveSlots: Int = 0,
    val fullBypassPermission: String = "pncaptcha.full.bypass",
    val fallbackToAnyRegistered: Boolean = false,
    val servers: List<RouteServerConfig> = listOf(RouteServerConfig("lobby"))
)

data class RouteServerConfig(
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val weight: Int = 1,
    val maxPlayers: Int = 0,
    val reserveSlots: Int = 0
)

data class SecurityConfig(
    val maxJoinsPerWindow: Int = 6,
    val joinWindowSeconds: Long = 10,
    val maxActiveCaptchas: Int = 128,
    val bypassPermission: String = "pncaptcha.bypass"
)

data class MetricsConfig(
    val enabled: Boolean = true,
    val customCharts: Boolean = true
)

data class UpdateConfig(
    val enabled: Boolean = true,
    val checkOnStartup: Boolean = true,
    val startupDelaySeconds: Long = 2,
    val repository: String = "pnFolder/pnCaptcha",
    val requestTimeoutSeconds: Long = 5,
    val notifyConsole: Boolean = true,
    val notifyPlayers: Boolean = true,
    val notifyPermission: String = "pncaptcha.admin",
    val announceUpToDate: Boolean = true
)

data class LimboConfig(
    val viewDistance: Int = 10,
    val simulationDistance: Int = 8,
    val autoExpandViewDistance: Boolean = true,
    val maxAutoViewDistance: Int = 16,
    val precreatePaddingChunks: Int = 1,
    val reducedDebugInfo: Boolean = false,
    val skyLightLevel: Int = 15,
    val blockLightLevel: Int = 15,
    val worldTimeTicks: Long = 6000,
    val fallingEnabled: Boolean = true,
    val pedestal: PedestalConfig = PedestalConfig()
)

data class PedestalConfig(
    val enabled: Boolean = true,
    val block: String = "minecraft:deepslate_tiles",
    val sizeX: Int = 1,
    val sizeZ: Int = 1,
    val yOffset: Int = -1
)

data class PlayerConfig(
    val gameMode: String = "creative",
    val lockPosition: Boolean = false,
    val lockRadiusBlocks: Double = 1.5,
    val spawn: SpawnConfig = SpawnConfig(),
    val camera: CameraConfig = CameraConfig(),
    val recovery: RecoveryConfig = RecoveryConfig()
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

data class RecoveryConfig(
    val enabled: Boolean = true,
    val belowSpawnBlocks: Double = 8.0,
    val aboveSpawnBlocks: Double = 40.0,
    val maxHorizontalDistanceBlocks: Double = 48.0,
    val cooldownMillis: Long = 500,
    val preserveCurrentLook: Boolean = false,
    val sendMessage: Boolean = true
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
    val frontThicknessBlocks: Int = 1,
    val backThicknessBlocks: Int = 1,
    val letterGapBlocks: Int = 2,
    val letterRiseBlocks: Double = 0.0,
    val letterDepthStepBlocks: Double = 0.0,
    val centerText: Boolean = true,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val extrudeTowardCamera: Boolean = false
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
    val depthJitterBlocks: Int = 1,
    val depthVariationBlocks: Int = 0,
    val rotationYawJitterDegrees: Double = 0.0,
    val rotationPitchJitterDegrees: Double = 0.0,
    val rotationRollJitterDegrees: Double = 0.0
)

data class SceneRandomnessConfig(
    val distanceJitterBlocks: Double = 0.0,
    val heightJitterBlocks: Double = 0.0,
    val lateralJitterBlocks: Double = 0.0,
    val forwardYawJitterDegrees: Double = 0.0,
    val rotationYawJitterDegrees: Double = 2.0,
    val rotationPitchJitterDegrees: Double = 0.0,
    val rotationRollJitterDegrees: Double = 0.0
)

data class PaletteConfig(
    val mode: String = "per-block",
    val front: MaterialGroup = MaterialGroup(
        listOf(
            WeightedMaterial("minecraft:polished_deepslate", 5),
            WeightedMaterial("minecraft:cyan_terracotta", 3),
            WeightedMaterial("minecraft:light_blue_terracotta", 2)
        )
    ),
    val side: MaterialGroup = MaterialGroup(
        listOf(
            WeightedMaterial("minecraft:deepslate_tiles", 5),
            WeightedMaterial("minecraft:deepslate_bricks", 3),
            WeightedMaterial("minecraft:blackstone", 2)
        )
    ),
    val back: MaterialGroup = MaterialGroup(
        listOf(
            WeightedMaterial("minecraft:blackstone", 4),
            WeightedMaterial("minecraft:polished_blackstone", 2)
        )
    ),
    val accent: AccentConfig = AccentConfig()
)

data class AccentConfig(
    val enabled: Boolean = true,
    val chancePercent: Double = 8.0,
    val group: MaterialGroup = MaterialGroup(
        listOf(
            WeightedMaterial("minecraft:sea_lantern", 1),
            WeightedMaterial("minecraft:verdant_froglight", 1)
        )
    )
)

data class MaterialGroup(
    val materials: List<WeightedMaterial>
) {
    fun requireValid(path: String) {
        require(materials.isNotEmpty()) { "$path materials must not be empty" }
        require(materials.all { it.block.isNotBlank() && it.weight > 0 }) {
            "$path entries must have a block and positive weight"
        }
    }
}

data class WeightedMaterial(
    val block: String,
    val weight: Int = 1
)

data class NoiseConfig(
    val enabled: Boolean = true,
    val count: Int = 10,
    val horizontalPaddingBlocks: Int = 4,
    val verticalPaddingBlocks: Int = 3,
    val depthMinBlocks: Int = 3,
    val depthMaxBlocks: Int = 8,
    val clusterSizeMin: Int = 1,
    val clusterSizeMax: Int = 2,
    val materials: List<WeightedMaterial> = listOf(
        WeightedMaterial("minecraft:gray_stained_glass", 5),
        WeightedMaterial("minecraft:light_blue_stained_glass", 2),
        WeightedMaterial("minecraft:cyan_stained_glass", 1)
    )
)

data class ActionsConfig(
    val enabled: Boolean = true,
    val triggers: Map<String, List<ActionDefinition>> = emptyMap()
)

data class ActionDefinition(
    val enabled: Boolean = true,
    val type: String = "message",
    val delayMillis: Long = 0,
    val chancePercent: Double = 100.0,
    val permission: String = "",
    val stopAfter: Boolean = false,
    val text: String = "",
    val lines: List<String> = emptyList(),
    val command: String = "",
    val server: String = "",
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val teleportYaw: Float? = null,
    val teleportPitch: Float? = null,
    val title: String = "",
    val subtitle: String = "",
    val fadeInMillis: Long = 250,
    val stayMillis: Long = 1500,
    val fadeOutMillis: Long = 250,
    val sound: String = "minecraft:block.note_block.pling",
    val source: String = "master",
    val volume: Float = 1.0f,
    val soundPitch: Float = 1.0f,
    val gameMode: String = "adventure"
)

data class MessageConfig(
    val enabled: Boolean = true,
    val prompt: List<String> = listOf(
        "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "<gradient:#54F4D1:#4D9CFF><bold>pnCaptcha</bold></gradient> <dark_gray>• <white>Проверка подключения",
        "<gray>Посмотри на объёмный код и <white>введи его в чат</white>.",
        "<gray>Попыток: <aqua>{max}</aqua> <dark_gray>• <gray>Время: <aqua>{timeout}s</aqua>",
        "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ),
    val wrong: List<String> = listOf(
        "<red><bold>✕ Неверный код</bold></red> <dark_gray>• <gray>попытка <white>{attempt}</white>/<white>{max}</white>",
        "<gray>Проверь символы и отправь ответ ещё раз."
    ),
    val passed: List<String> = listOf(
        "<green><bold>✓ Проверка пройдена</bold></green>",
        "<gray>Перенаправляю тебя на <white>{server}</white>…"
    ),
    val recovered: List<String> = listOf(
        "<yellow><bold>↺ Возврат на платформу</bold></yellow> <dark_gray>• <gray>{reason}"
    ),
    val timeout: List<String> = listOf("<red><bold>Время проверки истекло.</bold></red>"),
    val tooManyAttempts: List<String> = listOf("<red><bold>Слишком много неверных попыток.</bold></red>"),
    val busy: List<String> = listOf("<red>Сервис CAPTCHA сейчас перегружен. Попробуй чуть позже.</red>"),
    val rateLimited: List<String> = listOf("<red>Слишком много подключений с твоего IP. Попробуй позже.</red>"),
    val networkFull: List<String> = listOf("<red>Сеть заполнена. Попробуй подключиться позже.</red>"),
    val unavailable: List<String> = listOf("<red>CAPTCHA временно недоступна.</red>"),
    val routeUnavailable: List<String> = listOf("<red>Сейчас нет доступного сервера для подключения.</red>"),
    val updateAvailable: List<String> = listOf(
        "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "<gradient:#FFD166:#FF6B6B><bold>Доступно обновление pnCaptcha</bold></gradient>",
        "<gray>Установлено: <white>{current}</white> <dark_gray>→ <gray>новое: <green>{latest}</green>",
        "<click:open_url:'{url}'><hover:show_text:'<gray>Открыть GitHub Release'><aqua><underlined>Нажми сюда, чтобы открыть релиз</underlined></aqua></hover></click>",
        "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    )
)
