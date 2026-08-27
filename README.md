# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers.

Current test release: **0.2.3**.

## Architecture

- **LimboAPI** provides one shared in-memory virtual server.
- The Limbo world contains only **one physical pedestal block under the player**.
- **PacketEvents** renders each CAPTCHA as client-only fake block updates.
- The default five-character frame is intentionally kept inside chunks that LimboAPI sends immediately on spawn. This prevents the Minecraft client from discarding fake block changes for unloaded chunks.
- Fake blocks are grouped by 16×16×16 chunk section and sent with `MULTI_BLOCK_CHANGE`, so one frame needs far fewer packets than thousands of individual block changes.
- The renderer re-applies the same frame shortly after spawn because a late Limbo chunk packet would otherwise overwrite client-only fake blocks with air.
- Glyphs use corrected screen orientation, a narrow `1x` horizontal face, `2x` vertical scaling, and deep `6`-block Z extrusion. The player views the volume from an off-axis camera, so the side depth is real perspective rather than a fake 2D shadow.
- Different players can see different codes at the same coordinates because no CAPTCHA blocks are written into the shared world.

```text
Velocity
  |
  +-- verified cache hit ----------------------------> lobby
  |
  v
shared LimboAPI world
  |
  +-- one physical block under player
  +-- PacketEvents 3D client-only CAPTCHA
  +-- chat answer / timeout / attempts
  |
  +-- pass ------------------------------------------> lobby
  `-- fail ------------------------------------------> disconnect
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

On first start, `plugins/pncaptcha/config.properties` is generated:

```properties
target-server=lobby
captcha-length=5
max-attempts=3
timeout-seconds=30
verified-cache-minutes=720
noise-blocks=8
max-joins-per-window=6
join-window-seconds=10
glyph-scale-x=1
glyph-scale-y=2
glyph-depth=6
glyph-materials=minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:gray_concrete,minecraft:cyan_terracotta,minecraft:light_blue_terracotta
glyph-side-materials=minecraft:deepslate_tiles,minecraft:deepslate_bricks,minecraft:blackstone,minecraft:polished_blackstone
noise-material=minecraft:gray_stained_glass
```

### Upgrading from 0.2.1/0.2.2

Old `glyph-scale=2` is intentionally ignored by the 0.2.3 renderer. It made the five-character face too wide for the immediately available Limbo chunk set on some clients. Missing `glyph-scale-x` and `glyph-scale-y` values automatically use `1` and `2`, so deleting the old configuration is not required.

## Build

```bash
gradle clean build
```

The shaded plugin is written to `build/libs/pnCaptcha-0.2.3.jar`.
