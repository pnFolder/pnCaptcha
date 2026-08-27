package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.config.WeightedMaterial
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CaptchaLayout {
    fun build(
        answer: String,
        config: CaptchaConfig,
        font: CaptchaFont.ResolvedFont,
        scene: CaptchaScene.ResolvedScene,
        random: Random
    ): Map<BlockPos, String> {
        require(answer.isNotEmpty())

        val result = LinkedHashMap<BlockPos, String>()
        val glyphWidth = font.width * config.geometry.pixelWidth
        val totalWidth = answer.length * glyphWidth + (answer.length - 1) * config.geometry.letterGapBlocks
        val totalHeight = font.height * config.geometry.pixelHeight
        val firstU = if (config.geometry.centerText) -(totalWidth - 1) / 2.0 else 0.0
        val topV = (totalHeight - 1) / 2.0
        val depthSign = if (config.geometry.extrudeTowardCamera) -1.0 else 1.0

        answer.forEachIndexed { charIndex, char ->
            val pattern = font.pattern(char)
            val baseCharU = firstU + charIndex * (glyphWidth + config.geometry.letterGapBlocks)
            val charCenterU = baseCharU + (glyphWidth - 1) / 2.0
            val charVerticalBase = charIndex * config.geometry.letterRiseBlocks
            val charDepthBase = charIndex * config.geometry.letterDepthStepBlocks

            val charHorizontalJitter = signedJitter(config.randomness.character.horizontalJitterBlocks, config, random)
            val charVerticalJitter = signedJitter(config.randomness.character.verticalJitterBlocks, config, random)
            val charDepthJitter = signedJitter(config.randomness.character.depthJitterBlocks, config, random)
            val depthVariation = positiveJitter(config.randomness.character.depthVariationBlocks, config, random)
            val charDepth = config.geometry.depthBlocks + depthVariation

            val charYaw = signedAngle(config.randomness.character.rotationYawJitterDegrees, config, random)
            val charPitch = signedAngle(config.randomness.character.rotationPitchJitterDegrees, config, random)
            val charRoll = signedAngle(config.randomness.character.rotationRollJitterDegrees, config, random)

            val perCharacterFront = pick(config.palette.front.materials, random)
            val perCharacterSide = pick(config.palette.side.materials, random)
            val perCharacterBack = pick(config.palette.back.materials, random)

            pattern.forEachIndexed { visualRow, pixels ->
                val row = if (config.geometry.mirrorVertical) font.height - 1 - visualRow else visualRow
                pixels.indices.forEach { visualColumn ->
                    val column = if (config.geometry.mirrorHorizontal) font.width - 1 - visualColumn else visualColumn
                    if (pattern[row][column] != '1') return@forEach

                    for (pixelX in 0 until config.geometry.pixelWidth) {
                        for (pixelY in 0 until config.geometry.pixelHeight) {
                            val rawU = baseCharU + visualColumn * config.geometry.pixelWidth + pixelX + charHorizontalJitter
                            val rawV = topV - visualRow * config.geometry.pixelHeight - pixelY + charVerticalBase + charVerticalJitter
                            val frontD = charDepthBase + charDepthJitter

                            for (layer in charDepth - 1 downTo 0) {
                                val material = when {
                                    layer < config.geometry.frontThicknessBlocks -> accentedFront(
                                        materialFor(config.palette.mode, config.palette.front.materials, perCharacterFront, random),
                                        config,
                                        random
                                    )
                                    layer >= charDepth - config.geometry.backThicknessBlocks ->
                                        materialFor(config.palette.mode, config.palette.back.materials, perCharacterBack, random)
                                    else -> materialFor(config.palette.mode, config.palette.side.materials, perCharacterSide, random)
                                }

                                val local = rotateLocal(
                                    u = rawU,
                                    v = rawV,
                                    d = frontD + depthSign * layer,
                                    pivotU = charCenterU,
                                    pivotV = charVerticalBase,
                                    pivotD = charDepthBase,
                                    yawDegrees = charYaw,
                                    pitchDegrees = charPitch,
                                    rollDegrees = charRoll
                                )
                                result[toBlockPos(scene.transform(local.u, local.v, local.d))] = material
                            }
                        }
                    }
                }
            }
        }

        if (config.noise.enabled && config.noise.count > 0) {
            addNoise(result, firstU, totalWidth, topV, totalHeight, config, scene, random)
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
        val sign = if (config.geometry.extrudeTowardCamera) -1.0 else 1.0
        val baseDepth = config.geometry.depthBlocks

        repeat(config.noise.count) {
            val cluster = if (config.noise.clusterSizeMax <= config.noise.clusterSizeMin) {
                config.noise.clusterSizeMin
            } else {
                random.nextInt(config.noise.clusterSizeMax - config.noise.clusterSizeMin + 1) + config.noise.clusterSizeMin
            }
            val seedU = randomBetween(minU, maxU, random)
            val seedV = randomBetween(minV, maxV, random)
            val seedD = baseDepth + randomBetween(config.noise.depthMinBlocks.toDouble(), config.noise.depthMaxBlocks.toDouble(), random)

            repeat(cluster) {
                val u = seedU + random.nextInt(3) - 1
                val v = seedV + random.nextInt(3) - 1
                val d = sign * (seedD + random.nextInt(3) - 1)
                blocks.putIfAbsent(toBlockPos(scene.transform(u, v, d)), pick(config.noise.materials, random))
            }
        }
    }

    private fun accentedFront(base: String, config: CaptchaConfig, random: Random): String {
        val accent = config.palette.accent
        if (!accent.enabled || accent.chancePercent <= 0.0) return base
        return if (random.nextDouble() * 100.0 < accent.chancePercent) pick(accent.group.materials, random) else base
    }

    private fun rotateLocal(
        u: Double,
        v: Double,
        d: Double,
        pivotU: Double,
        pivotV: Double,
        pivotD: Double,
        yawDegrees: Double,
        pitchDegrees: Double,
        rollDegrees: Double
    ): LocalVec {
        var x = u - pivotU
        var y = v - pivotV
        var z = d - pivotD

        if (yawDegrees != 0.0) {
            val r = Math.toRadians(yawDegrees)
            val c = cos(r)
            val s = sin(r)
            val nx = x * c - z * s
            val nz = x * s + z * c
            x = nx
            z = nz
        }
        if (pitchDegrees != 0.0) {
            val r = Math.toRadians(pitchDegrees)
            val c = cos(r)
            val s = sin(r)
            val ny = y * c - z * s
            val nz = y * s + z * c
            y = ny
            z = nz
        }
        if (rollDegrees != 0.0) {
            val r = Math.toRadians(rollDegrees)
            val c = cos(r)
            val s = sin(r)
            val nx = x * c - y * s
            val ny = x * s + y * c
            x = nx
            y = ny
        }

        return LocalVec(pivotU + x, pivotV + y, pivotD + z)
    }

    private fun materialFor(mode: String, materials: List<WeightedMaterial>, perCharacter: String, random: Random): String =
        when (mode.lowercase()) {
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

    private fun positiveJitter(maximum: Int, config: CaptchaConfig, random: Random): Int {
        if (!config.randomness.enabled || maximum <= 0) return 0
        return random.nextInt(maximum + 1)
    }

    private fun signedAngle(maximum: Double, config: CaptchaConfig, random: Random): Double {
        if (!config.randomness.enabled || maximum <= 0.0) return 0.0
        return (random.nextDouble() * 2.0 - 1.0) * maximum
    }

    private fun randomBetween(min: Double, max: Double, random: Random): Double =
        if (max <= min) min else min + random.nextDouble() * (max - min)

    private fun toBlockPos(vector: CaptchaScene.Vec3): BlockPos =
        BlockPos(vector.x.roundToInt(), vector.y.roundToInt(), vector.z.roundToInt())

    private data class LocalVec(val u: Double, val v: Double, val d: Double)
}
