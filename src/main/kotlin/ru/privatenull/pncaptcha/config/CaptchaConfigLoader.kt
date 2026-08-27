package ru.privatenull.pncaptcha.config

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object CaptchaConfigLoader {
    private const val FILE_NAME = "config.yml"
    private const val CONFIG_VERSION = 1

    fun load(dataDirectory: Path): CaptchaConfig {
        Files.createDirectories(dataDirectory)
        val path = dataDirectory.resolve(FILE_NAME)

        if (Files.notExists(path)) {
            copyDefault(path)
        } else {
            val existing = readRoot(path)
            if (existing.int("config-version", 0) != CONFIG_VERSION) {
                val backup = dataDirectory.resolve("config.pre-0.5.0.yml.bak")
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
                copyDefault(path)
            }
        }

        val root = readRoot(path)
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
            updates = UpdateConfig(
                enabled = root.bool("updates.enabled", defaults.updates.enabled),
                checkOnStartup = root.bool("updates.check-on-startup", defaults.updates.checkOnStartup),
                startupDelaySeconds = root.long("updates.startup-delay-seconds", defaults.updates.startupDelaySeconds),
                repository = root.string("updates.repository", defaults.updates.repository),
                requestTimeoutSeconds = root.long("updates.request-timeout-seconds", defaults.updates.requestTimeoutSeconds),
                notifyConsole = root.bool("updates.notify-console", defaults.updates.notifyConsole),
                notifyPlayers = root.bool("updates.notify-players", defaults.updates.notifyPlayers),
                notifyPermission = root.string("updates.notify-permission", defaults.updates.notifyPermission),
                announceUpToDate = root.bool("updates.announce-up-to-date", defaults.updates.announceUpToDate)
            ),
            limbo = LimboConfig(
                viewDistance = root.int("limbo.view-distance", defaults.limbo.viewDistance),
                simulationDistance = root.int("limbo.simulation-distance", defaults.limbo.simulationDistance),
                autoExpandViewDistance = root.bool("limbo.auto-expand-view-distance", defaults.limbo.autoExpandViewDistance),
                maxAutoViewDistance = root.int("limbo.max-auto-view-distance", defaults.limbo.maxAutoViewDistance),
                precreatePaddingChunks = root.int("limbo.precreate-padding-chunks", defaults.limbo.precreatePaddingChunks),
                reducedDebugInfo = root.bool("limbo.reduced-debug-info", defaults.limbo.reducedDebugInfo),
                skyLightLevel = root.int("limbo.light.sky", defaults.limbo.skyLightLevel),
                blockLightLevel = root.int("limbo.light.block", defaults.limbo.blockLightLevel),
                pedestal = PedestalConfig(
                    enabled = root.bool("limbo.pedestal.enabled", defaults.limbo.pedestal.enabled),
                    block = root.string("limbo.pedestal.block", defaults.limbo.pedestal.block),
                    sizeX = root.int("limbo.pedestal.size-x", defaults.limbo.pedestal.sizeX),
                    sizeZ = root.int("limbo.pedestal.size-z", defaults.limbo.pedestal.sizeZ),
                    yOffset = root.int("limbo.pedestal.y-offset", defaults.limbo.pedestal.yOffset)
                )
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
                ),
                recovery = RecoveryConfig(
                    enabled = root.bool("player.recovery.enabled", defaults.player.recovery.enabled),
                    belowSpawnBlocks = root.double("player.recovery.below-spawn-blocks", defaults.player.recovery.belowSpawnBlocks),
                    aboveSpawnBlocks = root.double("player.recovery.above-spawn-blocks", defaults.player.recovery.aboveSpawnBlocks),
                    maxHorizontalDistanceBlocks = root.double(
                        "player.recovery.max-horizontal-distance-blocks",
                        defaults.player.recovery.maxHorizontalDistanceBlocks
                    ),
                    cooldownMillis = root.long("player.recovery.cooldown-millis", defaults.player.recovery.cooldownMillis),
                    preserveCurrentLook = root.bool("player.recovery.preserve-current-look", defaults.player.recovery.preserveCurrentLook),
                    sendMessage = root.bool("player.recovery.send-message", defaults.player.recovery.sendMessage)
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
                frontThicknessBlocks = root.int("geometry.front-thickness-blocks", defaults.geometry.frontThicknessBlocks),
                backThicknessBlocks = root.int("geometry.back-thickness-blocks", defaults.geometry.backThicknessBlocks),
                letterGapBlocks = root.int("geometry.letter-gap-blocks", defaults.geometry.letterGapBlocks),
                letterRiseBlocks = root.double("geometry.letter-rise-blocks", defaults.geometry.letterRiseBlocks),
                letterDepthStepBlocks = root.double("geometry.letter-depth-step-blocks", defaults.geometry.letterDepthStepBlocks),
                centerText = root.bool("geometry.center-text", defaults.geometry.centerText),
                mirrorHorizontal = root.bool("geometry.mirror-horizontal", defaults.geometry.mirrorHorizontal),
                mirrorVertical = root.bool("geometry.mirror-vertical", defaults.geometry.mirrorVertical),
                extrudeTowardCamera = root.bool("geometry.extrude-toward-camera", defaults.geometry.extrudeTowardCamera)
            ),
            randomness = RandomnessConfig(
                enabled = root.bool("randomness.enabled", defaults.randomness.enabled),
                seedMode = root.string("randomness.seed-mode", defaults.randomness.seedMode),
                fixedSeed = root.long("randomness.fixed-seed", defaults.randomness.fixedSeed),
                character = CharacterRandomnessConfig(
                    horizontalJitterBlocks = root.int("randomness.character.horizontal-jitter-blocks", defaults.randomness.character.horizontalJitterBlocks),
                    verticalJitterBlocks = root.int("randomness.character.vertical-jitter-blocks", defaults.randomness.character.verticalJitterBlocks),
                    depthJitterBlocks = root.int("randomness.character.depth-jitter-blocks", defaults.randomness.character.depthJitterBlocks),
                    depthVariationBlocks = root.int("randomness.character.depth-variation-blocks", defaults.randomness.character.depthVariationBlocks),
                    rotationYawJitterDegrees = root.double("randomness.character.rotation-yaw-jitter-degrees", defaults.randomness.character.rotationYawJitterDegrees),
                    rotationPitchJitterDegrees = root.double("randomness.character.rotation-pitch-jitter-degrees", defaults.randomness.character.rotationPitchJitterDegrees),
                    rotationRollJitterDegrees = root.double("randomness.character.rotation-roll-jitter-degrees", defaults.randomness.character.rotationRollJitterDegrees)
                ),
                scene = SceneRandomnessConfig(
                    distanceJitterBlocks = root.double("randomness.scene.distance-jitter-blocks", defaults.randomness.scene.distanceJitterBlocks),
                    heightJitterBlocks = root.double("randomness.scene.height-jitter-blocks", defaults.randomness.scene.heightJitterBlocks),
                    lateralJitterBlocks = root.double("randomness.scene.lateral-jitter-blocks", defaults.randomness.scene.lateralJitterBlocks),
                    forwardYawJitterDegrees = root.double("randomness.scene.forward-yaw-jitter-degrees", defaults.randomness.scene.forwardYawJitterDegrees),
                    rotationYawJitterDegrees = root.double("randomness.scene.rotation-yaw-jitter-degrees", defaults.randomness.scene.rotationYawJitterDegrees),
                    rotationPitchJitterDegrees = root.double("randomness.scene.rotation-pitch-jitter-degrees", defaults.randomness.scene.rotationPitchJitterDegrees),
                    rotationRollJitterDegrees = root.double("randomness.scene.rotation-roll-jitter-degrees", defaults.randomness.scene.rotationRollJitterDegrees)
                )
            ),
            palette = PaletteConfig(
                mode = root.string("palette.mode", defaults.palette.mode),
                front = MaterialGroup(root.weightedMaterials("palette.front", defaults.palette.front.materials)),
                side = MaterialGroup(root.weightedMaterials("palette.side", defaults.palette.side.materials)),
                back = MaterialGroup(root.weightedMaterials("palette.back", defaults.palette.back.materials)),
                accent = AccentConfig(
                    enabled = root.bool("palette.accent.enabled", defaults.palette.accent.enabled),
                    chancePercent = root.double("palette.accent.chance-percent", defaults.palette.accent.chancePercent),
                    group = MaterialGroup(root.weightedMaterials("palette.accent.materials", defaults.palette.accent.group.materials))
                )
            ),
            noise = NoiseConfig(
                enabled = root.bool("noise.enabled", defaults.noise.enabled),
                count = root.int("noise.count", defaults.noise.count),
                horizontalPaddingBlocks = root.int("noise.horizontal-padding-blocks", defaults.noise.horizontalPaddingBlocks),
                verticalPaddingBlocks = root.int("noise.vertical-padding-blocks", defaults.noise.verticalPaddingBlocks),
                depthMinBlocks = root.int("noise.depth-min-blocks", defaults.noise.depthMinBlocks),
                depthMaxBlocks = root.int("noise.depth-max-blocks", defaults.noise.depthMaxBlocks),
                clusterSizeMin = root.int("noise.cluster-size-min", defaults.noise.clusterSizeMin),
                clusterSizeMax = root.int("noise.cluster-size-max", defaults.noise.clusterSizeMax),
                materials = root.weightedMaterials("noise.materials", defaults.noise.materials)
            ),
            messages = MessageConfig(
                enabled = root.bool("messages.enabled", defaults.messages.enabled),
                prompt = root.stringList("messages.prompt", defaults.messages.prompt),
                wrong = root.stringList("messages.wrong", defaults.messages.wrong),
                passed = root.stringList("messages.passed", defaults.messages.passed),
                recovered = root.stringList("messages.recovered", defaults.messages.recovered),
                timeout = root.stringList("messages.timeout", defaults.messages.timeout),
                tooManyAttempts = root.stringList("messages.too-many-attempts", defaults.messages.tooManyAttempts),
                busy = root.stringList("messages.busy", defaults.messages.busy),
                rateLimited = root.stringList("messages.rate-limited", defaults.messages.rateLimited),
                unavailable = root.stringList("messages.unavailable", defaults.messages.unavailable),
                targetMissing = root.stringList("messages.target-missing", defaults.messages.targetMissing),
                updateAvailable = root.stringList("messages.update-available", defaults.messages.updateAvailable)
            )
        )
    }

    private fun readRoot(path: Path): Map<String, Any?> = Files.newBufferedReader(path).use { reader ->
        @Suppress("UNCHECKED_CAST")
        (Yaml().load<Any?>(reader) as? Map<String, Any?>).orEmpty()
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

    private fun Map<String, Any?>.stringList(path: String, default: List<String>): List<String> = when (val raw = value(path)) {
        is List<*> -> raw.mapNotNull { it?.toString() }.ifEmpty { default }
        is String -> listOf(raw)
        else -> default
    }

    private fun Map<String, Any?>.int(path: String, default: Int): Int = when (val raw = value(path)) {
        is Number -> raw.toInt()
        else -> raw?.toString()?.trim()?.toIntOrNull()
    } ?: default

    private fun Map<String, Any?>.long(path: String, default: Long): Long = when (val raw = value(path)) {
        is Number -> raw.toLong()
        else -> raw?.toString()?.trim()?.toLongOrNull()
    } ?: default

    private fun Map<String, Any?>.double(path: String, default: Double): Double = when (val raw = value(path)) {
        is Number -> raw.toDouble()
        else -> raw?.toString()?.trim()?.toDoubleOrNull()
    } ?: default

    private fun Map<String, Any?>.bool(path: String, default: Boolean): Boolean = when (val raw = value(path)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> when (raw?.toString()?.trim()?.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> default
        }
    }

    private fun Map<String, Any?>.weightedMaterials(path: String, defaults: List<WeightedMaterial>): List<WeightedMaterial> {
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
