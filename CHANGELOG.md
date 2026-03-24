# Changelog

## Unreleased

## 0.3 - 2026-03-25

- Launcher icons `mobile` и `wear` теперь берутся из PNG-исходников в `assets/icons/`.
- `mobile` и `wear` больше не держат фиксированный `versionCode`: build number теперь автоматически растёт между сборками и показывается в UI как новый `build`.
- README и GitHub Actions теперь явнее показывают signed/unsigned release state; отдельно зафиксировано, что существующий stable release keystore и уже заведённые GitHub secrets нужно переиспользовать, а генерация нового набора нужна только для первичной настройки.
- `ZONT overview` переведён на более спокойный text-first render path без noisy leading icon и без принудительного ручного переноса строки: provider отдаёт одну плоскую строку, а конкретный watch face renderer сам решает, где её переложить.
- `ZONT setpoint + coolant` и `ZONT room + air setpoint` оставлены в читаемом paired-виде с monochromatic pictogram и явной стрелочкой между текущим и целевым значением; даже если конкретный renderer скрывает leading icon, смысл пары остаётся понятен из текста.
- Добавлен отдельный `ZONT color overview`: emoji-first provider для color-capable renderers, не заменяющий обычный монохромный `ZONT overview`.
- `ZONT overview + icons` удалён из manifest и codebase как image-only provider без внятного практического сценария; если slot был привязан к нему раньше, после обновления его нужно переназначить через picker.
- Зафиксировано текущее platform limitation: используемая Wear OS / AndroidX API-ветка не поддерживает inline drawable-пиктограммы внутри `ComplicationText`, поэтому stage 8 завершён честным text-first fallback с live-проверкой на эмуляторе и Galaxy watch face, а не с preview-only drawable-обещанием.
- Inline `Material Symbols` для `ComplicationText` по-прежнему не считаются поддерживаемым путём: renderer watch face не даёт нам надёжно зафиксировать нужный font family, поэтому такие glyphs нельзя обещать как стабильный inline-вариант.

## 0.2 - 2026-03-24

- Автообновление на телефоне теперь различает transient и permanent refresh failures: transient background ошибки уходят в `WorkManager` retry/backoff, а постоянные проблемы уровня токена / `device_id` ставят auto-refresh на паузу вместо бесконечного тихого reschedule.
- `ZONT overview + icons` теперь входит в явный watch-side `ComplicationUpdater.requestAll()`, поэтому image-provider не остаётся случайно устаревшим после очередного phone -> watch sync.
- Телефон и часы теперь показывают установленный version/build прямо в статусных экранах, чтобы sideload и install/upgrade path можно было проверить без догадок.
- Release signing можно задать через gitignored `keystore.properties` или env vars; README и GitHub Actions теперь честно различают signed release APK и unsigned промежуточный output.
- Добавлен локальный генератор release signing materials и GitHub Actions теперь умеет восстанавливать keystore из repository secrets без хранения приватных ключей в git.
- `ZONT overview + icons` теперь публикуется только для image-compatible слотов, чтобы не деградировать в text-only fallback на части watch face.
- Обычные большие `LONG_TEXT`-слоты, включая нижний большой слот на совместимых watch face, теперь можно использовать не только для `ZONT overview`, но и для `ZONT setpoint + coolant` / `ZONT room + air setpoint`.
- В документации отдельно разведены обычные большие слоты с системным picker'ом и private Samsung slots уровня `Ultra Analog`.

## 0.1 - 2026-03-23

- Первая публичная версия приложения для отображения данных `ZONT Online` на Wear OS часах через complications и экран обзора на самих часах.
- Сохранена рабочая вертикаль `phone -> watch -> complications`: телефон получает snapshot из API ZONT, часы принимают его и публикуют providers.
- В `mobile` реализован flow `login/password -> get_authtoken`; ручной `X-ZONT-Token` сохранён как fallback.
- После `Get token` приложение запрашивает `devices[]` и по возможности автоматически подставляет `device_id`.
- Подтверждённый маппинг метрик сохранён: комнатная температура, температура теплоносителя, уставки и модуляция горелки не меняют смысл между телефоном и часами.
- Доступны 8 providers, включая `ZONT overview`, `ZONT overview + icons`, paired temperature layouts и отдельные room / burner / setpoint / coolant complications.
- `ZONT burner` поддерживает `RANGED_VALUE` вместе с `SHORT_TEXT`, что полезно для gauge-like слотов на части watch face.
- Обновлены иконки `burner` и `coolant`, а `overview + icons` больше не деградирует в буквенную легенду `R·B·S·C` в `LONG_TEXT`.
- Добавлен GitHub Actions workflow для сборки `mobile` и `wear` debug/release APK и публикации APK assets в GitHub Release по тегу.
- README расширен описанием реального сценария использования и дополнен скриншотами интерфейса Wear OS.

## Known Limitations

- Специальные Samsung Ultra-style slots могут оставаться недоступными для сторонних providers даже при наличии `RANGED_VALUE`; это вынесено в backlog этапа 5.
- Текущая Wear OS complications API-ветка не поддерживает inline drawable-пиктограммы внутри `ComplicationText`, поэтому `LONG_TEXT` providers ограничены text/title плюс одной общей icon-областью на всю complication.
- Без локального keystore или GitHub Actions signing secrets release build по-прежнему остаётся честным unsigned промежуточным output.
