package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom

class CaptchaGenerator(
    private val alphabet: String = DEFAULT_ALPHABET,
    private val random: SecureRandom = SecureRandom()
) {
    init {
        require(alphabet.isNotEmpty()) { "Captcha alphabet must not be empty" }
        require(alphabet.all(CaptchaFont::supports)) { "Captcha font must support the entire alphabet" }
    }

    fun generate(length: Int = DEFAULT_LENGTH): String {
        require(length > 0) { "Captcha length must be positive" }

        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        const val DEFAULT_LENGTH: Int = 5
        const val DEFAULT_ALPHABET: String = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}
