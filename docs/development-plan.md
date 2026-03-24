# ZONT -> Wear OS Complications: общий план разработки

## Цель

Сделать Android-приложение, которое:

- получает текущие данные из API ZONT;
- умеет либо принимать готовый `X-ZONT-Token`, либо получать его по логину и паролю через `get_authtoken`;
- нормализует ответ в один компактный `snapshot`;
- передаёт этот `snapshot` в Wear OS;
- публикует набор `SHORT_TEXT`, `LONG_TEXT` и bounded `RANGED_VALUE` complications;
- даёт простой status/debug экран на телефоне и на часах.

Основные показатели проекта:

1. температура в помещении;
2. модуляция горелки;
3. температура уставки;
4. температура теплоносителя.

Дополнительно проект уже поддерживает пятую связанную метрику:

5. желаемая температура воздуха в помещении (`roomSetpointTemperature`) для paired complication.

## Фактический статус на 2026-03-25

По состоянию на `2026-03-25` базовая версия проекта уже реализована и доведена до релиза `0.3`.

Что фактически есть в репозитории:

- модули `mobile`, `wear`, `shared`;
- рабочая цепочка `phone -> watch`;
- login/password flow для получения токена + ручной fallback через `X-ZONT-Token`;
- автоподстановка `device_id` после успешного запроса токена и `devices[]`;
- автообновление через `WorkManager` с разделением transient/permanent ошибок;
- 8 watch-side providers:
  - `ZONT overview`;
  - `ZONT color overview`;
  - `ZONT room`;
  - `ZONT burner`;
  - `ZONT setpoint`;
  - `ZONT coolant`;
  - `ZONT setpoint + coolant`;
  - `ZONT room + air setpoint`;
- watch-side статусный экран и отображение установленного `version/build` на телефоне и на часах;
- авто-инкремент `versionCode` между сборками с общим build number для `mobile` и `wear`;
- GitHub Actions workflow для сборки debug/release APK;
- локальный и CI-ready release signing path с явным unsigned fallback без секретов;
- обновлённые launcher icons `mobile` и `wear` из `assets/icons/*.png`;
- подтверждённый отрицательный результат Samsung-эксперимента: special/private slots `Ultra Analog` зарезервированы Samsung/system providers и не считаются обычным пользовательским сценарием для наших providers.

Для будущих Codex-этапов единый сводный контекст теперь собран в `docs/codex-stage-context.md`, чтобы не читать все stage-планы подряд.

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
- эксперимент со special Samsung slots завершился отрицательным выводом: специальные части `Ultra Analog` и похожих stock face зарезервированы Samsung/system providers и не открываются для сторонних complication data sources.

### Этап 6. Техническое усиление и release readiness — завершён

Что было сделано:

- автообновление разделяет transient/permanent ошибки и не создаёт лишние reschedule-цепочки;
- телефон и часы показывают установленный `version/build`;
- release signing поддерживается локально через `keystore.properties` / env vars;
- GitHub Actions умеет восстанавливать keystore из secrets и честно различает signed/unsigned release outputs;
- добавлены targeted unit-тесты для `shared` и refresh execution policy.

### Этап 7. Bounded Visual And Release Polish — завершён

Что было сделано:

- обновлены launcher icons приложений `mobile` и `wear` из PNG-исходников в `assets/icons/*.png`;
- release signing path доведён до practically achievable состояния локально и в GitHub Actions;
- `versionCode` теперь автоматически меняется между сборками и остаётся общим для `mobile` и `wear` в одном Gradle-запуске;
- проведён bounded review complication render path и подтверждено, что более глубокий complication follow-up нужно выносить в отдельный следующий этап;
- агрессивные visual-эксперименты, которые делали live-render хуже baseline, не были закреплены как "готовый результат".

### Этап 8. Complication Follow-Up And Provider Cleanup — завершён

Что было сделано:

- проведён review текущего complication render path с bad-скриншотами как антипримером;
- `ZONT overview` переведён на спокойный text-first render path без noisy leading icon и без жёстко прошитого ручного переноса строки: provider отдаёт одну строку, а renderer watch face сам решает, как её перенести;
- `ZONT setpoint + coolant` и `ZONT room + air setpoint` приведены к одному практическому paired-правилу: monochromatic pictogram сохраняется там, где renderer его показывает, а secondary value остаётся явно отмеченным стрелочкой `→`, поэтому пара остаётся понятной даже на face, который скрывает leading icon;
- `ZONT overview + icons` удалён из manifest/code/docs как provider без понятного практического сценария;
- platform limitation зафиксирована честно: текущая Wear OS API-ветка не поддерживает inline drawable-пиктограммы внутри `ComplicationText`, поэтому bounded fallback остался text-first и подтверждался не только preview, но и live-render проверкой на эмуляторе и Galaxy;
- manual check и docs обновлены согласованно, включая note о том, что slot с удалённым provider после обновления нужно переназначить через picker.
- отдельным follow-up после stage 8 добавлен `ZONT color overview` как emoji-first provider для color-capable renderers;
- inline `Material Symbols` вместо Unicode по-прежнему не считаются надёжным вариантом для `ComplicationText`.

### Этап 9. Phone-side usability и onboarding — следующий опциональный этап

После complication-focused stage 8 разумный следующий фокус уже снова phone-side UX.

Что может входить:

- выбор устройства из загруженного `devices[]` вместо ручного ввода `device_id`;
- более понятный first-run flow после `Get token`;
- явное подтверждение, какое устройство и какие источники метрик сейчас выбраны;
- более дружелюбная phone-side диагностика для `phone -> watch` sync и проблем с настройкой;
- открытие существующего watch-side приложения по нажатию на complication как ожидаемое Wear OS поведение;
- дополнительная полировка статуса/онбординга без изменения базовой архитектуры.

Этот этап не обязателен для готовности текущей версии, но выглядит логичным продолжением после завершения stage 8.

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
  - отдельный emoji-first `ZONT color overview`;
  - bounded `RANGED_VALUE` для `ZONT burner`;
- обычные большие слоты watch face, которые открывают стандартный picker и принимают `LONG_TEXT`, считаются рабочим сценарием;
- private Samsung slots уровня `Ultra Analog` считаются внешним ограничением конкретного stock face, а не дефектом проекта;
- `wear`-приложение существует и достаточно для работы providers и синхронизации;
- телефон и часы показывают установленный `version/build`, а номер сборки автоматически меняется между новыми `assemble`;
- release signing path описан локально и в GitHub Actions, а unsigned fallback помечен честно;
- в репозитории нет секретов;
- есть понятная инструкция по ручной проверке на эмуляторе, `Galaxy S23+` и `Galaxy Watch Ultra`.

Этап 7 больше не блокирует базовую готовность проекта и рассматривается как bounded visual polish и небольшое усиление safety net.

## Референсы

- ZONT API docs: https://lk.zont-online.ru/api/docs/
- референс по архитектуре Wear complications: https://github.com/pachi81/GlucoDataHandler
