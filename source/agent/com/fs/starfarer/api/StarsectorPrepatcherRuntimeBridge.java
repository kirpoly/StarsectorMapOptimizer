package com.fs.starfarer.api;

import com.starsector.prepatcher.agent.PrepatcherConfig;
import com.starsector.prepatcher.agent.PrepatcherLog;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Loader-neutral initialization and native AoTD ABI boundary. */
public final class StarsectorPrepatcherRuntimeBridge {
    public static final int AOTD_CONTRACT_ABI = 1;
    public static final long AOTD_CAPABILITY_CONTRACT_HANDSHAKE = 1L;
    public static final long AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS = 1L << 1;
    public static final long AOTD_CAPABILITY_NATIVE_MUTATION_BOUNDARIES = 1L << 2;
    public static final long AOTD_CAPABILITY_MARKET_GENERATIONS = 1L << 3;
    public static final long AOTD_CAPABILITY_CLEAN_DEFICIT_SEMANTICS = 1L << 4;
    public static final long AOTD_CAPABILITY_AUTHORITATIVE_MARKET_STATE = 1L << 5;
    public static final long AOTD_CAPABILITY_PURE_PRICE_OFFLOAD = 1L << 6;
    public static final long AOTD_CAPABILITY_GLOBAL_PHASE_COORDINATION = 1L << 7;

    private static final String AOTD_MOD_ID = "aotd_theory_of_toolbox";
    private static final long AOTD_SUPPORTED_CAPABILITIES =
            AOTD_CAPABILITY_CONTRACT_HANDSHAKE
                    | AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS
                    | AOTD_CAPABILITY_NATIVE_MUTATION_BOUNDARIES
                    | AOTD_CAPABILITY_MARKET_GENERATIONS
                    | AOTD_CAPABILITY_CLEAN_DEFICIT_SEMANTICS
                    | AOTD_CAPABILITY_AUTHORITATIVE_MARKET_STATE
                    | AOTD_CAPABILITY_PURE_PRICE_OFFLOAD
                    | AOTD_CAPABILITY_GLOBAL_PHASE_COORDINATION;
    private static final Object AOTD_CONTRACT_LOCK = new Object();
    private static final Object AOTD_MARKET_LOCK = new Object();
    private static final ReferenceQueue<Object> AOTD_MARKET_QUEUE = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, AoTDMarketState> AOTD_MARKET_STATES =
            new HashMap<>();
    private static final AtomicLong AOTD_DELIVERY_SEQUENCE = new AtomicLong();
    private static final AtomicLong AOTD_MUTATION_SEQUENCE = new AtomicLong();
    private static final AtomicLong AOTD_GLOBAL_SEQUENCE = new AtomicLong();
    private static final Object AOTD_GLOBAL_LOCK = new Object();
    private static long aotdGlobalToken;
    private static int aotdGlobalDepth;
    private static int aotdGlobalReasonMask;
    private static long aotdGlobalGeneration;
    private static final AtomicLong AOTD_DELIVERY_LISTENER_FAILURES = new AtomicLong();

    private static volatile boolean aotdContractRegistered;
    private static volatile String aotdForkVersion = "";
    private static volatile long aotdDeclaredCapabilities;
    private static volatile long aotdNegotiatedCapabilities;
    private static volatile Consumer<Object> aotdDeliveryListener;
    private static volatile String aotdDeliveryListenerStatus = "unregistered";
    private static volatile BiFunction<Object, Object, Object> aotdDeficitResolver;
    private static volatile boolean aotdCleanDeficitConfigured;

    private StarsectorPrepatcherRuntimeBridge() {}

    public static void configure(Object rawConfig, Path modRoot) {
        if (!(rawConfig instanceof PrepatcherConfig config)) {
            String actual = rawConfig == null ? "null" : rawConfig.getClass().getName();
            throw new IllegalArgumentException("Unexpected prepatcher configuration type: " + actual);
        }
        aotdCleanDeficitConfigured = config.aotdCleanDeficitPath;
        StarsectorPrepatcherHooks.configure(config, modRoot);
        StarsectorPrepatcherCoreWorldsRuntime.configure(config);
        StarsectorPrepatcherHyperspaceHooks.configure(config);
        StarsectorPrepatcherPresentationHooks.configure(config);
    }

    /** Stage-2 compatibility overload. */
    public static long registerAoTDForkContract(
            String modId, int abiVersion, String forkVersion, long declaredCapabilities) {
        return registerAoTDForkContract(
                modId, abiVersion, forkVersion, declaredCapabilities, null, null);
    }

    /** Stage-7 compatibility overload. */
    public static long registerAoTDForkContract(
            String modId, int abiVersion, String forkVersion, long declaredCapabilities,
            Consumer<Object> deliveryListener) {
        return registerAoTDForkContract(modId, abiVersion, forkVersion,
                declaredCapabilities, deliveryListener, null);
    }

