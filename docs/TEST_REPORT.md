# Build and validation report — 0.4.0-exp7

Дата exp7 build/structural/runtime regression: 2026-07-17. Structural harness прошёл на двух core
JAR, lifecycle GC и расширенный runtime regression — успешно. Два exp6 campaign-прогона измерили
allocation-эффект, обнаружили CPU-регрессию пустого `IdentityHashMap.clear()` и дали lifecycle
evidence между загрузками. После исправления exp7 графический performance-прогон ещё не выполнен.

## Проверенные входы

- чистый Starsector 0.98a-RC8: `C:\Games\Starsector\starsector-core\starfarer_obf.jar`;
- локализованная сборка 0.98a-RC8: `C:\Games\Starsector_test\starsector-core\starfarer_obf.jar`.

У соответствующих exp5 target methods обеих сборок атрибуты `Code` совпадают. Отличия изменённых
классов состоят в строковых константах перевода. Никакие JAR/class digests не используются
transformer-ом или installer-ом. Exp6 harness отдельно подтвердил `BaseLocation`,
`BaseCampaignEntity` и `Memory` на обоих фактически прочитанных JAR.

## Результат exp7 structural/GC/runtime harness

Команда:

```powershell
.\verify-structural.ps1 -CoreJars `
  'C:\Games\Starsector\starsector-core\starfarer_obf.jar', `
  'C:\Games\Starsector_test\starsector-core\starfarer_obf.jar'
```

Результат:

```text
OK structural jar=C:\Games\Starsector\starsector-core\starfarer_obf.jar classes=9 verifiedMethods=672
OK structural jar=C:\Games\Starsector_test\starsector-core\starfarer_obf.jar classes=9 verifiedMethods=672
OK negative-tests retainAll missing ambiguous unrelated-call unrelated-change marker-ownership scratch-scope-tamper wrapper-tamper wrapper-metadata idempotency lifecycle-missing-write lifecycle-wrong-source exp6-marker-ownership exp6-scratch-scope-tamper
SUMMARY jars=2 transformedClasses=18 verifiedMethods=1344
OK lifecycle-gc exact-caches=8 scratch-remove identity-idempotence two-phase-boundary engine-change reset-null generation-active-state generation-epoch lock-free-detach nebula-slot-detach out-of-order-fail-closed weak-cache-reachability weak-engine-arguments
OK exp6-runtime snapshot-isolation snapshot-reuse snapshot-reentrancy snapshot-fallback snapshot-no-retention empty-snapshot-singleton exceptional-snapshot-no-retention memory-iterator-empty-singleton memory-iterator-order memory-iterator-remove memory-iterator-no-retention empty-identity-clear-elision retain-empty-clear-elision identity-normal-cleanup identity-exceptional-cleanup identity-nested-cleanup
```

Для каждого класса проверено:

- точное число ожидаемых hooks;
- существование каждого emitted hook с точным descriptor и `public static` access в runtime-классе;
- `Analyzer<BasicValue>` + `BasicVerifier` для всех concrete methods;
- повторный parse после сериализации;
- неизменность public/protected field и method surface;
- полное покрытие normal/exceptional exits scratch scope и явный `F_FULL` handler frame;
- ownership marker, точная wrapper wiring и перенос method metadata;
- второй transform возвращает `null`, а каждый блок определяется как `ALREADY_APPLIED`;
- отсутствие новых `SKIPPED_STRUCTURAL` при idempotency pass.

## Observed transformation counts

| Target | Hooks/sites |
|---|---:|
| `H` | scratch 2, retainAll 1, labels 1, Intel index 1, nebula 1, sample throttle 1, grid 5 |
| `A` | scratch 2, map-location 1, hover wrapper 1 |
| `Z` | arrow callback 1, vectors 2 |
| `EventsPanel` | map-location 7, fast contains 1, existing-icon lookup 1 |
| `CampaignEngine` | lifecycle begin 2, completion 2, listener refresh 1 |
| `O0Oo` | jump candidates 3, system candidates 1 |
| `BaseLocation` | defensive snapshots 5, scratch scopes 2 |
| `BaseCampaignEntity` | script snapshot 1, scratch scope 1 |
| `Memory` | empty iterator fast paths 2 |

Размеры шести прежних clean target classes (exp5 baseline):

```text
H:              35,299 -> 36,376 bytes
A:              21,586 -> 21,868 bytes
Z:              12,290 -> 12,496 bytes
EventsPanel:    37,230 -> 37,401 bytes
CampaignEngine: 47,854 -> 47,839 bytes
O0Oo:           19,321 -> 19,400 bytes
```

