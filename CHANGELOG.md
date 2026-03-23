# Changelog

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
- Release APK прикладываются к GitHub Release как assets, но без приватной signing-инфраструктуры они остаются unsigned release outputs.
