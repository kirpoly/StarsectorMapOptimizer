# AoTD native contract — Stage 5

The original Prepatcher codebase remains the scheduler owner. Stage 5 adds only one negotiated capability:

```text
CAPABILITY_AUTHORITATIVE_MARKET_STATE = 1 << 5
```

Negotiated mask for the Stage 5 fork:

```text
0x2f
```

Meaning:

- loader-safe direct ABI is active;
- delivered market time callbacks are active;
- structural mutation boundaries are active;
- market generations are active;
- the fork now commits authoritative per-market derived state.

Prepatcher does **not** consume or schedule the AoTD dirty queue. It continues to own only delivered `Market.advance()` time and temporal ordering around structural mutations.

No scheduler cadence, coalescing, replay or save-flush policy was changed in this stage.