Размеры шести прежних localized target classes (exp5 baseline):

```text
H:              35,299 -> 36,376 bytes
A:              21,869 -> 22,151 bytes
Z:              12,290 -> 12,496 bytes
EventsPanel:    37,372 -> 37,543 bytes
CampaignEngine: 47,992 -> 47,909 bytes
O0Oo:           19,530 -> 19,609 bytes
```

## Отрицательные проверки

На копиях `H` в памяти проверено:

- unrelated instruction change не блокирует совместимый `retainAll` patch;
- дополнительный несвязанный `Set.retainAll` не принимается за map reconciliation;
- отсутствующий semantic site даёт `SKIPPED_STRUCTURAL` только для `retainAll`;
- два одинаковых semantic sites считаются неоднозначными и не патчатся;
- остальные шесть блоков `H` продолжают применяться после локального skip;
- hook-shaped bytecode без ownership marker не принимается за уже установленный патч;
- owned scratch hooks с удалённым scope не получают ложный `ALREADY_APPLIED`;
- посторонняя инструкция в wrapper даёт локальный `SKIPPED_STRUCTURAL`;
- method annotation переносится на публичный wrapper и не остаётся на private original;
- lifecycle patch отвергается, если `resetInstance` не пишет singleton или `setInstance` пишет
  значение не из argument 0; независимый listener patch при этом продолжает устанавливаться;
- hook-shaped exp6 bytecode без exp6 ownership marker не принимается за `ALREADY_APPLIED`;
- `BaseLocation` snapshot hooks без полного scratch scope дают локальный `SKIPPED_STRUCTURAL`;
- каждый полученный класс проходит `BasicVerifier`.

## Подтверждённые compatibility assumptions

- Номера local slots в `O0Oo` и `EventsPanel` выводятся через `SourceValue`, а не фиксируются.
- Три nebula lists определяются по producer/add chains.
- Grid matcher выбирает четыре bounds и один step; radar `2000f` не затрагивается.
- Campaign systems/hyperspace fields выводятся из `readdChangeListeners` и передаются hook напрямую.
- Lifecycle matcher доказывает единственное static self-typed singleton field и data-flow
  `setInstance(argument 0)`/`resetInstance(null)`, begin placement перед `PUTSTATIC` и token-matched
  completion placement перед каждым normal `RETURN`.
- Route backing systems field во время выполнения определяется уникальным identity-сопоставлением
  с public `getStarSystems()` result, а не по имени поля.
- Route indexes сверяют полный identity/relationship snapshot раз в TTL; обычный hit остаётся
  `O(1)`, а safe profile этот TTL-патч не включает.
- `BaseLocation`/`BaseCampaignEntity` snapshot locals принимаются только при доказанном
  iterator-only использовании без merge, mutation или escape и полном scratch scope.
- `Memory.advance` hooks принимаются только для expire list и `LinkedHashMap.values()` require
  view в прежнем порядке после clock conversion.
- Wrapper originals становятся private synthetic; публичная сигнатура остаётся исходной.

## Exp6 telemetry result и exp7 CPU-regression fix

Источники:

- exp5 baseline: `StarsectorMapTelemetry\telemetry\session-20260717-023929-040`;
- exp6, long-play save: `session-20260717-032507-985`;
- exp6, проблемный hyperspace сразу после load: `session-20260717-032627-811`.

Baseline и проблемный exp6-прогон являются близким controlled comparison: одинаковый seed
`MN-4664645813837931932`, почти точное число entities (`432 809` против `432 827`), одинаковые
location/course target и 66 включённых модов тех же версий. После первых 10 секунд:

| Метрика | exp5 baseline | exp6 | Изменение |
|---|---:|---:|---:|
| allocation/frame | `4.347 MiB` | `2.184 MiB` | `-49.8%` |
| mean frame time | `11.09 ms` | `29.67 ms` | `+167.5%` |
| FPS | `90.17` | `33.7` | `-62.6%` |

JFR allocation samples подтвердили, что exp6 устранил целевые `BaseLocation` и
`BaseCampaignEntity.runScripts` snapshots, а `Memory` iterators сократил почти до нуля на пустых
путях. Функциональность трёх allocation-патчей поэтому сохраняется в exp7.

