# Codex Context: Stages 1-8 And Current Baseline

## Purpose

Этот документ нужен как единая точка входа для следующих stage-prompts.

Если следующий этап просит "прочитать контекст", это означает:

- сначала прочитать этот файл;
- затем прочитать план текущего этапа;
- затем добрать только README и релевантный код/скриншоты для конкретной задачи.

Этот файл заменяет чтение всех предыдущих `docs/stage-0X-plan.md` подряд, если в prompt не сказано иное.

## Current Baseline On 2026-03-25

К текущему моменту в репозитории уже подтверждено:

- модули `mobile`, `wear`, `shared` работают как одна связка;
- рабочая вертикаль `phone -> watch -> complications` уже реализована и неоднократно проверялась;
- телефон умеет получить `X-ZONT-Token` по логину/паролю через `get_authtoken` и поддерживает ручной token fallback;
- после `Get token` приложение умеет запросить `devices[]` и по возможности подставить `device_id`;
- `wear`-приложение обязательно и остаётся частью решения;
- телефон и часы показывают установленный `version/build`;
- launcher icons `mobile` и `wear` уже обновлены из PNG-исходников в `assets/icons/*.png`;
- локальный release signing path уже работает через `keystore.properties` или env vars;
- GitHub Actions уже умеет собирать debug/release APK, восстанавливать keystore из secrets и честно различать signed/unsigned release outputs;
- `versionCode` уже инкрементируется автоматически между сборками:
  - по умолчанию из текущего Unix time в секундах;
  - через `ZONT_BUILD_NUMBER`, если нужен явный override;
  - `mobile` и `wear` используют один и тот же build number в одном Gradle-запуске.

## Providers And Metric Semantics

Текущий рабочий набор providers:

- `ZONT overview`
- `ZONT color overview`
- `ZONT room`
- `ZONT burner`
- `ZONT setpoint`
- `ZONT coolant`
- `ZONT setpoint + coolant`
- `ZONT room + air setpoint`

Что нельзя менять без явного намерения и проверки:

- `roomTemperature` — комнатная температура;
- `burnerModulation` — модуляция горелки;
- `targetTemperature` для подтверждённого `z3k-state` устройства — уставка теплоносителя (`setpoint_temp`), а не температура воздуха;
- `coolantTemperature` — фактическая температура теплоносителя;
- `roomSetpointTemperature` — отдельная желаемая температура воздуха в помещении.

Текущая placeholder/stale логика тоже считается подтверждённой:

- отсутствующее значение после подряд идущих пропусков не маскируется "старыми" числами;
- placeholder остаётся коротким и читаемым;
- stale и expiration поведение уже зафиксированы и не должны случайно ломаться cosmetic-правками.

## Wear-Side Reality We Already Learned

Несколько важных уроков из этапов 4-7 уже нельзя забывать:

- большие complications в Wear OS сильно зависят от конкретного watch face и конкретного slot;
- `LONG_TEXT` preview и реальный on-watch render могут выглядеть по-разному;
- наивные visual-правки в `LONG_TEXT` path уже приводили к шумным leading glyphs, неудачным layout-сдвигам и спорной типографике;
- отличие `burner` от температур нельзя автоматически считать "другим font size": часть различий создаётся длиной строки, типом slot и renderer watch face;
- inline pictograms внутри `ComplicationText` нельзя обещать без проверки по фактической API-ветке проекта;
- текущая AndroidX / Wear OS API-ветка проекта не даёт встроить наши drawable-пиктограммы как честные inline icons внутри `ComplicationText`;
- если desired visual behavior не поддерживается платформой, это нужно писать прямо, а не маскировать красивыми словами.

Практический вывод:

- review-first обязателен;
- visual polish допустим только как bounded change;
- после каждого заметного изменения нужен реальный check на устройстве или эмуляторе, а не только красивый preview.

## What Each Completed Stage Established

### Stage 1

- создан проект `mobile + wear + shared`;
- собрана минимальная вертикаль `settings -> refresh -> snapshot -> watch complications`.