    /** Registers the fork contract and its loader-neutral callbacks. */
    public static long registerAoTDForkContract(
            String modId, int abiVersion, String forkVersion, long declaredCapabilities,
            Consumer<Object> deliveryListener,
            BiFunction<Object, Object, Object> deficitResolver) {
        if (!AOTD_MOD_ID.equals(modId) || abiVersion != AOTD_CONTRACT_ABI
                || forkVersion == null || forkVersion.isBlank()
                || (declaredCapabilities & AOTD_CAPABILITY_CONTRACT_HANDSHAKE) == 0L) {
            System.setProperty("starsector.prepatcher.aotdContract", "rejected");
            PrepatcherLog.warn("Rejected AoTD fork contract: modId=" + modId
                    + ", abi=" + abiVersion + ", fork=" + forkVersion
                    + ", declared=0x" + Long.toHexString(declaredCapabilities));
            return 0L;
        }
        long negotiated = declaredCapabilities & AOTD_SUPPORTED_CAPABILITIES;
        if ((negotiated & AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS) != 0L
                && deliveryListener == null) {
            negotiated &= ~AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS;
        }
        if ((negotiated & AOTD_CAPABILITY_CLEAN_DEFICIT_SEMANTICS) != 0L
                && (!aotdCleanDeficitConfigured || deficitResolver == null)) {
            negotiated &= ~AOTD_CAPABILITY_CLEAN_DEFICIT_SEMANTICS;
        }
        synchronized (AOTD_CONTRACT_LOCK) {
            if (aotdContractRegistered
                    && (!aotdForkVersion.equals(forkVersion)
                    || aotdDeclaredCapabilities != declaredCapabilities)) {
                System.setProperty("starsector.prepatcher.aotdContract",
                        "conflicting-registration");
                PrepatcherLog.warn("Conflicting AoTD fork contract registration: existing="
                        + aotdForkVersion + "/0x"
                        + Long.toHexString(aotdDeclaredCapabilities)
                        + ", incoming=" + forkVersion + "/0x"
                        + Long.toHexString(declaredCapabilities));
                return 0L;
            }
            aotdContractRegistered = true;
            aotdForkVersion = forkVersion;
            aotdDeclaredCapabilities = declaredCapabilities;
            aotdNegotiatedCapabilities = negotiated;
            aotdDeliveryListener = deliveryListener;
            aotdDeliveryListenerStatus = deliveryListener == null
                    ? "not-negotiated" : "active";
            aotdDeficitResolver = deficitResolver;
        }
        System.setProperty("starsector.prepatcher.aotdContract", "active");
        System.setProperty("starsector.prepatcher.aotdForkVersion", forkVersion);
        System.setProperty("starsector.prepatcher.aotdDeclaredCapabilities",
                "0x" + Long.toHexString(declaredCapabilities));
        System.setProperty("starsector.prepatcher.aotdNegotiatedCapabilities",
                "0x" + Long.toHexString(negotiated));
        PrepatcherLog.info("AoTD fork contract active: abi=" + abiVersion
                + ", fork=" + forkVersion
                + ", declared=0x" + Long.toHexString(declaredCapabilities)
                + ", negotiated=0x" + Long.toHexString(negotiated));
        return negotiated;
    }

    public static long getAoTDNegotiatedCapabilities() {
        return aotdNegotiatedCapabilities;
    }

    public static String getAoTDForkContractStatus() {
        if (!aotdContractRegistered) return "unregistered";
        int markets;
        synchronized (AOTD_MARKET_LOCK) {
            expungeAoTDMarketsLocked();
            markets = AOTD_MARKET_STATES.size();
        }
        return "active; abi=" + AOTD_CONTRACT_ABI
                + "; fork=" + aotdForkVersion
                + "; declared=0x" + Long.toHexString(aotdDeclaredCapabilities)
                + "; negotiated=0x" + Long.toHexString(aotdNegotiatedCapabilities)
                + "; trackedMarkets=" + markets
                + "; deficitResolver=" + (aotdDeficitResolver != null)
                + "; deliveryListener=" + aotdDeliveryListenerStatus
                + "; callbackFailures=" + AOTD_DELIVERY_LISTENER_FAILURES.get();
    }

    /** Called by the clean BaseIndustry wrapper. Null means use preserved vanilla code. */
    public static Object resolveAoTDMaxDeficit(Object industry, String[] commodityIds) {
        if ((aotdNegotiatedCapabilities & AOTD_CAPABILITY_CLEAN_DEFICIT_SEMANTICS) == 0L) {
            return null;
        }
        BiFunction<Object, Object, Object> resolver = aotdDeficitResolver;
        if (resolver == null) {
            throw new IllegalStateException("AoTD clean deficit capability is active without a resolver");
        }
        Object result = resolver.apply(industry, commodityIds);
        if (result == null) {
            throw new IllegalStateException("AoTD deficit resolver returned null");
        }
        return result;
    }

