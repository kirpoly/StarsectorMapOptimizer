# AoTD native contract — Stage 2

This implementation is based on the original Prepatcher line, not on the
AoTD-adapted validation fork.

## Why a javaagent bridge is used

Starsector mod code must not rely on reflection for this integration. Prepatcher
therefore patches a fork-owned no-op class before it is defined. The resulting
AoTD bytecode performs a normal direct static call to a runtime class already
installed in the Starsector API loader.

## Safety properties

- no direct Prepatcher reference exists in the unmodified AoTD JAR bytecode;
- no mod-side reflection or loader probing is used;
- an unrelated/sibling class loader is rejected;
- marker/schema/method mismatches fail open to the AoTD no-op implementation;
- only capability `0x1` is supported in Stage 2;
- early JAR discovery is diagnostic and never activates the profile by itself;
- repeated identical registration is idempotent;
- conflicting registration is rejected.

## Transformation contract

Target:

```text
data/kaysaar/aotd/tot/compat/SchedulerBridge
```

Required source markers:

```text
BRIDGE_SCHEMA = 1
BRIDGE_MARKER = AOTD_SCHEDULER_BRIDGE_V1
initialize() -> SchedulerBridge.State
activateFromPrepatcher(long) -> SchedulerBridge.State
```

The transformed `initialize()` reads ABI values from `PrepatcherContract` and
calls:

```text
StarsectorPrepatcherRuntimeBridge.registerAoTDForkContract(...)
SchedulerBridge.activateFromPrepatcher(...)
```

## Current capability mask

```text
supported = CONTRACT_HANDSHAKE = 0x1
```

All later capabilities remain reserved and unavailable until their respective
implementation stages are completed.


Stage 3 extends the negotiated ABI with post-success delivery events, delivered/structural generations and exact local mutation barriers.
