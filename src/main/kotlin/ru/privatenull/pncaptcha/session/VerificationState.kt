package ru.privatenull.pncaptcha.session

enum class VerificationState {
    PRE_CHECK,
    CAPTCHA_LOADING,
    CAPTCHA_WAITING,
    VERIFIED
}
