package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.config.WeightedMaterial
import java.util.Random
import kotlin.math.roundToInt

/** Builds the complete voxel sculpture from the resolved public configuration. */
class CaptchaLayout {
    fun build(
        answer: String,
        config: CaptchaConfig,
        font: CaptchaFont.ResolvedFont,
        scene: CaptchaScene.ResolvedScene,
        random: Random
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty()) { "answer must not be empty" }

        val bodyBlocks = LinkedHashMap<BlockPos, String>()
        val frontBlocks = LinkedHashMap<BlockPos, String>()

        val glyphWidth = font.width * config.geometry.pixelWidth
        val totalWidth = answer.length * glyphWidth +
            (answer.length - 1) * config.geometry.letterGapBlocks
        val totalHeight = font.height * config.geometry.pixelHeight
        val firstU = if (config.geometry.centerText) -(totalWidth - 1) / 2.0 else 0.0
        val topV = (totalHeight - 1) / 2.0

        answer.forEachIndexed { charIndex, char ->
            val pattern = font.pattern(char)
            val baseCharU = firstU + charIndex * (glyphWidth + config.geometry.letterGapBlocks)

            val charHorizontalJitter = signedJitter(config.randomness.character.horizontalJitterBlocks, config, random)
            val charVerticalJitter = signedJitter(config.randomness.character.verticalJitterBlocks, config, random)
            val charDepthJitter = signedJitter(config.randomness.character.depthJitterBlocks, config, random)

            val perCharacterFront = pick(config.palette.front.materials, random)
            val perCharacterSide = pick(config.palette.side.materials, random)
            val perCharacterBack = pick(config.palette.back.materials, random)

            pattern.forEachIndexed { row, pixels ->
                pixels.forEachIndexed pixelLoop@{ column, pixel ->
                    if (pixel != '1') return@pixelLoop

                    for (pixelX in 0 until config.geometry.pixelWidth) {
                        for (pixelY in 0 until config.geometry.pixelHeight) {
                            val localU = baseCharU + column * config.geometry.pixelWidth + pixelX + charHorizontalJitter
                            val localV = topV - row * config.geometry.pixelHeight - pixelY + charVerticalJitter
                            val frontDepth = charDepthJitter.toDouble()

                            val frontMaterial = materialFor(
                                config.palette.mode,
                                config.palette.front.materials,
                                perCharacterFront,
                                random
                            )

                            for (layer in config.geometry.depthBlocks - 1 downTo 1) {
                                val isBack = layer == config.geometry.depthBlocks - 1
                                val group = if (isBack) config.palette.back.materials else config.palette.side.materials
                                val perCharacter = if (isBack) perCharacterBack else perCharacterSide
                                val material = materialFor(config.palette.mode, group, perCharacter, random)
                                val position = scene.transform(localU, localV, frontDepth + layer)
                                bodyBlocks[toBlockPos(position)] = material
                            }

                            frontBlocks[toBlockPos(scene.transform(localU, localV, frontDepth))] = frontMaterial
                        }
                    }
                }
            }
        }

        val result = LinkedHashMap<BlockPos, String>(
            bodyBlocks.size + frontBlocks.size + if (config.noise.enabled) config.noise.count else 0
        )
        result.putAll(bodyBlocks)
        result.putAll(frontBlocks)

        if (config.noise.enabled && config.noise.count > 0) {
            addNoise(
                blocks = result,
                firstU = firstU,
                totalWidth = totalWidth,
                topV = topV,
                totalHeight = totalHeight,
                config = config,
                scene = scene,
                random = random
            )
        }

        return result
    }

    private fun addNoise(
        blocks: MutableMap<BlockPos, String>,
        firstU: Double,
        totalWidth: Int,
        topV: Double,
        totalHeight: Int,
        config: CaptchaConfig,
        scene: CaptchaScene.ResolvedScene,
        random: Random
    ) {
        val minU = firstU - config.noise.horizontalPaddingBlocks
        val maxU = firstU + totalWidth - 1 + config.noise.horizontalPaddingBlocks
        val minV = topV - totalHeight + 1 - config.noise.verticalPaddingBlocks
        val maxV = topV + config.noise.verticalPaddingBlocks
        val minDepth = config.geometry.depthBlocks + config.noise.depthMinBlocks
        val maxDepth = config.geometry.depthBlocks + config.noise.depthMaxBlocks

        var placed = 0
        var tries = 0
        val maxTries = config.noise.count * 40 + 100

        while (placed < config.noise.count && tries++ < maxTries) {
            val u = randomBetween(minU, maxU, random)
            val v = randomBetween(minV, maxV, random)
            val d = randomBetween(minDepth.toDouble(), maxDepth.toDouble(), random)
            val position = toBlockPos(scene.transform(u, v, d))

            if (blocks.putIfAbsent(position, pick(config.noise.materials, random)) == null) {
                placed++
            }
        }
    }

    private fun materialFor(
        mode: String,
        materials: List<WeightedMaterial>,
        perCharacter: String,
        random: Random
    ): String = when (mode.lowercase()) {
        "solid" -> materials.first().block
        "per-character" -> perCharacter
        else -> pick(materials, random)
    }

    private fun pick(materials: List<WeightedMaterial>, random: Random): String {
        val totalWeight = materials.sumOf { it.weight }
        var cursor = random.nextInt(totalWeight)
        for (entry in materials) {
            cursor -= entry.weight
            if (cursor < 0) return entry.block
        }
        return materials.last().block
    }

    private fun signedJitter(maximum: Int, config: CaptchaConfig, random: Random): Int {
        if (!config.randomness.enabled || maximum <= 0) return 0
        return random.nextInt(maximum * 2 + 1) - maximum
    }

    private fun randomBetween(min: Double, max: Double, random: Random): Double {
        if (max <= min) return min
        return min + random.nextDouble() * (max - min)
    }

    private fun toBlockPos(vector: CaptchaScene.Vec3): BlockPos = BlockPos(
        vector.x.roundToInt(),
        vector.y.roundToInt(),
        vector.z.roundToInt()
    )
}
