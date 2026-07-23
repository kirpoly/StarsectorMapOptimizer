# AoTD native contract — Stage 8

## Schema

- Bridge schema: `5`
- Patch marker: `AOTD_SCHEDULER_BRIDGE_V5`
- Required production mask: `0xff`
- Clean deficit capability: `1 << 4`

The javaagent-generated registration call passes two loader-neutral JDK callbacks:

- `Consumer<Object>` for delivered market-time notifications;
- `BiFunction<Object,Object,Object>` for source-level deficit resolution.

The target-loader runtime stores these callbacks without linking to AoTD classes.

## BaseIndustry wrapper

Patch ID: `aotdCleanDeficitPath`.

The transformer:

- requires one concrete `getMaxDeficit(String...)` method;
- renames it to a private synthetic raw method;
- clones and owner-remaps the verified wrapper template;
- validates one runtime resolver call and one raw fallback call;
- rejects partial or structurally incompatible states;
- recognizes an already-installed matching wrapper as idempotent.

The clean wrapper is installed whenever the patch is enabled. With no active compatible AoTD contract, runtime resolution returns `null` and the preserved vanilla method is called.

## Production behavior

The clean capability is negotiated only when:

- `patch.aotdCleanDeficitPath=true`;
- the fork supplies a non-null resolver callback;
- ABI and classloader checks pass.

A partial profile remains inactive from the fork's point of view. The scheduler fork does not silently use vanilla deficit semantics.
