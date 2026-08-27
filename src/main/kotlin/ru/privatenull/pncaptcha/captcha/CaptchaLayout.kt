package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom
import java.util.Random

/**
 * Builds one client-side CAPTCHA frame.
 *
 * The glyph face deliberately stays inside the spawn/adjacent chunks that
 * LimboAPI sends immediately. This matters because a BLOCK_CHANGE sent for an
 * unloaded chunk is discarded by the Minecraft client and a later chunk packet
 * replaces it with air.
 *
 * A player looking roughly toward +Z sees -X as screen-right, therefore font
 * columns are written from +X toward -X to avoid mirrored characters.
 *
 * The front face is narrow (1 block per bitmap column), tall (2 blocks per
 * bitmap row), and extruded deeply along +Z. Combined with the off-axis camera
 * this produces the long, massive voxel depth from the reference image without
 * requiring a giant physical platform or world.
 */
class CaptchaLayout(
    private val centerX: Int = DEFAULT_CENTER_X,
    private val topY: Int = DEFAULT_TOP_Y,
    private val frontZ: Int = DEFAULT_FRONT_Z,
    private val glyphGapPixels: Int = 1,
    private val random: Random = SecureRandom()
) {
    fun build(
        answer: String,
        glyphMaterials: List<String>,
        sideMaterials: List<String>,
        noiseMaterial: String,
        noiseCount: Int,
        scaleX: Int,
        scaleY: Int,
        depth: Int
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
        require(sideMaterials.isNotEmpty()) { "sideMaterials must not be empty" }
        require(scaleX in 1..3) { "scaleX must be between 1 and 3" }
        require(scaleY in 1..3) { "scaleY must be between 1 and 3" }
        require(depth in 1..8) { "depth must be between 1 and 8" }

        val result = LinkedHashMap<BlockPos, String>()
        val glyphWidth = CaptchaFont.WIDTH * scaleX
        val gap = glyphGapPixels * scaleX
        val totalWidth = answer.length * glyphWidth + (answer.length - 1) * gap
        val screenLeftWorldX = centerX + totalWidth / 2

        answer.forEachIndexed { index, char ->
            val pattern = CaptchaFont.pattern(char)
            val charLeftWorldX = screenLeftWorldX - index * (glyphWidth + gap)
            val charTopY = topY + random.nextInt(3) - 1

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed { column, pixel ->
                    if (pixel != '1') return@forEachIndexed

                    val frontMaterial = glyphMaterials[random.nextInt(glyphMaterials.size)]
                    val sideSeed = random.nextInt(sideMaterials.size)

                    for (pixelX in 0 until scaleX) {
                        for (pixelY in 0 until scaleY) {
                            val x = charLeftWorldX - column * scaleX - pixelX
                            val y = charTopY - row * scaleY - pixelY

                            // Real depth. The rear layers are not shifted in X/Y;
                            // the visible side faces come from actual perspective.
                            for (layer in depth - 1 downTo 1) {
                                val sideMaterial = sideMaterials[(sideSeed + layer) % sideMaterials.size]
                                result[BlockPos(x, y, frontZ + layer)] = sideMaterial
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
                screenLeftWorldX = screenLeftWorldX,
                totalWidth = totalWidth,
                scaledHeight = CaptchaFont.HEIGHT * scaleY,
                depth = depth,
                noiseMaterial = noiseMaterial,
                noiseCount = noiseCount
            )
        }

        return result
    }

    private fun addBackgroundNoise(
        blocks: MutableMap<BlockPos, String>,
        screenLeftWorldX: Int,
        totalWidth: Int,
        scaledHeight: Int,
        depth: Int,
        noiseMaterial: String,
        noiseCount: Int
    ) {
        val minX = screenLeftWorldX - totalWidth - 1
        val maxX = screenLeftWorldX + 1
        val minY = topY - scaledHeight - 1
        val maxY = topY + 1
        val noiseZ = frontZ + depth
        var placed = 0
        var tries = 0
        val maxTries = noiseCount * 20 + 20

        while (placed < noiseCount && tries++ < maxTries) {
            val position = BlockPos(
                x = random.nextInt(maxX - minX + 1) + minX,
                y = random.nextInt(maxY - minY + 1) + minY,
                z = noiseZ
            )
            if (blocks.putIfAbsent(position, noiseMaterial) == null) {
                placed++
            }
        }
    }

    companion object {
        const val DEFAULT_CENTER_X: Int = 0
        const val DEFAULT_TOP_Y: Int = 78
        const val DEFAULT_FRONT_Z: Int = 14
    }
}
