# AoTD native contract — Stage 7

Bridge schema: `4`  
Capability bit: `1 << 7` (`GLOBAL_PHASE_COORDINATION`)  
Full negotiated mask: `0xef`

## API

```java
long beforeAoTDGlobalBoundary(int reasonMask, boolean hardFlush)
void afterAoTDGlobalBoundary(long token, long generation)
```

A hard boundary invokes the existing exact market-scheduler flush before returning. AoTD then observes delivery generations and runs its local pipeline before opening a committed trade cut. A soft boundary only identifies and validates an immutable AoTD global phase.

The bridge transformer verifies the exact fork-owned no-op surface and replaces it with direct static target-loader calls. It fails open to the unmodified no-op bridge on schema, loader or verification mismatch. No reflection is used by the AoTD integration ABI.
