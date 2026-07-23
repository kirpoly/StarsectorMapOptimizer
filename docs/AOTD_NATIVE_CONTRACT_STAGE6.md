# AoTD native contract — Stage 6

Added capability:

```text
CAPABILITY_PURE_PRICE_OFFLOAD = 1 << 6
```

The runtime-supported and fork-declared masks negotiate to `0x6f` when the
verified Stage 6 fork is loaded. Capability activation only states that the fork
uses pure DTO price workers and main-thread commit. It does not change
Prepatcher scheduling decisions.

The bridge remains direct-bytecode ABI without reflection in compatibility code.
