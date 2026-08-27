package ru.privatenull.pncaptcha.captcha

import java.security.SecureRandom

class CaptchaGenerator(
    private val alphabet: String = DEFAULT_ALPHABET,
    private val random: SecureRandom = SecureRandom()
) {
    init {
        require(alphabet.isNotBlank()) { "Captcha alphabet must not be empty" }
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
