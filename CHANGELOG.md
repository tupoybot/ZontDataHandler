# Changelog

## Unreleased

- Этап 7 задокументирован как косметический follow-up: обновление `LONG_TEXT` layout для paired complications и отдельная проверка typography `ZONT burner` перед возможной правкой размера шрифта.

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
- Для `overview + icons` итоговое поведение всё ещё зависит от конкретного watch face и типа slot; подробная практическая проверка вынесена в этап 5.
- Без локального keystore или GitHub Actions signing secrets release build по-прежнему остаётся честным unsigned промежуточным output.
