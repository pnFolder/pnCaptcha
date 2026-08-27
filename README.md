# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.3.0**.

## Architecture

0.3.0 removes the fragile packet-only block overlay completely.

- **LimboAPI** creates a tiny in-memory `VirtualWorld` for each active verification session.
- The generated CAPTCHA blocks are written into that virtual world **before the player joins it**.
- Minecraft therefore receives the CAPTCHA through normal Limbo chunk data. There is no `BLOCK_CHANGE` race with unloaded chunks and no late chunk packet can erase the challenge.
- **PacketEvents is no longer required by pnCaptcha.**
- The world is not a disk world/map. It exists only in RAM for one CAPTCHA session and is disposed when the player verifies, disconnects, times out, or fails.
- The player still has one pedestal block at spawn, plus the 3D CAPTCHA construction itself.
- The entire text plane is physically rotated in X/Z and every lit font pixel is extruded into real depth.
- `max-active-captchas` caps concurrent per-session worlds so a connection flood cannot allocate them without limit.

```text
Velocity
  |
  +-- cache hit -------------------------------------> lobby
  |
  v
create per-session Limbo VirtualWorld
  |
  +-- pedestal
  +-- real 3D CAPTCHA blocks already stored in chunks
  +-- creative/adventure inspection
  +-- chat answer / timeout / attempts
  |
  +-- pass ------------------------------------------> lobby
  `-- fail ------------------------------------------> disconnect
```

## Runtime requirements

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+

PacketEvents can remain installed for your other plugins, but pnCaptcha 0.3.0 does not depend on it.

## Why simulation/view distance did not fix 0.2.x

A larger view or simulation distance tells the client/server how much world may be visible/simulated, but it does not make a fake `BLOCK_CHANGE` persistent. If a block-change packet is sent before the chunk is loaded, the client can discard it; if a real chunk packet arrives later, that chunk data replaces the fake state. 0.3.0 avoids the problem instead of trying to time around it: the block is part of the Limbo chunk itself.

## Configuration

`plugins/pncaptcha/config.properties` is generated and older configs are migrated automatically.

```properties
config-version=6

target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=8
max-joins-per-window=6
join-window-seconds=10
max-active-captchas=128

# Player inspection
creative-mode=true
lock-player-position=false

# Per-session Limbo chunk settings
limbo-view-distance=8
limbo-simulation-distance=6

# Whole scene placement
captcha-distance-blocks=30.0
captcha-angle-degrees=28.0
captcha-center-height-blocks=8.0
camera-pitch-offset-degrees=0.0

# Glyph mass / size / spacing
glyph-scale-x=2
glyph-scale-y=2
glyph-depth=3
glyph-gap-blocks=2
glyph-jitter-y-blocks=1
glyph-jitter-depth-blocks=1

glyph-materials=minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:gray_concrete,minecraft:cyan_terracotta,minecraft:light_blue_terracotta
glyph-side-materials=minecraft:deepslate_tiles,minecraft:deepslate_bricks,minecraft:blackstone,minecraft:polished_blackstone
noise-material=minecraft:gray_stained_glass
```

### Important visual controls

- `captcha-distance-blocks` — distance to the centre of the CAPTCHA. Default `30`.
- `captcha-angle-degrees` — physical rotation of the whole CAPTCHA plane. Default `28`.
- `captcha-center-height-blocks` — vertical centre; the camera automatically aims at it.
- `glyph-scale-x` / `glyph-scale-y` — front-face fatness and size.
- `glyph-depth` — **real 3D thickness in blocks**. Default `3`.
- `glyph-gap-blocks` — spacing between characters.
- `glyph-jitter-y-blocks` / `glyph-jitter-depth-blocks` — small random offsets per character.
- `limbo-view-distance` — chunk view distance used by each CAPTCHA Limbo.
- `limbo-simulation-distance` — simulation distance used by each CAPTCHA Limbo.
- `creative-mode=true` — gives Creative mode inside the challenge for visual inspection.
- `lock-player-position=false` — lets you fly around the construction while tuning it.

## Reliability and load

Per-session Limbo worlds are more reliable than fake blocks, but they cost more RAM than one shared empty Limbo. pnCaptcha therefore keeps the existing per-IP rate limiter and also adds `max-active-captchas`. The world is tiny and in-memory, and it is disposed shortly after the player leaves the verification flow.

Wrong answers currently keep the same visible CAPTCHA for the remaining attempts. This is intentional in 0.3.0: it avoids switching worlds mid-session while the rendering architecture is being validated.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.3.0.jar`.
