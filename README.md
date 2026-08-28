# pnCaptcha

`pnCaptcha` — настраиваемый 3D CAPTCHA-шлюз для Velocity. Непроверенный игрок попадает в персональный LimboAPI `VirtualWorld`, видит объёмный voxel-код и только после успешной проверки маршрутизируется на доступный backend.

## Требования

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+

PacketEvents не требуется.

## Основное

- Реальная 3D voxel CAPTCHA внутри LimboAPI `VirtualWorld` без Paper/backend для проверки.
- Полная настройка расстояния, позиции, yaw/pitch/roll, размеров voxel, глубины, front/back-слоёв и расстояния между символами.
- Встроенный `classic-5x7` и пользовательский bitmap-шрифт из `0/1` прямо в `config.yml`.
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

## Конфигурация

Вся настройка находится в одном файле:

```text
plugins/pncaptcha/config.yml
```

Схема конфигурации `config-version: 3`. Каждая настройка, каждый Action и BossBar-параметр описаны комментариями непосредственно в стандартном `config.yml`; там же есть копируемые примеры.

При переходе со схемы 1.0.0 старый конфиг сохраняется как:

```text
plugins/pncaptcha/config.pre-1.1.0.yml.bak
```

После этого создаётся новый документированный конфиг.

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

Готовый shaded JAR:

```text
build/libs/pnCaptcha-1.1.1.jar
```
