# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.4**.

## Architecture

- **LimboAPI** provides one shared in-memory virtual server.
- The Limbo world contains only **one physical pedestal block under the player**.
- **PacketEvents** renders each CAPTCHA as client-only fake block updates.
- Fake blocks are grouped by 16×16×16 chunk section and sent with `MULTI_BLOCK_CHANGE`.
- The renderer re-applies the frame after spawn so late Limbo chunk packets cannot permanently overwrite the fake blocks with air.
- The CAPTCHA is now a **real rotated 3D object**: the entire local text plane is rotated around world Y, rather than keeping every character on one straight world-Z line and only moving the camera.
- Each lit font pixel becomes a configurable voxel cell (`glyph-scale-x` × `glyph-scale-y`) and is extruded by `glyph-depth` blocks along the rotated depth axis.
- Per-character Y/depth jitter can make the line less sterile while preserving readability.
- Different players can see different codes at the same coordinates because CAPTCHA blocks are never written into the shared Limbo world.

```text
                        farther side
                           ████
                       █████       rotated front plane
                   █████
               █████

player ■ ------------------------------ ~30 blocks
        camera automatically aims at CAPTCHA centre
```

## Runtime requirements

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+
- PacketEvents 2.13.0+

When PacketEvents is installed together with LimboAPI, enable LimboAPI compatibility mode and keep uncompressed packet saving enabled:

```yaml
main:
  save-uncompressed-packets: true
  compatibility-mode: true
```

## Configuration

`plugins/pncaptcha/config.properties` is generated and older configs are filled with newly introduced keys automatically.

```properties
config-version=4

target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=8
max-joins-per-window=6
join-window-seconds=10

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

### Visual controls

- `captcha-distance-blocks` — distance from the player to the centre of the front face. Default: `30`.
- `captcha-angle-degrees` — physical Y rotation of the whole CAPTCHA. `0` is straight-on; positive/negative values choose which side recedes. Default: `28`.
- `captcha-center-height-blocks` — vertical centre of the CAPTCHA above the player's feet. The camera automatically aims at this height.
- `camera-pitch-offset-degrees` — optional artistic correction after automatic aiming.
- `glyph-scale-x` — horizontal fatness of every font pixel. Set `3` for very thick strokes.
- `glyph-scale-y` — vertical size of every font pixel.
- `glyph-depth` — actual 3D extrusion thickness in blocks. Default: `3`.
- `glyph-gap-blocks` — clear space between characters.
- `glyph-jitter-y-blocks` — random per-character vertical shift.
- `glyph-jitter-depth-blocks` — random per-character forward/back shift.

The default `30 block / 28° / 2×2 / depth 3` setup is designed to look large and massive while still remaining practical for Limbo's nearby chunk delivery. pnCaptcha logs the calculated chunk bounds on startup. If a heavily enlarged custom scene reaches beyond the usual LimboAPI spawn radius of two chunks and outer parts do not appear, increase LimboAPI's `main.chunk-radius-send-on-spawn`.

### Upgrade from 0.2.3

`0.2.4` adds `config-version=4` and automatically appends the new scene controls. If the file still contains the exact stock 0.2.3 values `glyph-scale-x=1` and `glyph-depth=6`, they are migrated to the new defaults `2` and `3`. Custom older values are preserved.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.4.jar`.
