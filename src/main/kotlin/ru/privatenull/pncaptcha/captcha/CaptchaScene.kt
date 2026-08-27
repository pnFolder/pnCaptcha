package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Shared scene geometry used by both Limbo and the packet renderer.
 *
 * The player stays on one physical block at the origin. The CAPTCHA front face
 * is placed [captchaDistanceBlocks] away and its whole local X/Z coordinate
 * system is rotated by [captchaAngleDegrees]. That means the characters
 * themselves form one genuinely angled 3D sculpture instead of a flat line
 * viewed from a slightly displaced camera.
 */
object CaptchaScene {
    const val SPAWN_X: Double = 0.5
    const val SPAWN_Y: Double = 65.0
    const val SPAWN_Z: Double = 0.5

    const val PEDESTAL_X: Int = 0
    const val PEDESTAL_Y: Int = 64
    const val PEDESTAL_Z: Int = 0

    private const val PLAYER_EYE_HEIGHT = 1.62

    fun centerX(config: CaptchaConfig): Double = SPAWN_X

    fun centerY(config: CaptchaConfig): Double =
        SPAWN_Y + config.captchaCenterHeightBlocks

    fun centerZ(config: CaptchaConfig): Double =
        SPAWN_Z + config.captchaDistanceBlocks

    /** Screen-right direction on the rotated front face. */
    fun rightX(config: CaptchaConfig): Double = -cos(angleRadians(config))
    fun rightZ(config: CaptchaConfig): Double = sin(angleRadians(config))

    /** Direction from the bright front face into the dark 3D body. */
    fun depthX(config: CaptchaConfig): Double = sin(angleRadians(config))
    fun depthZ(config: CaptchaConfig): Double = cos(angleRadians(config))

    /**
     * The camera always aims at the configured CAPTCHA centre. The extra pitch
     * offset remains configurable for artistic fine tuning.
     */
    fun spawnYaw(config: CaptchaConfig): Float {
        val dx = centerX(config) - SPAWN_X
        val dz = centerZ(config) - SPAWN_Z
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }

    fun spawnPitch(config: CaptchaConfig): Float {
        val dx = centerX(config) - SPAWN_X
        val dz = centerZ(config) - SPAWN_Z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        val eyeY = SPAWN_Y + PLAYER_EYE_HEIGHT
        val dy = centerY(config) - eyeY
        val autoPitch = -Math.toDegrees(atan2(dy, horizontal))
        return (autoPitch + config.cameraPitchOffsetDegrees).toFloat()
    }

    /**
     * Conservative chunk bounds for the widest configured challenge. Limbo can
     * pre-create these chunks, while PacketCaptchaRenderer still owns the fake
     * blocks themselves.
     */
    fun chunkBounds(config: CaptchaConfig): ChunkBounds {
        val width = config.captchaLength * CaptchaFont.WIDTH * config.glyphScaleX +
            (config.captchaLength - 1) * config.glyphGapBlocks
        val halfWidth = width / 2.0 + 2.0
        val depth = config.glyphDepth + config.glyphJitterDepthBlocks + 2.0

        val rx = rightX(config)
        val rz = rightZ(config)
        val dx = depthX(config)
        val dz = depthZ(config)

        val centerX = centerX(config)
        val centerZ = centerZ(config)

        val xRadius = halfWidth * kotlin.math.abs(rx) + depth * kotlin.math.abs(dx)
        val zRadius = halfWidth * kotlin.math.abs(rz) + depth * kotlin.math.abs(dz)

        val minBlockX = floor(centerX - xRadius).toInt()
        val maxBlockX = ceil(centerX + xRadius).toInt()
        val minBlockZ = floor(centerZ - zRadius).toInt()
        val maxBlockZ = ceil(centerZ + zRadius).toInt()

        return ChunkBounds(
            minX = minOf(PEDESTAL_X shr 4, minBlockX shr 4),
            maxX = maxOf(PEDESTAL_X shr 4, maxBlockX shr 4),
            minZ = minOf(PEDESTAL_Z shr 4, minBlockZ shr 4),
            maxZ = maxOf(PEDESTAL_Z shr 4, maxBlockZ shr 4)
        )
    }

    fun recommendedViewDistance(config: CaptchaConfig): Int {
        val bounds = chunkBounds(config)
        return max(
            4,
            max(
                max(kotlin.math.abs(bounds.minX), kotlin.math.abs(bounds.maxX)),
                max(kotlin.math.abs(bounds.minZ), kotlin.math.abs(bounds.maxZ))
            ) + 1
        ).coerceAtMost(10)
    }

    private fun angleRadians(config: CaptchaConfig): Double =
        Math.toRadians(config.captchaAngleDegrees)

    data class ChunkBounds(
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int
    )
}
