# pnCaptcha

`pnCaptcha` — настраиваемый 3D CAPTCHA-шлюз для Velocity. Непроверенный игрок попадает в персональный LimboAPI `VirtualWorld`, видит объёмный voxel-код и только после успешной проверки маршрутизируется на доступный backend.

## Требования

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+

PacketEvents не требуется.

## Основное

- Реальная 3D voxel CAPTCHA внутри LimboAPI `VirtualWorld` без Paper/backend для проверки.
- Персональный VirtualWorld создаётся и полностью заполняется блоками до появления игрока.
- Полная настройка расстояния, позиции, yaw/pitch/roll, размеров voxel, глубины, front/back-слоёв и расстояния между символами.
- Встроенные шрифты `classic-5x7` и `ornate-9x12`, плюс пользовательский bitmap-шрифт из `0/1` прямо в `config.yml`.
- `porous` fill с настраиваемой плотностью, сохранением связности, защитой концов штрихов и контролем внешнего контура.
- `clustered` palette mode для цветовых пятен из нескольких Minecraft-материалов внутри одного символа.
- Отдельная outline-палитра для более читаемого внешнего силуэта porous-символов.
- Weighted-палитры front/side/back, accent-блоки, прозрачные glass-помехи и отдельные параметры рандомизации.
- Recovery при падении, слишком большой высоте и выходе за горизонтальный радиус.
- Многострочные MiniMessage-сообщения, HEX, gradients, hover и click.
- Actions: `message`, `actionbar`, `title`, `sound`, `command`, `disconnect`, `connect`, `teleport`, `gamemode`, `bossbar`.
- BossBar Actions поддерживают create/update, countdown/countup-анимацию, set/add progress, pause/resume, hide/remove и динамические placeholders времени/процента.
- Несколько backend-серверов с `priority`, `least-players`, `random`, `weighted-random`, `round-robin` и `first-available`.
- Общий лимит сети, резервные слоты и отдельные лимиты/резервные слоты каждого backend.
- IP rate-limit, TTL cache проверенных UUID/IP, bypass permission и лимит одновременно активных CAPTCHA-миров.
- Асинхронная проверка GitHub Releases при запуске.
- bStats Velocity с service id `33698`; библиотека shaded и relocated внутрь JAR.

## Новый mosaic-визуал

Новая визуальная система ориентирована на крупную CAPTCHA, расположенную дальше от камеры. Буквы остаются одним статическим объектом внутри Limbo, но перед входом игрока каждая сцена может получить собственную геометрию, пустоты и распределение материалов.

Основные параметры:

```yaml
scene:
  distance-blocks: 42.0
  rotation:
    yaw-degrees: 8.0

font:
  preset: "ornate-9x12"

geometry:
  depth-blocks: 4
  fill:
    mode: "porous"
    density: 0.78
    preserve-connectivity: true
    protect-endpoints: true
    outline-preserve-percent: 78.0

palette:
  mode: "clustered"
  cluster-size-min: 2
  cluster-size-max: 5
  outline:
    enabled: true
    chance-percent: 86.0
```

`porous` не просто случайно удаляет любые блоки: при включённом `preserve-connectivity` алгоритм старается не разваливать символ на независимые части, а `protect-endpoints` бережёт окончания штрихов.

`clustered` распределяет материалы небольшими группами, поэтому внутри одного символа появляются участки stained glass, terracotta, copper и тёмных блоков вместо полностью независимого цветного шума.

## Конфигурация

Вся runtime-настройка находится в одном файле:

```text
plugins/pncaptcha/config.yml
```

Схема конфигурации сейчас `config-version: 3`.

Стандартный `config.yml` намеренно большой: он является встроенной документацией. Для каждого параметра указано назначение, допустимые значения, специальные значения вроде `0` и практические примеры. Все Action types и BossBar operations также расписаны прямо в конфиге.

После изменения конфигурации требуется полный restart Velocity.

## Готовые примеры

В каталоге [`examples/`](examples/) находятся копируемые наборы настроек:

- `visual-mosaic-reference.yml` — основной mosaic/reference стиль;
- `visual-classic-dense.yml` — прежний плотный `classic-5x7`;
- `visual-frost-glass.yml` — холодный стеклянный стиль;
- `visual-copper-industrial.yml` — медный industrial;
- `visual-neon-void.yml` — тёмный neon/void;
- `visual-hard-readable.yml` — более сложный, но сохраняющий читаемость вариант;
- `visual-minimal-clean.yml` — чистый вариант для диагностики;
- `font-custom-example.yml` — пример собственного bitmap-шрифта;
- `actions-bossbar-showcase.yml` — большая BossBar-демонстрация;
- `actions-admin-feedback.yml` — примеры разных Action types;
- `routing-multi-lobby.yml` — пример нескольких lobby и резервного backend.

Примеры сделаны как фрагменты секций: копируй нужные блоки в основной `config.yml`, а не заменяй документационный конфиг вслепую.

## Быстрый возврат к прежнему визуалу

Новая система не требует удаления старого стиля. Для возврата к более плотной CAPTCHA достаточно примерно таких значений:

```yaml
scene:
  distance-blocks: 30.0
  rotation:
    yaw-degrees: 28.0

font:
  preset: "classic-5x7"

geometry:
  pixel-width: 2
  pixel-height: 2
  depth-blocks: 3
  fill:
    mode: "solid"
    density: 1.0

palette:
  mode: "per-block"
  outline:
    enabled: false
```

## BossBar Actions

Пример таймера, автоматически использующего `general.timeout-seconds`:

```yaml
actions:
  enabled: true
  triggers:
    challenge-start:
      - type: "bossbar"
        bossbar-id: "captcha-timer"
        bossbar-operation: "animate"
        text: "<aqua>Проверка</aqua> <gray>• {bossbar_seconds}s</gray>"
        bossbar-color: "blue"
        bossbar-overlay: "progress"
        bossbar-start-progress: 1.0
        bossbar-end-progress: 0.0
        bossbar-duration-ms: 0
        bossbar-update-interval-ms: 100
```

Уменьшение той же полоски после неверного ответа:

```yaml
wrong-answer:
  - type: "bossbar"
    bossbar-id: "captcha-timer"
    bossbar-operation: "add-progress"
    bossbar-progress-delta: -0.12
    bossbar-color: "red"
```

Операции: `show`, `update`, `animate`, `set-progress`, `add-progress`, `pause`, `resume`, `hide`, `remove`.

## Routing

```yaml
routing:
  strategy: "least-players"
  network-max-players: 0
  network-reserve-slots: 0

  servers:
    - name: "lobby"
      enabled: true
      priority: 10
      weight: 5
      max-players: 0
      reserve-slots: 0
```

Добавляй любое количество Velocity `RegisteredServer`. Сервер, достигший своего `max-players - reserve-slots`, автоматически исключается из выбора.

## Actions

Triggers:

```text
challenge-start
wrong-answer
passed
exhausted
timeout
recovery
rate-limited
busy
network-full
unavailable
route-unavailable
```

Основные placeholders: `{player}`, `{uuid}`, `{ip}`, `{online}`, `{attempt}`, `{max}`, `{remaining}`, `{timeout}`, `{reason}`, `{server}`, `{captcha}`.

BossBar-анимация дополнительно даёт `{bossbar_progress}`, `{bossbar_percent}`, `{bossbar_seconds}`, `{bossbar_millis}`.

## Build

```bash
gradle clean build
```

Пока feature-ветка не выпущена отдельным релизом, project version остаётся текущей стабильной версией. Финальное имя release-JAR будет приведено к pnFolder Release Authoring Contract только после утверждения релиза.
