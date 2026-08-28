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
- Actions на события CAPTCHA: `message`, `actionbar`, `title`, `sound`, `command`, `disconnect`, `connect`, `teleport`, `gamemode`.
- Несколько backend-серверов с `priority`, `least-players`, `random`, `weighted-random`, `round-robin` и `first-available`.
- Общий лимит сети, резервные слоты и отдельные лимиты/резервные слоты каждого backend.
- IP rate-limit, TTL cache проверенных UUID/IP, bypass permission и лимит одновременно активных CAPTCHA-миров.
- Асинхронная проверка GitHub Releases при запуске.
- bStats Velocity с service id `33692`; библиотека shaded и relocated внутрь JAR.

## Конфигурация

Вся настройка находится в одном файле:

```text
plugins/pncaptcha/config.yml
```

Схема конфигурации `config-version: 2`. При переходе со старой схемы существующий файл сохраняется как:

```text
plugins/pncaptcha/config.pre-1.0.0.yml.bak
```

После этого создаётся полный актуальный конфиг со всеми комментариями и примерами.

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

Actions выполняются по trigger-событиям:

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

Пример:

```yaml
actions:
  enabled: true
  triggers:
    wrong-answer:
      - type: "actionbar"
        text: "<red>Неверно</red> <gray>{attempt}/{max}</gray>"

      - type: "sound"
        sound: "minecraft:block.note_block.bass"
        volume: 0.8
        sound-pitch: 0.7
```

Для действий доступны placeholders `{player}`, `{uuid}`, `{ip}`, `{online}`, `{attempt}`, `{max}`, `{reason}`, `{server}` и `{captcha}`.

## Build

```bash
gradle clean build
```

Готовый shaded JAR:

```text
build/libs/pnCaptcha-1.0.0.jar
```
