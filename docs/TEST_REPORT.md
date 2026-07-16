# Build and validation report — 0.3.0-exp3

Дата сборки: 2026-07-16.

## Входная игра

```text
Starsector 0.98a-RC8
starfarer_obf.jar size: 6,029,184 bytes
SHA-256: 5dd222b9e266d2ac2d63b3dad4983eb05caaf5a247d7dfb82aaeba47ea774cc8
```

## Подтверждение map fix

Пользовательский прогон exp2 на большом секторе показал переход примерно с 10 FPS к ограничению 60 FPS. Предшествующая JFR-телеметрия атрибутировала 93–96% campaign-thread samples открытой map к `H.renderStuff -> AbstractCollection.retainAll`. Exp3 сохраняет тот же call-site patch и добавляет equals/hashCode fallback для modded entity classes, не меняя vanilla identity fast path.

## Статический анализ новых targets

### CampaignEngine

`CampaignEngine.advance()` содержит один call site `readdChangeListeners()` до основной симуляции. Сам метод проходит hyperspace и все star systems, вызывая `ObjectRepository.setListener(engine)`. `setListener` является простой записью поля. Штатные create/remove system paths уже управляют listener непосредственно.

Патч заменяет ровно один call site и не изменяет public method.

### Course widget O0Oo

В `getNextStep(SectorEntityToken)` найдены:

- 2 вызова `hyperspace.getJumpPoints()` с последующей фильтрацией на одну target system;
- 1 вызов `CampaignEngine.getStarSystems()` для anchor identity.

В `getLastLegDistance(SectorEntityToken)` найден ещё 1 такой jump-point scan.

Патч заменяет только источники candidates; downstream vanilla bytecode остаётся.

## Observed transformation counts

```text
H:             scratch=2, identity reconciliation=1, labels=1,
               Intel index=1, nebula cache=1, sample throttle=1, grid constants=5
A:             scratch=2, hover wrapper=1, getMapLocation callsites=1
Z:             getArrowData callsites=1, Vector2f constructors=2
EventsPanel:   getMapLocation callsites=7, List.contains=1, icons.values=1
CampaignEngine: listener-refresh callsite=1
O0Oo:          route jump-list callsites=3, route system-list callsite=1
```

Transformed sizes:

```text
H:              35,299 -> 35,489 bytes
A:              21,586 -> 21,413 bytes
Z:              12,290 -> 12,150 bytes
EventsPanel:    37,230 -> 36,942 bytes
CampaignEngine: 47,854 -> 47,358 bytes
O0Oo:           19,321 -> 19,274 bytes
```

## Bytecode validation

JDK-internal ASM `Analyzer<BasicValue>` + `BasicVerifier` успешно проверил:

```text
6 transformed classes
358 non-abstract/non-native methods
```

`javap` дополнительно подтвердил корректный stack order новых calls:

```text
routeJumpPointsForSystem(LocationAPI, Object)
routeSystemsForAnchor(Object, Object)
readdChangeListenersIfNeeded(Object)
```

Transformed `CampaignEngine` успешно загружен JVM с `-Xverify:all`. Некоторые raw obfuscated UI-классы игры, включая vanilla `O0Oo`, содержат yGuard identifiers, которые стандартный AppClassLoader отклоняет ещё без патча; для них применяется ASM data-flow verification и штатный Starsector loading path с сохранённым `-noverify`.

## Runtime helper smoke tests

Проверены:

- listener refresh на первом вызове;
- пропуск неизменённого следующего кадра;
- немедленный refresh при изменении system-list size;
- ordered route jump bucket для 40 synthetic jump points;
- full-list fallback при index miss;
- anchor -> singleton system identity lookup;
- full-list fallback неизвестного anchor;
- отсутствие stats thread при `logging.statsIntervalSeconds=0`.

Результат:

```text
OK listenerThrottle routeJumpIndex routeSystemIndex
```

## Совместимость с telemetry javaagent

Startup smoke test выполнен в обоих порядках:

```text
Telemetry 0.2.2 -> Optimizer 0.3.0-exp3
Optimizer 0.3.0-exp3 -> Telemetry 0.2.2
```

В обоих случаях optimizer установил transformer и преобразовал `CampaignEngine` с исходным expected hash.

## Предыдущие проверки, сохранённые в exp3

- 5,000 randomized equivalence rounds vanilla identity reconciliation;
- custom `equals/hashCode` reconciliation smoke test;
- unit tests Intel lookup/callback caches and vector pools;
- exact core/class SHA-256 guards;
- fail-open configuration paths.

## Не выполнено

Графический campaign-клиент exp3 в build-контейнере не запускался. Новые campaign/route patches требуют пользовательской интеграционной проверки на реальном mod list. Они спроектированы так, чтобы public API оставался оригинальным, а cache miss/error возвращал vanilla candidate lists.
