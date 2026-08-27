package ru.privatenull.pncaptcha.session

enum class VerificationState {
    CAPTCHA_LOADING,
    CAPTCHA_WAITING,
    VERIFIED,
    FAILED
}
