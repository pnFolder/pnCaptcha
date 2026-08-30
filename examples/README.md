# pnCaptcha configuration examples

Эта папка содержит готовые идеи и копируемые фрагменты для `plugins/pncaptcha/config.yml`.

Важно: это **примеры секций**, а не отдельные runtime-конфиги. Копируй нужные секции в основной `config.yml` и затем полностью перезапускай Velocity.

## Визуальные стили

- `visual-mosaic-reference.yml` — текущий мозаичный стиль: крупно, далеко, porous, glass/copper/deepslate.
- `visual-classic-dense.yml` — плотный старый `classic-5x7` без пористости.
- `visual-frost-glass.yml` — холодная ледяная палитра из голубого/белого стекла.
- `visual-copper-industrial.yml` — медь, deepslate и тёмный industrial look.
- `visual-neon-void.yml` — тёмная основа с яркими редкими акцентами.
- `visual-hard-readable.yml` — более сложная CAPTCHA, но без сильного хаоса символов.

## Actions и UI

- `actions-bossbar-showcase.yml` — countdown, штраф за ошибку, countup, pause/resume и cleanup.
- `actions-admin-feedback.yml` — Title, ActionBar, Sound, command и disconnect-примеры.

## Routing

- `routing-multi-lobby.yml` — несколько lobby, лимиты, reserve slots и fallback.

## Как собирать собственный стиль

Обычно достаточно менять четыре группы:

```yaml
scene:
  distance-blocks: 42.0

font:
  preset: "ornate-9x12"

geometry:
  fill:
    mode: "porous"
    density: 0.78

palette:
  mode: "clustered"
```

После этого уже настраивай глубину, материалы, outline и noise.

### Быстрые ориентиры

- `density: 0.90..1.00` — почти плотная буква.
- `density: 0.70..0.85` — заметная мозаика, обычно хороший production-диапазон.
- `density: 0.50..0.65` — агрессивная пористость; проверяй читаемость.
- `cluster-size-min/max: 1..2` — мелкая цветная крошка.
- `cluster-size-min/max: 3..6` — более крупные цветовые пятна.
- `scene.distance-blocks: 30..35` — ближе и массивнее.
- `scene.distance-blocks: 40..50` — дальше, строка лучше помещается в кадре.

Основной `config.yml` остаётся главным источником документации: в нём расписаны допустимые значения каждого параметра и примеры Actions.