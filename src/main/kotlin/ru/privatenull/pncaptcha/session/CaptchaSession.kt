package ru.privatenull.pncaptcha.session

import net.elytrium.limboapi.api.player.LimboPlayer
import java.time.Instant
import java.util.UUID

data class CaptchaSession(
    val id: UUID = UUID.randomUUID(),
    val playerId: UUID,
    var answer: String,
    val createdAt: Instant = Instant.now(),
    var state: VerificationState = VerificationState.CAPTCHA_LOADING,
    var attempts: Int = 0,
    @Volatile var limboPlayer: LimboPlayer? = null
) {
    fun matches(input: String): Boolean = answer.equals(input.trim(), ignoreCase = true)
}
