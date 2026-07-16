package com.starsector.mapoptimizer.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.starsector.mapoptimizer.agent.OptimizerConfig;
import com.starsector.mapoptimizer.agent.OptimizerLog;
import org.lwjgl.util.vector.Vector2f;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Runtime entry points called by transformed Starsector classes. */
public final class MapOptimizerHooks {
    private static volatile OptimizerConfig config;
    private static volatile Path modRoot;

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final Map<IntelInfoPlugin, IntelCache> INTEL_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final IdentityHashMap<LinkedHashMap<?, ?>, LabelIndex> LABEL_INDEXES =
            new IdentityHashMap<>();
    private static final IdentityHashMap<List<?>, IntelEntityIndex> INTEL_ENTITY_INDEXES =
            new IdentityHashMap<>();
    private static final IdentityHashMap<Object, HitCache> HIT_CACHES = new IdentityHashMap<>();
    private static final WeakHashMap<Object, Long> SAMPLE_CLEAR_TIMES = new WeakHashMap<>();
    private static final Map<Object, CampaignListenerState> CAMPAIGN_LISTENER_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LocationAPI, RouteJumpIndex> ROUTE_JUMP_INDEXES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, RouteSystemIndex> ROUTE_SYSTEM_INDEXES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final LongAdder RETAIN_CALLS = new LongAdder();
    private static final LongAdder RETAIN_UPPER_BOUND = new LongAdder();
    private static final LongAdder RETAIN_KEYS = new LongAdder();
    private static final LongAdder RETAIN_REMOVED = new LongAdder();
    private static final LongAdder RETAIN_EQUALITY_FALLBACKS = new LongAdder();
    private static final LongAdder HOVER_HITS = new LongAdder();
    private static final LongAdder HOVER_MISSES = new LongAdder();
    private static final LongAdder MAP_LOCATION_HITS = new LongAdder();
    private static final LongAdder MAP_LOCATION_MISSES = new LongAdder();
    private static final LongAdder ARROW_HITS = new LongAdder();
    private static final LongAdder ARROW_MISSES = new LongAdder();
    private static final LongAdder INTEL_INDEX_HITS = new LongAdder();
    private static final LongAdder INTEL_INDEX_BUILDS = new LongAdder();
    private static final LongAdder LABEL_TOTAL = new LongAdder();
    private static final LongAdder LABEL_CANDIDATES = new LongAdder();
    private static final LongAdder NEBULA_HITS = new LongAdder();
    private static final LongAdder NEBULA_MISSES = new LongAdder();
    private static final LongAdder SAMPLE_CLEAR_SKIPS = new LongAdder();
    private static final LongAdder CAMPAIGN_LISTENER_RUNS = new LongAdder();
    private static final LongAdder CAMPAIGN_LISTENER_SKIPS = new LongAdder();
    private static final LongAdder ROUTE_JUMP_INDEX_HITS = new LongAdder();
    private static final LongAdder ROUTE_JUMP_INDEX_BUILDS = new LongAdder();
    private static final LongAdder ROUTE_JUMP_INDEX_FALLBACKS = new LongAdder();
    private static final LongAdder ROUTE_SYSTEM_INDEX_HITS = new LongAdder();
    private static final LongAdder ROUTE_SYSTEM_INDEX_BUILDS = new LongAdder();
    private static final LongAdder ROUTE_SYSTEM_INDEX_FALLBACKS = new LongAdder();

    private static volatile NebulaCache nebulaCache;
    private static volatile float cachedGridSpacing = -1f;
    private static volatile float cachedGridWidth = Float.NaN;
    private static volatile float cachedGridHeight = Float.NaN;

    private MapOptimizerHooks() {}

    public static void configure(OptimizerConfig optimizerConfig, Path root) {
        config = optimizerConfig;
        modRoot = root;
        if (optimizerConfig.statsLogIntervalSeconds > 0) {
            Thread stats = new Thread(MapOptimizerHooks::statsLoop, "StarsectorMapOptimizer-Stats");
            stats.setDaemon(true);
            stats.setPriority(Thread.MIN_PRIORITY);
            stats.start();
        }
    }

    // ---------------------------------------------------------------------
    // Map reconciliation and scratch collection reuse
    // ---------------------------------------------------------------------

