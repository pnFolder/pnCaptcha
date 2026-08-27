package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.util.Random
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object CaptchaScene {
    private const val PLAYER_EYE_HEIGHT = 1.62

    fun resolve(config: CaptchaConfig, random: Random): ResolvedScene {
        val sceneRandom = config.randomness.scene
        val randomEnabled = config.randomness.enabled

        fun jitter(maximum: Double): Double {
            if (!randomEnabled || maximum <= 0.0) return 0.0
            return (random.nextDouble() * 2.0 - 1.0) * maximum
        }

        val spawn = Vec3(
            config.player.spawn.x,
            config.player.spawn.y,
            config.player.spawn.z
        )

        val distance = (config.scene.distanceBlocks + jitter(sceneRandom.distanceJitterBlocks)).coerceAtLeast(1.0)
        val forwardYaw = config.scene.forwardYawDegrees + jitter(sceneRandom.forwardYawJitterDegrees)
        val height = config.scene.centerHeightBlocks + jitter(sceneRandom.heightJitterBlocks)
        val objectYaw = forwardYaw + config.scene.rotationYawDegrees + jitter(sceneRandom.rotationYawJitterDegrees)
        val objectPitch = config.scene.rotationPitchDegrees + jitter(sceneRandom.rotationPitchJitterDegrees)
        val objectRoll = config.scene.rotationRollDegrees + jitter(sceneRandom.rotationRollJitterDegrees)

        val forwardRad = Math.toRadians(forwardYaw)
        val forward = Vec3(-sin(forwardRad), 0.0, cos(forwardRad))
        val lateral = Vec3(-cos(forwardRad), 0.0, sin(forwardRad))
        val center = spawn + forward * distance + lateral * config.scene.lateralOffsetBlocks + Vec3(0.0, height, 0.0)

        val yawRad = Math.toRadians(objectYaw)
        var right = Vec3(-cos(yawRad), 0.0, sin(yawRad))
        var up = Vec3(0.0, 1.0, 0.0)
        var depth = Vec3(sin(yawRad), 0.0, cos(yawRad))

        // Pitch around local right axis.
        val pitchRad = Math.toRadians(objectPitch)
        if (pitchRad != 0.0) {
            val c = cos(pitchRad)
            val s = sin(pitchRad)
            val pitchedUp = up * c - depth * s
            val pitchedDepth = up * s + depth * c
            up = pitchedUp
            depth = pitchedDepth
        }

        // Roll around the already pitched local depth axis.
        val rollRad = Math.toRadians(objectRoll)
        if (rollRad != 0.0) {
            val c = cos(rollRad)
            val s = sin(rollRad)
            val rolledRight = right * c + up * s
            val rolledUp = up * c - right * s
            right = rolledRight
            up = rolledUp
        }

        val camera = if (config.player.camera.autoAim) {
            val eye = Vec3(spawn.x, spawn.y + PLAYER_EYE_HEIGHT, spawn.z)
            val delta = center - eye
            val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z)
            CameraPose(
                x = spawn.x,
                y = spawn.y,
                z = spawn.z,
                yaw = (Math.toDegrees(atan2(-delta.x, delta.z)) + config.player.camera.yawOffsetDegrees).toFloat(),
                pitch = (-Math.toDegrees(atan2(delta.y, horizontal)) + config.player.camera.pitchOffsetDegrees).toFloat()
            )
        } else {
            CameraPose(
                x = spawn.x,
                y = spawn.y,
                z = spawn.z,
                yaw = (config.player.camera.yawDegrees + config.player.camera.yawOffsetDegrees).toFloat(),
                pitch = (config.player.camera.pitchDegrees + config.player.camera.pitchOffsetDegrees).toFloat()
            )
        }

        return ResolvedScene(
            center = center,
            right = right,
            up = up,
            depth = depth,
            camera = camera,
            distanceBlocks = distance,
            forwardYawDegrees = forwardYaw,
            rotationYawDegrees = objectYaw,
            rotationPitchDegrees = objectPitch,
            rotationRollDegrees = objectRoll
        )
    }

    fun chunkBounds(
        config: CaptchaConfig,
        scene: ResolvedScene,
        font: CaptchaFont.ResolvedFont
    ): ChunkBounds {
        val width = config.captchaLength * font.width * config.geometry.pixelWidth +
            (config.captchaLength - 1) * config.geometry.letterGapBlocks
        val height = font.height * config.geometry.pixelHeight

        val uRadius = width / 2.0 + config.randomness.character.horizontalJitterBlocks +
            if (config.noise.enabled) config.noise.horizontalPaddingBlocks else 0
        val vRadius = height / 2.0 + config.randomness.character.verticalJitterBlocks +
            if (config.noise.enabled) config.noise.verticalPaddingBlocks else 0
        val maxDepth = config.geometry.depthBlocks + config.randomness.character.depthJitterBlocks +
            if (config.noise.enabled) config.noise.depthMaxBlocks else 0

        val points = mutableListOf<Vec3>()
        for (u in listOf(-uRadius, uRadius)) {
            for (v in listOf(-vRadius, vRadius)) {
                for (d in listOf(-config.randomness.character.depthJitterBlocks.toDouble(), maxDepth.toDouble())) {
                    points += scene.transform(u, v, d)
                }
            }
        }

        val pedestalX = floor(config.player.spawn.x).toInt()
        val pedestalZ = floor(config.player.spawn.z).toInt()
        val minBlockX = minOf(points.minOf { floor(it.x).toInt() }, pedestalX)
        val maxBlockX = maxOf(points.maxOf { ceil(it.x).toInt() }, pedestalX)
        val minBlockZ = minOf(points.minOf { floor(it.z).toInt() }, pedestalZ)
        val maxBlockZ = maxOf(points.maxOf { ceil(it.z).toInt() }, pedestalZ)

        return ChunkBounds(
            minX = minBlockX shr 4,
            maxX = maxBlockX shr 4,
            minZ = minBlockZ shr 4,
            maxZ = maxBlockZ shr 4
        )
    }

    fun recommendedViewDistance(config: CaptchaConfig, bounds: ChunkBounds): Int {
        val spawnChunkX = floor(config.player.spawn.x).toInt() shr 4
        val spawnChunkZ = floor(config.player.spawn.z).toInt() shr 4
        val radius = max(
            max(kotlin.math.abs(bounds.minX - spawnChunkX), kotlin.math.abs(bounds.maxX - spawnChunkX)),
            max(kotlin.math.abs(bounds.minZ - spawnChunkZ), kotlin.math.abs(bounds.maxZ - spawnChunkZ))
        ) + 1
        return max(config.limbo.viewDistance, radius).coerceAtMost(32)
    }

    data class ResolvedScene(
        val center: Vec3,
        val right: Vec3,
        val up: Vec3,
        val depth: Vec3,
        val camera: CameraPose,
        val distanceBlocks: Double,
        val forwardYawDegrees: Double,
        val rotationYawDegrees: Double,
        val rotationPitchDegrees: Double,
        val rotationRollDegrees: Double
    ) {
        fun transform(localU: Double, localV: Double, localDepth: Double): Vec3 =
            center + right * localU + up * localV + depth * localDepth
    }

    data class CameraPose(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    )

    data class ChunkBounds(
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int
    )

    data class Vec3(val x: Double, val y: Double, val z: Double) {
        operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
        operator fun times(value: Double): Vec3 = Vec3(x * value, y * value, z * value)
    }
}
