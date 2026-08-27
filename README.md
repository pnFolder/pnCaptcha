# pnCaptcha

`pnCaptcha` — CAPTCHA-шлюз для Velocity 3.5.x. Проверка проходит в отдельном лёгком LimboAPI `VirtualWorld`, поэтому Paper/backend не принимает непроверенные подключения. CAPTCHA строится реальными виртуальными блоками внутри Limbo до входа игрока; PacketEvents не нужен.

Текущая версия: **0.5.0**.

## Требования

- Java 21+
- Velocity 3.5.x
- LimboAPI 1.1.26+

## Архитектура

```text
Internet -> Velocity -> проверка cache/rate-limit -> LimboAPI 3D CAPTCHA -> lobby
```

Для каждой активной проверки создаётся маленький in-memory мир. После успешной проверки, disconnect, timeout или исчерпания попыток мир освобождается. Количество одновременно активных CAPTCHA ограничивается через `security.max-active-captchas`.

## Один конфиг

Весь плагин настраивается через:

```text
plugins/pncaptcha/config.yml
```

`0.5.0` использует `config-version: 1`. Если найден старый конфиг без этой версии, pnCaptcha сохраняет его как `config.pre-0.5.0.yml.bak` и создаёт новый полный файл. Старый `config.properties` больше не используется.

## Что настраивается

- `general` — lobby, длина кода, попытки, timeout, cache.
- `security` — IP rate-limit и лимит одновременно активных Limbo-миров.
- `updates` — проверка нового GitHub Release при запуске, timeout запроса, уведомления консоли и игроков с permission.
- `limbo` — view/simulation distance, авторасширение view-distance, padding чанков, свет и платформа под игроком вплоть до её размера и материала.
- `player` — gamemode, spawn, камера, lock-position и recovery. Recovery возвращает игрока назад, если он упал ниже заданной высоты, улетел слишком далеко или поднялся слишком высоко.
- `scene` — расстояние, высота, боковое смещение, направление размещения и полный yaw/pitch/roll всего объекта.
- `font` — встроенный `classic-5x7` или полностью свой bitmap-шрифт из `0/1` прямо в YAML.
- `geometry` — ширина/высота одного pixel-voxel, общая глубина, толщина front/back слоёв, расстояние между буквами, подъём каждой следующей буквы, depth-step, зеркалирование и направление extrusion.
- `randomness` — отдельные jitter и 3D-повороты букв, случайная глубина букв, а также jitter всей сцены по позиции и углам.
- `palette` — независимые weighted-палитры front/side/back, режимы `per-block`, `per-character`, `solid`, плюс отдельная accent-палитра с шансом появления.
- `noise` — количество, cluster size, зона, глубина и weighted материалы помех. Stained glass можно использовать как визуально прозрачные помехи.
- `messages` — многострочные MiniMessage-сообщения с gradient/hex/hover/click и placeholders.

## Полезные настройки

Массивная CAPTCHA под углом:

```yaml
scene:
  distance-blocks: 30.0
  center-height-blocks: 8.0
  rotation:
    yaw-degrees: 28.0
    pitch-degrees: -3.0
    roll-degrees: 2.0

geometry:
  pixel-width: 3
  pixel-height: 3
  depth-blocks: 5
  front-thickness-blocks: 1
  back-thickness-blocks: 1
  letter-gap-blocks: 3
```

Автовозврат после падения:

```yaml
player:
  recovery:
    enabled: true
    below-spawn-blocks: 8.0
    above-spawn-blocks: 40.0
    max-horizontal-distance-blocks: 48.0
    cooldown-millis: 500
    preserve-current-look: false
```

Небольшой разброс букв:

```yaml
randomness:
  character:
    vertical-jitter-blocks: 1
    depth-jitter-blocks: 1
    depth-variation-blocks: 1
    rotation-yaw-jitter-degrees: 2.0
    rotation-pitch-jitter-degrees: 1.0
    rotation-roll-jitter-degrees: 1.0
```

Палитра задаётся Minecraft block-id и весом. Чем выше `weight`, тем чаще выбирается материал.

## Сообщения

`messages.*` принимает список строк MiniMessage. Например:

```yaml
messages:
  update-available:
    - "<gradient:#FFD166:#FF6B6B><bold>Доступно обновление pnCaptcha</bold></gradient>"
    - "<gray>{current} <dark_gray>→ <green>{latest}</green>"
    - "<click:open_url:'{url}'><aqua><underlined>Открыть релиз</underlined></aqua></click>"
```

Поддерживаемые placeholders зависят от события: `{attempt}`, `{max}`, `{timeout}`, `{reason}`, `{current}`, `{latest}`, `{url}`.

## Проверка обновлений

При запуске pnCaptcha может обратиться к `https://api.github.com/repos/<owner>/<repo>/releases/latest`. Проверка выполняется асинхронно. Если версия новее, в консоль выводится предупреждение, а игрокам с `updates.notify-permission` отправляется кликабельное сообщение.

## Сборка

```bash
gradle clean build
```

Готовый shaded JAR:

```text
build/libs/pnCaptcha-0.5.0.jar
```
