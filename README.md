# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.1**.

## Architecture

The plugin deliberately splits transport and rendering:

- **LimboAPI** provides one shared in-memory virtual server. There is no Paper CAPTCHA server and no world file per player.
- The shared Limbo contains only **one physical pedestal block under the player**. There is no platform and no CAPTCHA wall.
- **PacketEvents** renders the CAPTCHA as `BLOCK_CHANGE` packets that are sent to one client only.
- Each bitmap pixel is scaled into a larger voxel cell and extruded backwards several blocks, with a shifted dark side layer, so the glyphs are genuinely volumetric rather than a flat 2D plane.
- Decorative noise is placed behind the glyph volume so it adds depth without covering the readable front face.
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
  +-- one pedestal block under player
  +-- PacketEvents 3D voxel overlay: "K7M4P"
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

When PacketEvents is installed together with LimboAPI, enable LimboAPI compatibility mode and keep uncompressed packet saving enabled.

## Configuration

On first start, `plugins/pncaptcha/config.properties` is generated:

```properties
target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=12
max-joins-per-window=6
join-window-seconds=10
glyph-scale=2
glyph-depth=3
glyph-materials=minecraft:white_concrete,minecraft:light_gray_concrete,minecraft:cyan_concrete,minecraft:blue_concrete,minecraft:purple_concrete
glyph-side-material=minecraft:deepslate_tiles
noise-material=minecraft:gray_stained_glass
```

`glyph-scale=2` turns each font pixel into a 2x2 voxel cell. `glyph-depth=3` creates three Z layers. Rear layers are shifted slightly down/right and use `glyph-side-material`, making the depth clearly visible from the fixed camera position.

Existing 0.2.0 configs do not need to be deleted: missing 0.2.1 options automatically use the new defaults. If you want the cleaner default background noise amount, change `noise-blocks` from `22` to `12` manually.

The generated code excludes ambiguous `0/O/1/I/L` characters. The front face keeps the built-in 5x7 glyph shapes, but the 2x scaling makes the strokes much thicker and easier to read.

## Verification flow

1. LimboAPI fires `LoginLimboRegisterEvent`.
2. pnCaptcha skips a challenge only when the same UUID **and** IP are still in the verification cache.
3. A small per-IP join-window limiter rejects obvious reconnect floods before CAPTCHA rendering.
4. The player is spawned onto one pedestal block in the shared Limbo instance.
5. PacketEvents sends the 3D fake-block volume only to that player's client.
6. Limbo's session handler consumes chat input directly, so the answer never needs a Paper server.
7. Wrong answer: increment attempt counter and redraw the overlay in place.
8. Correct answer: cache the UUID+IP pair and hand the player to the configured backend.
9. Timeout or too many attempts: disconnect and clean the session.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.1.jar`.

GitHub Actions builds and tests every push and pull request.
