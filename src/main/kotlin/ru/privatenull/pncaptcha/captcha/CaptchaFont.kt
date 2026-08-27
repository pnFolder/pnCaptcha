package ru.privatenull.pncaptcha.captcha

import ru.privatenull.pncaptcha.config.FontConfig

object CaptchaFont {
    const val CLASSIC_WIDTH: Int = 5
    const val CLASSIC_HEIGHT: Int = 7

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

    fun resolve(config: FontConfig): ResolvedFont {
        val glyphs = when (config.preset.lowercase()) {
            "custom" -> validateCustom(config.customGlyphs)
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
