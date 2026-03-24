# ZONT -> Wear OS Complications: общий план разработки

## Цель

Сделать Android-приложение, которое:

- получает текущие данные из API ZONT;
- умеет либо принимать готовый `X-ZONT-Token`, либо получать его по логину и паролю через `get_authtoken`;
- нормализует ответ в один компактный `snapshot`;
- передаёт этот `snapshot` в Wear OS;
- публикует набор `SHORT_TEXT`, `LONG_TEXT`, image-compatible и bounded `RANGED_VALUE` complications;
- даёт простой status/debug экран на телефоне и на часах.

Основные показатели проекта:

1. температура в помещении;
2. модуляция горелки;
3. температура уставки;
4. температура теплоносителя.

Дополнительно проект уже поддерживает пятую связанную метрику:

5. желаемая температура воздуха в помещении (`roomSetpointTemperature`) для paired complication.

## Фактический статус на 2026-03-24

По состоянию на `2026-03-24` базовая версия проекта уже реализована и доведена до релиза `0.2`.

Что фактически есть в репозитории:

- модули `mobile`, `wear`, `shared`;
- рабочая цепочка `phone -> watch`;
- login/password flow для получения токена + ручной fallback через `X-ZONT-Token`;
- автоподстановка `device_id` после успешного запроса токена и `devices[]`;
- автообновление через `WorkManager` с разделением transient/permanent ошибок;
- 8 watch-side providers:
  - `ZONT overview`;
  - `ZONT overview + icons`;
  - `ZONT room`;
  - `ZONT burner`;
  - `ZONT setpoint`;
  - `ZONT coolant`;
  - `ZONT setpoint + coolant`;
  - `ZONT room + air setpoint`;
- watch-side статусный экран и отображение установленного `version/build` на телефоне и на часах;
- GitHub Actions workflow для сборки debug/release APK;
- локальный и CI-ready release signing path с явным unsigned fallback без секретов;
- зафиксированное ограничение Samsung `Ultra Analog`: special/private slots не считаются обычным пользовательским сценарием для наших providers.

## Что берём из API

Для MVP достаточно опираться на `POST https://my.zont.online/api/devices` с параметром `{"load_io": true}`.

Текущее приложение уже поддерживает две практические ветки данных:

- подтверждённый `z3k-state`-сценарий для реального устройства;
- fallback через `io.last-boiler-state` / `thermometers[]` для более простого формата.

Подтверждённый маппинг для реального `z3k-state` устройства:

- `roomTemperature` -> `z3k-state[heating_circuit.air_temp_sensor].curr_temp`;
- `roomSetpointTemperature` -> `z3k-state[heating_circuit].target_temp`;
- `burnerModulation` -> `z3k-state[boiler_adapter].ot.rml`;
- `targetTemperature` -> `z3k-state[heating_circuit].setpoint_temp`;
- `coolantTemperature` -> `z3k-state[boiler_adapter].ot.bt`.

Fallback для устройств без `z3k-state` по-прежнему опирается на:

- `io.last-boiler-state.target_temp` -> температура уставки;
- `io.last-boiler-state.ot.rml` -> модуляция горелки OpenTherm;
- `io.last-boiler-state.ot.bt` -> фактическая температура теплоносителя;
- `thermometers[].last_value` -> текущие температурные датчики.

Для legacy/fallback-ветки сохраняются такие правила:

- температура в помещении: первый активный датчик `thermometers[]`, у которого `functions[].f == "control"` и `zone == 1`;
- температура теплоносителя: сначала `io.last-boiler-state.ot.bt`, если её нет, то датчик `thermometers[]` с `functions[].f == "heat"` и `zone == 1`.

Изначальное предположение по `target_temp` как "единственной уставке" уже уточнено: для подтверждённого `z3k-state` устройства проект отдельно различает уставку теплоносителя и целевую температуру воздуха.

## Архитектура без лишних усложнений

Рекомендуемая структура:

- `mobile` -> настройки, запросы к ZONT, хранение последнего снимка данных, синхронизация с часами;
- `wear` -> complication data sources и минимальный экран статуса;
- `shared` -> общие модели данных и константы для связи phone <-> watch.

Технологический минимум:

