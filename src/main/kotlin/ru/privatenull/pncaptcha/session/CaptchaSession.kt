package ru.privatenull.pncaptcha.session

import java.time.Instant
import java.util.UUID

data class CaptchaSession(
    val playerId: UUID,
    val answer: String,
    val createdAt: Instant = Instant.now(),
    var state: VerificationState = VerificationState.CAPTCHA_LOADING,
    var attempts: Int = 0
) {
    fun matches(input: String): Boolean = answer.equals(input.trim(), ignoreCase = true)
}
