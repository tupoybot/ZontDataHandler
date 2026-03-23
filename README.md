# ZONT Data Handler

`ZONT Data Handler` - это Android / Wear OS приложение для отображения данных из `ZONT Online` на часах Wear OS через complications и отдельный экран обзора на самих часах.

Сценарий работы такой:

- `mobile` авторизуется в `ZONT Online`, получает актуальный snapshot устройства, хранит его локально и отправляет на часы;
- `wear` принимает snapshot, показывает ключевые значения прямо на часах и публикует complication providers для watch face;
- `shared` содержит общую модель данных, форматирование значений и контракт обмена между телефоном и часами.

Проект рассчитан на sideload-установку без Play Market и позволяет быстро вынести на циферблат главные метрики отопления: комнатную температуру, температуру теплоносителя, уставки и модуляцию горелки. Кодовая база целиком сгенерирована Codex. Репозиторий публичный, поэтому логины, пароли, токены и ключи подписи не хранятся в git.

## Screenshots

<p align="center">
  <img src="docs/screenshots/one-ui-dark.png" width="240" alt="Red analog watch face with ZONT metrics" />
  <img src="docs/screenshots/one-ui-light.png" width="240" alt="Light analog watch face with ZONT metrics" />
  <img src="docs/screenshots/emulator.png" width="227" alt="Dark digital watch face with ZONT metrics" />
</p>

## What It Does

Приложение закрывает простой практический кейс: у владельца котла и датчиков ZONT уже есть данные в облаке `ZONT Online`, но хочется видеть их на запястье без постоянного открытия телефона. Телефон выступает как клиент к API, а часы получают уже подготовленный snapshot и могут отрисовать его в компактном виде на циферблате.

На текущий момент приложение умеет:

- получить `X-ZONT-Token` по логину и паролю или принять уже готовый токен вручную;
- автоматически запросить список устройств и по возможности подставить `device_id`;
- синхронизировать данные по цепочке `phone -> watch`;
- показать на часах overview-экран с несколькими ключевыми метриками;
- опубликовать набор complication providers для разных типов слотов и watch face.

## Providers

Текущие providers:

- `ZONT overview`
- `ZONT overview + icons`
- `ZONT room`
- `ZONT burner`
- `ZONT setpoint`
- `ZONT coolant`
- `ZONT setpoint + coolant`
- `ZONT room + air setpoint`

Подтверждённый маппинг реального `z3k-state` устройства сохраняется:

- `roomTemperature` -> комнатная температура
- `burnerModulation` -> модуляция горелки
- `targetTemperature` -> уставка теплоносителя (`setpoint_temp`)
- `coolantTemperature` -> фактическая температура теплоносителя
- `roomSetpointTemperature` -> желаемая температура воздуха в помещении

## Build

Нужно:

- JDK 17
- Android SDK с `platforms;android-36` и `build-tools;36.0.0`

Локальная сборка:

```bash
./gradlew :mobile:assembleDebug :wear:assembleDebug
./gradlew :mobile:assembleRelease :wear:assembleRelease
```

APK после сборки:

- `mobile/build/outputs/apk/debug/mobile-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`
- `mobile/build/outputs/apk/release/mobile-release-unsigned.apk`
- `wear/build/outputs/apk/release/wear-release-unsigned.apk`

В GitHub Actions есть workflow `build-apks`, который собирает те же debug/release APK, публикует их как artifacts и при push тега создаёт GitHub Release с приложенными APK assets.

## Setup

1. Установите `mobile` на Android emulator или реальный телефон.
2. Установите `wear` на Wear OS emulator или реальные часы.
3. Запустите `wear` один раз, чтобы watch-side приложение создало локальное хранилище и provider'ы появились в системе.
4. На телефоне заполните `device_id`, `zone` и интервал обновления.
5. Получите токен через `Get token` по логину и паролю ZONT или вставьте `X-ZONT-Token` вручную.
6. После `Get token` приложение попытается загрузить `devices[]` и автоматически подставить `device_id`, если это возможно.
7. Нажмите `Refresh` и убедитесь, что snapshot появился на телефоне и дошёл до часов.

Что важно по секретам:

- пароль используется только для запроса `get_authtoken` и не сохраняется;
- токен и остальные настройки сохраняются только локально на телефоне;
- ручной ввод `X-ZONT-Token` остаётся полным fallback-сценарием.

## Manual Check

Базовая регрессия:

1. Проверить, что `Refresh` обновляет snapshot на телефоне без изменения подтверждённого маппинга метрик.
2. Проверить, что данные доходят до `wear` после успешного refresh.
3. Проверить исходные 7 providers на обычных текстовых слотах.
4. Отдельно проверить `ZONT overview + icons` на image-compatible слотах или в picker preview и убедиться, что он не деградирует в буквенную легенду.
5. Проверить placeholder/stale поведение, если refresh сломан или данные старые.

Реальный путь тестирования:

- Android emulator + Wear OS emulator для быстрой регрессии;
- Galaxy S23+ + Galaxy Watch Ultra для финальной ручной проверки sideload-сценария.

## Samsung Note

Для bounded Samsung-аудита `ZONT burner` теперь публикует не только `SHORT_TEXT`, но и `RANGED_VALUE`, что даёт безопасный эксперимент для gauge-like slots. Подробный следующий шаг вынесен в [docs/stage-05-plan.md](docs/stage-05-plan.md): там отдельно описаны исследование нужного `Galaxy Watch Ultra` slot и доотладка `ZONT overview + icons`, если конкретный watch face всё ещё сводит его к text-only fallback.
