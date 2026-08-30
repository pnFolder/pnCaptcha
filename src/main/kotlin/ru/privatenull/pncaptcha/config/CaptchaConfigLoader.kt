package ru.privatenull.pncaptcha.config

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object CaptchaConfigLoader {
    private const val FILE_NAME = "config.yml"
    private const val CONFIG_VERSION = 3

    fun load(dataDirectory: Path): CaptchaConfig {
        Files.createDirectories(dataDirectory)
        val path = dataDirectory.resolve(FILE_NAME)

        if (Files.notExists(path)) {
            copyDefault(path)
        } else {
            val existing = readRoot(path)
            if (existing.int("config-version", 0) != CONFIG_VERSION) {
                val backup = dataDirectory.resolve("config.pre-1.1.0.yml.bak")
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
                copyDefault(path)
            }
        }

        val root = readRoot(path)
        val defaults = CaptchaConfig()

        return CaptchaConfig(
            general = GeneralConfig(
                captchaLength = root.int("general.captcha-length", defaults.general.captchaLength),
                maxAttempts = root.int("general.max-attempts", defaults.general.maxAttempts),
                timeoutSeconds = root.long("general.timeout-seconds", defaults.general.timeoutSeconds),
                verifiedCacheMinutes = root.long("general.verified-cache-minutes", defaults.general.verifiedCacheMinutes),
                input = InputConfig(
                    caseSensitive = root.bool("general.input.case-sensitive", defaults.general.input.caseSensitive),
                    trim = root.bool("general.input.trim", defaults.general.input.trim),
                    removeSpaces = root.bool("general.input.remove-spaces", defaults.general.input.removeSpaces),
                    maxLength = root.int("general.input.max-length", defaults.general.input.maxLength)
                )
            ),
            routing = RoutingConfig(
                strategy = root.string("routing.strategy", defaults.routing.strategy),
                networkMaxPlayers = root.int("routing.network-max-players", defaults.routing.networkMaxPlayers),
                networkReserveSlots = root.int("routing.network-reserve-slots", defaults.routing.networkReserveSlots),
                fullBypassPermission = root.string("routing.full-bypass-permission", defaults.routing.fullBypassPermission),
                fallbackToAnyRegistered = root.bool("routing.fallback-to-any-registered", defaults.routing.fallbackToAnyRegistered),
                servers = root.routeServers("routing.servers", defaults.routing.servers)
            ),
            security = SecurityConfig(
                maxJoinsPerWindow = root.int("security.max-joins-per-window", defaults.security.maxJoinsPerWindow),
                joinWindowSeconds = root.long("security.join-window-seconds", defaults.security.joinWindowSeconds),
                maxActiveCaptchas = root.int("security.max-active-captchas", defaults.security.maxActiveCaptchas),
                bypassPermission = root.string("security.bypass-permission", defaults.security.bypassPermission)
            ),
            metrics = MetricsConfig(
                enabled = root.bool("metrics.enabled", defaults.metrics.enabled),
                customCharts = root.bool("metrics.custom-charts", defaults.metrics.customCharts)
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
                worldTimeTicks = root.long("limbo.world-time-ticks", defaults.limbo.worldTimeTicks),
                fallingEnabled = root.bool("limbo.falling-enabled", defaults.limbo.fallingEnabled),
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
                    maxHorizontalDistanceBlocks = root.double("player.recovery.max-horizontal-distance-blocks", defaults.player.recovery.maxHorizontalDistanceBlocks),
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
                extrudeTowardCamera = root.bool("geometry.extrude-toward-camera", defaults.geometry.extrudeTowardCamera),
                fill = FillConfig(
                    mode = root.string("geometry.fill.mode", defaults.geometry.fill.mode),
                    density = root.double("geometry.fill.density", defaults.geometry.fill.density),
                    preserveConnectivity = root.bool("geometry.fill.preserve-connectivity", defaults.geometry.fill.preserveConnectivity),
                    protectEndpoints = root.bool("geometry.fill.protect-endpoints", defaults.geometry.fill.protectEndpoints),
                    outlinePreservePercent = root.double("geometry.fill.outline-preserve-percent", defaults.geometry.fill.outlinePreservePercent),
                    minRetainedPixels = root.int("geometry.fill.min-retained-pixels", defaults.geometry.fill.minRetainedPixels)
                )
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
                clusterSizeMin = root.int("palette.cluster-size-min", defaults.palette.clusterSizeMin),
                clusterSizeMax = root.int("palette.cluster-size-max", defaults.palette.clusterSizeMax),
                front = MaterialGroup(root.weightedMaterials("palette.front", defaults.palette.front.materials)),
                side = MaterialGroup(root.weightedMaterials("palette.side", defaults.palette.side.materials)),
                back = MaterialGroup(root.weightedMaterials("palette.back", defaults.palette.back.materials)),
                accent = AccentConfig(
                    enabled = root.bool("palette.accent.enabled", defaults.palette.accent.enabled),
                    chancePercent = root.double("palette.accent.chance-percent", defaults.palette.accent.chancePercent),
                    group = MaterialGroup(root.weightedMaterials("palette.accent.materials", defaults.palette.accent.group.materials))
                ),
                outline = OutlineConfig(
                    enabled = root.bool("palette.outline.enabled", defaults.palette.outline.enabled),
                    chancePercent = root.double("palette.outline.chance-percent", defaults.palette.outline.chancePercent),
                    group = MaterialGroup(root.weightedMaterials("palette.outline.materials", defaults.palette.outline.group.materials))
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
            actions = ActionsConfig(
                enabled = root.bool("actions.enabled", defaults.actions.enabled),
                triggers = root.actionTriggers("actions.triggers")
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
                networkFull = root.stringList("messages.network-full", defaults.messages.networkFull),
                unavailable = root.stringList("messages.unavailable", defaults.messages.unavailable),
                routeUnavailable = root.stringList("messages.route-unavailable", defaults.messages.routeUnavailable),
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
                    val weight = entry.intValue("weight", 1)
                    if (block.isNotEmpty() && weight > 0) WeightedMaterial(block, weight) else null
                }
                else -> null
            }
        }
        return parsed.ifEmpty { defaults }
    }

    private fun Map<String, Any?>.routeServers(path: String, defaults: List<RouteServerConfig>): List<RouteServerConfig> {
        val raw = value(path) as? List<*> ?: return defaults
        val parsed = raw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            RouteServerConfig(
                name = name,
                enabled = map.boolValue("enabled", true),
                priority = map.intValue("priority", 100),
                weight = map.intValue("weight", 1),
                maxPlayers = map.intValue("max-players", 0),
                reserveSlots = map.intValue("reserve-slots", 0)
            )
        }
        return parsed.ifEmpty { defaults }
    }

    private fun Map<String, Any?>.actionTriggers(path: String): Map<String, List<ActionDefinition>> {
        val raw = value(path) as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (triggerKey, rawActions) ->
            val trigger = triggerKey?.toString()?.trim()?.lowercase().orEmpty()
            if (trigger.isEmpty()) return@mapNotNull null
            val actions = (rawActions as? List<*>)?.mapNotNull { actionEntry ->
                val map = actionEntry as? Map<*, *> ?: return@mapNotNull null
                ActionDefinition(
                    enabled = map.boolValue("enabled", true),
                    type = map["type"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "message" },
                    delayMillis = map.longValue("delay-ms", 0L),
                    chancePercent = map.doubleValue("chance-percent", 100.0),
                    permission = map["permission"]?.toString()?.trim().orEmpty(),
                    stopAfter = map.boolValue("stop-after", false),
                    text = map["text"]?.toString().orEmpty(),
                    lines = map.stringListValue("lines"),
                    command = map["command"]?.toString().orEmpty(),
                    server = map["server"]?.toString()?.trim().orEmpty(),
                    x = map.nullableDouble("x"),
                    y = map.nullableDouble("y"),
                    z = map.nullableDouble("z"),
                    teleportYaw = map.nullableDouble("yaw")?.toFloat(),
                    teleportPitch = map.nullableDouble("pitch")?.toFloat(),
                    title = map["title"]?.toString().orEmpty(),
                    subtitle = map["subtitle"]?.toString().orEmpty(),
                    fadeInMillis = map.longValue("fade-in-ms", 250L),
                    stayMillis = map.longValue("stay-ms", 1500L),
                    fadeOutMillis = map.longValue("fade-out-ms", 250L),
                    sound = map["sound"]?.toString()?.trim().orEmpty().ifEmpty { "minecraft:block.note_block.pling" },
                    source = map["source"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "master" },
                    volume = map.doubleValue("volume", 1.0).toFloat(),
                    soundPitch = map.doubleValue("sound-pitch", 1.0).toFloat(),
                    gameMode = map["game-mode"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "adventure" },
                    bossBarId = map["bossbar-id"]?.toString()?.trim().orEmpty().ifEmpty { "captcha" },
                    bossBarOperation = map["bossbar-operation"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "show" },
                    bossBarColor = map["bossbar-color"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "blue" },
                    bossBarOverlay = map["bossbar-overlay"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "progress" },
                    bossBarProgress = map.nullableDouble("bossbar-progress"),
                    bossBarProgressDelta = map.doubleValue("bossbar-progress-delta", 0.0),
                    bossBarStartProgress = map.nullableDouble("bossbar-start-progress"),
                    bossBarEndProgress = map.nullableDouble("bossbar-end-progress"),
                    bossBarDurationMillis = map.longValue("bossbar-duration-ms", 0L),
                    bossBarUpdateIntervalMillis = map.longValue("bossbar-update-interval-ms", 100L),
                    bossBarHideOnFinish = map.boolValue("bossbar-hide-on-finish", false),
                    bossBarRemoveOnFinish = map.boolValue("bossbar-remove-on-finish", false)
                )
            }.orEmpty()
            trigger to actions
        }.toMap()
    }

    private fun Map<String, Any?>.customGlyphs(path: String): Map<Char, List<String>> {
        val raw = value(path) as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val char = key?.toString()?.singleOrNull() ?: return@mapNotNull null
            val rows = (value as? List<*>)?.map { it?.toString().orEmpty() }.orEmpty()
            if (rows.isEmpty()) null else char to rows
        }.toMap()
    }

    private fun Map<*, *>.boolValue(key: String, default: Boolean): Boolean = when (val raw = this[key]) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> when (raw?.toString()?.trim()?.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> default
        }
    }

    private fun Map<*, *>.intValue(key: String, default: Int): Int = when (val raw = this[key]) {
        is Number -> raw.toInt()
        else -> raw?.toString()?.trim()?.toIntOrNull()
    } ?: default

    private fun Map<*, *>.longValue(key: String, default: Long): Long = when (val raw = this[key]) {
        is Number -> raw.toLong()
        else -> raw?.toString()?.trim()?.toLongOrNull()
    } ?: default

    private fun Map<*, *>.doubleValue(key: String, default: Double): Double = when (val raw = this[key]) {
        is Number -> raw.toDouble()
        else -> raw?.toString()?.trim()?.toDoubleOrNull()
    } ?: default

    private fun Map<*, *>.nullableDouble(key: String): Double? = when (val raw = this[key]) {
        is Number -> raw.toDouble()
        else -> raw?.toString()?.trim()?.toDoubleOrNull()
    }

    private fun Map<*, *>.stringListValue(key: String): List<String> = when (val raw = this[key]) {
        is List<*> -> raw.mapNotNull { it?.toString() }
        is String -> listOf(raw)
        else -> emptyList()
    }
}
