package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom
import java.util.Random

/**
 * Builds one client-side CAPTCHA frame.
 *
 * Every bitmap pixel is expanded into a square voxel cell and then extruded
 * backwards along Z. Deeper layers are shifted slightly down/right, producing
 * a very obvious blocky 3D/isometric silhouette instead of a flat 2D wall.
 */
class CaptchaLayout(
    private val topY: Int = DEFAULT_TOP_Y,
    private val frontZ: Int = DEFAULT_FRONT_Z,
    private val glyphGapPixels: Int = 1,
    private val random: Random = SecureRandom()
) {
    fun build(
        answer: String,
        glyphMaterials: List<String>,
        sideMaterial: String,
        noiseMaterial: String,
        noiseCount: Int,
        scale: Int,
        depth: Int
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
        require(scale in 1..3) { "scale must be between 1 and 3" }
        require(depth in 1..5) { "depth must be between 1 and 5" }

        val result = LinkedHashMap<BlockPos, String>()
        val glyphWidth = CaptchaFont.WIDTH * scale
        val gap = glyphGapPixels * scale
        val totalWidth = answer.length * glyphWidth + (answer.length - 1) * gap
        val startX = -(totalWidth / 2)

        answer.forEachIndexed { index, char ->
            val pattern = CaptchaFont.pattern(char)
            val charX = startX + index * (glyphWidth + gap)
            val charTopY = topY + random.nextInt(3) - 1
            val frontMaterial = glyphMaterials[random.nextInt(glyphMaterials.size)]

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed { column, pixel ->
                    if (pixel != '1') return@forEachIndexed

                    for (pixelX in 0 until scale) {
                        for (pixelY in 0 until scale) {
                            val x = charX + column * scale + pixelX
                            val y = charTopY - row * scale - pixelY

                            // Put the extrusion in first so the bright front face always wins
                            // where a shifted rear layer intersects another front voxel.
                            for (layer in depth - 1 downTo 1) {
                                val bevelShift = layer / 2
                                result[BlockPos(
                                    x = x + bevelShift,
                                    y = y - bevelShift,
                                    z = frontZ + layer
                                )] = sideMaterial
                            }

                            result[BlockPos(x, y, frontZ)] = frontMaterial
                        }
                    }
                }
            }
        }

        if (noiseCount > 0) {
            addBackgroundNoise(
                blocks = result,
                startX = startX,
                totalWidth = totalWidth,
                scaledHeight = CaptchaFont.HEIGHT * scale,
                depth = depth,
                noiseMaterial = noiseMaterial,
                noiseCount = noiseCount
            )
        }

        return result
    }

    private fun addBackgroundNoise(
        blocks: MutableMap<BlockPos, String>,
        startX: Int,
        totalWidth: Int,
        scaledHeight: Int,
        depth: Int,
        noiseMaterial: String,
        noiseCount: Int
    ) {
        val minX = startX - 3
        val maxX = startX + totalWidth + 2
        val minY = topY - scaledHeight - 2
        val maxY = topY + 2
        val noiseZ = frontZ + depth + 1
        var placed = 0
        var tries = 0
        val maxTries = noiseCount * 20 + 20

        // Noise is intentionally behind the letters, never on their front plane.
        // It adds depth without making the actual code harder for a human to read.
        while (placed < noiseCount && tries++ < maxTries) {
            val position = BlockPos(
                x = random.nextInt(maxX - minX + 1) + minX,
                y = random.nextInt(maxY - minY + 1) + minY,
                z = noiseZ + random.nextInt(2)
            )
            if (blocks.putIfAbsent(position, noiseMaterial) == null) {
                placed++
            }
        }
    }

    companion object {
        const val DEFAULT_TOP_Y: Int = 79
        const val DEFAULT_FRONT_Z: Int = 12
    }
}
