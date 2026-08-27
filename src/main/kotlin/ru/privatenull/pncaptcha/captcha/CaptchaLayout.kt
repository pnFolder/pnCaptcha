package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.security.SecureRandom
import java.util.Random
import kotlin.math.roundToInt

/**
 * Builds one client-only CAPTCHA as a genuinely rotated 3D voxel sculpture.
 *
 * Local U is screen-left -> screen-right across the text. Local D is depth from
 * the bright face into the dark body. Both axes are rotated around world Y by
 * captcha-angle-degrees, so the front faces themselves are no longer arranged
 * on one straight world-Z line. One side of the text is physically farther from
 * the player, exactly like viewing a thick sign from an angle.
 */
class CaptchaLayout(
    private val random: Random = SecureRandom()
) {
    fun build(answer: String, config: CaptchaConfig): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }

        val frontBlocks = LinkedHashMap<BlockPos, String>()
        val sideBlocks = LinkedHashMap<BlockPos, String>()

        val glyphWidth = CaptchaFont.WIDTH * config.glyphScaleX
        val totalWidth = answer.length * glyphWidth +
            (answer.length - 1) * config.glyphGapBlocks
        val totalHeight = CaptchaFont.HEIGHT * config.glyphScaleY

        // U increases toward screen-right. With angle=0, rightX=-1 preserves
        // the non-mirrored orientation that was fixed in 0.2.2.
        val firstU = -(totalWidth / 2)
        val topY = (CaptchaScene.centerY(config) + (totalHeight - 1) / 2.0).roundToInt()

        answer.forEachIndexed { charIndex, char ->
            val pattern = CaptchaFont.pattern(char)
            val charStartU = firstU + charIndex * (glyphWidth + config.glyphGapBlocks)
            val charJitterY = signedJitter(config.glyphJitterYBlocks)
            val charJitterDepth = signedJitter(config.glyphJitterDepthBlocks)

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed pixelLoop@{ column, pixel ->
                    if (pixel != '1') return@pixelLoop

                    val frontMaterial = config.glyphMaterials[random.nextInt(config.glyphMaterials.size)]
                    val sideSeed = random.nextInt(config.glyphSideMaterials.size)

                    for (pixelX in 0 until config.glyphScaleX) {
                        for (pixelY in 0 until config.glyphScaleY) {
                            val localU = charStartU + column * config.glyphScaleX + pixelX
                            val y = topY - row * config.glyphScaleY - pixelY + charJitterY

                            // Write the dark body first. The face map is merged
                            // afterwards so a bright front voxel always wins if
                            // integer voxelisation causes two rotated cells to meet.
                            for (layer in config.glyphDepth - 1 downTo 1) {
                                val localDepth = charJitterDepth + layer
                                val position = transform(localU, y, localDepth, config)
                                val material = config.glyphSideMaterials[
                                    (sideSeed + layer) % config.glyphSideMaterials.size
                                ]
                                sideBlocks[position] = material
                            }

                            val frontPosition = transform(localU, y, charJitterDepth, config)
                            frontBlocks[frontPosition] = frontMaterial
                        }
                    }
                }
            }
        }

        val result = LinkedHashMap<BlockPos, String>(sideBlocks.size + frontBlocks.size + config.noiseBlocks)
        result.putAll(sideBlocks)
        result.putAll(frontBlocks)

        if (config.noiseBlocks > 0) {
            addBackgroundNoise(
                blocks = result,
                firstU = firstU,
                totalWidth = totalWidth,
                topY = topY,
                totalHeight = totalHeight,
                config = config
            )
        }

        return result
    }

    private fun transform(
        localU: Int,
        y: Int,
        localDepth: Int,
        config: CaptchaConfig
    ): BlockPos {
        val worldX = CaptchaScene.centerX(config) +
            localU * CaptchaScene.rightX(config) +
            localDepth * CaptchaScene.depthX(config)
        val worldZ = CaptchaScene.centerZ(config) +
            localU * CaptchaScene.rightZ(config) +
            localDepth * CaptchaScene.depthZ(config)

        return BlockPos(
            x = worldX.roundToInt(),
            y = y,
            z = worldZ.roundToInt()
        )
    }

    private fun addBackgroundNoise(
        blocks: MutableMap<BlockPos, String>,
        firstU: Int,
        totalWidth: Int,
        topY: Int,
        totalHeight: Int,
        config: CaptchaConfig
    ) {
        val minU = firstU - 3
        val maxU = firstU + totalWidth + 2
        val minY = topY - totalHeight - 2
        val maxY = topY + 2
        val baseDepth = config.glyphDepth + config.glyphJitterDepthBlocks + 2

        var placed = 0
        var tries = 0
        val maxTries = config.noiseBlocks * 30 + 30

        while (placed < config.noiseBlocks && tries++ < maxTries) {
            val localU = random.nextInt(maxU - minU + 1) + minU
            val y = random.nextInt(maxY - minY + 1) + minY
            val localDepth = baseDepth + random.nextInt(3)
            val position = transform(localU, y, localDepth, config)

            if (blocks.putIfAbsent(position, config.noiseMaterial) == null) {
                placed++
            }
        }
    }

    private fun signedJitter(amount: Int): Int =
        if (amount == 0) 0 else random.nextInt(amount * 2 + 1) - amount
}
