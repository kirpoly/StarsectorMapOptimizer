# Stage 8.3 P1 — per-stat active mask

`CommodityTemporalEntry` больше не использует coarse-группы `AVAILABLE` и `TRADE`.
Каждый временной stat имеет отдельный бит активности и dirty-сигнала:

- available — `1`;
- AoTD excess — `2`;
- AoTD deficit — `4`;
- trade — `8`;
- tradePlus — `16`;
- tradeMinus — `32`.

Mutation hook записывает точный бит stat. Hot loop проверяет и продвигает только stats,
чьи биты активны. Для AoTD `available/excess/deficit` остаются одной семантической
группой вызова: если активен любой из трёх битов, один вызов
`AoTDAvailableStat.advance()` сохраняет исходный порядок и не допускает двойного
advance `excess/deficit`. После вызова маска группы пересчитывается по фактическим
временным модификаторам.

Неизвестные subclasses остаются на `VANILLA_ONLY`. Публичный ABI Hooks не изменён.
