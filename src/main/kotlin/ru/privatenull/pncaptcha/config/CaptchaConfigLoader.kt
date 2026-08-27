package ru.privatenull.pncaptcha.config

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object CaptchaConfigLoader {
    private const val FILE_NAME = "config.yml"

    fun load(dataDirectory: Path): CaptchaConfig {
        Files.createDirectories(dataDirectory)
        val path = dataDirectory.resolve(FILE_NAME)

        if (Files.notExists(path)) {
            copyDefault(path)
        }

        val root = Files.newBufferedReader(path).use { reader ->
            @Suppress("UNCHECKED_CAST")
            (Yaml().load<Any?>(reader) as? Map<String, Any?>).orEmpty()
        }

        val defaults = CaptchaConfig()

        return CaptchaConfig(
            general = GeneralConfig(
                targetServer = root.string("general.target-server", defaults.general.targetServer),
                captchaLength = root.int("general.captcha-length", defaults.general.captchaLength),
                maxAttempts = root.int("general.max-attempts", defaults.general.maxAttempts),
                timeoutSeconds = root.long("general.timeout-seconds", defaults.general.timeoutSeconds),
                verifiedCacheMinutes = root.long("general.verified-cache-minutes", defaults.general.verifiedCacheMinutes)
            ),
            security = SecurityConfig(
                maxJoinsPerWindow = root.int("security.max-joins-per-window", defaults.security.maxJoinsPerWindow),
                joinWindowSeconds = root.long("security.join-window-seconds", defaults.security.joinWindowSeconds),
                maxActiveCaptchas = root.int("security.max-active-captchas", defaults.security.maxActiveCaptchas)
            ),
            limbo = LimboConfig(
                viewDistance = root.int("limbo.view-distance", defaults.limbo.viewDistance),
                simulationDistance = root.int("limbo.simulation-distance", defaults.limbo.simulationDistance),
                reducedDebugInfo = root.bool("limbo.reduced-debug-info", defaults.limbo.reducedDebugInfo),
                pedestalEnabled = root.bool("limbo.pedestal.enabled", defaults.limbo.pedestalEnabled),
                pedestalBlock = root.string("limbo.pedestal.block", defaults.limbo.pedestalBlock)
            ),
            player = PlayerConfig(
                gameMode = root.string("player.game-mode", defaults.player.gameMode),
                lockPosition = root.bool("player.lock-position", defaults.player.lockPosition),
                lockRadiusBlocks = root.double("player.lock-radius-blocks", defaults.player.lockRadiusBlocks),
                spawn = SpawnConfig(
                    x = root.double("player.spawn.x", defaults.player.spawn.x),
                    y = root.double("player.spawn.y", defaults.player.spawn.y),
                    z = root.double("player.spawn.z", defaults.player.spawn.z)
                ),
                camera = CameraConfig(
                    autoAim = root.bool("player.camera.auto-aim", defaults.player.camera.autoAim),
                    yawDegrees = root.double("player.camera.yaw-degrees", defaults.player.camera.yawDegrees),
                    pitchDegrees = root.double("player.camera.pitch-degrees", defaults.player.camera.pitchDegrees),
                    yawOffsetDegrees = root.double("player.camera.yaw-offset-degrees", defaults.player.camera.yawOffsetDegrees),
                    pitchOffsetDegrees = root.double("player.camera.pitch-offset-degrees", defaults.player.camera.pitchOffsetDegrees)
                )
            ),
            scene = SceneConfig(
                distanceBlocks = root.double("scene.distance-blocks", defaults.scene.distanceBlocks),
                forwardYawDegrees = root.double("scene.forward-yaw-degrees", defaults.scene.forwardYawDegrees),
                lateralOffsetBlocks = root.double("scene.lateral-offset-blocks", defaults.scene.lateralOffsetBlocks),
                centerHeightBlocks = root.double("scene.center-height-blocks", defaults.scene.centerHeightBlocks),
                rotationYawDegrees = root.double("scene.rotation.yaw-degrees", defaults.scene.rotationYawDegrees),
                rotationPitchDegrees = root.double("scene.rotation.pitch-degrees", defaults.scene.rotationPitchDegrees),
                rotationRollDegrees = root.double("scene.rotation.roll-degrees", defaults.scene.rotationRollDegrees)
            ),
            font = FontConfig(
                preset = root.string("font.preset", defaults.font.preset),
                alphabet = root.string("font.alphabet", defaults.font.alphabet),
                customGlyphs = root.customGlyphs("font.custom-glyphs")
            ),
            geometry = GeometryConfig(
                pixelWidth = root.int("geometry.pixel-width", defaults.geometry.pixelWidth),
                pixelHeight = root.int("geometry.pixel-height", defaults.geometry.pixelHeight),
                depthBlocks = root.int("geometry.depth-blocks", defaults.geometry.depthBlocks),
                letterGapBlocks = root.int("geometry.letter-gap-blocks", defaults.geometry.letterGapBlocks),
                centerText = root.bool("geometry.center-text", defaults.geometry.centerText)
            ),
            randomness = RandomnessConfig(
                enabled = root.bool("randomness.enabled", defaults.randomness.enabled),
                seedMode = root.string("randomness.seed-mode", defaults.randomness.seedMode),
                fixedSeed = root.long("randomness.fixed-seed", defaults.randomness.fixedSeed),
                character = CharacterRandomnessConfig(
                    horizontalJitterBlocks = root.int(
                        "randomness.character.horizontal-jitter-blocks",
                        defaults.randomness.character.horizontalJitterBlocks
                    ),
                    verticalJitterBlocks = root.int(
                        "randomness.character.vertical-jitter-blocks",
                        defaults.randomness.character.verticalJitterBlocks
                    ),
                    depthJitterBlocks = root.int(
                        "randomness.character.depth-jitter-blocks",
                        defaults.randomness.character.depthJitterBlocks
                    )
                ),
                scene = SceneRandomnessConfig(
                    distanceJitterBlocks = root.double(
                        "randomness.scene.distance-jitter-blocks",
                        defaults.randomness.scene.distanceJitterBlocks
                    ),
                    heightJitterBlocks = root.double(
                        "randomness.scene.height-jitter-blocks",
                        defaults.randomness.scene.heightJitterBlocks
                    ),
                    forwardYawJitterDegrees = root.double(
                        "randomness.scene.forward-yaw-jitter-degrees",
                        defaults.randomness.scene.forwardYawJitterDegrees
                    ),
                    rotationYawJitterDegrees = root.double(
                        "randomness.scene.rotation-yaw-jitter-degrees",
                        defaults.randomness.scene.rotationYawJitterDegrees
                    ),
                    rotationPitchJitterDegrees = root.double(
                        "randomness.scene.rotation-pitch-jitter-degrees",
                        defaults.randomness.scene.rotationPitchJitterDegrees
                    ),
                    rotationRollJitterDegrees = root.double(
                        "randomness.scene.rotation-roll-jitter-degrees",
                        defaults.randomness.scene.rotationRollJitterDegrees
                    )
                )
            ),
            palette = PaletteConfig(
                mode = root.string("palette.mode", defaults.palette.mode),
                front = MaterialGroup(root.weightedMaterials("palette.front", defaults.palette.front.materials)),
                side = MaterialGroup(root.weightedMaterials("palette.side", defaults.palette.side.materials)),
                back = MaterialGroup(root.weightedMaterials("palette.back", defaults.palette.back.materials))
            ),
            noise = NoiseConfig(
                enabled = root.bool("noise.enabled", defaults.noise.enabled),
                count = root.int("noise.count", defaults.noise.count),
                horizontalPaddingBlocks = root.int("noise.horizontal-padding-blocks", defaults.noise.horizontalPaddingBlocks),
                verticalPaddingBlocks = root.int("noise.vertical-padding-blocks", defaults.noise.verticalPaddingBlocks),
                depthMinBlocks = root.int("noise.depth-min-blocks", defaults.noise.depthMinBlocks),
                depthMaxBlocks = root.int("noise.depth-max-blocks", defaults.noise.depthMaxBlocks),
                materials = root.weightedMaterials("noise.materials", defaults.noise.materials)
            ),
            messages = MessageConfig(
                prefix = root.string("messages.prefix", defaults.messages.prefix),
                prompt = root.string("messages.prompt", defaults.messages.prompt),
                wrong = root.string("messages.wrong", defaults.messages.wrong),
                passed = root.string("messages.passed", defaults.messages.passed),
                timeout = root.string("messages.timeout", defaults.messages.timeout),
                tooManyAttempts = root.string("messages.too-many-attempts", defaults.messages.tooManyAttempts),
                busy = root.string("messages.busy", defaults.messages.busy),
                rateLimited = root.string("messages.rate-limited", defaults.messages.rateLimited),
                unavailable = root.string("messages.unavailable", defaults.messages.unavailable),
                targetMissing = root.string("messages.target-missing", defaults.messages.targetMissing)
            )
        )
    }

    private fun copyDefault(path: Path) {
        val stream = CaptchaConfigLoader::class.java.getResourceAsStream("/config.yml")
            ?: error("Bundled config.yml is missing from pnCaptcha jar")
        stream.use { Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun Map<String, Any?>.value(path: String): Any? {
        var current: Any? = this
        for (part in path.split('.')) {
            current = (current as? Map<*, *>)?.get(part) ?: return null
        }
        return current
    }

    private fun Map<String, Any?>.string(path: String, default: String): String =
        value(path)?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: default

    private fun Map<String, Any?>.int(path: String, default: Int): Int =
        when (val value = value(path)) {
            is Number -> value.toInt()
            else -> value?.toString()?.trim()?.toIntOrNull()
        } ?: default

    private fun Map<String, Any?>.long(path: String, default: Long): Long =
        when (val value = value(path)) {
            is Number -> value.toLong()
            else -> value?.toString()?.trim()?.toLongOrNull()
        } ?: default

    private fun Map<String, Any?>.double(path: String, default: Double): Double =
        when (val value = value(path)) {
            is Number -> value.toDouble()
            else -> value?.toString()?.trim()?.toDoubleOrNull()
        } ?: default

    private fun Map<String, Any?>.bool(path: String, default: Boolean): Boolean =
        when (val value = value(path)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> when (value?.toString()?.trim()?.lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> default
            }
        }

    private fun Map<String, Any?>.weightedMaterials(
        path: String,
        defaults: List<WeightedMaterial>
    ): List<WeightedMaterial> {
        val raw = value(path) as? List<*> ?: return defaults
        val parsed = raw.mapNotNull { entry ->
            when (entry) {
                is String -> WeightedMaterial(entry.trim(), 1).takeIf { it.block.isNotEmpty() }
                is Map<*, *> -> {
                    val block = entry["block"]?.toString()?.trim().orEmpty()
                    val weight = when (val weightValue = entry["weight"]) {
                        is Number -> weightValue.toInt()
                        else -> weightValue?.toString()?.toIntOrNull() ?: 1
                    }
                    if (block.isNotEmpty() && weight > 0) WeightedMaterial(block, weight) else null
                }
                else -> null
            }
        }
        return parsed.ifEmpty { defaults }
    }

    private fun Map<String, Any?>.customGlyphs(path: String): Map<Char, List<String>> {
        val raw = value(path) as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val char = key?.toString()?.singleOrNull() ?: return@mapNotNull null
            val rows = (value as? List<*>)?.map { it.toString() } ?: return@mapNotNull null
            char.uppercaseChar() to rows
        }.toMap()
    }
}
