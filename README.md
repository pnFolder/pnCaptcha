# pnCaptcha

`pnCaptcha` is a Velocity CAPTCHA gate that keeps unverified connections away from real backend servers while rendering the challenge as a real 3D voxel sculpture inside a tiny per-session LimboAPI `VirtualWorld`.

Current development release: **0.4.0**.

## Architecture

- Velocity receives the connection.
- Verified UUID/IP pairs skip the challenge and continue to the configured backend.
- Unverified players receive their own tiny in-memory Limbo world.
- The entire CAPTCHA is written into that `VirtualWorld` before spawn, so normal chunk packets contain the blocks.
- No Paper backend is used for verification and pnCaptcha does not depend on PacketEvents.
- The session world is disposed after pass, fail, timeout, or disconnect.
- `security.max-active-captchas` and the IP join limiter cap resource usage under connection floods.

## Requirements

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+

## One configuration file

pnCaptcha 0.4.0 uses only:

```text
plugins/pncaptcha/config.yml
```

The old `config.properties` format is no longer used and is removed on startup. The default `config.yml` is copied from the jar with comments so every visual control is visible in one place.

## What can be configured

### Verification and protection

`general` controls target server, CAPTCHA length, attempts, timeout, and verified-cache lifetime. `security` controls per-IP connection rate and the maximum number of active per-session Limbo worlds.

### Limbo world

`limbo.view-distance` and `limbo.simulation-distance` control how much of the generated world is delivered. The single pedestal block under the player can be enabled/disabled and its block material can be changed.

### Player and camera

`player.game-mode` supports `creative`, `adventure`, `survival`, or `spectator`. Position locking can be enabled with a configurable radius. Spawn X/Y/Z are configurable.

The camera can automatically aim at the resolved CAPTCHA centre or use manual yaw/pitch. Separate yaw/pitch offsets are available for fine tuning.

### Full scene position and rotation

The whole CAPTCHA can be placed and oriented with:

```yaml
scene:
  distance-blocks: 30.0
  forward-yaw-degrees: 0.0
  lateral-offset-blocks: 0.0
  center-height-blocks: 8.0
  rotation:
    yaw-degrees: 28.0
    pitch-degrees: 0.0
    roll-degrees: 0.0
```

`forward-yaw-degrees` controls where the object is located around the player. `rotation.yaw-degrees` controls the strong receding-side perspective. `pitch` tilts the entire text up/down and `roll` rotates it clockwise/counter-clockwise.

### Font

The built-in `classic-5x7` font is available by default. Set `font.preset: custom` to define your own bitmap font directly in the same `config.yml` file. Every custom glyph is a matrix of `1` and `0`, so a server owner can completely replace the shape of every character without recompiling the plugin.

The generated character alphabet is also configurable.

### Size, fatness and 3D depth

```yaml
geometry:
  pixel-width: 2
  pixel-height: 2
  depth-blocks: 3
  letter-gap-blocks: 2
  center-text: true
```

`pixel-width` controls horizontal stroke fatness. `pixel-height` controls vertical size. `depth-blocks` is the real extrusion thickness. `letter-gap-blocks` controls spacing between letters.

### Randomness

Randomness can be turned off completely or switched to a fixed seed for repeatable screenshots/tests. Per-character horizontal, vertical and depth jitter are configurable. Per-session scene jitter can change distance, height, placement yaw, object yaw, object pitch and object roll.

### Materials / colors

Minecraft color is controlled by block materials. pnCaptcha supports separate weighted palettes for the bright/front face, side/body layers and deepest/back layer.

```yaml
palette:
  mode: "per-block"
  front:
    - block: "minecraft:polished_deepslate"
      weight: 4
    - block: "minecraft:cyan_terracotta"
      weight: 1
```

Supported palette modes:

- `per-block` — every voxel can independently select a weighted material.
- `per-character` — each character keeps one front, side and back material.
- `solid` — the first material in each palette is always used.

Any valid block id can be configured. Use concrete/stone for opaque geometry or stained glass for transparent/translucent-looking interference and accents.

### Noise / interference

Noise is generated behind the readable face so it does not simply cover the code. Its count, horizontal/vertical padding, minimum/maximum depth and weighted materials are configurable. Glass defaults give the reference-style floating interference without making the text impossible to read.

### Messages

All player-facing strings are in `messages`. The wrong-answer message supports `{attempt}` and `{max}` placeholders.

## Recommended visual starting point

The shipped defaults aim for a large reference-style scene:

```yaml
scene:
  distance-blocks: 30.0
  rotation:
    yaw-degrees: 28.0
    pitch-degrees: 0.0
    roll-degrees: 0.0

geometry:
  pixel-width: 2
  pixel-height: 2
  depth-blocks: 3
  letter-gap-blocks: 2
```

For an even more massive look, try `pixel-width: 3`, `pixel-height: 3`, and `depth-blocks: 4` or `5`, then raise `limbo.view-distance` if the scene becomes very large.

## Build

```bash
gradle clean build
```

The shaded plugin is written to:

```text
build/libs/pnCaptcha-0.4.0.jar
```