    /**
     * Replaces the exact H.renderStuff() reconciliation call.
     *
     * Vanilla invokes LinkedHashMap.keySet().retainAll(ArrayList), which scans the
     * ArrayList once for every icon key. The map keys are concrete campaign-entity
     * instances; all vanilla 0.98a-RC8 CampaignEntity implementations inherit
     * Object.equals/hashCode, so identity membership is the native domain semantic.
     *
     * A reusable IdentityHashMap avoids entity hashCode implementations for vanilla
     * entities and the explicit iterator avoids the generic AbstractCollection path.
     * Modded entity classes overriding equals/hashCode use a reusable HashSet so the
     * original Java collection semantics are retained.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean retainAllFast(Set target, Collection keep, Object mapOwner) {
        OptimizerConfig c = config;
        if (c == null || !c.retainAll) {
            return target.retainAll(keep);
        }

        Scratch scratch = SCRATCH.get();
        IdentityHashMap<Object, Boolean> membership = scratch.identityMembership;
        membership.clear();
        boolean customEquality = false;
        if (keep instanceof List list) {
            for (int i = 0, size = list.size(); i < size; i++) {
                Object entity = list.get(i);
                membership.put(entity, Boolean.TRUE);
                if (entity != null && CUSTOM_EQUALITY.get(entity.getClass())) customEquality = true;
            }
        } else {
            for (Object entity : keep) {
                membership.put(entity, Boolean.TRUE);
                if (entity != null && CUSTOM_EQUALITY.get(entity.getClass())) customEquality = true;
            }
        }

        if (!customEquality) {
            for (Object entity : target) {
                if (entity != null && CUSTOM_EQUALITY.get(entity.getClass())) {
                    customEquality = true;
                    break;
                }
            }
        }

        Set<Object> equalityMembership = null;
        if (customEquality) {
            // Preserve equals/hashCode semantics for modded entity classes that
            // override Object equality. Vanilla entities stay on the faster
            // identity path. LinkedHashMap keys already require a valid hashCode
            // contract, so HashSet membership matches normal Java collection rules.
            equalityMembership = scratch.equalityMembership;
            equalityMembership.clear();
            equalityMembership.addAll(keep);
            RETAIN_EQUALITY_FALLBACKS.increment();
        }

        RETAIN_CALLS.increment();
        RETAIN_UPPER_BOUND.add((long) target.size() * (long) keep.size());
        RETAIN_KEYS.add(target.size());

        boolean changed = false;
        long removed = 0L;
        try {
            Iterator iterator = target.iterator();
            while (iterator.hasNext()) {
                Object entity = iterator.next();
                boolean present = customEquality
                        ? equalityMembership.contains(entity)
                        : membership.containsKey(entity);
                if (!present) {
                    iterator.remove();
                    changed = true;
                    removed++;
                }
            }
            if (removed != 0L) RETAIN_REMOVED.add(removed);
            return changed;
        } finally {
            membership.clear();
            if (equalityMembership != null) equalityMembership.clear();
        }
    }

    private static final ClassValue<Boolean> CUSTOM_EQUALITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method equals = type.getMethod("equals", Object.class);
                Method hashCode = type.getMethod("hashCode");
                return equals.getDeclaringClass() != Object.class
                        || hashCode.getDeclaringClass() != Object.class;
            } catch (ReflectiveOperationException | SecurityException ex) {
                // Unknown/custom class shape: prefer collection semantics over the
                // identity optimization.
                return Boolean.TRUE;
            }
        }
    };

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArrayList borrowEntityList(Collection source) {
        OptimizerConfig c = config;
        if (c == null || !c.scratchCollections) return new ArrayList(source);
        ArrayList list = SCRATCH.get().entityList;
        list.clear();
        list.addAll(source);
        return list;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static HashSet borrowClassSet() {
        OptimizerConfig c = config;
        if (c == null || !c.scratchCollections) return new HashSet();
        HashSet set = SCRATCH.get().classSet;
        set.clear();
        return set;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArrayList borrowHitList(Collection source) {
        OptimizerConfig c = config;
        if (c == null || !c.scratchCollections) return new ArrayList(source);
        ArrayList list = SCRATCH.get().hitList;
        list.clear();
        list.addAll(source);
        return list;
    }

    public static Vector2f borrowHitPoint(float x, float y) {
        OptimizerConfig c = config;
        if (c == null || !c.scratchCollections) return new Vector2f(x, y);
        Vector2f vector = SCRATCH.get().hitPoint;
        vector.set(x, y);
        return vector;
    }

    public static Vector2f borrowArrowVector(float x, float y) {
        OptimizerConfig c = config;
        if (c == null || !c.arrowVectorPool) return new Vector2f(x, y);
        Scratch scratch = SCRATCH.get();
        Vector2f vector = scratch.arrowVectors[scratch.arrowVectorIndex++ & (scratch.arrowVectors.length - 1)];
        vector.set(x, y);
        return vector;
    }

    // ---------------------------------------------------------------------
    // Label candidate spatial index. Original method still performs exact
    // visibility and distance checks; this only narrows the candidate set.
    // ---------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection nearbyLabelIcons(LinkedHashMap icons, Object mapOwner, Object targetIcon) {
        OptimizerConfig c = config;
        if (c == null || !c.labelSpatialCandidates || icons.size() < 64) {
            return icons.values();
        }
        try {
            LabelIndex index;
            long now = System.nanoTime();
            synchronized (LABEL_INDEXES) {
                index = LABEL_INDEXES.get(icons);
                long ttlNs = c.labelIndexTtlMs * 1_000_000L;
                if (index == null || index.size != icons.size()
                        || (ttlNs > 0L && now - index.builtAtNanos > ttlNs)) {
                    index = LabelIndex.build(icons, now);
                    if (LABEL_INDEXES.size() >= 32 && !LABEL_INDEXES.containsKey(icons)) {
                        Iterator<LinkedHashMap<?, ?>> iterator = LABEL_INDEXES.keySet().iterator();
                        if (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                    }
                    LABEL_INDEXES.put(icons, index);
                }
            }
            SectorEntityToken token = iconToken(targetIcon);
            if (token == null || token.getLocation() == null || index.failed) return icons.values();
            Vector2f location = token.getLocation();
            int cx = floorCell(location.x, LabelIndex.CELL_X);
            int cy = floorCell(location.y, LabelIndex.CELL_Y);
            ArrayList<Object> result = SCRATCH.get().labelCandidates;
            result.clear();
            result.addAll(index.fallback);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Object> bucket = index.buckets.get(cellKey(cx + dx, cy + dy));
                    if (bucket != null) result.addAll(bucket);
                }
            }
            LABEL_TOTAL.add(icons.size());
            LABEL_CANDIDATES.add(result.size());
            return result;
        } catch (Throwable ex) {
            return icons.values();
        }
    }

    private static SectorEntityToken iconToken(Object icon) throws Exception {
        Method method = IconAccess.TOKEN_METHODS.get(icon.getClass());
        return (SectorEntityToken) method.invoke(icon);
    }

    private static final class IconAccess {
        private static final ClassValue<Method> TOKEN_METHODS = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                Class<?> current = type;
                while (current != null) {
                    try {
                        Method method = current.getDeclaredMethod("int");
                        method.setAccessible(true);
                        return method;
                    } catch (NoSuchMethodException ignored) {
                        current = current.getSuperclass();
                    }
                }
                throw new IllegalStateException("Unable to find map-icon token accessor for " + type.getName());
            }
        };
    }

    private static final class LabelIndex {
        static final float CELL_X = 10_000f;
        static final float CELL_Y = 3_000f;
        final int size;
        final long builtAtNanos;
        final Map<Long, List<Object>> buckets;
        final List<Object> fallback;
        final boolean failed;

        private LabelIndex(int size, long builtAtNanos, Map<Long, List<Object>> buckets,
                           List<Object> fallback, boolean failed) {
            this.size = size;
            this.builtAtNanos = builtAtNanos;
            this.buckets = buckets;
            this.fallback = fallback;
            this.failed = failed;
        }

        static LabelIndex build(LinkedHashMap<?, ?> icons, long now) {
            Map<Long, List<Object>> buckets = new HashMap<>();
            List<Object> fallback = new ArrayList<>();
            try {
                for (Object icon : icons.values()) {
                    try {
                        SectorEntityToken token = iconToken(icon);
                        Vector2f location = token == null ? null : token.getLocation();
                        if (location == null || !Float.isFinite(location.x) || !Float.isFinite(location.y)) {
                            fallback.add(icon);
                            continue;
                        }
                        int cx = floorCell(location.x, CELL_X);
                        int cy = floorCell(location.y, CELL_Y);
                        buckets.computeIfAbsent(cellKey(cx, cy), ignored -> new ArrayList<>()).add(icon);
                    } catch (Throwable ex) {
                        fallback.add(icon);
                    }
                }
                return new LabelIndex(icons.size(), now, buckets, fallback, false);
            } catch (ConcurrentModificationException ex) {
                return new LabelIndex(icons.size(), now, Collections.emptyMap(), new ArrayList<>(), true);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Intel callbacks
    // ---------------------------------------------------------------------

    public static SectorEntityToken getMapLocationCached(IntelInfoPlugin plugin, SectorMapAPI map) {
        OptimizerConfig c = config;
        if (c == null || !c.intelCallbackCache || c.intelMapLocationCacheMs <= 0) {
            return plugin.getMapLocation(map);
        }
        long now = System.nanoTime();
        long ttl = c.intelMapLocationCacheMs * 1_000_000L;
        IntelCache entry;
        synchronized (INTEL_CACHE) {
            entry = INTEL_CACHE.computeIfAbsent(plugin, ignored -> new IntelCache());
            if (entry.mapForLocation == map && entry.locationValid && now - entry.locationAtNanos <= ttl) {
                MAP_LOCATION_HITS.increment();
                return entry.location;
            }
        }
        SectorEntityToken value = plugin.getMapLocation(map);
        synchronized (INTEL_CACHE) {
            entry.mapForLocation = map;
            entry.location = value;
            entry.locationAtNanos = now;
            entry.locationValid = true;
        }
        MAP_LOCATION_MISSES.increment();
        return value;
    }

    @SuppressWarnings("rawtypes")
    public static List getArrowDataCached(IntelInfoPlugin plugin, SectorMapAPI map) {
        OptimizerConfig c = config;
        if (c == null || !c.intelCallbackCache || c.intelArrowDataCacheMs <= 0) {
            return plugin.getArrowData(map);
        }
        long now = System.nanoTime();
        long ttl = c.intelArrowDataCacheMs * 1_000_000L;
        IntelCache entry;
        synchronized (INTEL_CACHE) {
            entry = INTEL_CACHE.computeIfAbsent(plugin, ignored -> new IntelCache());
            if (entry.mapForArrows == map && entry.arrowsValid && now - entry.arrowsAtNanos <= ttl) {
                ARROW_HITS.increment();
                return entry.arrows;
            }
        }
        List value = plugin.getArrowData(map);
        synchronized (INTEL_CACHE) {
            entry.mapForArrows = map;
            entry.arrows = value;
            entry.arrowsAtNanos = now;
            entry.arrowsValid = true;
        }
        ARROW_MISSES.increment();
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean fastContains(Collection collection, Object value) {
        OptimizerConfig c = config;
        if (c == null || !c.intelFastContains || collection.size() < 16) {
            return collection.contains(value);
        }
        Scratch scratch = SCRATCH.get();
        if (scratch.containsSource != collection || scratch.containsSourceSize != collection.size()) {
            scratch.containsSet.clear();
            scratch.containsSet.addAll(collection);
            scratch.containsSource = collection;
            scratch.containsSourceSize = collection.size();
        }
        return scratch.containsSet.contains(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection existingIntelIconCandidates(LinkedHashMap icons, Object entity) {
        Object direct = icons.get(entity);
        if (direct != null) return Collections.singletonList(direct);
        return icons.values();
    }

    /** Identity index for H.getIntelIconEntity(), avoiding an O(M) scan per Intel item. */
    @SuppressWarnings("rawtypes")
    public static Object getIntelIconEntityIndexed(Object owner, List intelEntities, IntelInfoPlugin plugin) {
        OptimizerConfig c = config;
        if (c == null || !c.intelEntityIndex || intelEntities == null || plugin == null) {
            return invokeOriginalIntelIconLookup(owner, plugin);
        }
        try {
            long now = System.nanoTime();
            Object first = intelEntities.isEmpty() ? null : intelEntities.get(0);
            Object last = intelEntities.isEmpty() ? null : intelEntities.get(intelEntities.size() - 1);
            IntelEntityIndex index;
            synchronized (INTEL_ENTITY_INDEXES) {
                index = INTEL_ENTITY_INDEXES.get(intelEntities);
                long ttlNs = c.intelEntityIndexTtlMs * 1_000_000L;
                if (index == null || index.size != intelEntities.size()
                        || index.first != first || index.last != last
                        || (ttlNs > 0L && now - index.builtAtNanos > ttlNs)) {
                    index = IntelEntityIndex.build(intelEntities, first, last, now);
                    if (INTEL_ENTITY_INDEXES.size() >= 32 && !INTEL_ENTITY_INDEXES.containsKey(intelEntities)) {
                        Iterator<List<?>> iterator = INTEL_ENTITY_INDEXES.keySet().iterator();
                        if (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                    }
                    INTEL_ENTITY_INDEXES.put(intelEntities, index);
                    INTEL_INDEX_BUILDS.increment();
                }
            }
            if (index.failed) return invokeOriginalIntelIconLookup(owner, plugin);
            INTEL_INDEX_HITS.increment();
            return index.byPlugin.get(plugin);
        } catch (Throwable ex) {
            return invokeOriginalIntelIconLookup(owner, plugin);
        }
    }

    private static Object invokeOriginalIntelIconLookup(Object owner, IntelInfoPlugin plugin) {
        if (owner == null) return null;
        try {
            return IntelEntityAccess.ORIGINAL.get(owner.getClass()).invoke(owner, plugin);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to invoke original Starsector Intel entity lookup", ex);
        }
    }

    private static final class IntelEntityAccess {
        static final ClassValue<Method> ORIGINAL = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    Method method = type.getDeclaredMethod("smo$originalGetIntelIconEntity", IntelInfoPlugin.class);
                    method.setAccessible(true);
                    return method;
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        static final ClassValue<Field> PLUGIN_FIELD = new ClassValue<>() {
            @Override
            protected Field computeValue(Class<?> type) {
                Class<?> current = type;
                while (current != null) {
                    for (Field field : current.getDeclaredFields()) {
                        if (IntelInfoPlugin.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                    current = current.getSuperclass();
                }
                throw new IllegalStateException("No IntelInfoPlugin field in " + type.getName());
            }
        };
    }

    private static final class IntelEntityIndex {
        final int size;
        final Object first;
        final Object last;
        final long builtAtNanos;
        final IdentityHashMap<IntelInfoPlugin, Object> byPlugin;
        final boolean failed;

        IntelEntityIndex(int size, Object first, Object last, long builtAtNanos,
                         IdentityHashMap<IntelInfoPlugin, Object> byPlugin, boolean failed) {
            this.size = size;
            this.first = first;
            this.last = last;
            this.builtAtNanos = builtAtNanos;
            this.byPlugin = byPlugin;
            this.failed = failed;
        }

        @SuppressWarnings("rawtypes")
        static IntelEntityIndex build(List entities, Object first, Object last, long now) {
            IdentityHashMap<IntelInfoPlugin, Object> byPlugin = new IdentityHashMap<>();
            try {
                for (Object entity : entities) {
                    if (!(entity instanceof SectorEntityToken token)) continue;
                    Object data = token.getCustomData().get("intelIconData");
                    if (data == null) continue;
                    Field pluginField = IntelEntityAccess.PLUGIN_FIELD.get(data.getClass());
                    Object value = pluginField.get(data);
                    if (value instanceof IntelInfoPlugin plugin) byPlugin.put(plugin, entity);
                }
                return new IntelEntityIndex(entities.size(), first, last, now, byPlugin, false);
            } catch (ConcurrentModificationException ex) {
                return new IntelEntityIndex(entities.size(), first, last, now,
                        new IdentityHashMap<>(), true);
            } catch (Throwable ex) {
                return new IntelEntityIndex(entities.size(), first, last, now,
                        new IdentityHashMap<>(), true);
            }
        }
    }

    private static final class IntelCache {
        SectorMapAPI mapForLocation;
        SectorEntityToken location;
        long locationAtNanos;
        boolean locationValid;
        SectorMapAPI mapForArrows;
        List<?> arrows;
        long arrowsAtNanos;
        boolean arrowsValid;
    }

    // ---------------------------------------------------------------------
    // Hover / hit-test cache. The exact vanilla hit-test remains the source
    // of truth on misses; results are reused for a short time/cell.
    // ---------------------------------------------------------------------

    public static SectorEntityToken hitTestCached(Object handler, float x, float y, float radius) {
        OptimizerConfig c = config;
        if (c == null || !c.hoverHitTestCache) return invokeOriginalHitTest(handler, x, y, radius);
        try {
            HitAccess access = HitAccess.ACCESS.get(handler.getClass());
            Object map = access.mapField.get(handler);
            float factor = ((Number) access.getFactor.invoke(map)).floatValue();
            Object icons = access.getIcons.invoke(map);
            int iconCount = icons instanceof Map ? ((Map<?, ?>) icons).size() : -1;
            Object location = access.getLocation.invoke(map);
            long now = System.nanoTime();
            HitCache cache;
            synchronized (HIT_CACHES) {
                cache = HIT_CACHES.computeIfAbsent(handler, ignored -> new HitCache(c.hoverMaxCells));
                if (HIT_CACHES.size() > 64) {
                    Iterator<Object> iterator = HIT_CACHES.keySet().iterator();
                    while (iterator.hasNext() && HIT_CACHES.size() > 64) {
                        Object candidate = iterator.next();
                        if (candidate != handler) iterator.remove();
                    }
                }
            }
            if (cache.location != location || cache.iconCount != iconCount
                    || Float.floatToIntBits(cache.factor) != Float.floatToIntBits(factor)) {
                cache.reset(location, iconCount, factor);
            }
            long minInterval = c.hoverMaxHz <= 0 ? 0L : 1_000_000_000L / c.hoverMaxHz;
            if (cache.hasLast && minInterval > 0L && now - cache.lastAtNanos < minInterval) {
                HOVER_HITS.increment();
                return cache.lastResult;
            }
            if (c.hoverCellPixels > 0f && c.hoverCellTtlMs > 0 && c.hoverMaxCells > 0
                    && Float.isFinite(factor) && factor > 0f) {
                int cellX = safeFloorToInt((x * factor) / c.hoverCellPixels);
                int cellY = safeFloorToInt((y * factor) / c.hoverCellPixels);
                long key = cellKey(cellX, cellY) ^ ((long) Float.floatToIntBits(radius) * 0x9E3779B97F4A7C15L);
                CellHit hit = cache.cells.get(key);
                if (hit != null && now - hit.atNanos <= c.hoverCellTtlMs * 1_000_000L) {
                    cache.setLast(hit.value, now);
                    HOVER_HITS.increment();
                    return hit.value;
                }
                SectorEntityToken value = invokeOriginalHitTest(handler, x, y, radius);
                cache.cells.put(key, new CellHit(value, now));
                cache.setLast(value, now);
                HOVER_MISSES.increment();
                return value;
            }
            SectorEntityToken value = invokeOriginalHitTest(handler, x, y, radius);
            cache.setLast(value, now);
            HOVER_MISSES.increment();
            return value;
        } catch (Throwable ex) {
            return invokeOriginalHitTest(handler, x, y, radius);
        }
    }

    private static SectorEntityToken invokeOriginalHitTest(Object handler, float x, float y, float radius) {
        try {
            HitAccess access = HitAccess.ACCESS.get(handler.getClass());
            return (SectorEntityToken) access.original.invoke(handler, x, y, radius);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to invoke original Starsector map hit-test", ex);
        }
    }

    private static final class HitAccess {
        static final ClassValue<HitAccess> ACCESS = new ClassValue<>() {
            @Override
            protected HitAccess computeValue(Class<?> type) {
                try {
                    Field mapField = null;
                    for (Field field : type.getDeclaredFields()) {
                        if (field.getType().getName().equals("com.fs.starfarer.coreui.A.H")) {
                            mapField = field;
                            break;
                        }
                    }
                    if (mapField == null) throw new NoSuchFieldException("H map field");
                    mapField.setAccessible(true);
                    Class<?> mapType = mapField.getType();
                    Method getFactor = mapType.getMethod("getFactor");
                    Method getIcons = mapType.getMethod("getIcons");
                    Method getLocation = mapType.getMethod("getLocation");
                    Method original = type.getDeclaredMethod("smo$originalHitTest", float.class, float.class, float.class);
                    original.setAccessible(true);
                    return new HitAccess(mapField, getFactor, getIcons, getLocation, original);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        final Field mapField;
        final Method getFactor;
        final Method getIcons;
        final Method getLocation;
        final Method original;

        HitAccess(Field mapField, Method getFactor, Method getIcons, Method getLocation, Method original) {
            this.mapField = mapField;
            this.getFactor = getFactor;
            this.getIcons = getIcons;
            this.getLocation = getLocation;
            this.original = original;
        }
    }

    private static final class HitCache {
        Object location;
        int iconCount = Integer.MIN_VALUE;
        float factor = Float.NaN;
        boolean hasLast;
        SectorEntityToken lastResult;
        long lastAtNanos;
        final LinkedHashMap<Long, CellHit> cells;

        HitCache(int maxEntries) {
            final int limit = Math.max(1, maxEntries);
            cells = new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CellHit> eldest) {
                    return size() > limit;
                }
            };
        }

        void reset(Object newLocation, int newIconCount, float newFactor) {
            location = newLocation;
            iconCount = newIconCount;
            factor = newFactor;
            hasLast = false;
            lastResult = null;
            lastAtNanos = 0L;
            cells.clear();
        }

        void setLast(SectorEntityToken result, long now) {
            hasLast = true;
            lastResult = result;
            lastAtNanos = now;
        }
    }

    private static final class CellHit {
        final SectorEntityToken value;
        final long atNanos;
        CellHit(SectorEntityToken value, long atNanos) {
            this.value = value;
            this.atNanos = atNanos;
        }
    }

    // ---------------------------------------------------------------------
    // Map-open preprocessing caches
    // ---------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void updateSystemNebulasCached(Object owner, List systemNebulas,
                                                  List constellationLabels, List nebulaStars) {
        OptimizerConfig c = config;
        if (c == null || !c.systemNebulaCache) {
            invokeOriginalSystemNebulaBuilder(owner);
            return;
        }
        systemNebulas.clear();
        constellationLabels.clear();
        nebulaStars.clear();
        try {
            SectorAPI sector = Global.getSector();
            if (sector == null) {
                invokeOriginalSystemNebulaBuilder(owner);
                return;
            }
            NebulaCache cache = getOrBuildNebulaCache(sector, c);
            SystemNebulaAccess access = SystemNebulaAccess.ACCESS.get(owner.getClass());
            for (int i = 0; i < cache.systemNebulaSystems.size(); i++) {
                StarSystemAPI system = cache.systemNebulaSystems.get(i);
                Object terrain = access.createNebula.invoke(null, system);
                if (!(terrain instanceof SectorEntityToken token)) {
                    throw new IllegalStateException("createNebula returned " + terrain);
                }
                token.getCustomData().put("system", system);
                token.getCustomData().put("seed", Long.valueOf(cache.systemNebulaSeeds[i]));
                systemNebulas.add(terrain);
            }
            for (Constellation constellation : cache.constellations) {
                Object label = access.createConstellationLabel.invoke(null, constellation);
                if (!(label instanceof SectorEntityToken token)) {
                    throw new IllegalStateException("createConstellationLabel returned " + label);
                }
                token.getCustomData().put("constellationLabel", Boolean.TRUE);
                token.getCustomData().put("constellation", constellation);
                constellationLabels.add(label);
            }
            nebulaStars.addAll(cache.nebulaStars);
            NEBULA_HITS.increment();
        } catch (Throwable ex) {
            systemNebulas.clear();
            constellationLabels.clear();
            nebulaStars.clear();
            OptimizerLog.warn("System-nebula metadata cache failed; using vanilla builder: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            invokeOriginalSystemNebulaBuilder(owner);
        }
    }

    private static NebulaCache getOrBuildNebulaCache(SectorAPI sector, OptimizerConfig c) {
        int systems = sector.getStarSystems().size();
        long now = System.nanoTime();
        NebulaCache current = nebulaCache;
        if (current != null && current.sector.get() == sector && current.systemCount == systems
                && (c.systemNebulaMaxAgeMs <= 0
                    || now - current.createdAtNanos <= c.systemNebulaMaxAgeMs * 1_000_000L)) {
            return current;
        }
        synchronized (MapOptimizerHooks.class) {
            current = nebulaCache;
            if (current != null && current.sector.get() == sector && current.systemCount == systems
                    && (c.systemNebulaMaxAgeMs <= 0
                        || now - current.createdAtNanos <= c.systemNebulaMaxAgeMs * 1_000_000L)) {
                return current;
            }
            NebulaCache built = buildNebulaMetadata(sector, now);
            nebulaCache = built;
            NEBULA_MISSES.increment();
            return built;
        }
    }

    private static NebulaCache buildNebulaMetadata(SectorAPI sector, long now) {
        long baseSeed = 0L;
        String seedString = sector.getSeedString();
        if (seedString != null && seedString.length() > 3) {
            baseSeed = Long.parseLong(seedString.substring(3));
        }
        Random random = Misc.getRandom(baseSeed, 10);
        ArrayList<StarSystemAPI> nebulaSystems = new ArrayList<>();
        ArrayList<Long> seeds = new ArrayList<>();
        HashSet<Constellation> constellationSet = new HashSet<>();
        ArrayList<SectorEntityToken> nebulaStars = new ArrayList<>();
        for (StarSystemAPI system : sector.getStarSystems()) {
            if (Boolean.TRUE.equals(system.hasSystemwideNebula()) && system.getAge() != null) {
                nebulaSystems.add(system);
                seeds.add(random.nextLong());
            }
            if (system.isInConstellation()) {
                Constellation constellation = system.getConstellation();
                if (constellation != null) constellationSet.add(constellation);
            }
            if (system.getType() == StarSystemGenerator.StarSystemType.NEBULA
                    && system.getStar() != null
                    && system.getStar().getSpec().isNebulaCenter()) {
                nebulaStars.add(system.getStar());
            }
        }
        long[] seedArray = new long[seeds.size()];
        for (int i = 0; i < seeds.size(); i++) seedArray[i] = seeds.get(i);
        return new NebulaCache(new WeakReference<>(sector), sector.getStarSystems().size(), now,
                nebulaSystems, seedArray, new ArrayList<>(constellationSet), nebulaStars);
    }

    private static void invokeOriginalSystemNebulaBuilder(Object owner) {
        try {
            SystemNebulaAccess.ACCESS.get(owner.getClass()).original.invoke(owner);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to invoke original Starsector system-nebula builder", ex);
        }
    }

    private static final class SystemNebulaAccess {
        static final ClassValue<SystemNebulaAccess> ACCESS = new ClassValue<>() {
            @Override
            protected SystemNebulaAccess computeValue(Class<?> type) {
                try {
                    Method original = type.getDeclaredMethod("smo$originalUpdateSystemNebulas");
                    original.setAccessible(true);
                    Method createNebula = type.getMethod("createNebula", StarSystemAPI.class);
                    Method createConstellationLabel = type.getMethod("createConstellationLabel", Constellation.class);
                    return new SystemNebulaAccess(original, createNebula, createConstellationLabel);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        final Method original;
        final Method createNebula;
        final Method createConstellationLabel;

        SystemNebulaAccess(Method original, Method createNebula, Method createConstellationLabel) {
            this.original = original;
            this.createNebula = createNebula;
            this.createConstellationLabel = createConstellationLabel;
        }
    }

    public static void forceClearSampleCacheThrottled(Object plugin) {
        if (plugin == null) return;
        OptimizerConfig c = config;
        if (c == null || !c.sampleCacheClearThrottle || c.sampleCacheClearMinIntervalMs <= 0) {
            ((BaseTiledTerrain) plugin).forceClearSampleCache();
            return;
        }
        long now = System.nanoTime();
        long interval = c.sampleCacheClearMinIntervalMs * 1_000_000L;
        synchronized (SAMPLE_CLEAR_TIMES) {
            Long previous = SAMPLE_CLEAR_TIMES.get(plugin);
            if (previous != null && now - previous < interval) {
                SAMPLE_CLEAR_SKIPS.increment();
                return;
            }
            SAMPLE_CLEAR_TIMES.put(plugin, now);
        }
        ((BaseTiledTerrain) plugin).forceClearSampleCache();
    }

    public static float gridSpacing() {
        OptimizerConfig c = config;
        if (c == null || !c.gridLineCap || c.gridMaxLinesPerAxis <= 0) {
            return c == null ? 2000f : c.gridBaseSpacing;
        }
        try {
            float width = Math.abs(Global.getSettings().getFloat("sectorWidth"));
            float height = Math.abs(Global.getSettings().getFloat("sectorHeight"));
            if (Float.floatToIntBits(width) == Float.floatToIntBits(cachedGridWidth)
                    && Float.floatToIntBits(height) == Float.floatToIntBits(cachedGridHeight)
                    && cachedGridSpacing > 0f) {
                return cachedGridSpacing;
            }
            float base = c.gridBaseSpacing;
            float maxDimension = Math.max(width, height);
            float lines = maxDimension / base;
            int multiplier = Math.max(1, (int) Math.ceil(lines / c.gridMaxLinesPerAxis));
            float spacing = base * multiplier;
            cachedGridWidth = width;
            cachedGridHeight = height;
            cachedGridSpacing = spacing;
            if (spacing > base) {
                OptimizerLog.info("Map grid LOD: sector=" + width + "x" + height
                        + ", spacing=" + spacing + " (base=" + base + ")");
            }
            return spacing;
        } catch (Throwable ex) {
            return c.gridBaseSpacing;
        }
    }

    private static final class NebulaCache {
        final WeakReference<SectorAPI> sector;
        final int systemCount;
        final long createdAtNanos;
        final List<StarSystemAPI> systemNebulaSystems;
        final long[] systemNebulaSeeds;
        final List<Constellation> constellations;
        final List<SectorEntityToken> nebulaStars;

        NebulaCache(WeakReference<SectorAPI> sector, int systemCount, long createdAtNanos,
                    List<StarSystemAPI> systemNebulaSystems, long[] systemNebulaSeeds,
                    List<Constellation> constellations, List<SectorEntityToken> nebulaStars) {
            this.sector = sector;
            this.systemCount = systemCount;
            this.createdAtNanos = createdAtNanos;
            this.systemNebulaSystems = systemNebulaSystems;
            this.systemNebulaSeeds = systemNebulaSeeds;
            this.constellations = constellations;
            this.nebulaStars = nebulaStars;
        }
    }

    // ---------------------------------------------------------------------
    // Closed-map campaign bookkeeping.
    //
    // CampaignEngine.advance() calls readdChangeListeners() every frame. The
    // vanilla method walks hyperspace and every star system and only writes the
    // same ObjectRepository listener reference again. Creation/removal APIs
    // already update listeners immediately, so retain the public vanilla method
    // unchanged and throttle only the call site in advance(). Structural changes
    // trigger an immediate refresh; a periodic audit covers direct list mutation
    // by mods.
    // ---------------------------------------------------------------------

    public static void readdChangeListenersIfNeeded(Object engine) {
        if (engine == null) return;
        OptimizerConfig c = config;
        if (c == null || !c.campaignListenerThrottle) {
            invokeReaddChangeListeners(engine);
            return;
        }

        List<Object> systems;
        Object hyperspace;
        int size;
        Object first;
        Object last;
        long now;
        boolean refresh;
        try {
            CampaignListenerAccess access = CampaignListenerAccess.ACCESS.get(engine.getClass());
            @SuppressWarnings("unchecked")
            List<Object> currentSystems = (List<Object>) access.starSystems.get(engine);
            systems = currentSystems;
            hyperspace = access.hyperspace.get(engine);
            size = systems == null ? -1 : systems.size();
            first = size > 0 ? systems.get(0) : null;
            last = size > 0 ? systems.get(size - 1) : null;
            now = System.nanoTime();
            long auditNs = c.campaignListenerAuditMs * 1_000_000L;

            synchronized (CAMPAIGN_LISTENER_STATES) {
                CampaignListenerState state = CAMPAIGN_LISTENER_STATES.get(engine);
                refresh = state == null
                        || state.systems != systems
                        || state.hyperspace != hyperspace
                        || state.systemCount != size
                        || state.firstSystem != first
                        || state.lastSystem != last
                        || (auditNs > 0L && now - state.refreshedAtNanos >= auditNs);
            }
        } catch (Throwable ex) {
            // Compatibility is more important than this optimization. If any mod
            // changes the expected engine shape, execute the exact vanilla method.
            invokeReaddChangeListeners(engine);
            return;
        }

        if (!refresh) {
            CAMPAIGN_LISTENER_SKIPS.increment();
            return;
        }

        // Keep exceptions from the vanilla method exact: do not catch and retry it.
        invokeReaddChangeListeners(engine);
        try {
            synchronized (CAMPAIGN_LISTENER_STATES) {
                CampaignListenerState state = CAMPAIGN_LISTENER_STATES.computeIfAbsent(
                        engine, ignored -> new CampaignListenerState());
                state.systems = systems;
                state.hyperspace = hyperspace;
                state.systemCount = size;
                state.firstSystem = first;
                state.lastSystem = last;
                state.refreshedAtNanos = now;
            }
        } catch (Throwable ignored) {
            // A failed bookkeeping update merely causes another vanilla refresh on
            // the next frame; it must not affect campaign execution.
        }
        CAMPAIGN_LISTENER_RUNS.increment();
    }

    private static void invokeReaddChangeListeners(Object engine) {
        try {
            CampaignListenerAccess.ACCESS.get(engine.getClass()).readdChangeListeners.invoke(engine);
        } catch (InvocationTargetException ex) {
            throwUnchecked(ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to invoke CampaignEngine.readdChangeListeners()", ex);
        }
    }

    private static final class CampaignListenerAccess {
        static final ClassValue<CampaignListenerAccess> ACCESS = new ClassValue<>() {
            @Override
            protected CampaignListenerAccess computeValue(Class<?> type) {
                try {
                    Field systems = findField(type, "starSystems");
                    Field hyperspace = findField(type, "hyperspace");
                    Method refresh = type.getMethod("readdChangeListeners");
                    systems.setAccessible(true);
                    hyperspace.setAccessible(true);
                    refresh.setAccessible(true);
                    return new CampaignListenerAccess(systems, hyperspace, refresh);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Unexpected CampaignEngine layout: " + type.getName(), ex);
                }
            }
        };

        final Field starSystems;
        final Field hyperspace;
        final Method readdChangeListeners;

        CampaignListenerAccess(Field starSystems, Field hyperspace, Method readdChangeListeners) {
            this.starSystems = starSystems;
            this.hyperspace = hyperspace;
            this.readdChangeListeners = readdChangeListeners;
        }
    }

    private static final class CampaignListenerState {
        Object systems;
        Object hyperspace;
        Object firstSystem;
        Object lastSystem;
        int systemCount;
        long refreshedAtNanos;
    }

    // ---------------------------------------------------------------------
    // Route/pathfinding indexes.
    //
    // O0Oo.getNextStep() and getLastLegDistance() preserve all of their original
    // filtering, distance and tie-breaking code. These hooks only replace the
    // candidate source: an O(all hyperspace jump points) list becomes the same
    // ordered subset whose first destination belongs to the requested system.
    // Cache misses and malformed/custom data fall back to the vanilla full list.
    // ---------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List routeJumpPointsForSystem(LocationAPI hyperspace, Object system) {
        if (hyperspace == null) return null;
        List live = hyperspace.getJumpPoints();
        OptimizerConfig c = config;
        if (c == null || !c.routeJumpPointIndex || system == null || live == null || live.size() < 32) {
            ROUTE_JUMP_INDEX_FALLBACKS.increment();
            return live;
        }

        try {
            long now = System.nanoTime();
            RouteJumpIndex index;
            synchronized (ROUTE_JUMP_INDEXES) {
                index = ROUTE_JUMP_INDEXES.get(hyperspace);
                if (index == null || !index.matches(live, now, c.routeIndexTtlMs)) {
                    index = RouteJumpIndex.build(live, now);
                    ROUTE_JUMP_INDEXES.put(hyperspace, index);
                    ROUTE_JUMP_INDEX_BUILDS.increment();
                }
            }
            if (index.failed) {
                ROUTE_JUMP_INDEX_FALLBACKS.increment();
                return live;
            }
            List candidates = index.bySystem.get(system);
            if (candidates == null) {
                // A full-list fallback on a miss prevents stale indexes from ever
                // hiding a newly retargeted modded jump point.
                ROUTE_JUMP_INDEX_FALLBACKS.increment();
                return live;
            }
            ROUTE_JUMP_INDEX_HITS.increment();
            return candidates;
        } catch (Throwable ex) {
            ROUTE_JUMP_INDEX_FALLBACKS.increment();
            return live;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List routeSystemsForAnchor(Object engine, Object anchor) {
        if (engine == null || anchor == null) return vanillaStarSystems(engine);
        OptimizerConfig c = config;
        if (c == null || !c.routeJumpPointIndex) return vanillaStarSystems(engine);

        try {
            CampaignRouteAccess access = CampaignRouteAccess.ACCESS.get(engine.getClass());
            List systems = (List) access.starSystems.get(engine);
            if (systems == null || systems.size() < 32) return vanillaStarSystems(engine);

            long now = System.nanoTime();
            RouteSystemIndex index;
            synchronized (ROUTE_SYSTEM_INDEXES) {
                index = ROUTE_SYSTEM_INDEXES.get(engine);
                if (index == null || !index.matches(systems, now, c.routeIndexTtlMs)) {
                    index = RouteSystemIndex.build(systems, now);
                    ROUTE_SYSTEM_INDEXES.put(engine, index);
                    ROUTE_SYSTEM_INDEX_BUILDS.increment();
                }
            }
            Object system = index.byAnchor.get(anchor);
            if (system == null) {
                // Preserve immediate visibility of unusual direct mutations.
                ROUTE_SYSTEM_INDEX_FALLBACKS.increment();
                return vanillaStarSystems(engine);
            }
            ROUTE_SYSTEM_INDEX_HITS.increment();
            return Collections.singletonList(system);
        } catch (Throwable ex) {
            ROUTE_SYSTEM_INDEX_FALLBACKS.increment();
            return vanillaStarSystems(engine);
        }
    }

    @SuppressWarnings("rawtypes")
    private static List vanillaStarSystems(Object engine) {
        if (engine == null) return Collections.emptyList();
        try {
            return (List) CampaignRouteAccess.ACCESS.get(engine.getClass()).getStarSystems.invoke(engine);
        } catch (InvocationTargetException ex) {
            throwUnchecked(ex.getCause());
            return Collections.emptyList(); // unreachable
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to invoke CampaignEngine.getStarSystems()", ex);
        }
    }

    private static final class CampaignRouteAccess {
        static final ClassValue<CampaignRouteAccess> ACCESS = new ClassValue<>() {
            @Override
            protected CampaignRouteAccess computeValue(Class<?> type) {
                try {
                    Field systems = findField(type, "starSystems");
                    Method getter = type.getMethod("getStarSystems");
                    systems.setAccessible(true);
                    getter.setAccessible(true);
                    return new CampaignRouteAccess(systems, getter);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Unexpected CampaignEngine route layout: " + type.getName(), ex);
                }
            }
        };

        final Field starSystems;
        final Method getStarSystems;

        CampaignRouteAccess(Field starSystems, Method getStarSystems) {
            this.starSystems = starSystems;
            this.getStarSystems = getStarSystems;
        }
    }

    private static final class RouteJumpIndex {
        final int size;
        final Object first;
        final Object last;
        final long builtAtNanos;
        final IdentityHashMap<Object, List<SectorEntityToken>> bySystem;
        final boolean failed;

        RouteJumpIndex(int size, Object first, Object last, long builtAtNanos,
                       IdentityHashMap<Object, List<SectorEntityToken>> bySystem, boolean failed) {
            this.size = size;
            this.first = first;
            this.last = last;
            this.builtAtNanos = builtAtNanos;
            this.bySystem = bySystem;
            this.failed = failed;
        }

        boolean matches(List<?> live, long now, int ttlMs) {
            int currentSize = live.size();
            Object currentFirst = currentSize > 0 ? live.get(0) : null;
            Object currentLast = currentSize > 0 ? live.get(currentSize - 1) : null;
            long ttlNs = ttlMs * 1_000_000L;
            return size == currentSize && first == currentFirst && last == currentLast
                    && (ttlNs <= 0L || now - builtAtNanos < ttlNs);
        }

        @SuppressWarnings("rawtypes")
        static RouteJumpIndex build(List live, long now) {
            int size = live.size();
            Object first = size > 0 ? live.get(0) : null;
            Object last = size > 0 ? live.get(size - 1) : null;
            IdentityHashMap<Object, List<SectorEntityToken>> bySystem = new IdentityHashMap<>();
            try {
                for (int i = 0; i < size; i++) {
                    Object raw = live.get(i);
                    if (!(raw instanceof JumpPointAPI jumpPoint)) {
                        return new RouteJumpIndex(size, first, last, now, bySystem, true);
                    }
                    List<JumpPointAPI.JumpDestination> destinations = jumpPoint.getDestinations();
                    if (destinations == null || destinations.isEmpty()) continue;
                    JumpPointAPI.JumpDestination firstDestination = destinations.get(0);
                    if (firstDestination == null || firstDestination.getDestination() == null) {
                        return new RouteJumpIndex(size, first, last, now, bySystem, true);
                    }
                    SectorEntityToken destination = firstDestination.getDestination();
                    LocationAPI location = destination.getContainingLocation();
                    if (location == null) continue;
                    bySystem.computeIfAbsent(location, ignored -> new ArrayList<>(2))
                            .add((SectorEntityToken) raw);
                }
                return new RouteJumpIndex(size, first, last, now, bySystem, false);
            } catch (Throwable ex) {
                return new RouteJumpIndex(size, first, last, now, bySystem, true);
            }
        }
    }

    private static final class RouteSystemIndex {
        final int size;
        final Object first;
        final Object last;
        final long builtAtNanos;
        final IdentityHashMap<Object, Object> byAnchor;

        RouteSystemIndex(int size, Object first, Object last, long builtAtNanos,
                         IdentityHashMap<Object, Object> byAnchor) {
            this.size = size;
            this.first = first;
            this.last = last;
            this.builtAtNanos = builtAtNanos;
            this.byAnchor = byAnchor;
        }

        boolean matches(List<?> live, long now, int ttlMs) {
            int currentSize = live.size();
            Object currentFirst = currentSize > 0 ? live.get(0) : null;
            Object currentLast = currentSize > 0 ? live.get(currentSize - 1) : null;
            long ttlNs = ttlMs * 1_000_000L;
            return size == currentSize && first == currentFirst && last == currentLast
                    && (ttlNs <= 0L || now - builtAtNanos < ttlNs);
        }

        @SuppressWarnings("rawtypes")
        static RouteSystemIndex build(List systems, long now) {
            int size = systems.size();
            Object first = size > 0 ? systems.get(0) : null;
            Object last = size > 0 ? systems.get(size - 1) : null;
            IdentityHashMap<Object, Object> byAnchor = new IdentityHashMap<>(Math.max(32, size * 2));
            for (int i = 0; i < size; i++) {
                Object raw = systems.get(i);
                if (raw instanceof StarSystemAPI system) {
                    SectorEntityToken anchor = system.getHyperspaceAnchor();
                    if (anchor != null) byAnchor.put(anchor, raw);
                }
            }
            return new RouteSystemIndex(size, first, last, now, byAnchor);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void throwUnchecked(Throwable cause) {
        if (cause instanceof RuntimeException runtime) throw runtime;
        if (cause instanceof Error error) throw error;
        throw new RuntimeException(cause);
    }

    // ---------------------------------------------------------------------
    // Helpers and stats
    // ---------------------------------------------------------------------

    private static int floorCell(float value, float cellSize) {
        return safeFloorToInt(value / cellSize);
    }

    private static int safeFloorToInt(float value) {
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) Math.floor(value);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static void statsLoop() {
        OptimizerConfig c = config;
        int seconds = c == null ? 0 : c.statsLogIntervalSeconds;
        if (seconds <= 0) return;
        while (true) {
            try {
                Thread.sleep(seconds * 1000L);
                OptimizerLog.info("stats: retainCalls=" + RETAIN_CALLS.sumThenReset()
                        + ", avoidedContainsUpperBound=" + RETAIN_UPPER_BOUND.sumThenReset()
                        + ", retainKeysScanned=" + RETAIN_KEYS.sumThenReset()
                        + ", retainKeysRemoved=" + RETAIN_REMOVED.sumThenReset()
                        + ", retainEqualityFallbacks=" + RETAIN_EQUALITY_FALLBACKS.sumThenReset()
                        + ", hoverHits=" + HOVER_HITS.sumThenReset()
                        + ", hoverMisses=" + HOVER_MISSES.sumThenReset()
                        + ", mapLocationHits=" + MAP_LOCATION_HITS.sumThenReset()
                        + ", mapLocationMisses=" + MAP_LOCATION_MISSES.sumThenReset()
                        + ", arrowHits=" + ARROW_HITS.sumThenReset()
                        + ", arrowMisses=" + ARROW_MISSES.sumThenReset()
                        + ", intelIndexHits=" + INTEL_INDEX_HITS.sumThenReset()
                        + ", intelIndexBuilds=" + INTEL_INDEX_BUILDS.sumThenReset()
                        + ", labelCandidates=" + LABEL_CANDIDATES.sumThenReset()
                        + "/" + LABEL_TOTAL.sumThenReset()
                        + ", nebulaHits=" + NEBULA_HITS.sumThenReset()
                        + ", nebulaMisses=" + NEBULA_MISSES.sumThenReset()
                        + ", sampleClearSkips=" + SAMPLE_CLEAR_SKIPS.sumThenReset()
                        + ", campaignListenerRuns=" + CAMPAIGN_LISTENER_RUNS.sumThenReset()
                        + ", campaignListenerSkips=" + CAMPAIGN_LISTENER_SKIPS.sumThenReset()
                        + ", routeJumpIndexHits=" + ROUTE_JUMP_INDEX_HITS.sumThenReset()
                        + ", routeJumpIndexBuilds=" + ROUTE_JUMP_INDEX_BUILDS.sumThenReset()
                        + ", routeJumpIndexFallbacks=" + ROUTE_JUMP_INDEX_FALLBACKS.sumThenReset()
                        + ", routeSystemIndexHits=" + ROUTE_SYSTEM_INDEX_HITS.sumThenReset()
                        + ", routeSystemIndexBuilds=" + ROUTE_SYSTEM_INDEX_BUILDS.sumThenReset()
                        + ", routeSystemIndexFallbacks=" + ROUTE_SYSTEM_INDEX_FALLBACKS.sumThenReset());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ex) {
                OptimizerLog.error("Stats logger failed", ex);
            }
        }
    }

    private static final class Scratch {
        final ArrayList<Object> entityList = new ArrayList<>(1024);
        final ArrayList<Object> hitList = new ArrayList<>(1024);
        final HashSet<Object> classSet = new HashSet<>(16);
        final IdentityHashMap<Object, Boolean> identityMembership = new IdentityHashMap<>(2048);
        final HashSet<Object> equalityMembership = new HashSet<>(2048);
        final HashSet<Object> containsSet = new HashSet<>(512);
        Collection<?> containsSource;
        int containsSourceSize = -1;
        final ArrayList<Object> labelCandidates = new ArrayList<>(256);
        final Vector2f hitPoint = new Vector2f();
        final Vector2f[] arrowVectors = new Vector2f[] {
                new Vector2f(), new Vector2f(), new Vector2f(), new Vector2f(),
                new Vector2f(), new Vector2f(), new Vector2f(), new Vector2f()
        };
        int arrowVectorIndex;
    }
}
