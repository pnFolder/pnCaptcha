package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.CaptchaConfig
import ru.privatenull.pncaptcha.config.MaterialGroup
import ru.privatenull.pncaptcha.config.WeightedMaterial
import java.util.ArrayDeque
import java.util.Random
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
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
            val surface = buildSurface(pattern, font, config, random)
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

            val picker = CharacterMaterialPicker(config, random)

            surface.retained.forEach { cell ->
                val rawU = baseCharU + cell.x + charHorizontalJitter
                val rawV = topV - cell.y + charVerticalBase + charVerticalJitter
                val frontD = charDepthBase + charDepthJitter
                val outline = cell in surface.originalOutline

                for (layer in charDepth - 1 downTo 0) {
                    val material = when {
                        layer < config.geometry.frontThicknessBlocks -> picker.front(cell, layer, outline)
                        layer >= charDepth - config.geometry.backThicknessBlocks -> picker.back(cell, layer)
                        else -> picker.side(cell, layer)
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

        if (config.noise.enabled && config.noise.count > 0) {
            addNoise(result, firstU, totalWidth, topV, totalHeight, config, scene, random)
        }

        return result
    }

    private fun buildSurface(
        pattern: List<String>,
        font: CaptchaFont.ResolvedFont,
        config: CaptchaConfig,
        random: Random
    ): SurfaceMask {
        val original = LinkedHashSet<SurfaceCell>()

        pattern.indices.forEach { visualRow ->
            val row = if (config.geometry.mirrorVertical) font.height - 1 - visualRow else visualRow
            pattern[row].indices.forEach { visualColumn ->
                val column = if (config.geometry.mirrorHorizontal) font.width - 1 - visualColumn else visualColumn
                if (pattern[row][column] != '1') return@forEach

                for (pixelX in 0 until config.geometry.pixelWidth) {
                    for (pixelY in 0 until config.geometry.pixelHeight) {
                        original += SurfaceCell(
                            x = visualColumn * config.geometry.pixelWidth + pixelX,
                            y = visualRow * config.geometry.pixelHeight + pixelY
                        )
                    }
                }
            }
        }

        val outline = original.filterTo(LinkedHashSet()) { cell ->
            CARDINAL.any { (dx, dy) -> SurfaceCell(cell.x + dx, cell.y + dy) !in original }
        }

        val fill = config.geometry.fill
        if (fill.mode.equals("solid", ignoreCase = true) || fill.density >= 0.999 || original.size <= 2) {
            return SurfaceMask(original, outline)
        }

        val retained = LinkedHashSet(original)
        val originalComponents = componentCount(original)
        val target = max(fill.minRetainedPixels, ceil(original.size * fill.density).toInt())
            .coerceAtMost(original.size)

        val shuffled = original.shuffled(random).sortedBy { it in outline }
        for (candidate in shuffled) {
            if (retained.size <= target) break

            val degree = CARDINAL.count { (dx, dy) -> SurfaceCell(candidate.x + dx, candidate.y + dy) in retained }
            if (fill.protectEndpoints && degree <= 1) continue
            if (candidate in outline && random.nextDouble() * 100.0 < fill.outlinePreservePercent) continue

            retained.remove(candidate)
            if (fill.preserveConnectivity && componentCount(retained) > originalComponents) {
                retained.add(candidate)
            }
        }

        return SurfaceMask(retained, outline)
    }

    private fun componentCount(cells: Set<SurfaceCell>): Int {
        if (cells.isEmpty()) return 0
        val unseen = cells.toMutableSet()
        var components = 0
        val queue = ArrayDeque<SurfaceCell>()

        while (unseen.isNotEmpty()) {
            components++
            val start = unseen.first()
            unseen.remove(start)
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                CARDINAL.forEach { (dx, dy) ->
                    val next = SurfaceCell(current.x + dx, current.y + dy)
                    if (unseen.remove(next)) queue.add(next)
                }
            }
        }
        return components
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

    private inner class CharacterMaterialPicker(
        private val config: CaptchaConfig,
        private val random: Random
    ) {
        private val mode = config.palette.mode.lowercase()
        private val clusterSize = if (config.palette.clusterSizeMax <= config.palette.clusterSizeMin) {
            config.palette.clusterSizeMin
        } else {
            random.nextInt(config.palette.clusterSizeMax - config.palette.clusterSizeMin + 1) + config.palette.clusterSizeMin
        }
        private val clusterDepth = max(1, clusterSize / 2)
        private val clusters = HashMap<ClusterKey, String>()
        private val perCharacterFront = pick(config.palette.front.materials, random)
        private val perCharacterSide = pick(config.palette.side.materials, random)
        private val perCharacterBack = pick(config.palette.back.materials, random)
        private val perCharacterOutline = pick(config.palette.outline.group.materials, random)

        fun front(cell: SurfaceCell, layer: Int, outline: Boolean): String {
            if (outline && config.palette.outline.enabled &&
                random.nextDouble() * 100.0 < config.palette.outline.chancePercent
            ) {
                return material("outline", config.palette.outline.group, perCharacterOutline, cell, layer)
            }

            val base = material("front", config.palette.front, perCharacterFront, cell, layer)
            val accent = config.palette.accent
            return if (accent.enabled && accent.chancePercent > 0.0 && random.nextDouble() * 100.0 < accent.chancePercent) {
                pick(accent.group.materials, random)
            } else {
                base
            }
        }

        fun side(cell: SurfaceCell, layer: Int): String =
            material("side", config.palette.side, perCharacterSide, cell, layer)

        fun back(cell: SurfaceCell, layer: Int): String =
            material("back", config.palette.back, perCharacterBack, cell, layer)

        private fun material(
            role: String,
            group: MaterialGroup,
            perCharacter: String,
            cell: SurfaceCell,
            layer: Int
        ): String = when (mode) {
            "solid" -> group.materials.first().block
            "per-character" -> perCharacter
            "clustered" -> {
                val key = ClusterKey(
                    role = role,
                    x = Math.floorDiv(cell.x, clusterSize),
                    y = Math.floorDiv(cell.y, clusterSize),
                    z = Math.floorDiv(layer, clusterDepth)
                )
                clusters.getOrPut(key) { pick(group.materials, random) }
            }
            else -> pick(group.materials, random)
        }
    }

    private data class SurfaceMask(
        val retained: Set<SurfaceCell>,
        val originalOutline: Set<SurfaceCell>
    )

    private data class SurfaceCell(val x: Int, val y: Int)
    private data class ClusterKey(val role: String, val x: Int, val y: Int, val z: Int)
    private data class LocalVec(val u: Double, val v: Double, val d: Double)

    private companion object {
        val CARDINAL = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