- Kotlin;
- Gradle Android project;
- Jetpack Compose для простого UI на телефоне и, при необходимости, на часах;
- OkHttp/Retrofit + kotlinx serialization или другой простой HTTP-стек;
- WorkManager для периодического автообновления на телефоне;
- Wear Data Layer для передачи снимка на часы;
- Wear OS Complications API (`SuspendingComplicationDataSourceService` или эквивалентный актуальный API).

## Политика автообновления и заглушек

Текущее рабочее поведение такое:

- автообновление настраивается пользователем в приложении;
- значение по умолчанию: 1 обновление раз в 5 минут;
- для ручной проверки допускается интервал `1` минута;
- автообновление работает через цепочку `OneTimeWorkRequest` в `WorkManager`, а не через бесконечный периодический job;
- transient background-ошибки уходят в retry/backoff `WorkManager`;
- постоянные ошибки уровня токена / `device_id` ставят auto-refresh на паузу до исправления настроек или успешного ручного refresh;
- если конкретный показатель отсутствует 2 обновления подряд, в UI и в complication показывается заглушка;
- заглушка должна быть короткой и читаемой, например `--`;
- признак stale показывается после 2 интервалов автообновления без свежего `snapshot`;
- старые значения скрываются целиком после 10 интервалов, чтобы complication не держал бесконечно устаревшее значение.

## Этапы и фактический статус

### Этап 1. MVP-вертикаль — завершён

Что было сделано:

- создан проект `mobile + wear + shared`;
- реализован settings/status screen на телефоне;
- сохранён токеновый режим, `device_id`, `zone` и интервал автообновления;
- реализованы `snapshot`, sync на часы и базовые providers.

### Этап 2. Нативный watch UX — завершён

Что было сделано:

- убраны тяжёлые технические подписи из complications;
- добавлены более нативные иконки и paired layouts;
- исправлен баг с интервалом автообновления меньше `5` минут;
- закреплены правила stale/missing presentation.

### Этап 3. Release `0.1` и удобный auth flow — завершён

Что было сделано:

- добавлен flow `login/password -> get_authtoken` с ручным token fallback;
- после `Get token` приложение запрашивает `devices[]` и по возможности подставляет `device_id`;
- добавлены GitHub Actions, README и release `0.1`;
- появились дополнительные providers сверх MVP-базы.

### Этап 4. Visual polish complications — завершён

Что было сделано:

- обновлены иконки `burner` и `coolant`;
- исправлена визуальная иерархия "текущее значение vs уставка" в paired complications;
- `ZONT overview` приведён к стабильному двухстрочному виду;
- визуальная проверка закреплена как обязательная часть ручной проверки.

### Этап 5. Galaxy Ultra и special slots — завершён как bounded audit

Что было сделано:

- обычные большие `LONG_TEXT`-слоты отделены от private Samsung slots;
- `ZONT burner` получил безопасный bounded-эксперимент с `RANGED_VALUE`;
- `ZONT overview + icons` переведён в image-compatible типы, чтобы не деградировать в text-only fallback;
- ограничение `Ultra Analog` и похожих private slots зафиксировано в `docs/galaxy-specific.md`.

### Этап 6. Техническое усиление и release readiness — завершён

Что было сделано:

- автообновление разделяет transient/permanent ошибки и не создаёт лишние reschedule-цепочки;
- телефон и часы показывают установленный `version/build`;
- release signing поддерживается локально через `keystore.properties` / env vars;
- GitHub Actions умеет восстанавливать keystore из secrets и честно различает signed/unsigned release outputs;
- добавлены targeted unit-тесты для `shared` и refresh execution policy.

### Этап 7. Финальный visual follow-up — следующий этап

Что осталось сделать:

- нормализовать `ZONT overview`, чтобы он не выглядел как сжатая строка с агрессивным `...` в части больших слотов;
- обновить launcher icons приложений `mobile` и `wear` из PNG-исходников в `assets/icons/*.png`;
- убрать лишнюю стартовую пиктограмму у `LONG_TEXT` providers, если она действительно создаёт шум;
- перепроверить, можно ли честно использовать пиктограмму перед каждым значением в `LONG_TEXT`; если платформа этого не даёт, оставить `LONG_TEXT` чисто текстовым, а `ZONT overview + icons` считать image-first вариантом;
- если конкретный большой слот не вмещает полный `overview`, выбрать честный и предсказуемый fallback вместо случайного обрезания строки;
- проверить typography `ZONT burner` на нескольких face/slot combinations и исправлять только подтверждённую layout-независимую проблему;
- добавить лёгкую regression-страховку для wear-side `LONG_TEXT` / preview data / supported types, чтобы visual polish не сломал providers;
- синхронно обновить README, manual check и, если понадобится, screenshots после финальной visual-правки.

