# Starsector Map Optimizer 0.3.0-exp3

Точечные runtime-патчи для **Starsector 0.98a-RC8**, рассчитанные на очень большие сектора. Версия exp3 сохраняет исправления sector/hyperspace/Intel map и добавляет два консервативных направления: обычную campaign-сцену при закрытой карте и вычисление следующего шага маршрута.

Основное исправление карты уже проверено на реальном сохранении: сцена, работавшая примерно в **10 FPS**, после патча упёрлась в установленный предел **60 FPS**.

Мод состоит из:

- sandbox-safe mod-plugin, который сообщает состояние в `starsector.log`;
- startup `javaagent`, который до загрузки engine-классов проверяет SHA-256 игры и меняет только известные call sites.

Обычный script classloader Starsector запрещает reflection и файловый доступ, поэтому javaagent обязателен.

## Поддерживаемая сборка

```text
Starsector 0.98a-RC8
starfarer_obf.jar SHA-256:
5dd222b9e266d2ac2d63b3dad4983eb05caaf5a247d7dfb82aaeba47ea774cc8
```

На другой сборке агент по умолчанию ничего не меняет. Помимо core JAR проверяется SHA-256 каждого из шести target-классов.

## Обновление с exp1/exp2

1. Полностью закройте игру.
2. Замените каталог `<Starsector>\mods\StarsectorMapOptimizer` новым.
3. Строку в `vmparams` менять не требуется: имя и путь agent JAR прежние.
4. Включите мод в launcher и запустите игру.

Для новой установки запустите `install-agent.bat` из каталога мода. Он создаёт резервную копию `vmparams` и добавляет:

```text
-javaagent:../mods/StarsectorMapOptimizer/agent/StarsectorMapOptimizerAgent.jar
```

Telemetry javaagent можно оставить: JVM поддерживает несколько `-javaagent`.

## Что исправляется

### Sector/hyperspace/Intel map

Главный per-frame дефект:

```java
icons.keySet().retainAll(entityArrayList);
```

заменяется явной identity-reconciliation:

1. vanilla entities отмечаются в переиспользуемом `IdentityHashMap`;
2. `icons.keySet()` обходится один раз;
3. отсутствующие ключи удаляются iterator-ом.

Если modded entity переопределяет `equals/hashCode`, helper автоматически переключает этот кадр на reusable `HashSet`, сохраняя обычную Java collection-семантику. Для vanilla fast path сложность меняется с `O(K × E)` на `O(K + E)` без `AbstractCollection.retainAll` и `ArrayList.contains/indexOfRange`.

Также остаются best-effort патчи hover, label layout, Intel callbacks/indexes, synthetic nebula preparation, временных allocations и LOD сетки большого сектора.

### Campaign при закрытой карте

`CampaignEngine.advance()` в vanilla каждый кадр вызывает `readdChangeListeners()`. Этот метод проходит hyperspace и все star systems, хотя внутри лишь повторно записывает тот же listener в `ObjectRepository`.

Exp3 меняет **только внутренний call site в `advance()`**:

- первый вызов выполняется полностью;
- изменение списка систем/hyperspace вызывает немедленный полный refresh;
- раз в `campaign.listenerAuditMs` выполняется совместимый контрольный refresh;
- публичный `CampaignEngine.readdChangeListeners()` не меняется: моды, вызывающие его напрямую, получают точное vanilla-поведение.

Vanilla `createStarSystem()` и `removeStarSystem()` уже устанавливают/снимают listener непосредственно, поэтому штатные изменения сектора не ждут audit.

### Route/pathfinding

Виджет курса `coreui.A.O0Oo` при активном маршруте может каждый кадр:

- дважды пройти все hyperspace jump points, чтобы найти входы в одну систему;
- пройти все star systems, чтобы сопоставить hyperspace anchor;
- повторить scan jump points в `getLastLegDistance()`.

Exp3 строит короткоживущие identity-indexes:

- `destination system -> ordered jump-point candidates`;
- `hyperspace anchor -> star system`.

Исходные vanilla-проверки остаются в `O0Oo`: wormhole/star filtering, расстояние, допуск 300, hashCode tie-break и fallback anchor не переписаны. Helper только сужает исходный ordered candidate list. При miss, malformed/custom data или ошибке возвращается полный vanilla list.

Стандартный TTL индекса — 250 мс. Это ограничивает задержку при нестандартной прямой мутации destination модом, сохраняя большую часть выигрыша в секторе с тысячами систем.

## Совместимость

- Публичные Starsector API и сигнатуры методов не меняются.
- Формат сохранений не меняется; все кэши transient и живут только в JVM.
- Модовые callbacks по-прежнему выполняются на campaign thread.
- Route-result выбирается оригинальным vanilla-кодом; index не принимает решение за него.
- Public `readdChangeListeners()` остаётся оригинальным.
- Exact class hashes предотвращают применение к уже изменённому неизвестным agent-ом bytecode.
- Любой block отключается отдельно в `optimizer.properties`.

Не патчится проход, который распределяет advance неактивных систем по 60 кадрам: его удаление затрагивает `activeThisFrame` и может изменить ожидания модов. Не патчится `NavigationModule.advance()`: статический анализ показал локальные avoid-lists, а не sector-wide scan.

## Проверка запуска

Лог:

```text
mods\StarsectorMapOptimizer\logs\map-optimizer.log
```

После загрузки campaign и открытия map/Intel UI ожидаются до шести строк:

```text
Patched com/fs/starfarer/campaign/CampaignEngine: ...
Patched com/fs/starfarer/coreui/A/O0Oo: ...
Patched com/fs/starfarer/coreui/A/H: ...
Patched com/fs/starfarer/coreui/A/A: ...
Patched com/fs/starfarer/coreui/A/Z: ...
Patched com/fs/starfarer/campaign/comms/v2/EventsPanel: ...
```

UI-классы загружаются лениво, поэтому не все строки обязаны появиться до первого открытия соответствующего экрана.

Каждые 30 секунд лог дополнительно показывает:

- map reconciliation/hover/Intel/label/nebula counters;
- `campaignListenerRuns` и `campaignListenerSkips`;
- build/hit/fallback route jump/system indexes.

## Настройка

Корневой `optimizer.properties` — рекомендуемый best-effort профиль. Основные новые параметры:

```properties
patch.campaignListenerThrottle=true
campaign.listenerAuditMs=1000

patch.routeJumpPointIndex=true
route.indexTtlMs=250
```

`profiles/safe.properties` оставляет только близкие к точной семантике изменения, включая оба новых патча. `profiles/telemetry-confirmed.properties` изолирует только доказанный map `retainAll` fix.

## Откат

Запустите `uninstall-agent.bat`, отключите мод и перезапустите игру. Удаление безопасно для сохранений.
