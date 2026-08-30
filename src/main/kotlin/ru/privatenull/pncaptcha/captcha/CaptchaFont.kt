package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.FontConfig
import kotlin.math.abs
import kotlin.math.roundToInt

object CaptchaFont {
    const val CLASSIC_WIDTH: Int = 5
    const val CLASSIC_HEIGHT: Int = 7
    const val ORNATE_WIDTH: Int = 9
    const val ORNATE_HEIGHT: Int = 12

    private val classicGlyphs: Map<Char, List<String>> = mapOf(
        'A' to glyph("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to glyph("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to glyph("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
        'D' to glyph("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to glyph("01111", "10000", "10000", "10111", "10001", "10001", "01110"),
        'H' to glyph("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'J' to glyph("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to glyph("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'M' to glyph("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to glyph("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
        'P' to glyph("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to glyph("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to glyph("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to glyph("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to glyph("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to glyph("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to glyph("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to glyph("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
        'X' to glyph("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to glyph("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to glyph("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        '2' to glyph("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to glyph("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to glyph("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to glyph("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
        '6' to glyph("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
        '7' to glyph("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to glyph("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to glyph("01110", "10001", "10001", "01111", "00001", "00001", "01110")
    )

    private val ornateGlyphs: Map<Char, List<String>> by lazy {
        classicGlyphs.mapValues { (_, pattern) -> buildOrnate(pattern) }
    }

    fun resolve(config: FontConfig): ResolvedFont {
        val glyphs = when (config.preset.lowercase()) {
            "custom" -> validateCustom(config.customGlyphs)
            "ornate-9x12" -> ornateGlyphs
            else -> classicGlyphs
        }

        val alphabet = config.alphabet.uppercase()
        require(alphabet.all(glyphs::containsKey)) {
            val missing = alphabet.filterNot(glyphs::containsKey).toSet().joinToString("")
            "Font does not contain every character from alphabet. Missing: $missing"
        }

        val first = glyphs.values.first()
        return ResolvedFont(
            glyphs = glyphs,
            width = first.first().length,
            height = first.size,
            alphabet = alphabet
        )
    }

    fun pattern(char: Char): List<String> = classicGlyphs[char]
        ?: error("Unsupported captcha character: $char")

    fun supports(char: Char): Boolean = classicGlyphs.containsKey(char)

    private fun buildOrnate(source: List<String>): List<String> {
        val skeleton = Array(ORNATE_HEIGHT) { BooleanArray(ORNATE_WIDTH) }

        fun mapX(sourceX: Int): Int = (sourceX * (ORNATE_WIDTH - 1).toDouble() / (CLASSIC_WIDTH - 1)).roundToInt()
        fun mapY(sourceY: Int): Int = (sourceY * (ORNATE_HEIGHT - 1).toDouble() / (CLASSIC_HEIGHT - 1)).roundToInt()

        val directions = listOf(1 to 0, 0 to 1, 1 to 1, -1 to 1)
        for (y in 0 until CLASSIC_HEIGHT) {
            for (x in 0 until CLASSIC_WIDTH) {
                if (source[y][x] != '1') continue
                val x0 = mapX(x)
                val y0 = mapY(y)
                skeleton[y0][x0] = true

                for ((dx, dy) in directions) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until CLASSIC_WIDTH || ny !in 0 until CLASSIC_HEIGHT) continue
                    if (source[ny][nx] != '1') continue
                    drawLine(skeleton, x0, y0, mapX(nx), mapY(ny))
                }
            }
        }

        val thick = Array(ORNATE_HEIGHT) { y -> skeleton[y].clone() }
        for (y in 0 until ORNATE_HEIGHT) {
            for (x in 0 until ORNATE_WIDTH) {
                if (!skeleton[y][x]) continue
                val horizontal = listOf(x - 1, x + 1).count { it in 0 until ORNATE_WIDTH && skeleton[y][it] }
                val vertical = listOf(y - 1, y + 1).count { it in 0 until ORNATE_HEIGHT && skeleton[it][x] }

                if (vertical >= horizontal) {
                    when {
                        x + 1 < ORNATE_WIDTH -> thick[y][x + 1] = true
                        x > 0 -> thick[y][x - 1] = true
                    }
                } else {
                    when {
                        y + 1 < ORNATE_HEIGHT -> thick[y + 1][x] = true
                        y > 0 -> thick[y - 1][x] = true
                    }
                }
            }
        }

        // Small serif caps on endpoints preserve the decorative reference style without
        // turning the glyph into a fully solid slab.
        for (sy in 0 until CLASSIC_HEIGHT) {
            for (sx in 0 until CLASSIC_WIDTH) {
                if (source[sy][sx] != '1') continue
                val neighbours = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    .filter { (dx, dy) ->
                        val nx = sx + dx
                        val ny = sy + dy
                        nx in 0 until CLASSIC_WIDTH && ny in 0 until CLASSIC_HEIGHT && source[ny][nx] == '1'
                    }
                if (neighbours.size != 1) continue

                val cx = mapX(sx)
                val cy = mapY(sy)
                val (dx, dy) = neighbours.single()
                if (dy != 0) {
                    if (cx > 0) thick[cy][cx - 1] = true
                    if (cx + 1 < ORNATE_WIDTH) thick[cy][cx + 1] = true
                } else if (dx != 0) {
                    if (cy > 0) thick[cy - 1][cx] = true
                    if (cy + 1 < ORNATE_HEIGHT) thick[cy + 1][cx] = true
                }
            }
        }

        return thick.map { row -> row.joinToString("") { if (it) "1" else "0" } }
    }

    private fun drawLine(canvas: Array<BooleanArray>, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        var x = fromX
        var y = fromY
        val dx = abs(toX - fromX)
        val sx = if (fromX < toX) 1 else -1
        val dy = -abs(toY - fromY)
        val sy = if (fromY < toY) 1 else -1
        var error = dx + dy

        while (true) {
            if (y in canvas.indices && x in canvas[y].indices) canvas[y][x] = true
            if (x == toX && y == toY) break
            val e2 = 2 * error
            if (e2 >= dy) {
                error += dy
                x += sx
            }
            if (e2 <= dx) {
                error += dx
                y += sy
            }
        }
    }

    private fun validateCustom(source: Map<Char, List<String>>): Map<Char, List<String>> {
        require(source.isNotEmpty()) { "font.custom-glyphs must not be empty when preset=custom" }
        val normalized = source.mapKeys { it.key.uppercaseChar() }
        val height = normalized.values.first().size
        val width = normalized.values.first().firstOrNull()?.length ?: 0
        require(height > 0 && width > 0) { "Custom font glyphs must not be empty" }
        normalized.forEach { (char, rows) ->
            require(rows.size == height) { "Custom glyph '$char' must have $height rows" }
            require(rows.all { row -> row.length == width && row.all { it == '0' || it == '1' } }) {
                "Custom glyph '$char' must be a ${width}x$height matrix of 0/1"
            }
        }
        return normalized
    }

    private fun glyph(vararg rows: String): List<String> {
        require(rows.size == CLASSIC_HEIGHT)
        require(rows.all { it.length == CLASSIC_WIDTH && it.all { pixel -> pixel == '0' || pixel == '1' } })
        return rows.toList()
    }

    data class ResolvedFont(
        val glyphs: Map<Char, List<String>>,
        val width: Int,
        val height: Int,
        val alphabet: String
    ) {
        fun pattern(char: Char): List<String> = glyphs[char.uppercaseChar()]
            ?: error("Unsupported captcha character: $char")
    }
}
