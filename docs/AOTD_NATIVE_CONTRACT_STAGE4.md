# AoTD native contract — Stage 4

The Prepatcher remains responsible for delivered market time and exact replay before structural mutation. AoTD now owns the market registry and dirty queue.

The transformed `SchedulerBridge.afterMarketMutation()` executes in this order:

1. commit the mutation to `StarsectorPrepatcherRuntimeBridge`;
2. read the committed structural generation;
3. call the fork-local `SchedulerBridge.acceptMarketMutation()`;
4. merge the dirty mask into `MarketRegistry`.

This is a direct same-loader call. No reflection or runtime-to-AoTD class reference is introduced.

Bridge schema: 3  
Marker: `AOTD_SCHEDULER_BRIDGE_V3`

Negotiated capabilities remain `0x0f`; Stage 4 does not advertise worker or global-settlement capabilities.
