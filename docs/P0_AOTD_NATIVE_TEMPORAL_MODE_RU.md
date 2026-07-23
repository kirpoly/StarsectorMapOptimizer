# P0: нативный temporal mode для AoTD

## Цель

Убрать `AoTDCommodityOnMarket` из постоянного `vanillaOnly`-пути
`CommodityTemporalMarketState.advancePrepared()`.

Ранее строгая проверка точного класса принимала только:

- `CommodityOnMarket.class`;
- `MutableStatWithTempMods.class`.

AoTD использует `AoTDCommodityOnMarket` и `AoTDAvailableStat`, поэтому каждый commodity
оставался навсегда активным и при каждой доставке времени выполнял полный цикл
`available/trade/tradePlus/tradeMinus/reapplyEventMod`.

## Реализация

Добавлен закрытый режим `AOTD_NATIVE`, включаемый только для точного класса:

`data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket`.

При построении market state Prepatcher один раз:

1. получает `getExcDefData()`;
2. связывает публичные поля `excess` и `deficit` через cached `MethodHandle`;
3. сохраняет прямые ссылки на шесть stats:
   - `available`;
   - `excess`;
   - `deficit`;
   - `trade`;
   - `tradePlus`;
   - `tradeMinus`;
4. привязывает каждый stat к владельцу temporal entry.

Reflection/MethodHandle lookup не выполняется в `advancePrepared()`.

## Семантика advance

`AoTDAvailableStat.advance(days)` уже выполняет:

1. `super.advance(days)` для availability;
2. `excess.advance(days)`;
3. `deficit.advance(days)`.

Поэтому native path вызывает `available.advance(days)` ровно один раз, если активен
хотя бы один stat из availability-группы. `excess` и `deficit` отдельно не вызываются,
что исключает двойное продвижение времени.

Три trade stats обрабатываются отдельно только при наличии временных модификаторов.

`AoTDCommodityOnMarket.reapplyEventMod()` в этой ветке является пустым override и в
native mode не вызывается.

Entry удаляется из active set, когда все шесть temporal maps пусты. Любая mutation
привязанного stat снова будит entry через существующий dirty hook.

## Безопасность совместимости

- Неизвестные subclasses остаются в `vanillaOnly`.
- Exact vanilla mode не изменён.
- Если AoTD class/method/field contract не совпадает, конкретный entry безопасно
  возвращается в полный vanilla path.
- Публичный API `StarsectorPrepatcherHooks` не изменён.
- AoTD-классы не импортируются и не линкуются напрямую из Prepatcher.

## Новая телеметрия

В периодический stats log добавлены sampled counters:

- `commodityTemporalExactVanillaEntries`;
- `commodityTemporalAoTDNativeEntries`;
- `commodityTemporalVanillaOnlyEntries`;
- `commodityTemporalExactVanillaActive`;
- `commodityTemporalAoTDNativeActive`;
- `commodityTemporalVanillaOnlyActive`.

Ожидаемый признак успешной работы в игре: `AoTDNativeEntries` велико, а
`AoTDNativeActive` после стартового аудита и expiry существенно ниже общего числа
AoTD entries.
