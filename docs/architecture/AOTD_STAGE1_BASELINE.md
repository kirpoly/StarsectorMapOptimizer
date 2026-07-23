# AoTD scheduler fork: stage 1 baseline

Stage 1 changes the original Prepatcher only to expose observable scheduler
state. It intentionally does not add AoTD method transformers, native mutation
boundaries, a deficit patch, or delivered-time callbacks into AoTD.

## Pending versus delivered

For every scheduler-owned market the runtime records:

- pending step count and amount;
- exact run-length history retained for replay;
- debt sequence and scheduler source attribution;
- successful concrete `Market.advance()` callback count and amount;
- last delivered callback sequence, origin and timestamp;
- construction/full-rate and disabled state.

A callback is counted as delivered only after `Market.advance()` returns
successfully. A failed callback is not counted.

## Capture points

`StarsectorPrepatcherRuntimeBridge.dumpMarketSchedulerBaseline(reason)` writes a
CSV and JSON summary without delivering pending debt. Save flushing captures
one dump before and one dump after the flush so the transition can be audited.

## Scope boundary

The adapted AoTD compatibility implementation is reference material only. This
stage is implemented on the original Prepatcher tree.