### Этап 8. Phone-side usability и onboarding — опциональный следующий этап

Если после этапа 7 захочется двигаться дальше, разумный следующий фокус уже не visual polish complications, а удобство настройки приложения.

Что может входить:

- выбор устройства из загруженного `devices[]` вместо ручного ввода `device_id`;
- более понятный first-run flow после `Get token`;
- явное подтверждение, какое устройство и какие источники метрик сейчас выбраны;
- более дружелюбная phone-side диагностика для `phone -> watch` sync и проблем с настройкой;
- дополнительная полировка статуса/онбординга без изменения базовой архитектуры.

Этот этап не обязателен для готовности текущей версии, но выглядит самым логичным продолжением после завершения stage 7.

## Что не нужно в первом приближении

- публикация в Play Market;
- серверная часть;
- управление котлом из приложения;
- история, графики и аналитика;
- полноценный web-auth flow с captcha/browser-обвязкой сверх уже реализованного `get_authtoken`;
- сложная локальная БД;
- полноценный пользовательский интерфейс на часах сверх того, что нужно для работы complications и отладки.

## Безопасность и репозиторий

Так как репозиторий будет публичным:

- не хранить реальные токены, логины и пароли в коде, `gradle.properties`, ресурсах или `BuildConfig`;
- любые локальные dev-файлы с секретами класть только в gitignored-пути;
- значения для API хранить на устройстве пользователя локально;
- в README дать пример с заглушками, а не с реальными значениями.

Практичный вариант для MVP:

- секреты вводятся через экран настроек приложения;
- сохраняются только локально на устройстве;
- в репозитории остаются только шаблоны и документация.

## Уточнение по Wear OS части

`wear`-приложение нужно и используется как часть решения.

Для MVP достаточно такого состава:

- watch-side приложение/модуль;
- watch-side компонент с data source providers;
- синхронизация данных с телефона на часы;
- без полноценного пользовательского watch UI, если он не нужен для отладки.

## Критерий готовности базовой версии

Функционально базовую версию проекта уже можно считать готовой.

Для текущего состояния это означает:

- телефон получает актуальные данные ZONT для нужного устройства;
- пользователь может либо запросить токен по логину/паролю, либо ввести `X-ZONT-Token` вручную;
- после успешного `Get token` приложение умеет загрузить `devices[]` и по возможности подставить `device_id`;
- пользователь может настроить частоту автообновления, по умолчанию `5` минут, с допустимым `1`-минутным интервалом для ручной проверки;
- при 2 подряд пропусках конкретного показателя в UI и complications показывается `--`;
- stale и expiration поведение явно определены и уже реализованы;
- на часах доступны 8 complication providers, включая:
  - обычные `SHORT_TEXT` providers;
  - `LONG_TEXT` combined/paired providers;
  - image-compatible `ZONT overview + icons`;
  - bounded `RANGED_VALUE` для `ZONT burner`;
- обычные большие слоты watch face, которые открывают стандартный picker и принимают `LONG_TEXT`, считаются рабочим сценарием;
- private Samsung slots уровня `Ultra Analog` считаются внешним ограничением конкретного stock face, а не дефектом проекта;
- `wear`-приложение существует и достаточно для работы providers и синхронизации;
- телефон и часы показывают установленный `version/build`;
- release signing path описан локально и в GitHub Actions, а unsigned fallback помечен честно;
- в репозитории нет секретов;
- есть понятная инструкция по ручной проверке на эмуляторе, `Galaxy S23+` и `Galaxy Watch Ultra`.

Этап 7 больше не блокирует базовую готовность проекта и рассматривается как bounded visual polish и небольшое усиление safety net.

## Референсы

- ZONT API docs: https://lk.zont-online.ru/api/docs/
- референс по архитектуре Wear complications: https://github.com/pachi81/GlucoDataHandler
