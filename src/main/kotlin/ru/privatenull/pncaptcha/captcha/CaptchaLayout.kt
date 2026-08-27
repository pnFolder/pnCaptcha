package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom
import java.util.Random

/**
 * Builds one client-side CAPTCHA frame.
 *
 * Important coordinate detail: a player looking roughly toward +Z sees -X as
 * screen-right. The bitmap therefore has to be written from +X toward -X.
 * Writing columns in the normal +X direction mirrors every glyph for the user.
 *
 * Each lit font pixel is expanded into a square voxel cell and extruded straight
 * away from the player along +Z. The camera is intentionally placed off-axis by
 * CaptchaLimboEnvironment, so the real Z depth becomes visible in perspective
 * instead of relying on a fake diagonal layer shift.
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
        scale: Int,
        depth: Int
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }
        require(sideMaterials.isNotEmpty()) { "sideMaterials must not be empty" }
        require(scale in 1..3) { "scale must be between 1 and 3" }
        require(depth in 1..8) { "depth must be between 1 and 8" }

        val result = LinkedHashMap<BlockPos, String>()
        val glyphWidth = CaptchaFont.WIDTH * scale
        val gap = glyphGapPixels * scale
        val totalWidth = answer.length * glyphWidth + (answer.length - 1) * gap

        // The first character is screen-left. For our south-east camera that is
        // the +X side of the world, so glyph columns deliberately decrease X.
        val screenLeftWorldX = centerX + totalWidth / 2

        answer.forEachIndexed { index, char ->
            val pattern = CaptchaFont.pattern(char)
            val charLeftWorldX = screenLeftWorldX - index * (glyphWidth + gap)
            val charTopY = topY + random.nextInt(3) - 1

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed { column, pixel ->
                    if (pixel != '1') return@forEachIndexed

                    // A little material variation per logical voxel gives the
                    // broken-stone look from the visual reference without
                    // damaging the silhouette/readability of the character.
                    val frontMaterial = glyphMaterials[random.nextInt(glyphMaterials.size)]
                    val sideSeed = random.nextInt(sideMaterials.size)

                    for (pixelX in 0 until scale) {
                        for (pixelY in 0 until scale) {
                            val x = charLeftWorldX - column * scale - pixelX
                            val y = charTopY - row * scale - pixelY

                            // Real volume: no synthetic X/Y shift. Perspective
                            // comes from the player's off-axis camera.
                            for (layer in depth - 1 downTo 1) {
                                val sideMaterial = sideMaterials[(sideSeed + layer) % sideMaterials.size]
                                result[BlockPos(x, y, frontZ + layer)] = sideMaterial
                            }

                            // Bright/mottled front face is always written last.
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
        screenLeftWorldX: Int,
        totalWidth: Int,
        scaledHeight: Int,
        depth: Int,
        noiseMaterial: String,
        noiseCount: Int
    ) {
        val minX = screenLeftWorldX - totalWidth - 3
        val maxX = screenLeftWorldX + 3
        val minY = topY - scaledHeight - 2
        val maxY = topY + 2
        val noiseZ = frontZ + depth + 2
        var placed = 0
        var tries = 0
        val maxTries = noiseCount * 20 + 20

        // Noise sits behind the entire 3D object. It cannot punch holes in or
        // visually overwrite the front face of the answer.
        while (placed < noiseCount && tries++ < maxTries) {
            val position = BlockPos(
                x = random.nextInt(maxX - minX + 1) + minX,
                y = random.nextInt(maxY - minY + 1) + minY,
                z = noiseZ + random.nextInt(3)
            )
            if (blocks.putIfAbsent(position, noiseMaterial) == null) {
                placed++
            }
        }
    }

    companion object {
        const val DEFAULT_CENTER_X: Int = 10
        const val DEFAULT_TOP_Y: Int = 80
        const val DEFAULT_FRONT_Z: Int = 42
    }
}
