# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.2**.

## Architecture

The plugin deliberately splits transport and rendering:

- **LimboAPI** provides one shared in-memory virtual server. There is no Paper CAPTCHA server and no world file per player.
- The shared Limbo contains only **one physical pedestal block under the player**. There is no platform and no CAPTCHA wall.
- **PacketEvents** renders the CAPTCHA as `BLOCK_CHANGE` packets that are sent to one client only.
- Glyph columns are written from world `+X` toward `-X`, matching the player's screen orientation and preventing mirrored text.
- Each font pixel is scaled into a larger voxel cell and extruded **straight along Z** into a real block volume.
- The player is intentionally spawned off-axis and looks diagonally across the CAPTCHA, so the extrusion is visible through normal Minecraft perspective instead of a fake shifted 2D shadow.
- Front voxels use a mottled stone/blue-gray palette while the side volume uses darker deepslate/blackstone variants.
- Decorative noise is placed behind the complete glyph volume so it does not cover the readable front silhouette.
- CAPTCHA letters are **not real blocks** in Limbo; different players can see different codes at the same coordinates.
- A wrong answer clears the previous client-side overlay and draws a new one without recreating the Limbo world.
- A successful answer moves the player from Limbo to the configured backend (`lobby` by default).

```text
                           real Z extrusion
                         ┌───────────────►
                         █████
                       █████
                     █████       fake client-only voxels
                   █████

     player ■  ↗ oblique view
        one physical pedestal block
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
glyph-depth=5
glyph-materials=minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:gray_concrete,minecraft:cyan_terracotta,minecraft:light_blue_terracotta
glyph-side-materials=minecraft:deepslate_tiles,minecraft:deepslate_bricks,minecraft:blackstone,minecraft:polished_blackstone
noise-material=minecraft:gray_stained_glass
```

`glyph-scale=2` turns each font pixel into a 2x2 voxel cell. `glyph-depth=5` gives the text five blocks of real Z depth. The default camera is roughly 19 degrees off-axis, far enough away to keep the whole five-character challenge visible while exposing the side volume.

For an existing 0.2.1 config, set `glyph-depth=5`. The old singular `glyph-side-material` key is still accepted for compatibility; the new `glyph-side-materials` list gives the intended mottled side texture.

The generated code excludes ambiguous `0/O/1/I/L` characters.

## Verification flow

1. LimboAPI fires `LoginLimboRegisterEvent`.
2. pnCaptcha skips a challenge only when the same UUID **and** IP are still in the verification cache.
3. A small per-IP join-window limiter rejects obvious reconnect floods before CAPTCHA rendering.
4. The player is spawned onto one pedestal block in the shared Limbo instance.
5. PacketEvents sends the distant 3D fake-block volume only to that player's client.
6. Limbo's session handler consumes chat input directly, so the answer never needs a Paper server.
7. Wrong answer: increment attempt counter and redraw the overlay in place.
8. Correct answer: cache the UUID+IP pair and hand the player to the configured backend.
9. Timeout or too many attempts: disconnect and clean the session.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.2.jar`.

GitHub Actions builds and tests every push and pull request.
