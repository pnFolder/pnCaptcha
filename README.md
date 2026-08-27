# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.0**.

## Architecture

The plugin deliberately splits transport and rendering:

- **LimboAPI** provides one shared in-memory virtual server. There is no Paper CAPTCHA server and no world file per player.
- The shared Limbo contains only a small static floor and dark background wall.
- **PacketEvents** renders the CAPTCHA as `BLOCK_CHANGE` packets that are sent to one client only.
- CAPTCHA letters are therefore **not real blocks**, are never written to the shared virtual world, and different players can see different codes at the same coordinates.
- A wrong answer clears the previous client-side overlay and draws a new one without recreating the Limbo world.
- A successful answer moves the player from Limbo to the configured backend (`lobby` by default).

```text
client
  |
  v
Velocity
  |
  +-- verified UUID+IP cache hit --------------------> lobby
  |
  +-- join rate limit
  |
  v
shared LimboAPI world
  |
  +-- PacketEvents fake block overlay: "K7M4P"
  +-- chat answer / timeout / attempts
  |
  +-- pass ------------------------------------------> lobby
  `-- fail ------------------------------------------> disconnect
```

## Runtime requirements

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+ (use a current build that supports your Minecraft protocol)
- PacketEvents 2.13.0+

The project uses public LimboAPI and PacketEvents APIs and does not create a Paper CAPTCHA backend.

## Configuration

On first start, `plugins/pncaptcha/config.properties` is generated:

```properties
target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=22
max-joins-per-window=6
join-window-seconds=10
glyph-materials=minecraft:white_concrete,minecraft:light_gray_concrete,minecraft:cyan_concrete,minecraft:blue_concrete,minecraft:purple_concrete
noise-material=minecraft:gray_stained_glass
```

The generated code excludes ambiguous `0/O/1/I/L` characters. Glyphs use a built-in 5x7 font, per-character vertical jitter, randomized materials, and non-overlapping visual noise.

## Verification flow

1. LimboAPI fires `LoginLimboRegisterEvent`.
2. pnCaptcha skips a challenge only when the same UUID **and** IP are still in the verification cache.
3. A small per-IP join-window limiter rejects obvious reconnect floods before CAPTCHA rendering.
4. The player is spawned into the single shared Limbo instance.
5. PacketEvents sends fake block updates for that player's code only to that client.
6. Limbo's session handler consumes chat input directly, so the answer never needs a Paper server.
7. Wrong answer: increment attempt counter and redraw the overlay in place.
8. Correct answer: cache the UUID+IP pair and hand the player to the configured backend.
9. Timeout or too many attempts: disconnect and clean the session.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.0.jar`.

GitHub Actions builds and tests every push and pull request.
