package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom
import java.util.Random

class CaptchaLayout(
    private val topY: Int = DEFAULT_TOP_Y,
    private val planeZ: Int = DEFAULT_PLANE_Z,
    private val glyphGap: Int = 1,
    private val random: Random = SecureRandom()
) {
    fun build(
        answer: String,
        glyphMaterials: List<String>,
        noiseMaterial: String,
        noiseCount: Int
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }
        require(glyphMaterials.isNotEmpty()) { "glyphMaterials must not be empty" }

        val result = LinkedHashMap<BlockPos, String>()
        val totalWidth = answer.length * CaptchaFont.WIDTH + (answer.length - 1) * glyphGap
        val startX = -(totalWidth / 2)

        answer.forEachIndexed { index, char ->
            val pattern = CaptchaFont.pattern(char)
            val charX = startX + index * (CaptchaFont.WIDTH + glyphGap)
            val charTopY = topY + random.nextInt(3) - 1
            val material = glyphMaterials[random.nextInt(glyphMaterials.size)]

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed { column, pixel ->
                    if (pixel == '1') {
                        result[BlockPos(charX + column, charTopY - row, planeZ)] = material
                    }
                }
            }
        }

        if (noiseCount > 0) {
            addNoise(result, startX, totalWidth, noiseMaterial, noiseCount)
        }

        return result
    }

    private fun addNoise(
        blocks: MutableMap<BlockPos, String>,
        startX: Int,
        totalWidth: Int,
        noiseMaterial: String,
        noiseCount: Int
    ) {
        val minX = startX - 2
        val maxX = startX + totalWidth + 1
        val minY = topY - CaptchaFont.HEIGHT - 2
        val maxY = topY + 2
        var placed = 0
        var tries = 0
        val maxTries = noiseCount * 20 + 20

        while (placed < noiseCount && tries++ < maxTries) {
            val position = BlockPos(
                x = random.nextInt(maxX - minX + 1) + minX,
                y = random.nextInt(maxY - minY + 1) + minY,
                z = planeZ
            )
            if (blocks.putIfAbsent(position, noiseMaterial) == null) {
                placed++
            }
        }
    }

    companion object {
        const val DEFAULT_TOP_Y: Int = 73
        const val DEFAULT_PLANE_Z: Int = 13
    }
}
