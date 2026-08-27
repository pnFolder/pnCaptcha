# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.6**.

## Architecture

- **LimboAPI** provides one shared in-memory virtual server.
- The Limbo world contains only **one physical pedestal block under the player**.
- **PacketEvents** renders each CAPTCHA as client-only fake block updates.
- Fake blocks are grouped by 16×16×16 chunk section and sent with `MULTI_BLOCK_CHANGE`.
- The renderer re-applies the frame after spawn so late Limbo chunk packets cannot permanently overwrite the fake blocks with air.
- The CAPTCHA is a **real rotated 3D object**: the entire text plane is rotated around world Y, so one side is physically farther from the player.
- Each lit font pixel becomes a configurable voxel cell (`glyph-scale-x` × `glyph-scale-y`) and is extruded by `glyph-depth` blocks along the rotated depth axis.
- Different players can see different codes at the same coordinates because CAPTCHA blocks are never written into the shared Limbo world.

## Runtime requirements

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+
- PacketEvents 2.13.0+

When PacketEvents is installed together with LimboAPI:

```yaml
main:
  save-uncompressed-packets: true
  compatibility-mode: true
```

LimboAPI defaults `chunk-radius-send-on-spawn` to `2`, which means the spawn chunk plus directly adjacent chunks. pnCaptcha 0.2.6 deliberately keeps its stock scene inside those immediately available chunks.

## Configuration

`plugins/pncaptcha/config.properties` is generated and older configs are migrated automatically.

```properties
config-version=5

target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=8
max-joins-per-window=6
join-window-seconds=10

# Inspection/player behaviour
creative-mode=true
lock-player-position=false

# Whole scene placement
captcha-distance-blocks=20.0
captcha-angle-degrees=24.0
captcha-center-height-blocks=8.0
camera-pitch-offset-degrees=0.0

# Glyph mass / size / spacing
glyph-scale-x=1
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

- `creative-mode=true` gives the player Creative mode inside CAPTCHA Limbo.
- `lock-player-position=false` lets the player fly around and inspect the 3D construction. Set it to `true` later for a locked production view.
- `captcha-distance-blocks` controls how many blocks away the CAPTCHA centre is. Default `20` is intentionally conservative for reliable visibility.
- `captcha-angle-degrees` physically rotates the entire text line. Default `24` gives clear perspective without pushing the far edge too deep into unloaded chunks.
- `captcha-center-height-blocks` changes vertical placement; the camera automatically aims at the configured centre.
- `glyph-scale-x` and `glyph-scale-y` control front-face fatness/size.
- `glyph-depth` is the **real 3D thickness in blocks**. Default `3`.
- `glyph-gap-blocks` controls spacing between characters.
- `glyph-jitter-y-blocks` and `glyph-jitter-depth-blocks` add small per-character offsets like the reference image.

### If you want about 30 blocks distance

You can use:

```properties
captcha-distance-blocks=30.0
captcha-angle-degrees=28.0
glyph-depth=3
```

With a large/angled CAPTCHA at that distance, also change LimboAPI to send a wider initial chunk area:

```yaml
main:
  chunk-radius-send-on-spawn: 3
```

Otherwise the far side of the rotated CAPTCHA can land outside the chunks the client has received when fake block updates are sent.

## Upgrade notes

`0.2.6` uses `config-version=5`. Exact stock 0.2.5 visual values (`30.0` distance, `28.0` angle, `glyph-scale-x=2`) are migrated to the safer visible defaults (`20.0`, `24.0`, `1`). Custom values are preserved. New keys such as `creative-mode` and `lock-player-position` are appended automatically.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.6.jar`.
