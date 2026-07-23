# AoTD native contract — Stage 4 audit

The audit preserves the original Prepatcher scheduler policy and corrects nested
source-boundary behavior.

`beforeAoTDMarketMutation()` now follows this order:

1. under the runtime lock, detect an already-open boundary;
2. for a nested boundary, merge the reason mask, increase depth and return the
   existing token without scheduler replay;
3. only for the outermost boundary, release the lock and replay exact pending debt;
4. reacquire the lock and re-read state because replay may invoke callbacks;
5. create or join the boundary and return its token.

This prevents repeated exact replay when a structural operation invokes another
covered operation internally. Delivery remains post-success only. The parent runtime
continues to communicate through JDK types and direct transformed ABI calls; it has
no reference to AoTD classes and uses no reflection.
