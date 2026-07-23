# AoTD native contract — Stage 3

## Capability mask

```text
0x01 CONTRACT_HANDSHAKE
0x02 NATIVE_DELIVERY_EVENTS
0x04 NATIVE_MUTATION_BOUNDARIES
0x08 MARKET_GENERATIONS
```

The clean deficit capability remains reserved.

## Loader model

The AoTD JAR contains no direct runtime dependency and no reflective discovery.
The javaagent verifies bridge schema 2 and rewrites the fork-owned no-op methods
to direct calls into `StarsectorPrepatcherRuntimeBridge` before class definition.

The delivery direction uses `java.util.function.Consumer<Object>`. This JDK type
is visible to both loader sides and does not require the parent runtime to link a
class defined only by the mod loader.

## Delivered market time

Hooks publish an event only after an actual `Market.advance()` invocation
returns. Merely enqueuing/coalescing scheduler input does not change the delivered
generation.

State is keyed by weak object identity and records:

- delivered generation;
- last delivery sequence;
- last delivered amount;
- structural generation;
- nested mutation token/depth.

## Exact local boundary

`beforeAoTDMarketMutation` invokes the existing exact scheduler replay helper.
This replays old pending time before the new market structure becomes visible.
The operation is local to the affected market and is not used for ordinary AoTD
reads or calculations.

`afterAoTDMarketMutation` closes a nested token. Only the outermost close commits
a new structural generation.

## Failure behavior

- missing javaagent: AoTD bridge remains a no-op;
- incompatible marker/schema/loader: transformation is skipped;
- delivery listener failure: logged with exponential throttling and ignored;
- invalid/non-market object at the exact replay helper: ignored;
- conflicting contract registration: rejected.
