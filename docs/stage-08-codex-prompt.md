# Промпт Для Codex На Этап 8

```text
Ты работаешь в уже существующем Android-проекте, где этапы 1-7 уже завершены, а базовая вертикаль `phone -> watch -> complications` считается рабочей и не должна быть сломана.

Перед началом реализации обязательно прочитай:
- `docs/codex-stage-context.md`
- `docs/stage-08-plan.md`
- `README.md`
- `docs/screenshots/bad/overview-1.png`
- `docs/screenshots/bad/overview-2.png`
- `wear`-код вокруг complication providers, formatter, preview data и manifest-supported types
- `shared/src/main/kotlin/com/botkin/zontdatahandler/shared/SnapshotFormatting.kt`

Важно: единый `docs/codex-stage-context.md` здесь специально заменяет чтение всех предыдущих stage-планов подряд. Не трать время на последовательное чтение `stage-01 .. stage-07`, если только этого отдельно не требует задача.

Сначала согласуй реализацию с этим контекстом, затем переходи к коду.

Контекст:
- репозиторий публичный, поэтому никаких secrets в git;
- `wear`-приложение остаётся обязательной частью решения;
- build number уже инкрементируется автоматически между сборками, это поведение нельзя регрессировать;
- signed/unsigned release path уже оформлен и тоже не должен ломаться;
- прошлый visual follow-up показал, что наивные правки `LONG_TEXT` могут ухудшить complication layout, typography и live-render по сравнению с preview.

Что уже должно остаться рабочим:
- синхронизация `phone -> watch`;
- placeholder/stale логика;
- подтверждённый маппинг метрик;
- текущие полезные providers:
  - `ZONT overview`
  - `ZONT room`
  - `ZONT burner`
  - `ZONT setpoint`
  - `ZONT coolant`
  - `ZONT setpoint + coolant`
  - `ZONT room + air setpoint`
- текущая release/debug инфраструктура.

Отдельно имей в виду:
- `ZONT overview + icons` сейчас ещё есть в кодовой базе, но этот этап рассматривает его удаление;
- если manifest-level удаление provider может повлиять на уже выбранные пользователем слоты, это нужно явно проверить и описать, а не скрыть.

Главный фокус этапа 8:
1. Починить/улучшить текущее complex/overview представление, которое сейчас выглядит плохо на bad-скриншотах.
2. Убрать квадратную/шумную стартовую пиктограмму в начале, если именно она создаёт визуальный мусор.
3. Вместо разделителя-точки попробовать использовать разделители в виде наших pictogram-иконок для соседних значений.
4. Использовать bad-скриншоты как антипример, а sunrise/sunset-подачу на одном из них — как ориентир по идее "иконка как разделитель/легенда", а не как ведущий квадрат слева.
5. Убрать `ZONT overview + icons`, если у него нет внятного практического применения и он только усложняет provider set.
6. Для paired complications перестать зависеть от leading icon: заменить стрелочку у secondary value на соответствующую пиктограмму и убрать стартовую pictogram из самого paired presentation, если именно она даёт разнобой между watch faces.
7. Все paired-правки применять одинаково к `ZONT setpoint + coolant` и `ZONT room + air setpoint`.

Что нужно сделать:
1. Сначала проверь, какие именно providers и какие API-ветки сейчас формируют:
   - `ZONT overview`
   - paired `LONG_TEXT` complications
   - `ZONT overview + icons`
2. Отдельно зафиксируй текущее наблюдение по paired providers:
   - на эмуляторе в paired complication может одновременно быть leading icon и secondary value со стрелкой;
   - на Galaxy leading icon может отсутствовать;
   - итоговое решение не должно зависеть от такого расхождения renderer/watch face.
3. Убедись, что желаемый visual result вообще поддерживается актуальным Wear OS complications API проекта.
4. Не повторяй прошлую ошибку "сначала поменяли, потом увидели регрессию":
   - review-first;
   - затем минимальная реализация;
   - затем device/emulator verification.
5. Если inline pictogram внутри `LONG_TEXT` реально поддерживается и не ломает layout, реализуй её минимально и аккуратно.
6. Если платформа не даёт сделать это честно:
   - не маскируй ограничение;
   - выбери ближайший bounded visual вариант;
   - прямо объясни, что именно упирается в API/watch-face renderer.
7. Для `ZONT setpoint + coolant` и `ZONT room + air setpoint` предпочитай такой paired result, где:
   - leading icon больше не является обязательной частью presentation;
   - secondary value получает свою pictogram-метку вместо стрелочки;
   - оба paired providers оформлены по одному и тому же правилу.
8. При замене разделителей или secondary marker не сломай `room`, `coolant`, `burner`, placeholder/stale и подтверждённый маппинг.
9. При удалении `ZONT overview + icons` обнови manifest/provider declarations, preview/manual check и docs согласованно.
10. Если изменение затрагивает README, stage docs, development plan или screenshots, обнови их без противоречий.
11. После значимых правок собирай и проверяй:
   - тесты;
   - `mobile`;
   - `wear`;
   - complication behavior настолько, насколько это реально проверить локально.
12. В paired-части проверки отдельно сравни хотя бы:
   - эмуляторный render;
   - Galaxy-наблюдение или эквивалентный live-render результат;
   чтобы не зафиксировать решение, которое красиво только на одном renderer.
13. По завершении этапа разверни только `debug` на эмуляторы.
14. `release` на физические устройства на этом этапе не накатывай, если пользователь отдельно не попросил.
15. Если нужен локальный `adb`, используй уже сохранённый путь/alias из `.codex` memory, а не ищи SDK заново.
16. Не делай commit, tag, merge или push без явной команды пользователя.

Ограничения:
- не добавляй новые продуктовые фичи вне complications;
- не переписывай архитектуру complications;
- не меняй смысл существующих метрик;
- не обещай platform behavior, который не подтверждён кодом или проверкой;
- не превращай bounded complication-polish в большой redesign.

Что проверить перед завершением:
- проект собирается;
- тесты проходят;
- старая рабочая вертикаль не сломана;
- новый visual result либо реализован, либо platform limitation описан честно;
- `ZONT overview + icons` либо удалён осознанно, либо сохранён только при очень чётком и проверенном обосновании;
- paired-подача у `ZONT setpoint + coolant` и `ZONT room + air setpoint` стала аккуратнее и не менее читаемой;
- итог не зависит от случайного наличия или отсутствия leading icon на конкретном watch face;
- debug раскатан на эмуляторы;
- commit/push не делался без команды пользователя.
```
