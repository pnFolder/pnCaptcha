# pnCaptcha

Block-based CAPTCHA verification for Velocity proxies.

## Goal

Keep unverified connections away from backend Paper servers. The verification flow is designed around a lightweight limbo stage where a player sees a generated block CAPTCHA, submits the answer, and is forwarded to the configured lobby only after successful verification.

## Target

- Velocity 3.5.0+
- Kotlin
- Java 21 bytecode

## Architecture

```text
CONNECT
  -> PRE_CHECK
  -> CAPTCHA_LOADING
  -> CAPTCHA_WAITING
       -> correct: VERIFIED -> LOBBY
       -> wrong: retry / regenerate
       -> timeout: disconnect
```

Current milestone contains the project skeleton, secure CAPTCHA code generator, verification states, per-player sessions, session manager, and core unit tests.

## Next milestone

Implement the verification coordinator and the limbo transport layer, then render the first block-font CAPTCHA in front of the player.
