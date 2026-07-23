# Stage 8.3 P0 — AoTD native temporal mode

## Результат

В старую очищенную Stage 8.3 runtime-hotfix ветку добавлен нативный temporal mode
для `AoTDCommodityOnMarket`. Актуальная Stage 8.9 не изменялась.

AoTD runtime JAR не изменён. Изменён только StarsectorPrepatcher agent и его исходник.

## Исправленная причина нагрузки

Предыдущая реализация принимала в fast path только точные классы
`CommodityOnMarket` и `MutableStatWithTempMods`. AoTD использует подклассы
`AoTDCommodityOnMarket` и `AoTDAvailableStat`, поэтому его commodities навсегда
переходили в `vanillaOnly` и выполняли полный temporal цикл при каждой доставке времени.

## Реализация

Добавлены три закрытых режима:

- `EXACT_VANILLA`;
- `AOTD_NATIVE`;
- `VANILLA_ONLY`.

`AOTD_NATIVE` включается только для точного имени класса
`data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket`.

При построении state один раз через cached `MethodHandle` захватываются:

- `available`;
- `AoTDExcDefData.excess`;
- `AoTDExcDefData.deficit`;
- `trade`;
- `tradePlus`;
- `tradeMinus`.

Все шесть stats получают существующую owner/role binding. После этого hot loop
использует прямые ссылки и не выполняет reflection.

## Сохранённая семантика

`AoTDAvailableStat.advance()` уже вызывает availability, excess и deficit в нужном
порядке. Native mode вызывает его один раз, если активен любой из этих трёх stats.
Отдельные вызовы excess/deficit не выполняются, поэтому двойного advance нет.

Три trade stats вызываются только при наличии временных модификаторов.

Пустой `AoTDCommodityOnMarket.reapplyEventMod()` не вызывается.

После исчезновения всех шести temporary maps entry удаляется из active set. Новая
mutation любого связанного stat будит его существующим dirty hook.

Неизвестные subclasses и несовпавший AoTD contract остаются на прежнем полном fallback.
Exact vanilla mode не изменён.

## Телеметрия

В stats log добавлены sampled counters:

- `commodityTemporalExactVanillaEntries`;
- `commodityTemporalAoTDNativeEntries`;
- `commodityTemporalVanillaOnlyEntries`;
- `commodityTemporalExactVanillaActive`;
- `commodityTemporalAoTDNativeActive`;
- `commodityTemporalVanillaOnlyActive`.

Они позволят непосредственно измерить, сколько AoTD entries реально остаётся активным,
вместо вывода по общему `commodityTemporalActive`.

## Проверки

Пройдены:

- компиляция Java 17;
- synthetic differential test шести stats;
- отсутствие двойного advance excess/deficit;
- sleep после expiry;
- wakeup от bound deficit mutation;
- отсутствие вызова AoTD `reapplyEventMod()`;
- сохранение exact vanilla reapply semantics;
- сохранение fallback неизвестного subclass;
- Stage 8.3 delivery fail-stop regression;
- SchedulerBridge transformer regression;
- ASM analysis всех 69 классов семейства `StarsectorPrepatcherHooks`;
- `-Xverify:all` на runtime-тестах;
- публичный API Hooks: 84/84, без изменений;
- external reference audit: отличия только в трёх намеренно изменённых классах;
- AoTD contract bytecode: `reapplyEventMod()` состоит из одного `return`;
- agent payload inventory: 86 runtime classes, как до патча.

## Ограничения

Полный запуск Starsector с реальной кампанией после этого P0-патча в текущем окружении
не выполнялся. Следующий игровой замер должен подтвердить снижение
`commodityTemporalAoTDNativeActive` и доли
`CommodityTemporalMarketState.advancePrepared` в JFR.