    /** Called by Hooks only after a real Market.advance callback returned. */
    public static void publishAoTDMarketTimeDelivered(
            Object market, float deliveredAmount, int origin) {
        if (market == null || !aotdContractRegistered) return;
        Consumer<Object> listener;
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, true);
            state.deliveredGeneration = nextPositive(state.deliveredGeneration);
            state.lastDeliverySequence = AOTD_DELIVERY_SEQUENCE.incrementAndGet();
            state.lastDeliveredAmount = deliveredAmount;
            state.lastDeliveryOrigin = origin;
            listener = aotdDeliveryListener;
        }
        if ((aotdNegotiatedCapabilities & AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS) == 0L
                || listener == null) return;
        try {
            listener.accept(market);
        } catch (LinkageError failure) {
            long failures = AOTD_DELIVERY_LISTENER_FAILURES.incrementAndGet();
            boolean disabled = false;
            synchronized (AOTD_CONTRACT_LOCK) {
                if (aotdDeliveryListener == listener) {
                    aotdDeliveryListener = null;
                    aotdNegotiatedCapabilities &= ~AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS;
                    aotdDeliveryListenerStatus = "disabled-linkage:"
                            + failure.getClass().getName();
                    disabled = true;
                }
            }
            if (disabled) {
                System.setProperty("starsector.prepatcher.aotdContract",
                        "delivery-listener-disabled");
                System.setProperty("starsector.prepatcher.aotdNegotiatedCapabilities",
                        "0x" + Long.toHexString(aotdNegotiatedCapabilities));
                PrepatcherLog.error("AoTD delivery listener disabled after linkage failure (#"
                        + failures + "); native delivery events capability was removed for "
                        + "this session. Rebuild the complete SchedulerBridge class family.",
                        failure);
            }
        } catch (Throwable failure) {
            long failures = AOTD_DELIVERY_LISTENER_FAILURES.incrementAndGet();
            if (failures <= 4L || (failures & (failures - 1L)) == 0L) {
                PrepatcherLog.warn("AoTD delivery listener failed open (#" + failures
                        + "): " + failure);
            }
        }
    }

    public static long getAoTDMarketDeliveredGeneration(Object market) {
        return readMarketLong(market, 0);
    }

    public static long getAoTDMarketLastDeliverySequence(Object market) {
        return readMarketLong(market, 1);
    }

    public static float getAoTDMarketLastDeliveredAmount(Object market) {
        if (market == null) return 0f;
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, false);
            return state == null ? 0f : state.lastDeliveredAmount;
        }
    }

    public static long getAoTDMarketStructuralGeneration(Object market) {
        return readMarketLong(market, 2);
    }

    private static long readMarketLong(Object market, int field) {
        if (market == null) return 0L;
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, false);
            if (state == null) return 0L;
            return switch (field) {
                case 0 -> state.deliveredGeneration;
                case 1 -> state.lastDeliverySequence;
                case 2 -> state.structuralGeneration;
                default -> 0L;
            };
        }
    }

    public static long beforeAoTDMarketMutation(Object market, int reasonMask) {
        requireAoTDCapability(AOTD_CAPABILITY_NATIVE_MUTATION_BOUNDARIES);
        if (market == null) return 0L;

        // A nested source boundary shares the already-flushed outer temporal cut.
        // Avoid re-entering the scheduler replay path for every helper mutation.
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, true);
            if (state.mutationDepth > 0) {
                state.mutationReasonMask |= reasonMask;
                state.mutationDepth++;
                return state.mutationToken;
            }
        }

        // Exact replay belongs to Hooks because it owns scheduler state. The
        // state is checked again afterwards because replay may invoke campaign
        // callbacks that open and close their own source boundary.
        StarsectorPrepatcherHooks.flushPendingMarketBeforeAoTDMutation(market);
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, true);
            if (state.mutationDepth == 0) {
                state.mutationToken = AOTD_MUTATION_SEQUENCE.incrementAndGet();
                state.mutationReasonMask = reasonMask;
            } else {
                state.mutationReasonMask |= reasonMask;
            }
            state.mutationDepth++;
            return state.mutationToken;
        }
    }

    public static void afterAoTDMarketMutation(
            long token, Object market, int dirtyMask, long sourceGeneration) {
        requireAoTDCapability(AOTD_CAPABILITY_NATIVE_MUTATION_BOUNDARIES);
        if (market == null || token == 0L) return;
        synchronized (AOTD_MARKET_LOCK) {
            AoTDMarketState state = stateForLocked(market, false);
            if (state == null || state.mutationDepth <= 0
                    || state.mutationToken != token) {
                PrepatcherLog.warn("Unbalanced AoTD market mutation boundary: token="
                        + token + ", market=" + market);
                return;
            }
            state.mutationDirtyMask |= dirtyMask;
            state.lastSourceGeneration = sourceGeneration;
            state.mutationDepth--;
            if (state.mutationDepth == 0) {
                state.structuralGeneration = nextPositive(state.structuralGeneration);
                state.lastCommittedDirtyMask = state.mutationDirtyMask;
                state.mutationDirtyMask = 0;
                state.mutationReasonMask = 0;
                state.mutationToken = 0L;
            }
        }
    }

    public static long beforeAoTDGlobalBoundary(int reasonMask, boolean hardFlush) {
        requireAoTDCapability(AOTD_CAPABILITY_GLOBAL_PHASE_COORDINATION);
        synchronized (AOTD_GLOBAL_LOCK) {
            if (aotdGlobalDepth > 0) {
                aotdGlobalDepth++;
                aotdGlobalReasonMask |= reasonMask;
                return aotdGlobalToken;
            }
        }
        if (hardFlush) {
            StarsectorPrepatcherHooks.flushMarketSchedulerBeforeSave();
        }
        synchronized (AOTD_GLOBAL_LOCK) {
            if (aotdGlobalDepth == 0) {
                aotdGlobalToken = AOTD_GLOBAL_SEQUENCE.incrementAndGet();
                aotdGlobalReasonMask = reasonMask;
            } else {
                aotdGlobalReasonMask |= reasonMask;
            }
            aotdGlobalDepth++;
            return aotdGlobalToken;
        }
    }

    public static void afterAoTDGlobalBoundary(long token, long generation) {
        requireAoTDCapability(AOTD_CAPABILITY_GLOBAL_PHASE_COORDINATION);
        synchronized (AOTD_GLOBAL_LOCK) {
            if (token == 0L || token != aotdGlobalToken || aotdGlobalDepth <= 0) {
                PrepatcherLog.warn("Unbalanced AoTD global boundary: token=" + token);
                return;
            }
            aotdGlobalDepth--;
            if (aotdGlobalDepth == 0) {
                aotdGlobalGeneration = Math.max(aotdGlobalGeneration, generation);
                aotdGlobalToken = 0L;
                aotdGlobalReasonMask = 0;
            }
        }
    }

    private static void requireAoTDCapability(long capability) {
        if ((aotdNegotiatedCapabilities & capability) != capability) {
            throw new IllegalStateException(
                    "AoTD capability was not negotiated: 0x" + Long.toHexString(capability));
        }
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    private static AoTDMarketState stateForLocked(Object market, boolean create) {
        expungeAoTDMarketsLocked();
        IdentityWeakReference lookup = new IdentityWeakReference(market);
        AoTDMarketState state = AOTD_MARKET_STATES.get(lookup);
        if (state == null && create) {
            state = new AoTDMarketState();
            AOTD_MARKET_STATES.put(
                    new IdentityWeakReference(market, AOTD_MARKET_QUEUE), state);
        }
        return state;
    }

    private static void expungeAoTDMarketsLocked() {
        IdentityWeakReference reference;
        while ((reference = (IdentityWeakReference) AOTD_MARKET_QUEUE.poll()) != null) {
            AOTD_MARKET_STATES.remove(reference);
        }
    }

    /** Loader-neutral registration endpoint used by the mod call-site transformer. */
    public static void registerDirectMarketCallSite(long siteId, String metadata) {
        StarsectorPrepatcherHooks.registerDirectMarketCallSite(siteId, metadata);
    }

    /** Writes pending-vs-delivered scheduler state without forcing a flush. */
    public static String dumpMarketSchedulerBaseline(String reason) {
        return StarsectorPrepatcherHooks.dumpMarketSchedulerBaseline(reason);
    }

    private static final class AoTDMarketState {
        long deliveredGeneration;
        long lastDeliverySequence;
        float lastDeliveredAmount;
        int lastDeliveryOrigin;
        long structuralGeneration;
        long lastSourceGeneration;
        long mutationToken;
        int mutationDepth;
        int mutationReasonMask;
        int mutationDirtyMask;
        int lastCommittedDirtyMask;
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int identityHash;

        IdentityWeakReference(Object referent) {
            super(referent);
            identityHash = System.identityHashCode(referent);
        }

        IdentityWeakReference(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() { return identityHash; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityWeakReference reference)) return false;
            Object left = get();
            Object right = reference.get();
            return left != null && left == right;
        }
    }
}
