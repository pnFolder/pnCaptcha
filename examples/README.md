# pnCaptcha configuration examples

Эта папка содержит готовые идеи и копируемые фрагменты для `plugins/pncaptcha/config.yml`.

Важно: это **примеры секций**, а не отдельные runtime-конфиги. Копируй нужные секции в основной `config.yml` и затем полностью перезапускай Velocity.

## Визуальные стили

- `visual-mosaic-reference.yml` — текущий мозаичный стиль: крупно, далеко, porous, glass/copper/deepslate.
- `visual-classic-dense.yml` — плотный старый `classic-5x7` без пористости.
- `visual-frost-glass.yml` — холодная ледяная палитра из голубого/белого стекла.
- `visual-copper-industrial.yml` — медь, deepslate и тёмный industrial look.
- `visual-neon-void.yml` — почти чёрная конструкция с редкими яркими акцентами.
- `visual-hard-readable.yml` — более сложная CAPTCHA, но без сильного хаоса символов.
- `visual-minimal-clean.yml` — чистая диагностическая сцена без noise и случайности.

## Шрифты

- `font-custom-example.yml` — полноценный пример собственного bitmap-шрифта и подходящей геометрии.

## Actions и UI

- `actions-bossbar-showcase.yml` — countdown, штраф за ошибку, отдельная полоса попыток, countup, pause/resume и cleanup.
- `actions-admin-feedback.yml` — Message, Title, ActionBar, Sound, permission, command, disconnect, teleport, gamemode и connect-примеры.

## Routing

- `routing-multi-lobby.yml` — несколько lobby, лимиты, reserve slots, priority/least-players/weighted-random.

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
- `density: 0.50..0.65` — агрессивная пористость; обязательно проверяй читаемость глазами.
- `cluster-size-min/max: 1..2` — мелкая цветная крошка.
- `cluster-size-min/max: 3..6` — более крупные цветовые пятна.
- `scene.distance-blocks: 30..35` — ближе и массивнее.
- `scene.distance-blocks: 40..50` — дальше, строка лучше помещается в кадре.
- `outline.chance-percent: 80..95` — хороший диапазон для тёмного читаемого силуэта.

## Практический порядок настройки

1. Сначала отключи `noise` и поставь `randomness.enabled: false`.
2. Настрой `scene`, размер, глубину и шрифт.
3. Включи `fill.mode: porous` и подбери `density`.
4. Настрой `palette` и `outline`.
5. Последним включай `noise` и небольшую случайность.

Так проще понять, какая именно настройка изменила внешний вид.

Основной `config.yml` остаётся главным источником документации: в нём расписаны допустимые значения каждого параметра, особые значения вроде `0` и копируемые примеры Actions.