### Stage 2

- complications стали ближе к нативному watch UX;
- закреплены базовые правила placeholder/stale и более читаемая watch-side подача.

### Stage 3

- добавлен `login/password -> get_authtoken` flow;
- появился ручной token fallback;
- после `Get token` приложение стало запрашивать `devices[]` и помогать с `device_id`.

### Stage 4

- прошёл visual polish первых complication layouts;
- зафиксирован paired layout для связанных температурных метрик;
- `ZONT overview` был приведён к рабочему двухстрочному baseline-виду.

### Stage 5

- отдельно был проведён bounded audit Samsung/private slot ограничений;
- итог этого эксперимента отрицательный: специальные части stock `Galaxy Watch Ultra` / `Ultra Analog` зарезервированы Samsung/system providers и не открываются как обычные сторонние complication slots;
- `ZONT burner` получил bounded `RANGED_VALUE`-эксперимент для gauge-like slots;
- обычные слоты со стандартным picker остаются единственным нормальным сценарием для наших providers на этих часах.

### Stage 6

- усилены background/retry сценарии и release-readiness;
- release signing оформлен локально и в CI;
- проект начал явно различать signed и unsigned release path;
- появились version/build в UI и более полезные targeted tests.

### Stage 7

- обновлены launcher icons;
- signed release path через GitHub Actions доведён до practically achievable состояния;
- build number теперь автоматически меняется между сборками;
- выполнен bounded review complication render path;
- стало ясно, что дальнейшая complication-polish работа должна идти отдельным этапом и с повышенной осторожностью, потому что агрессивные изменения `LONG_TEXT` легко ухудшают итоговый layout.

### Stage 8

- `ZONT overview` переведён на более спокойный text-first path без noisy leading icon и без жёстко заданного ручного переноса строки;
- paired providers оставлены в практичном виде с monochromatic pictogram там, где renderer его показывает, и явной стрелочкой `→` в тексте, чтобы смысл пары не терялся даже на face, который прячет leading icon;
- `ZONT overview + icons` удалён из manifest/code/docs как provider без внятного практического сценария;
- platform limitation зафиксирована честно: inline drawable-пиктограммы внутри `ComplicationText` текущей API-веткой не поддерживаются, поэтому итоговый fallback остался text-first;
- если пользователь раньше выбрал удалённый `ZONT overview + icons`, такой slot после обновления нужно переназначить через picker.

### Post-Stage 8 Follow-Up

- добавлен отдельный `ZONT color overview` как emoji-first вариант overview для color-capable renderers;
- обычный `ZONT overview` остаётся базовым text-first вариантом;
- inline `Material Symbols` внутри `ComplicationText` по-прежнему не считаются честно поддерживаемым решением, потому что provider не управляет font family renderer'а watch face.

## Invariants For Future Stages

Будущие этапы не должны ломать без явной причины:

- рабочую вертикаль `phone -> watch`;
- существующую Data Layer синхронизацию;
- подтверждённый маппинг метрик;
- placeholder/stale semantics;
- release signing path и честное signed/unsigned distinction;
- build-number auto increment;
- обязательность `wear`-модуля как части решения.

Также всегда действуют проектные ограничения:

- никаких токенов, паролей, keystore и иных secrets в git;
- не обещать platform behavior, который не подтверждён кодом или проверкой;
- не делать commit, tag, merge или push без прямой команды пользователя.

## Deployment And Working Conventions

- для быстрой регрессии базовый сценарий — `debug` на эмуляторы;
- `release` на физические устройства выкатывается только по явной команде пользователя;
- локальный путь к `adb` уже сохранён в `.codex` memory, поэтому его не нужно заново искать и не нужно хардкодить в публичные docs;
- если future stage меняет внешний вид complications, нужно отдельно проверять и preview, и live-render;
- если doc changes меняют фактическое состояние проекта, README, stage plan и development plan должны обновляться согласованно.
