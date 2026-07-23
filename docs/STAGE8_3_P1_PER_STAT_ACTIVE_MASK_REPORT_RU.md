# Stage 8.3 P1 — per-stat active mask

## Область изменения

Работа выполнена в старой очищенной Stage 8.3-ветке поверх P0 native AoTD temporal
mode. Актуальная Stage 8.9 не изменялась.

AoTD runtime JAR остался побайтно неизменным. Пересобран только полный class-family
`StarsectorPrepatcherHooks*` в agent JAR.

## Проблема P0

P0 уже позволял AoTD commodity засыпать, но внутри активного entry использовал две
coarse-группы:

- availability: available + excess + deficit;
- trade: trade + tradePlus + tradeMinus.

Mutation одного trade stat будила всю trade-группу, а hot loop повторно проверял
несколько соседних stats. При большом числе активных commodities это оставляло
лишние VarHandle/map reads и усложняло точную телеметрию.

## Реализация

Введено шесть независимых битов:

- `COMMODITY_STAT_AVAILABLE = 1`;
- `COMMODITY_STAT_AOTD_EXCESS = 2`;
- `COMMODITY_STAT_AOTD_DEFICIT = 4`;
- `COMMODITY_STAT_TRADE = 8`;
- `COMMODITY_STAT_TRADE_PLUS = 16`;
- `COMMODITY_STAT_TRADE_MINUS = 32`.

`CommodityTemporalEntry` теперь хранит:

- `supportedMask` — допустимые для режима stats;
- `activeMask` — stats, у которых есть temporal work;
- `dirtyMask` — точные stats, изменённые mutator-hook.

`markDirty()` добавляет только переданный stat-бит. `process()` проверяет только
кандидаты из `activeMask | dirtyMask`, продвигает только активные trade stats и
обновляет только обработанные/изменённые биты.

## AoTD availability group

`AoTDAvailableStat.advance()` внутри себя продвигает:

1. base availability;
2. excess;
3. deficit.

Поэтому три бита отслеживаются независимо, но выполняются одним групповым вызовом,
если активен хотя бы один из них. После вызова пересчитываются все три бита. Это
сохраняет исходный порядок и исключает двойной advance.

## Exact vanilla

Для exact vanilla доступны только четыре бита:

- available;
- trade;
- tradePlus;
- tradeMinus.

`reapplyEventMod()` сохраняет прежнюю консервативную семантику и вызывается при
подтверждённом изменении поддерживаемого stat. Внутренний availability key `eMod`
по-прежнему не будит entry повторно.

## Неизвестные классы

Generic subclass policy не расширялась. Неизвестные commodity/stat subclasses
остаются в `VANILLA_ONLY` и выполняют полный исходный цикл.

## Телеметрия

Добавлены sampled counters:

- `commodityTemporalActiveAvailable`;
- `commodityTemporalActiveAoTDExcess`;
- `commodityTemporalActiveAoTDDeficit`;
- `commodityTemporalActiveTrade`;
- `commodityTemporalActiveTradePlus`;
- `commodityTemporalActiveTradeMinus`.

Они позволяют проверить фактическое распределение temporal work по stats.

## Проверки

Пройдены:

- Java 17 compilation;
- synthetic differential test P0/P1 semantics;
- exact owner-role binding 1/2/4/8/16/32;
- excess-only activity не активирует trade stats;
- tradePlus-only activity не вызывает AoTD availability group;
- tradePlus-only activity не вызывает trade/tradeMinus;
- смешанная deficit + tradePlus activity сохраняет оба точных бита;
- vanilla tradeMinus-only path не продвигает соседние stats;
- sleep после expiry и wakeup от bound mutation;
- AoTD no-op `reapplyEventMod()` не вызывается;
- unknown subclass остаётся `VANILLA_ONLY`;
- delivery listener fail-stop regression;
- SchedulerBridge transformer regression;
- `-Xverify:all` для runtime tests;
- ASM Analyzer: 69 Hooks classes, 491 methods;
- публичный API `StarsectorPrepatcherHooks` не изменён;
- agent JAR: 155 class files, max major 61, duplicate entries отсутствуют.

## Ограничения

Полный запуск Starsector после P1 в текущем окружении не выполнялся. Следующий игровой
замер должен сравнить новые per-stat counters и долю
`CommodityTemporalEntry.process/advancePrepared` с P0.