CPU-регрессия локализована независимо raw sampler и JFR. В проблемном прогоне
`IdentityHashMap.clear()` занимал `56.93%` raw и `64.48%` JFR execution samples; в long-play
прогоне — `39.08%` и `49.19%`. Точный stack:
`BaseCampaignEntity.runScripts -> MapOptimizerHooks.endScratchScope -> ScratchFrame.clear ->
IdentityHashMap.clear`. Карта создаётся с expected size `2048`, что в Java 17 даёт таблицу на
`8192` slots; `clear()` обходит её полностью даже при `size == 0`. Число JFR clear samples/frame
масштабировалось с общим числом campaign entities с коэффициентом `2.435`, практически совпадая
с ростом entities `2.433x`.

Exp7 заменяет три безусловных clear этой карты на `isEmpty()`-guard. Normal path после фактического
заполнения и exceptional path после частичного заполнения по-прежнему очищают все ссылки. Runtime
regression детерминированно проверяет отсутствие structural clear пустой карты, normal/exceptional
cleanup и разделение вложенных scratch frames без timing thresholds.

Оба новых exp6-прогона записаны в одной JVM. Перед второй записью lifecycle reset сработал, а между
сессиями Shenandoah выполнил пять cycles; used heap снизился примерно с `12 272` до `4 880 MiB`,
несмотря на загрузку значительно большего save. В логах нет `Copies of campaign data in memory`
или `Memory leak detected`. Это сильное косвенное подтверждение освобождения прежней campaign
graph, но telemetry не записывает прямое значение `CampaignEngine.getAllInstances()`.

Post-fix exp7 campaign telemetry ещё нужна, чтобы измерить итоговый frame time и подтвердить
исчезновение `IdentityHashMap.clear` из sampled hot path.

## Подтверждённые runtime evidence предыдущих версий

Предыдущая пользовательская телеметрия показала примерно 10 -> 60 FPS после map fix. В проблемной
локализованной сессии 93.33% steady global-map samples находились в vanilla
`H.renderStuff -> retainAll -> ArrayList.contains`; exp5 успешно устанавливает этот hook на
том же локализованном JAR.

17 июля для exp5 выполнен отдельный GC harness против собранного agent JAR. Он заполнил все восемь
static map-кэшей, `nebulaCacheSlot.value` и scratch одним sentinel, затем проверил engine replace и
`reset(null)`. После lifecycle reset sentinel и прежние engine arguments были помещены в
`ReferenceQueue`; все кэши оказались пусты, generation epoch менялся только при смене identity,
generation-active flag оставался выключен между begin/completion и после reset. Harness также
подтвердил volatile detachment всех восьми map-roots и nebula-slot, а также fail-closed очистку при
out-of-order completion token.
Harness намеренно отказывается работать с `-XX:+DisableExplicitGC`.

До этого для exp4 был выполнен полный графический campaign smoke test на большой modded-сборке.
Bootstrap записал в `starsector.log` статус `structural-transformer-installed`; ранняя строка
показывает один уже загруженный target и `skippedPatchesSoFar=0`. После открытия campaign UI/map/Intel
`logs/map-optimizer.log` содержит 17 `APPLIED` для всех 14 patch id и не содержит `WARN`, `ERROR`,
`SKIPPED_STRUCTURAL` или `SKIPPED_ERROR`.

Суммарные activity counters за записанные интервалы:

```text
retainCalls=13383, avoidedContainsUpperBound=473079916544, retainKeysScanned=27456733
labelCandidates=2798/1019091, hoverHits/Misses=2122/3929
mapLocationHits/Misses=506/102, arrowHits/Misses=8355/985
intelIndexHits/Builds=47/12, nebulaHits/Misses=6/1
campaignListenerRuns/Skips=203/17762
routeJumpIndexHits/Builds/Fallbacks=6636/1/0
```

Также сработал grid LOD: для сектора `492000 x 312000` spacing вырос с `2000` до `4000`. Route
system index и sample-clear skip в этой сессии не были задействованы. В `starsector.log` остаются
ошибки отсутствующих weapon/hull/resource specs сторонних модов, но stack trace или ошибки
Map Optimizer отсутствуют.

Отдельная frame-time/allocation телеметрия в exp4 smoke-запуске не записывалась, поэтому новых численных
утверждений о производительности он не добавляет. Матрица ещё не закрытых behavior/A-B проверок
ведётся в [`PATCH_VALIDATION_CHECKLIST.md`](PATCH_VALIDATION_CHECKLIST.md).
