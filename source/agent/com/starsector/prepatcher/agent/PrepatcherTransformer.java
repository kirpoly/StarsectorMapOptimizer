package com.starsector.prepatcher.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.FieldNode;
import jdk.internal.org.objectweb.asm.tree.FrameNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LocalVariableNode;
import jdk.internal.org.objectweb.asm.tree.LookupSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.MultiANewArrayInsnNode;
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.TypeInsnNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicVerifier;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies each optimization only when its local bytecode contract can be proven
 * against the class bytes actually supplied by the JVM. No JAR or class digest is
 * used as a compatibility decision.
 */
public final class PrepatcherTransformer implements ClassFileTransformer {
    private static final String HOOKS =
            "com/fs/starfarer/api/StarsectorPrepatcherHooks";
    private static final String TEMP_MOD_HOOKS =
            "com/fs/starfarer/api/StarsectorPrepatcherTempModHooks";
    static final String H = "com/fs/starfarer/coreui/A/H";
    static final String A = "com/fs/starfarer/coreui/A/A";
    static final String Z = "com/fs/starfarer/coreui/A/Z";
    static final String EVENTS = "com/fs/starfarer/campaign/comms/v2/EventsPanel";
    static final String CAMPAIGN_ENGINE = "com/fs/starfarer/campaign/CampaignEngine";
    static final String COURSE_WIDGET = "com/fs/starfarer/coreui/A/O0Oo";
    static final String BASE_LOCATION = "com/fs/starfarer/campaign/BaseLocation";
    static final String BASE_CAMPAIGN_ENTITY = "com/fs/starfarer/campaign/BaseCampaignEntity";
    static final String MEMORY = "com/fs/starfarer/campaign/rules/Memory";
    static final String ECONOMY = "com/fs/starfarer/campaign/econ/Economy";
    static final String MARKET = "com/fs/starfarer/campaign/econ/Market";
    static final String BASE_INDUSTRY =
            "com/fs/starfarer/api/impl/campaign/econ/impl/BaseIndustry";
    static final String COMMODITY_ON_MARKET =
            "com/fs/starfarer/campaign/econ/CommodityOnMarket";
    private static final String ECONOMY_PERSISTENT_STATE_FIELD =
            "smo$economyMarketsSnapshotState";
    private static final String ECONOMY_PERSISTENT_ACCESSOR =
            "smo$borrowPersistentMarketsSnapshot";
    private static final String MARKET_CONDITIONS_STATE_FIELD =
            "smo$marketConditionsSnapshotState";
    private static final String MARKET_INDUSTRIES_STATE_FIELD =
            "smo$marketIndustriesSnapshotState";
    private static final String MARKET_CONDITIONS_ACCESSOR =
            "smo$borrowPersistentConditionsSnapshot";
    private static final String MARKET_INDUSTRIES_ACCESSOR =
            "smo$borrowPersistentIndustriesSnapshot";
    private static final String PERSISTENT_STATE_DESC = "Ljava/lang/Object;";
    static final String MUTABLE_STAT =
            "com/fs/starfarer/api/combat/MutableStat";
    static final String MUTABLE_STAT_WITH_TEMP_MODS =
            "com/fs/starfarer/api/combat/MutableStatWithTempMods";
    static final String INTEL_MANAGER = "com/fs/starfarer/campaign/comms/v2/IntelManager";
    static final String SHIP = "com/fs/starfarer/combat/entities/Ship";
    static final String DYNAMIC_PARTICLE_GROUP = "com/fs/graphics/particle/DynamicParticleGroup";
    static final String LOADING_UTILS = "com/fs/starfarer/loading/LoadingUtils";
    static final String SCRIPT_STORE_RUNNER = "com/fs/starfarer/loading/scripts/ScriptStore$3";
    static final String RULES = "com/fs/starfarer/campaign/rules/Rules";
    static final String SPEC_STORE = "com/fs/starfarer/loading/SpecStore";
    static final String TEXTURE_LOADER = "com/fs/graphics/TextureLoader";
    static final String SOUND = "sound/Sound";
    static final String PROGRESS_INPUT = "com/fs/starfarer/util/oOoOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO";
    static final String PROGRESS_OUTPUT = "com/fs/starfarer/util/do";
    static final String CAMPAIGN_GAME_MANAGER = "com/fs/starfarer/campaign/save/CampaignGameManager";
    static final Set<String> TARGET_CLASSES = Set.of(H, A, Z, EVENTS, CAMPAIGN_ENGINE,
            COURSE_WIDGET, BASE_LOCATION, BASE_CAMPAIGN_ENTITY, MEMORY, ECONOMY,
            MARKET, BASE_INDUSTRY, COMMODITY_ON_MARKET, MUTABLE_STAT, MUTABLE_STAT_WITH_TEMP_MODS,
            INTEL_MANAGER, SHIP,
            DYNAMIC_PARTICLE_GROUP, LOADING_UTILS,
            SCRIPT_STORE_RUNNER, RULES, SPEC_STORE, TEXTURE_LOADER, SOUND,
            PROGRESS_INPUT, PROGRESS_OUTPUT, CAMPAIGN_GAME_MANAGER,
            HyperspacePatches.BASE_TILED, HyperspacePatches.HYPER_TERRAIN,
            HyperspacePatches.AUTOMATON, HyperspacePatches.STARFIELD);
    private static final String ENTITY_TOKEN_DESC = "Lcom/fs/starfarer/api/campaign/SectorEntityToken;";
    private static final String MAP_API_DESC = "Lcom/fs/starfarer/api/ui/SectorMapAPI;";
    private static final String INTEL_DESC = "Lcom/fs/starfarer/api/campaign/comm/IntelInfoPlugin;";
    private static final String LIST_DESC = "Ljava/util/List;";
    private static final String LINKED_MAP_DESC = "Ljava/util/LinkedHashMap;";
    private static final String STAR_SYSTEM_DESC = "Lcom/fs/starfarer/api/campaign/StarSystemAPI;";
    private static final String LOCATION_DESC = "Lcom/fs/starfarer/api/campaign/LocationAPI;";
    private static final String HYPERSPACE_DESC = "Lcom/fs/starfarer/campaign/Hyperspace;";
    private static final String PATCH_MARKER_PREFIX = "smo$patched$";
    private static final String PATCH_MARKER_DESC = "Ljava/lang/String;";
    // This is a patch-schema identifier, not the release version. Keeping it stable prevents a
    // routine SemVer bump from changing idempotency/ownership semantics.
    private static final String PATCH_MARKER_VALUE_PREFIX = "StarsectorPrepatcher:patch-v1:";
    private final PrepatcherConfig config;
    /**
     * Loader that owns the typed runtime hooks. A null value is used only by
     * the offline structural harness, which transforms byte arrays without
     * defining them in a JVM class loader.
     */
    private final ClassLoader runtimeLoader;
    private final AtomicInteger patchedClasses = new AtomicInteger();
    private final AtomicInteger appliedPatches = new AtomicInteger();
    private final AtomicInteger skippedPatches = new AtomicInteger();
    private final AtomicInteger alreadyPatched = new AtomicInteger();

    public PrepatcherTransformer(PrepatcherConfig config) {
        this(config, null);
    }

    public PrepatcherTransformer(PrepatcherConfig config, ClassLoader runtimeLoader) {
        this.config = config;
        this.runtimeLoader = runtimeLoader;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET_CLASSES.contains(className) || !isTargetEnabled(className)) return null;
        // FR child-loads com.fs.* while keeping javaagents in a sibling loader.
        // Never emit a typed hook call unless caller and hook are owned by the
        // exact same loader; a mismatch would otherwise become a late
        // loader-constraint LinkageError at the first invocation.
        if (runtimeLoader != null && className.startsWith("com/fs/")
                && loader != runtimeLoader) {
            String key = "starsector.prepatcher.patchStatus."
                    + className.replace('/', '.') + ".classLoader";
            if (!"SKIPPED_LOADER".equals(System.getProperty(key))) {
                System.setProperty(key, "SKIPPED_LOADER");
                PrepatcherLog.warn("SKIPPED_LOADER " + className
                        + ": target loader=" + loaderName(loader)
                        + ", runtime loader=" + loaderName(runtimeLoader));
            }
            return null;
        }

        TransformState state = new TransformState(className, classfileBuffer);
        switch (className) {
            case H -> {
                apply(state, "retainAll", config.retainAll, this::patchRetainAll);
                apply(state, "scratchCollections", config.scratchCollections, this::patchHScratchCollections);
                apply(state, "labelSpatialCandidates", config.labelSpatialCandidates, this::patchLabelCandidates);
                apply(state, "intelEntityIndex", config.intelEntityIndex, this::patchIntelEntityIndex);
                apply(state, "systemNebulaCache", config.systemNebulaCache, this::patchSystemNebulaCache);
                apply(state, "sampleCacheClearThrottle", config.sampleCacheClearThrottle,
                        this::patchSampleCacheClearThrottle);
                apply(state, "gridLineCap", config.gridLineCap, this::patchGridLineCap);
            }
            case A -> {
                apply(state, "scratchCollections", config.scratchCollections, this::patchAScratchCollections);
                apply(state, "intelCallbackCache", config.intelCallbackCache,
                        node -> patchIntelMapLocationCalls(node, 1));
                // Wrapper last so earlier patches are applied to the preserved original body.
                apply(state, "hoverHitTestCache", config.hoverHitTestCache, this::patchHoverHitTestCache);
            }
            case Z -> {
                apply(state, "intelCallbackCache", config.intelCallbackCache, this::patchArrowCallbackCache);
                apply(state, "arrowVectorPool", config.arrowVectorPool, this::patchArrowVectorPool);
            }
            case EVENTS -> {
                apply(state, "intelCallbackCache", config.intelCallbackCache,
                        node -> patchIntelMapLocationCalls(node, 7));
                apply(state, "intelFastContains", config.intelFastContains, this::patchIntelFastContains);
                apply(state, "intelExistingIconLookup", config.intelExistingIconLookup,
                        this::patchExistingIntelIconLookup);
            }
            case CAMPAIGN_ENGINE -> {
                apply(state, "campaignCacheLifecycle", campaignCacheLifecycleEnabled(),
                        this::patchCampaignCacheLifecycle);
                apply(state, "campaignListenerThrottle",
                        config.campaignListenerThrottle, this::patchCampaignListenerThrottle);
            }
            case COURSE_WIDGET -> apply(state, "routeJumpPointIndex",
                    config.routeJumpPointIndex, this::patchRouteJumpPointIndex);
            case BASE_LOCATION -> apply(state, "campaignSnapshotReuse",
                    config.campaignSnapshotReuse, this::patchCampaignSnapshotReuse);
            case BASE_CAMPAIGN_ENTITY -> {
                apply(state, "entityScriptSnapshotReuse",
                        config.entityScriptSnapshotReuse, this::patchEntityScriptSnapshotReuse);
                apply(state, "planetConditionMarketAdvanceBridge",
                        config.planetConditionMarketScheduler || config.directMarketObservation,
                        this::patchPlanetConditionMarketAdvanceBridge);
            }
            case MEMORY -> apply(state, "emptyMemoryAdvanceFastPath",
                    config.emptyMemoryAdvanceFastPath, this::patchEmptyMemoryAdvanceFastPath);
            case ECONOMY -> {
                apply(state, "economyPersistentSnapshots", config.economyPersistentSnapshots,
                        this::patchEconomyPersistentSnapshots);
                apply(state, "economyLocationCache", config.economyLocationCache,
                        this::patchEconomyLocationCache);
                apply(state, "economySnapshotReuse",
                        config.economySnapshotReuse && !config.economyPersistentSnapshots,
                        this::patchEconomySnapshots);
                apply(state, "remoteMarketScheduler", config.remoteMarketScheduler,
                        this::patchRemoteMarketScheduler);
                apply(state, "planetConditionMarketFrameClock",
                        config.planetConditionMarketScheduler && !config.remoteMarketScheduler,
                        this::patchPlanetConditionMarketFrameClock);
            }
            case MARKET -> {
                apply(state, "economyPersistentSnapshots", config.economyPersistentSnapshots,
                        this::patchMarketPersistentSnapshots);
                apply(state, "economySnapshotReuse",
                        config.economySnapshotReuse && !config.economyPersistentSnapshots,
                        this::patchMarketSnapshots);
                apply(state, "commodityTemporalFastPath", config.commodityTemporalFastPath,
                        this::patchMarketCommodityTemporalFastPath);
                apply(state, "directMarketObservation", config.directMarketObservation,
                        this::patchDirectMarketObservationEntry);
            }
            case BASE_INDUSTRY -> apply(state, "marketNoOpCallbacks",
                    config.marketNoOpCallbacks && config.marketNoOpIndustryAuditFrames > 0,
                    this::patchBaseIndustryDormantFastPath);
            case COMMODITY_ON_MARKET -> apply(state, "commodityEventModDirtyCache",
                    config.commodityEventModDirtyCache,
                    this::patchCommodityEventModDirtyCache);
            case MUTABLE_STAT -> apply(state, "commodityTemporalBinding",
                    config.commodityTemporalFastPath, this::patchCommodityTemporalBaseBinding);
            case MUTABLE_STAT_WITH_TEMP_MODS -> apply(state, "tempModExpiryScheduler",
                    config.tempModExpiryScheduler || config.commodityTemporalFastPath,
                    this::patchTempModExpiryScheduler);
            case INTEL_MANAGER -> apply(state, "commRelaySystemIndex", config.commRelaySystemIndex,
                    this::patchCommRelaySystemIndex);
            case SHIP -> apply(state, "shipAdvanceScratch", config.shipAdvanceScratch,
                    this::patchShipAdvanceScratch);
            case DYNAMIC_PARTICLE_GROUP -> apply(state, "particleCleanup", config.particleCleanup,
                    this::patchDynamicParticleCleanup);
            case LOADING_UTILS -> {
                apply(state, "loadingTextReader", config.loadingTextReader, this::patchLoadingTextReader);
                apply(state, "startupLogAggregation", config.startupLogAggregation,
                        this::patchLoadingUtilsStartupLogs);
            }
            case SCRIPT_STORE_RUNNER -> apply(state, "startupLogAggregation",
                    config.startupLogAggregation, this::patchScriptStoreStartupLogs);
            case RULES -> {
                apply(state, "startupLogAggregation", config.startupLogAggregation,
                        this::patchRulesStartupLogs);
                apply(state, "rulesLiteralParser", config.rulesLiteralParser,
                        this::patchRulesLiteralParser);
            }
            case SPEC_STORE -> apply(state, "startupLogAggregation",
                    config.startupLogAggregation, this::patchSpecStoreStartupLogs);
            case TEXTURE_LOADER -> apply(state, "startupLogAggregation",
                    config.startupLogAggregation, this::patchTextureStartupLogs);
            case SOUND -> apply(state, "startupLogAggregation",
                    config.startupLogAggregation, this::patchSoundStartupLogs);
            case PROGRESS_INPUT -> apply(state, "saveLoadProgressThrottle",
                    config.saveLoadProgressThrottle,
                    node -> patchSaveLoadProgressThrottle(node, "super"));
            case PROGRESS_OUTPUT -> apply(state, "saveLoadProgressThrottle",
                    config.saveLoadProgressThrottle,
                    node -> patchSaveLoadProgressThrottle(node, "o00000"));
            case CAMPAIGN_GAME_MANAGER -> {
                apply(state, "saveOutputBufferDedup",
                        config.saveOutputBufferDedup, this::patchSaveOutputBufferDedup);
                apply(state, "remoteMarketScheduler",
                        config.remoteMarketScheduler || config.planetConditionMarketScheduler,
                        this::patchRemoteMarketSaveFlush);
            }
            case HyperspacePatches.BASE_TILED -> {
                applyHyperspace(state, "hyperspaceCulling", config.hyperspaceCulling,
                        "culling", 2, HyperspacePatches::patchCull,
                        HyperspacePatches::countPatchedCull);
                applyHyperspace(state, "hyperspaceYClamp", config.hyperspaceYClamp,
                        "yClamp", 2, HyperspacePatches::patchClamp,
                        HyperspacePatches::countPatchedClamp);
                applyHyperspace(state, "terrainRandomReuse", config.terrainRandomReuse,
                        "random", 3, HyperspacePatches::patchRandom,
                        HyperspacePatches::countPatchedRandom);
            }
            case HyperspacePatches.HYPER_TERRAIN -> {
                applyHyperspace(state, "skipNoOpTerrainLayer", config.skipNoOpTerrainLayer,
                        "noOpLayer", 1, HyperspacePatches::patchLayers,
                        HyperspacePatches::countPatchedLayers);
                applyHyperspace(state, "terrainRandomReuse", config.terrainRandomReuse,
                        "random", 1, HyperspacePatches::patchRandom,
                        HyperspacePatches::countPatchedRandom);
                applyHyperspace(state, "automatonInternalReads", config.automatonBufferReuse,
                        "internalCells", 2, HyperspacePatches::patchAutomatonInternalReads,
                        HyperspacePatches::countPatchedAutomatonInternalReads);
            }
            case HyperspacePatches.AUTOMATON -> applyHyperspace(state,
                    "automatonBufferReuse", config.automatonBufferReuse,
                    "automatonBuffers", 1, HyperspacePatches::patchAutomaton,
                    HyperspacePatches::countPatchedAutomaton);
            case HyperspacePatches.STARFIELD -> applyHyperspace(state,
                    "starfieldCleanupBuffers", config.starfieldCleanupBuffers,
                    "cleanup", 1, HyperspacePatches::patchCleanup,
                    HyperspacePatches::countPatchedCleanup);
            default -> { return null; }
        }

        if (!state.changed) {
            PrepatcherLog.info("Structural scan completed for " + className
                    + "; no new compatible patch was applied.");
            return null;
        }

        int count = patchedClasses.incrementAndGet();
        System.setProperty("starsector.prepatcher.patchedClasses", Integer.toString(count));
        PrepatcherLog.info("Patched " + className + " structurally: "
                + String.join(", ", state.appliedDetails) + ", bytes "
                + classfileBuffer.length + " -> " + state.bytes.length);
        return state.bytes;
    }

    private void apply(TransformState state, String patchId, boolean enabled, PatchAction action) {
        if (!enabled) return;
        boolean markerPresent = false;
        try {
            ClassNode node = readClass(state.bytes);
            Set<String> publicApi = publicApi(node);
            markerPresent = hasPatchMarker(node, patchId);
            PatchReport report = action.apply(node);
            if (report.total == 0) {
                throw mismatch("patch reported no changed sites");
            }
            if (markerPresent) {
                throw mismatch("optimizer marker is present but the structural postcondition is not");
            }
            addPatchMarker(node, patchId);
            if (!publicApi.equals(publicApi(node))) {
                throw mismatch("public/protected API surface changed before serialization");
            }

            byte[] candidate = writeClass(node);
            verifyClass(candidate);
            ClassNode reparsed = readClass(candidate);
            if (!publicApi.equals(publicApi(reparsed))) {
                throw mismatch("public/protected API surface changed after serialization");
            }
            verifyPatchPostcondition(action, reparsed, patchId);

            state.bytes = candidate;
            state.changed = true;
            String detail = patchId + "{" + report + "}";
            state.appliedDetails.add(detail);
            int count = appliedPatches.incrementAndGet();
            System.setProperty("starsector.prepatcher.appliedPatches", Integer.toString(count));
            recordStatus(state.className, patchId, "APPLIED");
            PrepatcherLog.info("APPLIED " + patchId + " to " + state.className + ": " + report);
        } catch (AlreadyPatchedException ex) {
            if (!markerPresent) {
                recordStructuralSkip(state.className, patchId,
                        "hook-shaped postcondition exists without this optimizer's structural marker");
            } else {
                int count = alreadyPatched.incrementAndGet();
                System.setProperty("starsector.prepatcher.alreadyPatched", Integer.toString(count));
                recordStatus(state.className, patchId, "ALREADY_APPLIED");
                PrepatcherLog.info("ALREADY_APPLIED " + patchId + " in " + state.className
                        + ": " + ex.getMessage());
            }
        } catch (StructuralMismatchException ex) {
            recordStructuralSkip(state.className, patchId, ex.getMessage());
        } catch (Throwable ex) {
            int count = skippedPatches.incrementAndGet();
            System.setProperty("starsector.prepatcher.skippedPatches", Integer.toString(count));
            recordStatus(state.className, patchId, "SKIPPED_ERROR");
            PrepatcherLog.error("SKIPPED_ERROR " + patchId + " in " + state.className
                    + "; the preceding class bytes remain active.", ex);
        }
    }

    private void applyHyperspace(TransformState state, String patchId, boolean enabled,
                                 String detail, int expected, CountPatchAction action,
                                 CountPatchAction postcondition) {
        apply(state, patchId, enabled, node -> {
            if (hasPatchMarker(node, patchId)) {
                int actual = postcondition.apply(node);
                requireCount(detail + " postcondition sites", actual, expected);
                throw already("structural marker and complete postcondition are present");
            }
            int actual = action.apply(node);
            requireCount(detail + " structural sites", actual, expected);
            PatchReport report = new PatchReport();
            report.add(detail, actual);
            return report;
        });
    }

    private void recordStructuralSkip(String className, String patchId, String reason) {
        int count = skippedPatches.incrementAndGet();
        System.setProperty("starsector.prepatcher.skippedPatches", Integer.toString(count));
        recordStatus(className, patchId, "SKIPPED_STRUCTURAL");
        PrepatcherLog.warn("SKIPPED_STRUCTURAL " + patchId + " in " + className + ": " + reason);
    }

    private static void verifyPatchPostcondition(PatchAction action, ClassNode node,
                                                 String patchId) {
        try {
            PatchReport repeated = action.apply(node);
            throw mismatch(patchId + " remains patchable after serialization: " + repeated);
        } catch (AlreadyPatchedException expected) {
            // The serialized bytes must satisfy the complete idempotency contract.
        }
    }

    private static void recordStatus(String className, String patchId, String status) {
        System.setProperty("starsector.prepatcher.patchStatus."
                + className.replace('/', '.') + "." + patchId, status);
    }

    boolean isTargetEnabled(String className) {
        return switch (className) {
            case H -> config.retainAll || config.scratchCollections || config.labelSpatialCandidates
                    || config.intelEntityIndex || config.systemNebulaCache
                    || config.sampleCacheClearThrottle || config.gridLineCap;
            case A -> config.scratchCollections || config.hoverHitTestCache || config.intelCallbackCache;
            case Z -> config.intelCallbackCache || config.arrowVectorPool;
            case EVENTS -> config.intelCallbackCache || config.intelFastContains
                    || config.intelExistingIconLookup;
            case CAMPAIGN_ENGINE -> campaignCacheLifecycleEnabled() || config.campaignListenerThrottle;
            case COURSE_WIDGET -> config.routeJumpPointIndex;
            case BASE_LOCATION -> config.campaignSnapshotReuse;
            case BASE_CAMPAIGN_ENTITY -> config.entityScriptSnapshotReuse
                    || config.planetConditionMarketScheduler || config.directMarketObservation;
            case MEMORY -> config.emptyMemoryAdvanceFastPath;
            case ECONOMY -> config.economyLocationCache || config.economySnapshotReuse
                    || config.economyPersistentSnapshots || config.remoteMarketScheduler
                    || config.planetConditionMarketScheduler;
            case MARKET -> config.economySnapshotReuse || config.economyPersistentSnapshots
                    || config.commodityTemporalFastPath || config.directMarketObservation;
            case BASE_INDUSTRY -> config.marketNoOpCallbacks
                    && config.marketNoOpIndustryAuditFrames > 0;
            case COMMODITY_ON_MARKET -> config.commodityEventModDirtyCache;
            case MUTABLE_STAT -> config.commodityTemporalFastPath;
            case MUTABLE_STAT_WITH_TEMP_MODS ->
                    config.tempModExpiryScheduler || config.commodityTemporalFastPath;
            case INTEL_MANAGER -> config.commRelaySystemIndex;
            case SHIP -> config.shipAdvanceScratch;
            case DYNAMIC_PARTICLE_GROUP -> config.particleCleanup;
            case LOADING_UTILS -> config.loadingTextReader || config.startupLogAggregation;
            case SCRIPT_STORE_RUNNER, SPEC_STORE, TEXTURE_LOADER, SOUND -> config.startupLogAggregation;
            case RULES -> config.startupLogAggregation || config.rulesLiteralParser;
            case PROGRESS_INPUT, PROGRESS_OUTPUT -> config.saveLoadProgressThrottle;
            case CAMPAIGN_GAME_MANAGER -> config.saveOutputBufferDedup
                    || config.remoteMarketScheduler || config.planetConditionMarketScheduler;
            case HyperspacePatches.BASE_TILED -> config.hyperspaceCulling
                    || config.hyperspaceYClamp || config.terrainRandomReuse;
            case HyperspacePatches.HYPER_TERRAIN -> config.skipNoOpTerrainLayer
                    || config.terrainRandomReuse || config.automatonBufferReuse;
            case HyperspacePatches.AUTOMATON -> config.automatonBufferReuse;
            case HyperspacePatches.STARFIELD -> config.starfieldCleanupBuffers;
            default -> false;
        };
    }

    private boolean campaignCacheLifecycleEnabled() {
        return config.labelSpatialCandidates || config.intelCallbackCache
                || config.intelEntityIndex || config.hoverHitTestCache
                || config.systemNebulaCache || config.sampleCacheClearThrottle
                || config.campaignListenerThrottle || config.routeJumpPointIndex
                || config.economyLocationCache || config.remoteMarketScheduler
                || config.planetConditionMarketScheduler || config.commRelaySystemIndex
                || config.commodityTemporalFastPath;
    }


    // ---------------------------------------------------------------------
    // Startup loading and save/load
    // ---------------------------------------------------------------------

    private PatchReport patchLoadingTextReader(ClassNode node) {
        MethodNode method = requireMethod(node, "super", "(Ljava/io/InputStream;)Ljava/lang/String;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "readUtf8Normalized",
                "(Ljava/io/InputStream;)Ljava/lang/String;");
        int buffers = 0;
        int oneMiB = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof IntInsnNode array && array.getOpcode() == Opcodes.NEWARRAY
                    && array.operand == Opcodes.T_BYTE) buffers++;
            if (insn instanceof LdcInsnNode ldc && Integer.valueOf(1_048_576).equals(ldc.cst)) oneMiB++;
        }
        int stringBuffers = countCalls(method, Opcodes.INVOKESPECIAL,
                "java/lang/StringBuffer", "<init>", "()V");
        int replaceAll = countCalls(method, Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "replaceAll",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        if (hooks == 1 && buffers == 0 && oneMiB == 0 && stringBuffers == 0 && replaceAll == 0) {
            throw already("streaming UTF-8 text reader postcondition matches");
        }
        requireCount("text reader hook", hooks, 0);
        requireCount("text reader byte-array allocation", buffers, 1);
        requireCount("text reader 1 MiB constant", oneMiB, 1);
        requireCount("text reader StringBuffer", stringBuffers, 1);
        requireCount("text reader CR regex", replaceAll, 1);
        requireOriginalLoadingTextReaderShape(method);

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
        if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "readUtf8Normalized", "(Ljava/io/InputStream;)Ljava/lang/String;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;

        PatchReport report = new PatchReport();
        report.add("streamingUtf8Reader", 1);
        return report;
    }

    private PatchReport patchLoadingUtilsStartupLogs(ClassNode node) {
        int jsonSites = countStringConstants(node, "Loading JSON from [");
        int csvSites = countStringConstants(node, "Loading CSV data from [");
        int jsonHooks = countCategoryHooks(node, "startupLogSuppressed", "(I)V", 0);
        int csvHooks = countCategoryHooks(node, "startupLogSuppressed", "(I)V", 1);
        if (jsonSites == 0 && csvSites == 0 && jsonHooks == 2 && csvHooks == 3) {
            throw already("LoadingUtils startup INFO suppression postcondition matches");
        }
        requireCount("LoadingUtils JSON INFO literals", jsonSites, 2);
        requireCount("LoadingUtils CSV INFO literals", csvSites, 3);
        requireCount("LoadingUtils JSON suppression hooks", jsonHooks, 0);
        requireCount("LoadingUtils CSV suppression hooks", csvHooks, 0);
        int json = suppressExactInfoBlocks(node, "Loading JSON from [", 2, 0);
        int csv = suppressExactInfoBlocks(node, "Loading CSV data from [", 3, 1);
        PatchReport report = new PatchReport();
        report.add("jsonInfoBlocks", json);
        report.add("csvInfoBlocks", csv);
        return report;
    }

    private PatchReport patchScriptStoreStartupLogs(ClassNode node) {
        int count = suppressExactInfoBlocks(node,
                "] already loaded (perhaps from jar file, or due to a reference from another class), skipping compilation.",
                1, 2);
        PatchReport report = new PatchReport();
        report.add("alreadyLoadedScriptInfo", count);
        return report;
    }

    private PatchReport patchRulesStartupLogs(ClassNode node) {
        int count = suppressExactInfoBlocks(node, "Loading rule: ", 1, 3);
        PatchReport report = new PatchReport();
        report.add("perRuleInfo", count);
        return report;
    }

    private PatchReport patchSpecStoreStartupLogs(ClassNode node) {
        List<MethodInsnNode> originals = new ArrayList<>();
        for (MethodNode method : node.methods) {
            originals.addAll(calls(method, Opcodes.INVOKEVIRTUAL,
                    "org/apache/log4j/Logger", "info", "(Ljava/lang/Object;)V"));
        }
        int hooks = countCategoryHooks(node, "suppressStartupInfo",
                "(Ljava/lang/Object;Ljava/lang/Object;I)V", 4);
        if (originals.isEmpty() && hooks == 53) {
            throw already("SpecStore INFO suppression postcondition matches");
        }
        requireCount("SpecStore Logger.info sites", originals.size(), 53);
        requireCount("SpecStore suppression hooks", hooks, 0);
        for (MethodInsnNode call : originals) {
            MethodNode owner = findMethod(node, call);
            // The logger receiver and already-built message remain on the stack.
            // Add only the category argument immediately before the replacement call.
            owner.instructions.insertBefore(call, new LdcInsnNode(4));
            call.owner = HOOKS;
            call.name = "suppressStartupInfo";
            call.desc = "(Ljava/lang/Object;Ljava/lang/Object;I)V";
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.itf = false;
        }
        PatchReport report = new PatchReport();
        report.add("specInfoCalls", originals.size());
        return report;
    }

    private PatchReport patchTextureStartupLogs(ClassNode node) {
        int count = suppressExactInfoBlocks(node,
                "Cleaned buffer for texture %s (using reflection)", 1, 5);
        PatchReport report = new PatchReport();
        report.add("textureCleanupInfo", count);
        return report;
    }

    private PatchReport patchSoundStartupLogs(ClassNode node) {
        // sound.Sound is parent-loaded by Faster Rendering and cannot resolve
        // the typed runtime installed in FR's child system loader. Removing
        // this pure INFO block inline preserves the optimization without a
        // cross-loader helper call. The structural marker remains the source
        // of ownership/idempotency truth; only the optional runtime counter is
        // intentionally absent for this one parent-owned target.
        int count = suppressExactInfoBlocks(node, "Loading sound [", 1, 6, false);
        PatchReport report = new PatchReport();
        report.add("soundLoadInfo", count);
        return report;
    }

    private PatchReport patchRulesLiteralParser(ClassNode node) {
        MethodNode method = requireMethod(node, "o00000",
                "(Lcom/fs/starfarer/loading/ResourceLoaderState;)V");
        List<MethodInsnNode> carriageReturns = new ArrayList<>();
        List<MethodInsnNode> newlineToSpace = new ArrayList<>();
        List<MethodInsnNode> newlineSplits = new ArrayList<>();
        List<MethodInsnNode> orSplits = new ArrayList<>();
        List<MethodInsnNode> colonSplits = new ArrayList<>();

        for (MethodInsnNode call : calls(method, Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "replaceAll",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
            AbstractInsnNode replacement = previousMeaningful(call);
            AbstractInsnNode pattern = previousMeaningful(replacement);
            if (isStringLdc(pattern, "\\r") && isStringLdc(replacement, "")) {
                carriageReturns.add(call);
            } else if (isStringLdc(pattern, "\\n") && isStringLdc(replacement, " ")) {
                newlineToSpace.add(call);
            }
        }
        for (MethodInsnNode call : calls(method, Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")) {
            AbstractInsnNode delimiter = previousMeaningful(call);
            if (isStringLdc(delimiter, "\\n")) {
                newlineSplits.add(call);
            } else if (isStringLdc(delimiter, "\nOR\n")) {
                orSplits.add(call);
            } else if (delimiter instanceof MethodInsnNode quote
                    && callMatches(quote, Opcodes.INVOKESTATIC, "java/util/regex/Pattern",
                    "quote", "(Ljava/lang/String;)Ljava/lang/String;")
                    && isStringLdc(previousMeaningful(quote), ":")) {
                colonSplits.add(call);
            }
        }

        int crHooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "removeCarriageReturns", "(Ljava/lang/String;)Ljava/lang/String;");
        int spaceHooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "replaceNewlinesWithSpace", "(Ljava/lang/String;)Ljava/lang/String;");
        int newlineHooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "splitNewlines", "(Ljava/lang/String;)[Ljava/lang/String;");
        int orHooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "splitOrBlocks", "(Ljava/lang/String;)[Ljava/lang/String;");
        int colonHooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "splitColon", "(Ljava/lang/String;)[Ljava/lang/String;");
        if (carriageReturns.isEmpty() && newlineToSpace.isEmpty() && newlineSplits.isEmpty()
                && orSplits.isEmpty() && colonSplits.isEmpty()
                && crHooks == 3 && spaceHooks == 1 && newlineHooks == 3
                && orHooks == 1 && colonHooks == 1) {
            throw already("literal rules parser postcondition matches");
        }
        requireCount("rules CR replacements", carriageReturns.size(), 3);
        requireCount("rules newline-to-space replacements", newlineToSpace.size(), 1);
        requireCount("rules newline splits", newlineSplits.size(), 3);
        requireCount("rules OR splits", orSplits.size(), 1);
        requireCount("rules colon splits", colonSplits.size(), 1);
        requireCount("rules CR hooks", crHooks, 0);
        requireCount("rules newline-to-space hooks", spaceHooks, 0);
        requireCount("rules newline hooks", newlineHooks, 0);
        requireCount("rules OR hooks", orHooks, 0);
        requireCount("rules colon hooks", colonHooks, 0);

        for (MethodInsnNode call : carriageReturns) {
            AbstractInsnNode replacement = previousMeaningful(call);
            AbstractInsnNode pattern = previousMeaningful(replacement);
            method.instructions.remove(pattern);
            method.instructions.remove(replacement);
            makeStatic(call, HOOKS, "removeCarriageReturns",
                    "(Ljava/lang/String;)Ljava/lang/String;");
        }
        for (MethodInsnNode call : newlineToSpace) {
            AbstractInsnNode replacement = previousMeaningful(call);
            AbstractInsnNode pattern = previousMeaningful(replacement);
            method.instructions.remove(pattern);
            method.instructions.remove(replacement);
            makeStatic(call, HOOKS, "replaceNewlinesWithSpace",
                    "(Ljava/lang/String;)Ljava/lang/String;");
        }
        for (MethodInsnNode call : newlineSplits) {
            method.instructions.remove(previousMeaningful(call));
            makeStatic(call, HOOKS, "splitNewlines",
                    "(Ljava/lang/String;)[Ljava/lang/String;");
        }
        for (MethodInsnNode call : orSplits) {
            method.instructions.remove(previousMeaningful(call));
            makeStatic(call, HOOKS, "splitOrBlocks",
                    "(Ljava/lang/String;)[Ljava/lang/String;");
        }
        for (MethodInsnNode call : colonSplits) {
            AbstractInsnNode quote = previousMeaningful(call);
            AbstractInsnNode colon = previousMeaningful(quote);
            method.instructions.remove(colon);
            method.instructions.remove(quote);
            makeStatic(call, HOOKS, "splitColon",
                    "(Ljava/lang/String;)[Ljava/lang/String;");
        }

        PatchReport report = new PatchReport();
        report.add("removeCR", carriageReturns.size());
        report.add("newlineToSpace", newlineToSpace.size());
        report.add("splitNewline", newlineSplits.size());
        report.add("splitOR", orSplits.size());
        report.add("splitColon", colonSplits.size());
        return report;
    }

    private PatchReport patchSaveLoadProgressThrottle(ClassNode node, String methodName) {
        MethodNode method = requireMethod(node, methodName, "(Z)V");
        List<LdcInsnNode> originals = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode ldc && isFloat(ldc, 1f / 60f)) originals.add(ldc);
        }
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "saveLoadProgressMinIntervalSeconds", "()F");
        if (originals.isEmpty() && hooks == 1) {
            throw already("save/load progress throttle postcondition matches");
        }
        requireCount("save/load progress 60 Hz constants", originals.size(), 1);
        requireCount("save/load progress hooks", hooks, 0);
        method.instructions.set(originals.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "saveLoadProgressMinIntervalSeconds", "()F", false));
        PatchReport report = new PatchReport();
        report.add("progressInterval", 1);
        return report;
    }

    private PatchReport patchSaveOutputBufferDedup(ClassNode node) {
        MethodNode method = requireMethod(node, "o00000",
                "(Lcom/fs/starfarer/campaign/CampaignEngine$o;JZ)Ljava/lang/String;");
        List<BufferConstruction> plans = new ArrayList<>();
        for (MethodInsnNode call : calls(method, Opcodes.INVOKESPECIAL,
                "java/io/BufferedOutputStream", "<init>", "(Ljava/io/OutputStream;I)V")) {
            AbstractInsnNode size = previousMeaningful(call);
            AbstractInsnNode stream = previousMeaningful(size);
            AbstractInsnNode duplicate = previousMeaningful(stream);
            AbstractInsnNode allocation = previousMeaningful(duplicate);
            AbstractInsnNode store = nextMeaningful(call);
            if (!(size instanceof LdcInsnNode ldc) || !Integer.valueOf(1_048_576).equals(ldc.cst)) continue;
            if (!(stream instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) continue;
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) continue;
            if (!(allocation instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW
                    || !type.desc.equals("java/io/BufferedOutputStream")) continue;
            if (!(store instanceof VarInsnNode target) || target.getOpcode() != Opcodes.ASTORE
                    || target.var != load.var) continue;
            requireNoControlBoundary(allocation, store, "save output buffer construction");
            plans.add(new BufferConstruction(allocation, store));
        }
        if (plans.isEmpty()) throw already("outer save buffer is already absent");
        requireCount("redundant outer save buffers", plans.size(), 1);
        removeRange(method, plans.get(0).start, plans.get(0).end);
        PatchReport report = new PatchReport();
        report.add("outer1MiBBufferRemoved", 1);
        return report;
    }

    private PatchReport patchRemoteMarketSaveFlush(ClassNode node) {
        MethodNode method = requireMethod(node, "o00000",
                "(Lcom/fs/starfarer/campaign/CampaignEngine$o;JZ)Ljava/lang/String;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "flushRemoteMarketsBeforeSave", "()V");
        if (hooks == 1) {
            AbstractInsnNode first = firstMeaningful(method);
            if (!(first instanceof MethodInsnNode call)
                    || !callMatches(call, Opcodes.INVOKESTATIC, HOOKS,
                    "flushRemoteMarketsBeforeSave", "()V")) {
                throw mismatch("remote-market save flush is not the first save instruction");
            }
            throw already("remote-market save flush postcondition matches");
        }
        requireCount("remote-market save flush hooks", hooks, 0);

        method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "flushRemoteMarketsBeforeSave", "()V", false));
        requireCount("remote-market save flush hooks",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                        "flushRemoteMarketsBeforeSave", "()V"), 1);
        PatchReport report = new PatchReport();
        report.add("pending remote-market time flushed before save", 1);
        return report;
    }

    private static int suppressExactInfoBlocks(ClassNode node, String literal,
                                               int expected, int category) {
        return suppressExactInfoBlocks(node, literal, expected, category, true);
    }

    private static int suppressExactInfoBlocks(ClassNode node, String literal,
                                               int expected, int category,
                                               boolean emitRuntimeCounter) {
        List<LogBlock> plans = new ArrayList<>();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!isStringLdc(insn, literal)) continue;
                MethodInsnNode info = null;
                int meaningful = 0;
                for (AbstractInsnNode cursor = insn; cursor != null && meaningful <= 40;
                     cursor = cursor.getNext()) {
                    if (cursor.getOpcode() >= 0) meaningful++;
                    if (cursor instanceof MethodInsnNode call
                            && callMatches(call, Opcodes.INVOKEVIRTUAL,
                            "org/apache/log4j/Logger", "info", "(Ljava/lang/Object;)V")) {
                        info = call;
                        break;
                    }
                }
                if (info == null) throw mismatch("no Logger.info after startup literal " + literal);
                FieldInsnNode logger = null;
                meaningful = 0;
                for (AbstractInsnNode cursor = insn; cursor != null && meaningful <= 40;
                     cursor = cursor.getPrevious()) {
                    if (cursor.getOpcode() >= 0) meaningful++;
                    if (cursor instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                            && field.desc.equals("Lorg/apache/log4j/Logger;")) {
                        logger = field;
                        break;
                    }
                }
                if (logger == null) throw mismatch("no logger field before startup literal " + literal);
                requireNoControlBoundary(logger, info, "startup INFO block " + literal);
                requirePureStartupInfoBlock(logger, info, literal);
                plans.add(new LogBlock(method, logger, info));
            }
        }
        int hooks = countCategoryHooks(node, "startupLogSuppressed", "(I)V", category);
        int expectedHooks = emitRuntimeCounter ? expected : 0;
        if (plans.isEmpty() && hooks == expectedHooks) {
            throw already("startup INFO suppression postcondition matches for " + literal);
        }
        requireCount("startup INFO blocks for " + literal, plans.size(), expected);
        requireCount("startup suppression hooks for category " + category, hooks, 0);
        for (LogBlock plan : plans) {
            if (emitRuntimeCounter) {
                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(category));
                replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                        "startupLogSuppressed", "(I)V", false));
                plan.method.instructions.insertBefore(plan.start, replacement);
            }
            removeRange(plan.method, plan.start, plan.end);
        }
        return plans.size();
    }

    private static String loaderName(ClassLoader loader) {
        return loader == null ? "<bootstrap>"
                : loader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(loader));
    }

    private static void requireOriginalLoadingTextReaderShape(MethodNode method) {
        requireCount("text reader String(byte[],int,int,String) constructor",
                countCalls(method, Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                        "([BIILjava/lang/String;)V"), 1);
        requireCount("text reader StringBuffer append",
                countCalls(method, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuffer", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuffer;"), 1);
        requireCount("text reader InputStream.read",
                countCalls(method, Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I"), 1);
        requireCount("text reader InputStream.close",
                countCalls(method, Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V"), 3);
        requireCount("text reader StringBuffer.toString",
                countCalls(method, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuffer", "toString",
                        "()Ljava/lang/String;"), 1);
        requireCount("text reader total method calls", countMethodInstructions(method), 9);
        requireCount("text reader field accesses", countFieldInstructions(method), 0);
        requireCount("text reader invokedynamic sites", countOpcode(method, Opcodes.INVOKEDYNAMIC), 0);
        requireCount("text reader try/catch regions", method.tryCatchBlocks.size(), 2);
        requireCount("text reader UTF-8 literals", countStringConstants(method, "UTF-8"), 1);
        requireCount("text reader CR literals", countStringConstants(method, "\\r"), 1);
        requireCount("text reader empty literals", countStringConstants(method, ""), 1);

        int newString = 0;
        int newStringBuffer = 0;
        int otherNew = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW) continue;
            if (type.desc.equals("java/lang/String")) newString++;
            else if (type.desc.equals("java/lang/StringBuffer")) newStringBuffer++;
            else otherNew++;
        }
        requireCount("text reader new String", newString, 1);
        requireCount("text reader new StringBuffer", newStringBuffer, 1);
        requireCount("text reader other object allocations", otherNew, 0);
    }

    /**
     * Whole-block INFO suppression is permitted only for compiler-generated message
     * construction. This makes the patch fail open if another javaagent inserts a
     * getter, callback, probe, or other potentially observable call into the block.
     */
    private static void requirePureStartupInfoBlock(AbstractInsnNode start,
                                                    AbstractInsnNode end,
                                                    String literal) {
        int infoCalls = 0;
        for (AbstractInsnNode cursor = start; cursor != null; cursor = cursor.getNext()) {
            if (cursor instanceof MethodInsnNode call) {
                boolean allowed;
                if (callMatches(call, Opcodes.INVOKEVIRTUAL, "org/apache/log4j/Logger",
                        "info", "(Ljava/lang/Object;)V")) {
                    infoCalls++;
                    allowed = true;
                } else if (call.owner.equals("java/lang/StringBuilder")) {
                    allowed = (call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>"))
                            || (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && (call.name.equals("append") || call.name.equals("toString")));
                } else if (callMatches(call, Opcodes.INVOKESTATIC, "java/lang/String",
                        "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
                    allowed = true;
                } else if (callMatches(call, Opcodes.INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;")) {
                    allowed = true;
                } else {
                    allowed = false;
                }
                if (!allowed) {
                    throw mismatch("startup INFO block " + literal
                            + " contains an observable call " + call.owner + "."
                            + call.name + call.desc);
                }
            } else if (cursor instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD) {
                throw mismatch("startup INFO block " + literal + " writes field "
                        + field.owner + "." + field.name);
            } else if (cursor instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTSTATIC) {
                throw mismatch("startup INFO block " + literal + " writes static field "
                        + field.owner + "." + field.name);
            } else if (cursor.getOpcode() == Opcodes.INVOKEDYNAMIC
                    || cursor.getOpcode() == Opcodes.MONITORENTER
                    || cursor.getOpcode() == Opcodes.MONITOREXIT) {
                throw mismatch("startup INFO block " + literal
                        + " contains unsupported opcode " + cursor.getOpcode());
            }
            if (cursor == end) break;
        }
        requireCount("Logger.info calls in startup block " + literal, infoCalls, 1);
    }

    private static int countStringConstants(MethodNode method, String value) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isStringLdc(insn, value)) count++;
        }
        return count;
    }

    private static int countMethodInstructions(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) count++;
        }
        return count;
    }

    private static int countFieldInstructions(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode) count++;
        }
        return count;
    }

    private static int countStringConstants(ClassNode node, String value) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isStringLdc(insn, value)) count++;
            }
        }
        return count;
    }

    private static int countCategoryHooks(ClassNode node, String name, String desc, int category) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (MethodInsnNode call : calls(method, Opcodes.INVOKESTATIC, HOOKS, name, desc)) {
                if (isIntegerLdc(previousMeaningful(call), category)) count++;
            }
        }
        return count;
    }

    private static MethodNode findMethod(ClassNode node, AbstractInsnNode target) {
        for (MethodNode method : node.methods) {
            if (method.instructions.indexOf(target) >= 0) return method;
        }
        throw mismatch("instruction is not owned by the target class");
    }

    private static boolean isStringLdc(AbstractInsnNode insn, String value) {
        return insn instanceof LdcInsnNode ldc && value.equals(ldc.cst);
    }

    private static boolean isIntegerLdc(AbstractInsnNode insn, int value) {
        if (insn instanceof LdcInsnNode ldc) return Integer.valueOf(value).equals(ldc.cst);
        int opcode = insn == null ? -1 : insn.getOpcode();
        return (value == -1 && opcode == Opcodes.ICONST_M1)
                || (value >= 0 && value <= 5 && opcode == Opcodes.ICONST_0 + value)
                || (insn instanceof IntInsnNode integer && integer.operand == value
                && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH));
    }

    private static void requireNoControlBoundary(AbstractInsnNode start, AbstractInsnNode end,
                                                 String label) {
        for (AbstractInsnNode cursor = start; cursor != null; cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode || cursor instanceof FrameNode
                    || cursor instanceof JumpInsnNode) {
                throw mismatch(label + " contains a control-flow boundary");
            }
            if (cursor == end) return;
        }
        throw mismatch(label + " end is not reachable from start");
    }

    private static void removeRange(MethodNode method, AbstractInsnNode start, AbstractInsnNode end) {
        AbstractInsnNode after = end.getNext();
        for (AbstractInsnNode cursor = start; cursor != after; ) {
            AbstractInsnNode next = cursor.getNext();
            method.instructions.remove(cursor);
            cursor = next;
        }
    }

    // ---------------------------------------------------------------------
    // H: core map renderer
    // ---------------------------------------------------------------------

    private PatchReport patchRetainAll(ClassNode node) {
        MethodNode method = requireMethod(node, "renderStuff", "(FZ)V");
        boolean scopeInstalled = ensureScratchScope(node.name, method, "H.renderStuff");
        String hookDesc = "(Ljava/util/Set;Ljava/util/Collection;Ljava/lang/Object;)Z";
        List<MethodInsnNode> rawCalls = calls(method, Opcodes.INVOKEINTERFACE,
                "java/util/Set", "retainAll", "(Ljava/util/Collection;)Z");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "retainAllFast", hookDesc);
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        List<MethodInsnNode> original = new ArrayList<>();
        for (MethodInsnNode candidate : rawCalls) {
            if (isMapReconciliationSite(node, method, candidate, frames)) original.add(candidate);
        }
        if (scopeInstalled && original.isEmpty() && hooks == 1) {
            throw mismatch("H.renderStuff retainAll hook exists without its required scratch scope");
        }
        requireUnpatchedOrAlready("H.renderStuff semantic retainAll", original.size(), 1, hooks, 1);

        MethodInsnNode call = only(original, "H.renderStuff semantic retainAll");
        MethodInsnNode keySet = requireSourceMethod(receiverSource(method, call, frames),
                Opcodes.INVOKEVIRTUAL, "java/util/LinkedHashMap", "keySet", "()Ljava/util/Set;",
                "retainAll receiver");
        FieldInsnNode iconsField = requireSourceField(receiverSource(method, keySet, frames),
                Opcodes.GETFIELD, node.name, LINKED_MAP_DESC, "icons map receiver");
        requireIconsGetter(node, iconsField.name);

        int entityListLocal = requireLoadedLocal(argumentSource(method, call, frames, 0),
                Opcodes.ALOAD, "retainAll keep collection");
        AbstractInsnNode beforeCall = previousMeaningful(call);
        if (!(beforeCall instanceof VarInsnNode load)
                || load.getOpcode() != Opcodes.ALOAD || load.var != entityListLocal) {
            throw mismatch("retainAll keep collection is not loaded directly from its proven local");
        }
        requireLocalConstructor(method, entityListLocal, "java/util/ArrayList",
                "(Ljava/util/Collection;)V", "render entity list");
        if (nextMeaningful(call) == null || nextMeaningful(call).getOpcode() != Opcodes.POP) {
            throw mismatch("retainAll boolean result is not discarded as expected");
        }

        method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
        makeStatic(call, HOOKS, "retainAllFast", hookDesc);
        requireCount("retainAllFast postcondition",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "retainAllFast", hookDesc), 1);
        requireCount("non-target retainAll preservation", calls(method, Opcodes.INVOKEINTERFACE,
                "java/util/Set", "retainAll", "(Ljava/util/Collection;)Z").size(), rawCalls.size() - 1);

        PatchReport report = new PatchReport();
        report.add("O(K*E) retainAll -> linear reconciliation", 1);
        return report;
    }

    private static boolean isMapReconciliationSite(ClassNode node, MethodNode method,
                                                   MethodInsnNode call, Frame<SourceValue>[] frames) {
        try {
            MethodInsnNode keySet = requireSourceMethod(receiverSource(method, call, frames),
                    Opcodes.INVOKEVIRTUAL, "java/util/LinkedHashMap", "keySet", "()Ljava/util/Set;",
                    "retainAll receiver");
            FieldInsnNode iconsField = requireSourceField(receiverSource(method, keySet, frames),
                    Opcodes.GETFIELD, node.name, LINKED_MAP_DESC, "icons map receiver");
            requireIconsGetter(node, iconsField.name);
            int entityListLocal = requireLoadedLocal(argumentSource(method, call, frames, 0),
                    Opcodes.ALOAD, "retainAll keep collection");
            requireLocalConstructor(method, entityListLocal, "java/util/ArrayList",
                    "(Ljava/util/Collection;)V", "render entity list");
            return nextMeaningful(call) != null && nextMeaningful(call).getOpcode() == Opcodes.POP;
        } catch (StructuralMismatchException ex) {
            return false;
        }
    }

    private PatchReport patchHScratchCollections(ClassNode node) {
        MethodNode method = requireMethod(node, "renderStuff", "(FZ)V");
        boolean scopeInstalled = ensureScratchScope(node.name, method, "H.renderStuff");
        List<AllocationSpec> specs = List.of(
                new AllocationSpec("java/util/ArrayList", "(Ljava/util/Collection;)V",
                        "borrowEntityList", "(Ljava/util/Collection;)Ljava/util/ArrayList;", 1),
                new AllocationSpec("java/util/HashSet", "()V",
                        "borrowClassSet", "()Ljava/util/HashSet;", 1));
        int changed = replaceAllocationGroup(node.name, method, specs, scopeInstalled,
                "H.renderStuff scratch");
        PatchReport report = new PatchReport();
        report.add("render scratch collections", changed);
        return report;
    }

    private PatchReport patchLabelCandidates(ClassNode node) {
        MethodNode method = requireMethod(node, "getTextAlignmentFor",
                "(Lcom/fs/starfarer/coreui/A/ooOO;)Lcom/fs/starfarer/api/ui/Alignment;");
        boolean scopeInstalled = ensureScratchScope(node.name, method, "H.getTextAlignmentFor");
        String hookDesc = "(Ljava/util/LinkedHashMap;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Collection;";
        List<MethodInsnNode> original = calls(method, Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedHashMap", "values", "()Ljava/util/Collection;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "nearbyLabelIcons", hookDesc);
        if (scopeInstalled && original.isEmpty() && hooks == 1) {
            throw mismatch("H.getTextAlignmentFor label hook exists without its required scratch scope");
        }
        requireUnpatchedOrAlready("H.getTextAlignmentFor values", original.size(), 1, hooks, 1);

        MethodInsnNode values = only(original, "H.getTextAlignmentFor values");
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        FieldInsnNode iconsField = requireSourceField(receiverSource(method, values, frames),
                Opcodes.GETFIELD, node.name, LINKED_MAP_DESC, "label icons receiver");
        requireIconsGetter(node, iconsField.name);
        MethodInsnNode iterator = asMethod(nextMeaningful(values), "label values iterator");
        requireCall(iterator, Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator",
                "()Ljava/util/Iterator;", "label values iterator");
        if (countCalls(method, -1, "com/fs/starfarer/coreui/A/ooOO", "int", "()" + ENTITY_TOKEN_DESC) < 1) {
            throw mismatch("label icon SectorEntityToken accessor is absent");
        }

        int targetLocal = argumentLocal(method, 0);
        method.instructions.insertBefore(values, new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.insertBefore(values, new VarInsnNode(Opcodes.ALOAD, targetLocal));
        makeStatic(values, HOOKS, "nearbyLabelIcons", hookDesc);

        PatchReport report = new PatchReport();
        report.add("label spatial candidates", 1);
        return report;
    }

    private PatchReport patchIntelEntityIndex(ClassNode node) {
        String desc = "(" + INTEL_DESC + ")Lcom/fs/starfarer/campaign/CustomCampaignEntity;";
        String renamed = "smo$originalGetIntelIconEntity";
        String hookDesc = "(Ljava/lang/Object;Ljava/util/List;" + INTEL_DESC + ")Ljava/lang/Object;";
        boolean wrapperAlready = methods(node, renamed, desc).size() == 1;
        MethodNode original = requireWrapperSource(node, "getIntelIconEntity", desc, renamed,
                "getIntelIconEntityIndexed", hookDesc);

        if (containsWrite(original)) {
            throw mismatch("getIntelIconEntity has field/static writes and is not a pure lookup");
        }
        requireCount("getIntelIconEntity semantic call count", totalMethodCalls(original), 5);
        Frame<SourceValue>[] frames = sourceFrames(node.name, original);
        Set<String> listFields = new LinkedHashSet<>();
        for (MethodInsnNode iterator : calls(original, Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;")) {
            FieldInsnNode field = optionalSourceField(receiverSource(original, iterator, frames),
                    Opcodes.GETFIELD, node.name, LIST_DESC);
            if (field != null) listFields.add(field.name);
        }
        if (listFields.size() != 1) {
            throw mismatch("expected one iterated Intel-data List field, found " + listFields);
        }
        String listField = listFields.iterator().next();
        requireCount("CustomCampaignEntity.getCustomData", countCalls(original, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/CustomCampaignEntity", "getCustomData", "()Ljava/util/Map;"), 1);
        requireCount("Intel custom-data Map.get", countCalls(original, Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), 1);
        requireCount("INTEL_ICON_DATA_KEY reads", countFields(original, Opcodes.GETSTATIC,
                node.name, "INTEL_ICON_DATA_KEY", "Ljava/lang/String;"), 1);

        int pluginArg = argumentLocal(original, 0);
        int identityComparisons = 0;
        for (AbstractInsnNode insn : original.instructions.toArray()) {
            if (!(insn instanceof JumpInsnNode jump)
                    || (jump.getOpcode() != Opcodes.IF_ACMPEQ && jump.getOpcode() != Opcodes.IF_ACMPNE)) continue;
            Frame<SourceValue> frame = frameBefore(original, jump, frames);
            if (frame.getStackSize() < 2) continue;
            SourceValue left = frame.getStack(frame.getStackSize() - 2);
            SourceValue right = frame.getStack(frame.getStackSize() - 1);
            boolean match = (sourceIsFieldDesc(left, INTEL_DESC) && sourceIsLocal(right, Opcodes.ALOAD, pluginArg))
                    || (sourceIsFieldDesc(right, INTEL_DESC) && sourceIsLocal(left, Opcodes.ALOAD, pluginArg));
            if (match) identityComparisons++;
        }
        requireCount("Intel plugin identity comparison", identityComparisons, 1);
        requireNullReturn(original, "getIntelIconEntity");
        if (wrapperAlready) {
            requireIntelEntityWrapper(node, desc, listField, hookDesc);
            throw already("getIntelIconEntity wrapper, original semantics, and field wiring match");
        }

        int originalAccess = original.access;
        String signature = original.signature;
        String[] exceptions = exceptions(original);
        original.name = renamed;
        makePrivateSynthetic(original);

        MethodNode wrapper = new MethodNode(Opcodes.ASM8, originalAccess, "getIntelIconEntity",
                desc, signature, exceptions);
        moveWrapperMetadata(original, wrapper, "getIntelIconEntity");
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, listField, LIST_DESC));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, argumentLocal(wrapper, 0)));
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "getIntelIconEntityIndexed", hookDesc, false));
        wrapper.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
                "com/fs/starfarer/campaign/CustomCampaignEntity"));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(wrapper);
        requireWrapperPostcondition(node, "getIntelIconEntity", desc, renamed,
                "getIntelIconEntityIndexed", hookDesc);
        requireIntelEntityWrapper(node, desc, listField, hookDesc);

        PatchReport report = new PatchReport();
        report.add("Intel entity identity index", 1);
        return report;
    }

    private PatchReport patchSystemNebulaCache(ClassNode node) {
        String desc = "()V";
        String renamed = "smo$originalUpdateSystemNebulas";
        String hookDesc = "(Ljava/lang/Object;Ljava/util/List;Ljava/util/List;Ljava/util/List;)V";
        boolean wrapperAlready = methods(node, renamed, desc).size() == 1;
        MethodNode original = requireWrapperSource(node, "updateSystemNebulas", desc, renamed,
                "updateSystemNebulasCached", hookDesc);
        Frame<SourceValue>[] frames = sourceFrames(node.name, original);
        requireCount("updateSystemNebulas semantic call count", totalMethodCalls(original), 57);

        String createNebulaDesc = "(" + STAR_SYSTEM_DESC + ")Lcom/fs/starfarer/campaign/CampaignTerrain;";
        String createLabelDesc = "(Lcom/fs/starfarer/api/impl/campaign/procgen/Constellation;)"
                + "Lcom/fs/starfarer/campaign/CustomCampaignEntity;";
        requireMethod(node, "createNebula", createNebulaDesc);
        requireMethod(node, "createConstellationLabel", createLabelDesc);
        int nebulaLocal = requireStoredCallResultLocal(original, Opcodes.INVOKESTATIC,
                node.name, "createNebula", createNebulaDesc, "system nebula result");
        int labelLocal = requireStoredCallResultLocal(original, Opcodes.INVOKESTATIC,
                node.name, "createConstellationLabel", createLabelDesc, "constellation label result");

        String systemNebulas = null;
        String constellationLabels = null;
        String nebulaStars = null;
        List<MethodInsnNode> adds = calls(original, Opcodes.INVOKEINTERFACE,
                "java/util/List", "add", "(Ljava/lang/Object;)Z");
        requireCount("updateSystemNebulas List.add sites", adds.size(), 3);
        for (MethodInsnNode add : adds) {
            FieldInsnNode field = requireSourceField(receiverSource(original, add, frames),
                    Opcodes.GETFIELD, node.name, LIST_DESC, "system-nebula output list");
            SourceValue value = argumentSource(original, add, frames, 0);
            if (sourceIsLocal(value, Opcodes.ALOAD, nebulaLocal)) {
                systemNebulas = uniqueRole(systemNebulas, field.name, "system nebula list");
            } else if (sourceIsLocal(value, Opcodes.ALOAD, labelLocal)) {
                constellationLabels = uniqueRole(constellationLabels, field.name, "constellation label list");
            } else if (hasCallImmediatelyBefore(add, Opcodes.INVOKEINTERFACE,
                    "com/fs/starfarer/api/campaign/StarSystemAPI", "getStar",
                    "()Lcom/fs/starfarer/api/campaign/PlanetAPI;", 4)) {
                nebulaStars = uniqueRole(nebulaStars, field.name, "nebula-star list");
            } else {
                throw mismatch("unable to assign semantic role to List.add in updateSystemNebulas");
            }
        }
        if (systemNebulas == null || constellationLabels == null || nebulaStars == null
                || Set.of(systemNebulas, constellationLabels, nebulaStars).size() != 3) {
            throw mismatch("system-nebula output lists are missing or not distinct");
        }
        for (String field : List.of(systemNebulas, constellationLabels, nebulaStars)) {
            int clears = countCallsWithFieldReceiver(node.name, original, frames,
                    Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", field, LIST_DESC);
            requireCount("clear of system-nebula list " + field, clears, 1);
        }
        if (wrapperAlready) {
            requireSystemNebulaWrapper(node, desc,
                    List.of(systemNebulas, constellationLabels, nebulaStars), hookDesc);
            throw already("updateSystemNebulas wrapper, original semantics, and field wiring match");
        }

        int originalAccess = original.access;
        String signature = original.signature;
        String[] exceptions = exceptions(original);
        original.name = renamed;
        makePrivateSynthetic(original);
        MethodNode wrapper = new MethodNode(Opcodes.ASM8, originalAccess, "updateSystemNebulas",
                desc, signature, exceptions);
        moveWrapperMetadata(original, wrapper, "updateSystemNebulas");
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (String field : List.of(systemNebulas, constellationLabels, nebulaStars)) {
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            wrapper.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, field, LIST_DESC));
        }
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "updateSystemNebulasCached", hookDesc, false));
        wrapper.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(wrapper);
        requireWrapperPostcondition(node, "updateSystemNebulas", desc, renamed,
                "updateSystemNebulasCached", hookDesc);
        requireSystemNebulaWrapper(node, desc,
                List.of(systemNebulas, constellationLabels, nebulaStars), hookDesc);

        PatchReport report = new PatchReport();
        report.add("system-nebula preprocessing cache", 1);
        return report;
    }

    private PatchReport patchSampleCacheClearThrottle(ClassNode node) {
        MethodNode method = requireMethod(node, "<init>", "(Z)V");
        String terrain = "com/fs/starfarer/api/impl/campaign/terrain/HyperspaceTerrainPlugin";
        String hookDesc = "(Ljava/lang/Object;)V";
        List<MethodInsnNode> original = calls(method, Opcodes.INVOKEVIRTUAL,
                terrain, "forceClearSampleCache", "()V");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "forceClearSampleCacheThrottled", hookDesc);
        requireUnpatchedOrAlready("H constructor forceClearSampleCache", original.size(), 1, hooks, 1);

        MethodInsnNode call = only(original, "forceClearSampleCache");
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        int pluginLocal = requireLoadedLocal(receiverSource(method, call, frames),
                Opcodes.ALOAD, "hyperspace terrain plugin");
        MethodInsnNode getter = only(calls(method, Opcodes.INVOKESTATIC,
                "com/fs/starfarer/api/util/Misc", "getHyperspaceTerrainPlugin", "()L" + terrain + ";"),
                "getHyperspaceTerrainPlugin");
        AbstractInsnNode stored = nextMeaningful(getter);
        if (!(stored instanceof VarInsnNode store)
                || store.getOpcode() != Opcodes.ASTORE || store.var != pluginLocal) {
            throw mismatch("terrain plugin receiver is not the value returned by Misc.getHyperspaceTerrainPlugin");
        }
        if (!hasNullGuard(method, store, call, pluginLocal)) {
            throw mismatch("terrain plugin call is not protected by the expected null guard");
        }
        makeStatic(call, HOOKS, "forceClearSampleCacheThrottled", hookDesc);

        PatchReport report = new PatchReport();
        report.add("hyperspace sample-cache clear throttle", 1);
        return report;
    }

    private PatchReport patchGridLineCap(ClassNode node) {
        MethodNode method = requireMethod(node, "null", "(F)V");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "gridSpacing", "()F");
        List<LdcInsnNode> bounds = new ArrayList<>();
        List<LdcInsnNode> step = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode ldc) || !isFloat(ldc, 2000f)) continue;
            AbstractInsnNode n1 = nextMeaningful(ldc);
            AbstractInsnNode n2 = nextMeaningful(n1);
            AbstractInsnNode n3 = nextMeaningful(n2);
            AbstractInsnNode n4 = nextMeaningful(n3);
            AbstractInsnNode prev = previousMeaningful(ldc);
            if (prev != null && prev.getOpcode() == Opcodes.FLOAD
                    && n1 != null && n1.getOpcode() == Opcodes.FDIV
                    && n2 != null && n2.getOpcode() == Opcodes.F2I
                    && n3 != null && n3.getOpcode() == Opcodes.ISTORE) {
                bounds.add(ldc);
            } else if (n1 instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                    && n2 instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(node.name) && field.desc.equals("F")
                    && n3 != null && n3.getOpcode() == Opcodes.FMUL
                    && n4 != null && n4.getOpcode() == Opcodes.FSTORE) {
                step.add(ldc);
            }
        }
        if (bounds.isEmpty() && step.isEmpty() && hooks == 5) {
            throw already("five structurally verified gridSpacing hooks are already present");
        }
        if (hooks != 0) throw mismatch("mixed/foreign gridSpacing hook state: " + hooks);
        requireCount("global-grid bound constants", bounds.size(), 4);
        requireCount("global-grid step constant", step.size(), 1);
        List<MethodInsnNode> starscape = calls(method, Opcodes.INVOKEVIRTUAL,
                node.name, "isStarscapeMode", "()Z");
        requireCount("grid starscape guard", starscape.size(), 1);
        int guardIndex = method.instructions.indexOf(starscape.get(0));
        for (LdcInsnNode ldc : concat(bounds, step)) {
            if (method.instructions.indexOf(ldc) <= guardIndex) {
                throw mismatch("a selected grid constant is not dominated by the starscape branch marker");
            }
        }
        for (LdcInsnNode ldc : concat(bounds, step)) {
            method.instructions.set(ldc, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "gridSpacing", "()F", false));
        }
        requireCount("gridSpacing postcondition",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "gridSpacing", "()F"), 5);

        PatchReport report = new PatchReport();
        report.add("large-sector grid line cap", 5);
        return report;
    }

    // ---------------------------------------------------------------------
    // A/Z/EventsPanel: hit tests and Intel map
    // ---------------------------------------------------------------------

    private PatchReport patchAScratchCollections(ClassNode node) {
        String desc = "(FFF)" + ENTITY_TOKEN_DESC;
        List<MethodNode> preserved = methods(node, "smo$originalHitTest", desc);
        MethodNode method = preserved.isEmpty()
                ? requireMethod(node, "OO0000", desc)
                : only(preserved, "preserved hit-test method");
        boolean scopeInstalled = ensureScratchScope(node.name, method, "A.OO0000");
        List<AllocationSpec> specs = List.of(
                new AllocationSpec("java/util/ArrayList", "(Ljava/util/Collection;)V",
                        "borrowHitList", "(Ljava/util/Collection;)Ljava/util/ArrayList;", 1),
                new AllocationSpec("org/lwjgl/util/vector/Vector2f", "(FF)V",
                        "borrowHitPoint", "(FF)Lorg/lwjgl/util/vector/Vector2f;", 1));
        int changed = replaceAllocationGroup(node.name, method, specs, scopeInstalled,
                "A.OO0000 scratch");
        PatchReport report = new PatchReport();
        report.add("hit-test scratch allocations", changed);
        return report;
    }

    private PatchReport patchHoverHitTestCache(ClassNode node) {
        String desc = "(FFF)" + ENTITY_TOKEN_DESC;
        String renamed = "smo$originalHitTest";
        String hookDesc = "(Ljava/lang/Object;FFF)" + ENTITY_TOKEN_DESC;
        boolean wrapperAlready = methods(node, renamed, desc).size() == 1;
        MethodNode original = requireWrapperSource(node, "OO0000", desc, renamed,
                "hitTestCached", hookDesc);
        if (containsWriteOrMonitor(original)) {
            throw mismatch("hit-test method writes state or uses monitor/invokedynamic operations");
        }
        int scopeBegins = countCalls(original, Opcodes.INVOKESTATIC,
                HOOKS, "beginScratchScope", "()V");
        if (scopeBegins == 0) {
            requireCount("hit-test semantic call count", totalMethodCalls(original), 50);
        } else {
            ensureScratchScope(node.name, original, "A.OO0000");
            requireCount("scoped hit-test semantic call count", totalMethodCalls(original), 54);
        }
        List<FieldNode> mapFields = fields(node, "L" + H + ";");
        requireCount("A map-handler fields", mapFields.size(), 1);
        requireCountAtLeast("hit-test H.getLocation", countCalls(original, Opcodes.INVOKEVIRTUAL,
                H, "getLocation", "()Lcom/fs/starfarer/campaign/BaseLocation;"), 1);
        requireCountAtLeast("hit-test H.getFactor", countCalls(original, Opcodes.INVOKEVIRTUAL,
                H, "getFactor", "()F"), 1);
        requireCountAtLeast("hit-test H.getIcons", countCalls(original, Opcodes.INVOKEVIRTUAL,
                H, "getIcons", "()Ljava/util/LinkedHashMap;"), 1);
        if (wrapperAlready) {
            requireHitTestWrapper(node, desc, hookDesc);
            throw already("OO0000 wrapper and original semantic contract match");
        }

        int originalAccess = original.access;
        String signature = original.signature;
        String[] exceptions = exceptions(original);
        original.name = renamed;
        makePrivateSynthetic(original);
        MethodNode wrapper = new MethodNode(Opcodes.ASM8, originalAccess, "OO0000",
                desc, signature, exceptions);
        moveWrapperMetadata(original, wrapper, "OO0000");
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, argumentLocal(wrapper, 0)));
        wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, argumentLocal(wrapper, 1)));
        wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, argumentLocal(wrapper, 2)));
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hitTestCached", hookDesc, false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(wrapper);
        requireWrapperPostcondition(node, "OO0000", desc, renamed, "hitTestCached", hookDesc);
        requireHitTestWrapper(node, desc, hookDesc);

        PatchReport report = new PatchReport();
        report.add("hyperspace/map hover hit-test cache", 1);
        return report;
    }

    private PatchReport patchIntelMapLocationCalls(ClassNode node, int expected) {
        String originalDesc = "(" + MAP_API_DESC + ")" + ENTITY_TOKEN_DESC;
        String hookDesc = "(" + INTEL_DESC + MAP_API_DESC + ")" + ENTITY_TOKEN_DESC;
        List<MethodInsnNode> originals = new ArrayList<>();
        int hooks = 0;
        for (MethodNode method : node.methods) {
            originals.addAll(calls(method, Opcodes.INVOKEINTERFACE,
                    "com/fs/starfarer/api/campaign/comm/IntelInfoPlugin",
                    "getMapLocation", originalDesc));
            hooks += countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "getMapLocationCached", hookDesc);
        }
        requireUnpatchedOrAlready(node.name + " getMapLocation", originals.size(), expected, hooks, expected);
        for (MethodInsnNode call : originals) {
            if (nextMeaningful(call) != null && nextMeaningful(call).getOpcode() == Opcodes.POP) {
                throw mismatch("getMapLocation result is discarded at a candidate site");
            }
        }
        for (MethodInsnNode call : originals) {
            makeStatic(call, HOOKS, "getMapLocationCached", hookDesc);
        }
        PatchReport report = new PatchReport();
        report.add("Intel map-location callback cache", originals.size());
        return report;
    }

    private PatchReport patchArrowCallbackCache(ClassNode node) {
        MethodNode method = requireMethod(node, "o00000", "(FF)V");
        String originalDesc = "(" + MAP_API_DESC + ")Ljava/util/List;";
        String hookDesc = "(" + INTEL_DESC + MAP_API_DESC + ")Ljava/util/List;";
        List<MethodInsnNode> original = calls(method, Opcodes.INVOKEINTERFACE,
                "com/fs/starfarer/api/campaign/comm/IntelInfoPlugin", "getArrowData", originalDesc);
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "getArrowDataCached", hookDesc);
        requireUnpatchedOrAlready("Z arrow-data callback", original.size(), 1, hooks, 1);
        MethodInsnNode call = only(original, "Z arrow-data callback");
        if (nextMeaningful(call) != null && nextMeaningful(call).getOpcode() == Opcodes.POP) {
            throw mismatch("getArrowData result is discarded");
        }
        makeStatic(call, HOOKS, "getArrowDataCached", hookDesc);
        PatchReport report = new PatchReport();
        report.add("Intel arrow-data callback cache", 1);
        return report;
    }

    private PatchReport patchArrowVectorPool(ClassNode node) {
        MethodNode method = requireMethod(node, "o00000", "(FF)V");
        boolean scopeInstalled = ensureScratchScope(node.name, method, "Z.o00000");
        List<AllocationSpec> specs = List.of(new AllocationSpec(
                "org/lwjgl/util/vector/Vector2f", "(FF)V",
                "borrowArrowVector", "(FF)Lorg/lwjgl/util/vector/Vector2f;", 2));
        int changed = replaceAllocationGroup(node.name, method, specs, scopeInstalled,
                "Z arrow vectors");
        PatchReport report = new PatchReport();
        report.add("Intel arrow vector pool", changed);
        return report;
    }

    private PatchReport patchIntelFastContains(ClassNode node) {
        MethodNode method = requireMethod(node, "addMissingIconsAndRows", "()V");
        boolean scopeInstalled = ensureScratchScope(node.name, method,
                "EventsPanel.addMissingIconsAndRows");
        String hookDesc = "(Ljava/util/Collection;Ljava/lang/Object;)Z";
        List<MethodInsnNode> original = calls(method, Opcodes.INVOKEINTERFACE,
                "java/util/List", "contains", "(Ljava/lang/Object;)Z");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "fastContains", hookDesc);
        if (scopeInstalled && original.isEmpty() && hooks == 1) {
            throw mismatch("EventsPanel fastContains hook exists without its required scratch scope");
        }
        requireUnpatchedOrAlready("EventsPanel missing-list contains", original.size(), 1, hooks, 1);

        MethodInsnNode call = only(original, "EventsPanel missing-list contains");
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        int listLocal = requireLoadedLocal(receiverSource(method, call, frames),
                Opcodes.ALOAD, "missing Intel list");
        int pluginLocal = requireLoadedLocal(argumentSource(method, call, frames, 0),
                Opcodes.ALOAD, "current Intel plugin");
        requireLocalConstructor(method, listLocal, "java/util/ArrayList", "()V", "missing Intel list");
        requireStoredCallResult(method, pluginLocal, -1,
                null, "getInfo", "()" + INTEL_DESC, "row Intel plugin");
        if (hasMutatorAfter(node.name, method, call, listLocal)) {
            throw mismatch("missing Intel list is mutated after the contains site");
        }
        makeStatic(call, HOOKS, "fastContains", hookDesc);
        PatchReport report = new PatchReport();
        report.add("Intel missing-list hash contains", 1);
        return report;
    }

    private PatchReport patchExistingIntelIconLookup(ClassNode node) {
        MethodNode method = requireMethod(node, "addMissingIconsAndRows", "()V");
        String hookDesc = "(Ljava/util/LinkedHashMap;Ljava/lang/Object;)Ljava/util/Collection;";
        List<MethodInsnNode> original = calls(method, Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedHashMap", "values", "()Ljava/util/Collection;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "existingIntelIconCandidates", hookDesc);
        requireUnpatchedOrAlready("EventsPanel existing icon values", original.size(), 1, hooks, 1);

        MethodInsnNode values = only(original, "EventsPanel existing icon values");
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        requireSourceMethod(receiverSource(method, values, frames), Opcodes.INVOKEVIRTUAL,
                H, "getIcons", "()Ljava/util/LinkedHashMap;", "existing icon map receiver");
        MethodInsnNode entityLookup = only(calls(method, Opcodes.INVOKEVIRTUAL, H,
                "getIntelIconEntity", "(" + INTEL_DESC + ")Lcom/fs/starfarer/campaign/CustomCampaignEntity;"),
                "H.getIntelIconEntity in addMissingIconsAndRows");
        AbstractInsnNode storeInsn = nextMeaningful(entityLookup);
        if (!(storeInsn instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
            throw mismatch("Intel entity lookup result is not stored in a local");
        }
        int entityLocal = store.var;
        if (!hasNullGuard(method, store, values, entityLocal)) {
            throw mismatch("Intel entity values scan is not guarded by an entity null check");
        }
        int comparisons = countIdentityComparisons(method, frames, values, entityLocal,
                "com/fs/starfarer/coreui/A/ooOO", "int", "()" + ENTITY_TOKEN_DESC);
        requireCount("existing icon entity identity comparison", comparisons, 1);

        method.instructions.insertBefore(values, new VarInsnNode(Opcodes.ALOAD, entityLocal));
        makeStatic(values, HOOKS, "existingIntelIconCandidates", hookDesc);
        PatchReport report = new PatchReport();
        report.add("Intel direct existing-icon lookup", 1);
        return report;
    }

    // ---------------------------------------------------------------------
    // Campaign and route hot paths
    // ---------------------------------------------------------------------

    private PatchReport patchCampaignCacheLifecycle(ClassNode node) {
        String engineDesc = "L" + CAMPAIGN_ENGINE + ";";
        String beginDesc = "(Ljava/lang/Object;)J";
        String completeDesc = "(Ljava/lang/Object;J)V";
        MethodNode reset = requireMethod(node, "resetInstance", "()V");
        MethodNode set = requireMethod(node, "setInstance", "(" + engineDesc + ")V");
        if ((reset.access & Opcodes.ACC_STATIC) == 0 || (set.access & Opcodes.ACC_STATIC) == 0) {
            throw mismatch("CampaignEngine lifecycle methods are not static");
        }
        if ((reset.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || (set.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw mismatch("CampaignEngine lifecycle methods have no patchable code");
        }

        FieldNode singleton = null;
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0 || !field.desc.equals(engineDesc)) continue;
            if (singleton != null) {
                throw mismatch("CampaignEngine has multiple static self-typed fields");
            }
            singleton = field;
        }
        if (singleton == null) throw mismatch("CampaignEngine singleton field was not found");
        FieldInsnNode resetWrite = requireSingletonWrite(
                reset, node.name, singleton.name, engineDesc, "resetInstance");
        FieldInsnNode setWrite = requireSingletonWrite(
                set, node.name, singleton.name, engineDesc, "setInstance");
        Frame<SourceValue> resetFrame = frameBefore(reset, resetWrite, sourceFrames(node.name, reset));
        Frame<SourceValue> setFrame = frameBefore(set, setWrite, sourceFrames(node.name, set));
        if (resetFrame.getStackSize() < 1
                || !sourceIsOpcode(resetFrame.getStack(resetFrame.getStackSize() - 1), Opcodes.ACONST_NULL)) {
            throw mismatch("resetInstance singleton write is not produced by null");
        }
        if (setFrame.getStackSize() < 1
                || !sourceIsLocal(setFrame.getStack(setFrame.getStackSize() - 1),
                Opcodes.ALOAD, argumentLocal(set, 0))) {
            throw mismatch("setInstance singleton write is not produced by its engine argument");
        }

        List<AbstractInsnNode> resetReturns = campaignReturns(reset);
        List<AbstractInsnNode> setReturns = campaignReturns(set);
        int resetBegins = countCalls(reset, Opcodes.INVOKESTATIC, HOOKS,
                "beginCampaignEngineChange", beginDesc);
        int setBegins = countCalls(set, Opcodes.INVOKESTATIC, HOOKS,
                "beginCampaignEngineChange", beginDesc);
        int resetCompletes = countCalls(reset, Opcodes.INVOKESTATIC, HOOKS,
                "completeCampaignEngineChange", completeDesc);
        int setCompletes = countCalls(set, Opcodes.INVOKESTATIC, HOOKS,
                "completeCampaignEngineChange", completeDesc);
        if (resetBegins == 1 && setBegins == 1
                && resetCompletes == resetReturns.size() && setCompletes == setReturns.size()) {
            int resetToken = requireCampaignBeginPlacement(
                    reset, resetWrite, false, beginDesc);
            int setToken = requireCampaignBeginPlacement(set, setWrite, true, beginDesc);
            requireCampaignCompletionPlacement(
                    reset, resetReturns, false, resetToken, completeDesc);
            requireCampaignCompletionPlacement(
                    set, setReturns, true, setToken, completeDesc);
            requireCampaignTokenInitializer(reset, resetToken);
            requireCampaignTokenInitializer(set, setToken);
            throw already("CampaignEngine lifecycle cache hooks are already present");
        }
        if (resetBegins != 0 || setBegins != 0 || resetCompletes != 0 || setCompletes != 0) {
            throw mismatch("CampaignEngine lifecycle hooks are partial or ambiguous: resetBegin="
                    + resetBegins + ", setBegin=" + setBegins + ", resetComplete="
                    + resetCompletes + ", setComplete=" + setCompletes);
        }

        int resetToken = reset.maxLocals;
        reset.maxLocals += 2;
        InsnList resetInitializer = new InsnList();
        resetInitializer.add(new LdcInsnNode(Long.valueOf(-1L)));
        resetInitializer.add(new VarInsnNode(Opcodes.LSTORE, resetToken));
        reset.instructions.insert(resetInitializer);
        InsnList resetPrefix = new InsnList();
        resetPrefix.add(new InsnNode(Opcodes.ACONST_NULL));
        resetPrefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "beginCampaignEngineChange", beginDesc, false));
        resetPrefix.add(new VarInsnNode(Opcodes.LSTORE, resetToken));
        reset.instructions.insertBefore(resetWrite, resetPrefix);
        for (AbstractInsnNode returnInsn : resetReturns) {
            InsnList completion = new InsnList();
            completion.add(new InsnNode(Opcodes.ACONST_NULL));
            completion.add(new VarInsnNode(Opcodes.LLOAD, resetToken));
            completion.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    HOOKS, "completeCampaignEngineChange", completeDesc, false));
            reset.instructions.insertBefore(returnInsn, completion);
        }

        int setToken = set.maxLocals;
        set.maxLocals += 2;
        InsnList setInitializer = new InsnList();
        setInitializer.add(new LdcInsnNode(Long.valueOf(-1L)));
        setInitializer.add(new VarInsnNode(Opcodes.LSTORE, setToken));
        set.instructions.insert(setInitializer);
        InsnList setPrefix = new InsnList();
        setPrefix.add(new VarInsnNode(Opcodes.ALOAD, argumentLocal(set, 0)));
        setPrefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "beginCampaignEngineChange", beginDesc, false));
        setPrefix.add(new VarInsnNode(Opcodes.LSTORE, setToken));
        set.instructions.insertBefore(setWrite, setPrefix);
        for (AbstractInsnNode returnInsn : setReturns) {
            InsnList completion = new InsnList();
            completion.add(new VarInsnNode(Opcodes.ALOAD, argumentLocal(set, 0)));
            completion.add(new VarInsnNode(Opcodes.LLOAD, setToken));
            completion.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    HOOKS, "completeCampaignEngineChange", completeDesc, false));
            set.instructions.insertBefore(returnInsn, completion);
        }

        requireCampaignBeginPlacement(reset, resetWrite, false, beginDesc);
        requireCampaignBeginPlacement(set, setWrite, true, beginDesc);
        requireCampaignCompletionPlacement(
                reset, resetReturns, false, resetToken, completeDesc);
        requireCampaignCompletionPlacement(
                set, setReturns, true, setToken, completeDesc);
        requireCampaignTokenInitializer(reset, resetToken);
        requireCampaignTokenInitializer(set, setToken);
        PatchReport report = new PatchReport();
        report.add("campaign cache two-phase boundaries",
                2 + resetReturns.size() + setReturns.size());
        return report;
    }

    private static int requireCampaignBeginPlacement(MethodNode method, FieldInsnNode write,
                                                     boolean engineArgument, String beginDesc) {
        AbstractInsnNode storeInsn = previousMeaningful(write);
        if (!(storeInsn instanceof VarInsnNode store) || store.getOpcode() != Opcodes.LSTORE) {
            throw mismatch(method.name + " lifecycle transition token is not stored before publication");
        }
        AbstractInsnNode hookInsn = previousMeaningful(store);
        MethodInsnNode hook = asMethod(hookInsn, method.name + " lifecycle hook");
        requireCall(hook, Opcodes.INVOKESTATIC, HOOKS, "beginCampaignEngineChange", beginDesc,
                method.name + " lifecycle hook");
        AbstractInsnNode argument = previousMeaningful(hook);
        if (engineArgument) {
            requireVar(argument, Opcodes.ALOAD, argumentLocal(method, 0),
                    method.name + " lifecycle engine argument");
        } else if (argument == null || argument.getOpcode() != Opcodes.ACONST_NULL) {
            throw mismatch(method.name + " lifecycle reset argument is not null");
        }
        return store.var;
    }

    private static void requireCampaignCompletionPlacement(
            MethodNode method, List<AbstractInsnNode> returns, boolean engineArgument,
            int tokenLocal, String completeDesc) {
        for (AbstractInsnNode returnInsn : returns) {
            MethodInsnNode hook = asMethod(previousMeaningful(returnInsn),
                    method.name + " lifecycle completion hook");
            requireCall(hook, Opcodes.INVOKESTATIC, HOOKS,
                    "completeCampaignEngineChange", completeDesc,
                    method.name + " lifecycle completion hook");
            AbstractInsnNode token = previousMeaningful(hook);
            requireVar(token, Opcodes.LLOAD, tokenLocal,
                    method.name + " lifecycle completion token");
            AbstractInsnNode argument = previousMeaningful(token);
            if (engineArgument) {
                requireVar(argument, Opcodes.ALOAD, argumentLocal(method, 0),
                        method.name + " lifecycle completion engine argument");
            } else if (argument == null || argument.getOpcode() != Opcodes.ACONST_NULL) {
                throw mismatch(method.name + " lifecycle completion reset argument is not null");
            }
        }
    }

    private static void requireCampaignTokenInitializer(MethodNode method, int tokenLocal) {
        AbstractInsnNode first = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                first = insn;
                break;
            }
        }
        if (!(first instanceof LdcInsnNode ldc) || !(ldc.cst instanceof Long value)
                || value.longValue() != -1L) {
            throw mismatch(method.name + " lifecycle transition token initializer changed");
        }
        requireVar(nextMeaningful(first), Opcodes.LSTORE, tokenLocal,
                method.name + " lifecycle transition token initializer");
    }

    private static List<AbstractInsnNode> campaignReturns(MethodNode method) {
        ArrayList<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) returns.add(insn);
        }
        if (returns.isEmpty()) throw mismatch(method.name + " has no normal return");
        return returns;
    }

    private static FieldInsnNode requireSingletonWrite(MethodNode method, String owner,
                                                       String name, String desc, String label) {
        FieldInsnNode result = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTSTATIC
                    || !field.owner.equals(owner) || !field.name.equals(name) || !field.desc.equals(desc)) continue;
            if (result != null) throw mismatch(label + " has multiple singleton writes");
            result = field;
        }
        if (result == null) throw mismatch(label + " singleton write was not found");
        return result;
    }

    private PatchReport patchCampaignListenerThrottle(ClassNode node) {
        MethodNode refresh = requireMethod(node, "readdChangeListeners", "()V");
        if (containsWrite(refresh)) {
            throw mismatch("readdChangeListeners contains field/static writes");
        }
        Frame<SourceValue>[] refreshFrames = sourceFrames(node.name, refresh);
        List<MethodInsnNode> listenerCalls = calls(refresh, Opcodes.INVOKEVIRTUAL,
                "com/fs/util/container/repo/ObjectRepository", "setListener", null);
        requireCount("readdChangeListeners setListener calls", listenerCalls.size(), 2);

        MethodInsnNode systemIterator = only(calls(refresh, Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;"), "starSystems iterator");
        FieldInsnNode systemsField = requireSourceField(receiverSource(refresh, systemIterator, refreshFrames),
                Opcodes.GETFIELD, node.name, LIST_DESC, "CampaignEngine starSystems field");
        MethodInsnNode getObjects = only(calls(refresh, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/Hyperspace", "getObjects",
                "()Lcom/fs/util/container/repo/ObjectRepository;"), "hyperspace repository access");
        FieldInsnNode hyperspaceField = requireSourceField(receiverSource(refresh, getObjects, refreshFrames),
                Opcodes.GETFIELD, node.name, HYPERSPACE_DESC, "CampaignEngine hyperspace field");

        MethodNode advance = requireMethod(node, "advance", "(FLcom/fs/starfarer/util/A/new;)V");
        String hookDesc = "(Ljava/lang/Object;Ljava/util/List;Ljava/lang/Object;)V";
        List<MethodInsnNode> original = calls(advance, Opcodes.INVOKEVIRTUAL,
                node.name, "readdChangeListeners", "()V");
        int hooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "readdChangeListenersIfNeeded", hookDesc);
        requireUnpatchedOrAlready("CampaignEngine.advance listener refresh", original.size(), 1, hooks, 1);
        MethodInsnNode call = only(original, "CampaignEngine.advance listener refresh");
        Frame<SourceValue>[] advanceFrames = sourceFrames(node.name, advance);
        if (!sourceIsLocal(receiverSource(advance, call, advanceFrames), Opcodes.ALOAD, 0)) {
            throw mismatch("CampaignEngine.advance listener receiver is not this");
        }
        advance.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
        advance.instructions.insertBefore(call, new FieldInsnNode(Opcodes.GETFIELD,
                node.name, systemsField.name, LIST_DESC));
        advance.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
        advance.instructions.insertBefore(call, new FieldInsnNode(Opcodes.GETFIELD,
                node.name, hyperspaceField.name, HYPERSPACE_DESC));
        makeStatic(call, HOOKS, "readdChangeListenersIfNeeded", hookDesc);

        PatchReport report = new PatchReport();
        report.add("incremental campaign repository-listener refresh", 1);
        return report;
    }

    private PatchReport patchRouteJumpPointIndex(ClassNode node) {
        MethodNode next = requireMethod(node, "getNextStep", "(" + ENTITY_TOKEN_DESC + ")" + ENTITY_TOKEN_DESC);
        MethodNode distance = requireMethod(node, "getLastLegDistance", "(" + ENTITY_TOKEN_DESC + ")F");
        String jumpHookDesc = "(" + LOCATION_DESC + "Ljava/lang/Object;)Ljava/util/List;";
        String systemsHookDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;";

        List<MethodInsnNode> nextJumps = calls(next, Opcodes.INVOKEINTERFACE,
                "com/fs/starfarer/api/campaign/LocationAPI", "getJumpPoints", "()Ljava/util/List;");
        List<MethodInsnNode> distanceJumps = calls(distance, Opcodes.INVOKEINTERFACE,
                "com/fs/starfarer/api/campaign/LocationAPI", "getJumpPoints", "()Ljava/util/List;");
        int jumpHooks = countCalls(next, Opcodes.INVOKESTATIC, HOOKS,
                "routeJumpPointsForSystem", jumpHookDesc)
                + countCalls(distance, Opcodes.INVOKESTATIC, HOOKS,
                "routeJumpPointsForSystem", jumpHookDesc);
        List<MethodInsnNode> systems = calls(next, Opcodes.INVOKEVIRTUAL,
                CAMPAIGN_ENGINE, "getStarSystems", "()Ljava/util/List;");
        int systemsHooks = countCalls(next, Opcodes.INVOKESTATIC, HOOKS,
                "routeSystemsForAnchor", systemsHookDesc);

        boolean fullyPatched = nextJumps.isEmpty() && distanceJumps.isEmpty() && jumpHooks == 3
                && systems.isEmpty() && systemsHooks == 1;
        if (fullyPatched) throw already("three jump hooks and one system hook are already present");
        if (jumpHooks != 0 || systemsHooks != 0) {
            throw mismatch("mixed/foreign route hook state: jumps=" + jumpHooks + ", systems=" + systemsHooks);
        }
        requireCount("getNextStep jump scans", nextJumps.size(), 2);
        requireCount("getLastLegDistance jump scans", distanceJumps.size(), 1);
        requireCount("getNextStep system scan", systems.size(), 1);

        int[] nextSystemLocals = new int[2];
        for (int i = 0; i < nextJumps.size(); i++) {
            AbstractInsnNode boundary = i + 1 < nextJumps.size() ? nextJumps.get(i + 1) : null;
            nextSystemLocals[i] = findComparedLocationLocal(node.name, next, nextJumps.get(i), boundary);
            requireSystemLocalProducer(next, nextSystemLocals[i], nextJumps.get(i),
                    "getNextStep jump scan " + (i + 1));
        }
        int distanceSystemLocal = findComparedLocationLocal(node.name, distance, distanceJumps.get(0), null);
        requireSystemLocalProducer(distance, distanceSystemLocal, distanceJumps.get(0),
                "getLastLegDistance jump scan");
        int anchorLocal = findAnchorComparisonLocal(node.name, next, systems.get(0));
        int expectedAnchor = argumentLocal(next, 0);
        if (anchorLocal != expectedAnchor) {
            throw mismatch("route anchor local is not the SectorEntityToken argument: found "
                    + anchorLocal + ", expected " + expectedAnchor);
        }

        for (int i = 0; i < nextJumps.size(); i++) {
            MethodInsnNode call = nextJumps.get(i);
            next.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, nextSystemLocals[i]));
            makeStatic(call, HOOKS, "routeJumpPointsForSystem", jumpHookDesc);
        }
        MethodInsnNode distanceCall = distanceJumps.get(0);
        distance.instructions.insertBefore(distanceCall, new VarInsnNode(Opcodes.ALOAD, distanceSystemLocal));
        makeStatic(distanceCall, HOOKS, "routeJumpPointsForSystem", jumpHookDesc);

        MethodInsnNode systemsCall = systems.get(0);
        next.instructions.insertBefore(systemsCall, new VarInsnNode(Opcodes.ALOAD, anchorLocal));
        makeStatic(systemsCall, HOOKS, "routeSystemsForAnchor", systemsHookDesc);

        PatchReport report = new PatchReport();
        report.add("route jump-point system index", 3);
        report.add("route hyperspace-anchor system index", 1);
        return report;
    }

    private PatchReport patchCampaignSnapshotReuse(ClassNode node) {
        String methodDesc = "(FLcom/fs/starfarer/util/A/new;)V";
        String hookDesc = "(Ljava/util/Collection;)Ljava/util/List;";
        AllocationSpec advanceSnapshots = new AllocationSpec(
                "java/util/ArrayList", "(Ljava/util/Collection;)V",
                "borrowLocationAdvanceSnapshot", hookDesc, 3);
        AllocationSpec pausedSnapshots = new AllocationSpec(
                "java/util/ArrayList", "(Ljava/util/Collection;)V",
                "borrowPausedLocationSnapshot", hookDesc, 2);

        MethodNode advance = requireMethod(node, "advance", methodDesc);
        boolean advanceScopeInstalled = ensureScratchScope(
                node.name, advance, "BaseLocation.advance");
        int advanceChanged = replaceSnapshotAllocationGroup(node.name, advance,
                advanceSnapshots, advanceScopeInstalled,
                "BaseLocation.advance defensive snapshots");

        MethodNode paused = requireMethod(node, "advanceEvenIfPaused", methodDesc);
        boolean pausedScopeInstalled = ensureScratchScope(
                node.name, paused, "BaseLocation.advanceEvenIfPaused");
        int pausedChanged = replaceSnapshotAllocationGroup(node.name, paused,
                pausedSnapshots, pausedScopeInstalled,
                "BaseLocation.advanceEvenIfPaused defensive snapshots");

        PatchReport report = new PatchReport();
        report.add("reusable BaseLocation defensive snapshots",
                advanceChanged + pausedChanged);
        return report;
    }

    private PatchReport patchEntityScriptSnapshotReuse(ClassNode node) {
        MethodNode runScripts = requireMethod(node, "runScripts", "(F)V");
        List<MethodInsnNode> empties = calls(runScripts, Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z");
        AllocationSpec spec = new AllocationSpec(
                "java/util/ArrayList", "(Ljava/util/Collection;)V",
                "borrowEntityScriptSnapshot",
                "(Ljava/util/Collection;)Ljava/util/List;", 1);
        List<SnapshotAllocationPlan> plans = snapshotAllocationPlans(node.name, runScripts, spec);
        requireCount("BaseCampaignEntity.runScripts defensive snapshot", plans.size(), 1);
        requireCount("BaseCampaignEntity.runScripts retired snapshot hook",
                countCalls(runScripts, Opcodes.INVOKESTATIC, HOOKS,
                        spec.hookName, spec.hookDesc), 0);

        SnapshotAllocationPlan plan = plans.get(0);
        MethodInsnNode snapshot = plan.constructor;
        Frame<SourceValue>[] frames = sourceFrames(node.name, runScripts);
        FieldInsnNode scriptsField = requireSourceField(argumentSource(
                        runScripts, snapshot, frames, 0),
                Opcodes.GETFIELD, node.name, LIST_DESC,
                "BaseCampaignEntity.runScripts scripts field");
        if (!scriptsField.name.equals("scripts")) {
            throw mismatch("BaseCampaignEntity.runScripts snapshot no longer reads scripts");
        }
        requireSnapshotUseContract(node.name, runScripts, plan.store, 1,
                "BaseCampaignEntity.runScripts defensive snapshot");

        boolean guarded = empties.size() == 1 && hasEntityScriptEmptyGuard(
                node.name, runScripts, scriptsField.name);
        if (!empties.isEmpty() && !guarded) {
            throw mismatch("BaseCampaignEntity.runScripts has a foreign/partial empty guard");
        }
        requireEntityScriptEmptyPathShape(node.name, runScripts, plan,
                scriptsField.name, guarded);
        if (guarded) {
            throw already("BaseCampaignEntity.runScripts empty fast path is already present");
        }

        // Most campaign entities have no scripts. The previous reusable-snapshot
        // implementation still opened and cleared a large generic scratch frame for
        // every one of those entities. Return before any scope/allocation instead;
        // the uncommon non-empty path is left byte-for-byte vanilla and therefore
        // keeps a fresh mutation-safe ArrayList snapshot.
        AbstractInsnNode oldFirst = runScripts.instructions.getFirst();
        LabelNode nonEmpty = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                scriptsField.name, LIST_DESC));
        guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z", true));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, nonEmpty));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(nonEmpty);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        runScripts.instructions.insertBefore(oldFirst, guard);

        requireCount("BaseCampaignEntity.runScripts empty guard",
                countCalls(runScripts, Opcodes.INVOKEINTERFACE,
                        "java/util/List", "isEmpty", "()Z"), 1);
        if (!hasEntityScriptEmptyGuard(node.name, runScripts, scriptsField.name)) {
            throw mismatch("BaseCampaignEntity.runScripts empty guard postcondition failed");
        }
        requireEntityScriptEmptyPathShape(node.name, runScripts, plan,
                scriptsField.name, true);

        PatchReport report = new PatchReport();
        report.add("empty entity-script fast path", 1);
        return report;
    }

    private static boolean hasEntityScriptEmptyGuard(String owner, MethodNode method,
                                                     String fieldName) {
        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (!(first instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD
                || load.var != 0) return false;
        AbstractInsnNode fieldInsn = nextMeaningful(load);
        if (!(fieldInsn instanceof FieldInsnNode field)
                || field.getOpcode() != Opcodes.GETFIELD
                || !field.owner.equals(owner) || !field.name.equals(fieldName)
                || !field.desc.equals(LIST_DESC)) return false;
        AbstractInsnNode emptyInsn = nextMeaningful(field);
        if (!(emptyInsn instanceof MethodInsnNode empty)
                || !callMatches(empty, Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z")) return false;
        AbstractInsnNode jumpInsn = nextMeaningful(empty);
        if (!(jumpInsn instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFEQ) {
            return false;
        }
        AbstractInsnNode emptyReturn = nextMeaningful(jump);
        if (emptyReturn == null || emptyReturn.getOpcode() != Opcodes.RETURN) return false;
        return nextMeaningful(jump.label) == nextMeaningful(emptyReturn);
    }

    private static void requireEntityScriptEmptyPathShape(
            String owner, MethodNode method, SnapshotAllocationPlan plan,
            String scriptsFieldName, boolean guarded) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw mismatch("BaseCampaignEntity.runScripts gained exception handlers");
        }
        requireCount("BaseCampaignEntity.runScripts returns",
                countOpcode(method, Opcodes.RETURN), guarded ? 2 : 1);

        AbstractInsnNode entry = firstMeaningful(method);
        AbstractInsnNode bodyStart = entry;
        if (guarded) {
            if (!(entry instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD
                    || load.var != 0) {
                throw mismatch("BaseCampaignEntity.runScripts guard is not at method entry");
            }
            AbstractInsnNode fieldInsn = nextMeaningful(load);
            AbstractInsnNode emptyInsn = nextMeaningful(fieldInsn);
            AbstractInsnNode jumpInsn = nextMeaningful(emptyInsn);
            AbstractInsnNode returnInsn = nextMeaningful(jumpInsn);
            if (!(fieldInsn instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(owner) || !field.name.equals(scriptsFieldName)
                    || !field.desc.equals(LIST_DESC)
                    || !(emptyInsn instanceof MethodInsnNode empty)
                    || !callMatches(empty, Opcodes.INVOKEINTERFACE,
                    "java/util/List", "isEmpty", "()Z")
                    || !(jumpInsn instanceof JumpInsnNode jump)
                    || jump.getOpcode() != Opcodes.IFEQ
                    || returnInsn == null || returnInsn.getOpcode() != Opcodes.RETURN) {
                throw mismatch("BaseCampaignEntity.runScripts entry guard changed structurally");
            }
            bodyStart = nextMeaningful(returnInsn);
            if (nextMeaningful(jump.label) != bodyStart) {
                throw mismatch("BaseCampaignEntity.runScripts guard no longer rejoins vanilla entry");
            }
        }

        if (bodyStart != plan.allocation
                || nextMeaningful(plan.allocation) != plan.duplicate) {
            throw mismatch("BaseCampaignEntity.runScripts has a foreign entry prologue");
        }
        AbstractInsnNode loadThis = nextMeaningful(plan.duplicate);
        AbstractInsnNode fieldInsn = nextMeaningful(loadThis);
        if (!(loadThis instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD
                || load.var != 0
                || !(fieldInsn instanceof FieldInsnNode field)
                || field.getOpcode() != Opcodes.GETFIELD
                || !field.owner.equals(owner) || !field.name.equals(scriptsFieldName)
                || !field.desc.equals(LIST_DESC)
                || nextMeaningful(fieldInsn) != plan.constructor
                || nextMeaningful(plan.constructor) != plan.store) {
            throw mismatch("BaseCampaignEntity.runScripts snapshot entry sequence changed");
        }
        AbstractInsnNode snapshotLoad = nextMeaningful(plan.store);
        AbstractInsnNode iteratorInsn = nextMeaningful(snapshotLoad);
        AbstractInsnNode iteratorStoreInsn = nextMeaningful(iteratorInsn);
        AbstractInsnNode initialJumpInsn = nextMeaningful(iteratorStoreInsn);
        if (!(snapshotLoad instanceof VarInsnNode snapshotVar)
                || snapshotVar.getOpcode() != Opcodes.ALOAD || snapshotVar.var != plan.store.var
                || !(iteratorInsn instanceof MethodInsnNode iterator)
                || !callMatches(iterator, Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;")
                || !(iteratorStoreInsn instanceof VarInsnNode iteratorStore)
                || iteratorStore.getOpcode() != Opcodes.ASTORE
                || !(initialJumpInsn instanceof JumpInsnNode initialJump)
                || initialJump.getOpcode() != Opcodes.GOTO) {
            throw mismatch("BaseCampaignEntity.runScripts snapshot loop setup changed");
        }
        AbstractInsnNode body = nextMeaningful(initialJump);
        AbstractInsnNode testLoadInsn = nextMeaningful(initialJump.label);
        AbstractInsnNode hasNextInsn = nextMeaningful(testLoadInsn);
        AbstractInsnNode loopJumpInsn = nextMeaningful(hasNextInsn);
        AbstractInsnNode terminalReturn = nextMeaningful(loopJumpInsn);
        if (!(testLoadInsn instanceof VarInsnNode testLoad)
                || testLoad.getOpcode() != Opcodes.ALOAD || testLoad.var != iteratorStore.var
                || !(hasNextInsn instanceof MethodInsnNode hasNext)
                || !callMatches(hasNext, Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "hasNext", "()Z")
                || !(loopJumpInsn instanceof JumpInsnNode loopJump)
                || loopJump.getOpcode() != Opcodes.IFNE
                || nextMeaningful(loopJump.label) != body
                || terminalReturn == null || terminalReturn.getOpcode() != Opcodes.RETURN
                || nextMeaningful(terminalReturn) != null) {
            throw mismatch("BaseCampaignEntity.runScripts empty-path control flow changed");
        }
    }

    private PatchReport patchEmptyMemoryAdvanceFastPath(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        FieldNode expireField = requireField(node, "expire", "Ljava/util/List;");
        FieldNode requireField = requireField(node, "require", "Ljava/util/LinkedHashMap;");

        int legacyExpireHooks = countCalls(advance, Opcodes.INVOKESTATIC,
                HOOKS, "memoryExpireIterator", "(Ljava/util/List;)Ljava/util/Iterator;");
        int legacyRequireHooks = countCalls(advance, Opcodes.INVOKESTATIC,
                HOOKS, "memoryRequireIterator", "(Ljava/util/Collection;)Ljava/util/Iterator;");
        if (legacyExpireHooks != 0 || legacyRequireHooks != 0) {
            throw mismatch("Memory.advance contains the retired iterator-hook implementation");
        }

        List<MethodInsnNode> expireIterators = calls(advance, Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;");
        List<MethodInsnNode> requireIterators = calls(advance, Opcodes.INVOKEINTERFACE,
                "java/util/Collection", "iterator", "()Ljava/util/Iterator;");
        requireCount("Memory.advance expire iterator", expireIterators.size(), 1);
        requireCount("Memory.advance require iterator", requireIterators.size(), 1);

        Frame<SourceValue>[] frames = sourceFrames(node.name, advance);
        FieldInsnNode expireSource = requireSourceField(receiverSource(
                        advance, expireIterators.get(0), frames),
                Opcodes.GETFIELD, node.name, "Ljava/util/List;",
                "Memory.advance expire iterator source");
        if (!expireSource.name.equals(expireField.name)) {
            throw mismatch("Memory.advance expire iterator no longer reads expire");
        }
        MethodInsnNode values = requireSourceMethod(
                receiverSource(advance, requireIterators.get(0), frames),
                Opcodes.INVOKEVIRTUAL, "java/util/LinkedHashMap", "values",
                "()Ljava/util/Collection;", "Memory.advance require values view");
        FieldInsnNode requireSource = requireSourceField(receiverSource(advance, values, frames),
                Opcodes.GETFIELD, node.name, LINKED_MAP_DESC,
                "Memory.advance require values source");
        if (!requireSource.name.equals(requireField.name)) {
            throw mismatch("Memory.advance require iterator no longer reads require");
        }

        int expireEmptyCalls = countCalls(advance, Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z");
        int requireEmptyCalls = countCalls(advance, Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedHashMap", "isEmpty", "()Z");
        boolean guarded = expireEmptyCalls == 1 && requireEmptyCalls == 1
                && hasMemoryEmptyGuard(node.name, advance,
                expireField.name, requireField.name);
        if ((expireEmptyCalls != 0 || requireEmptyCalls != 0) && !guarded) {
            throw mismatch("Memory.advance has a foreign/partial empty guard");
        }

        List<MethodInsnNode> getInstances = calls(advance, Opcodes.INVOKESTATIC,
                "com/fs/starfarer/campaign/CampaignEngine", "getInstance",
                "()Lcom/fs/starfarer/campaign/CampaignEngine;");
        requireCount("Memory.advance CampaignEngine.getInstance calls", getInstances.size(), 2);
        MethodInsnNode paused = only(calls(advance, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/CampaignEngine", "isPaused", "()Z"),
                "Memory.advance pause check");
        MethodInsnNode pauseSource = requireSourceMethod(
                receiverSource(advance, paused, frames), Opcodes.INVOKESTATIC,
                "com/fs/starfarer/campaign/CampaignEngine", "getInstance",
                "()Lcom/fs/starfarer/campaign/CampaignEngine;",
                "Memory.advance pause receiver");
        if (pauseSource != getInstances.get(0)) {
            throw mismatch("Memory.advance pause check moved after timed work");
        }
        MethodInsnNode getClock = only(calls(advance, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/CampaignEngine", "getClock",
                "()Lcom/fs/starfarer/campaign/CampaignClock;"),
                "Memory.advance clock lookup");
        MethodInsnNode clockSource = requireSourceMethod(
                receiverSource(advance, getClock, frames), Opcodes.INVOKESTATIC,
                "com/fs/starfarer/campaign/CampaignEngine", "getInstance",
                "()Lcom/fs/starfarer/campaign/CampaignEngine;",
                "Memory.advance clock receiver");
        MethodInsnNode workStart = getInstances.get(1);
        if (clockSource != workStart) {
            throw mismatch("Memory.advance timed work no longer starts from the second engine read");
        }
        MethodInsnNode convert = only(calls(advance, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/CampaignClock", "convertToDays", "(F)F"),
                "Memory.advance clock conversion");
        if (requireSourceMethod(receiverSource(advance, convert, frames), Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/CampaignEngine", "getClock",
                "()Lcom/fs/starfarer/campaign/CampaignClock;",
                "Memory.advance conversion receiver") != getClock
                || !sourceIsLocal(argumentSource(advance, convert, frames, 0),
                Opcodes.FLOAD, 1)) {
            throw mismatch("Memory.advance convertToDays input changed");
        }
        AbstractInsnNode convertedStore = nextMeaningful(convert);
        if (!(convertedStore instanceof VarInsnNode store)
                || store.getOpcode() != Opcodes.FSTORE) {
            throw mismatch("Memory.advance clock conversion is not stored");
        }

        requireMemoryEmptyPathShape(node.name, advance, expireField.name,
                requireField.name, guarded, getInstances.get(0), paused, workStart,
                getClock, convert, store, expireIterators.get(0), values,
                requireIterators.get(0));
        if (guarded) {
            throw already("Memory.advance empty fast return is already present");
        }

        // The old implementation replaced both iterator() calls with static
        // helpers. Telemetry showed that the helper/config/isEmpty overhead was
        // greater than the saved empty-iterator allocations. Skip the complete
        // expiration/require section instead. Restoration and pause handling stay
        // byte-for-byte vanilla; only an unpaused Memory with both work queues
        // empty returns before clock conversion and iterator creation.
        LabelNode nonEmpty = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                expireField.name, expireField.desc));
        guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z", true));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, nonEmpty));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                requireField.name, requireField.desc));
        guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedHashMap", "isEmpty", "()Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, nonEmpty));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(nonEmpty);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        advance.instructions.insertBefore(workStart, guard);

        if (!hasMemoryEmptyGuard(node.name, advance,
                expireField.name, requireField.name)) {
            throw mismatch("Memory.advance empty fast return postcondition failed");
        }
        requireMemoryEmptyPathShape(node.name, advance, expireField.name,
                requireField.name, true, getInstances.get(0), paused, workStart,
                getClock, convert, store, expireIterators.get(0), values,
                requireIterators.get(0));

        PatchReport report = new PatchReport();
        report.add("inline empty Memory.advance fast return", 1);
        return report;
    }

    private static boolean hasMemoryEmptyGuard(String owner, MethodNode method,
                                               String expireFieldName,
                                               String requireFieldName) {
        for (MethodInsnNode expireEmpty : calls(method, Opcodes.INVOKEINTERFACE,
                "java/util/List", "isEmpty", "()Z")) {
            AbstractInsnNode receiver = previousMeaningful(expireEmpty);
            if (!(receiver instanceof FieldInsnNode expireField)
                    || expireField.getOpcode() != Opcodes.GETFIELD
                    || !expireField.owner.equals(owner)
                    || !expireField.name.equals(expireFieldName)
                    || !expireField.desc.equals("Ljava/util/List;")) continue;
            AbstractInsnNode loadThis = previousMeaningful(expireField);
            if (!(loadThis instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.ALOAD || load.var != 0) continue;
            AbstractInsnNode firstJumpInsn = nextMeaningful(expireEmpty);
            if (!(firstJumpInsn instanceof JumpInsnNode firstJump)
                    || firstJump.getOpcode() != Opcodes.IFEQ) continue;

            AbstractInsnNode requireLoad = nextMeaningful(firstJump);
            if (!(requireLoad instanceof VarInsnNode requireThis)
                    || requireThis.getOpcode() != Opcodes.ALOAD || requireThis.var != 0) continue;
            AbstractInsnNode requireFieldInsn = nextMeaningful(requireLoad);
            if (!(requireFieldInsn instanceof FieldInsnNode requireField)
                    || requireField.getOpcode() != Opcodes.GETFIELD
                    || !requireField.owner.equals(owner)
                    || !requireField.name.equals(requireFieldName)
                    || !requireField.desc.equals("Ljava/util/LinkedHashMap;")) continue;
            AbstractInsnNode requireEmptyInsn = nextMeaningful(requireField);
            if (!(requireEmptyInsn instanceof MethodInsnNode requireEmpty)
                    || !callMatches(requireEmpty, Opcodes.INVOKEVIRTUAL,
                    "java/util/LinkedHashMap", "isEmpty", "()Z")) continue;
            AbstractInsnNode secondJumpInsn = nextMeaningful(requireEmpty);
            if (!(secondJumpInsn instanceof JumpInsnNode secondJump)
                    || secondJump.getOpcode() != Opcodes.IFEQ
                    || secondJump.label != firstJump.label) continue;
            AbstractInsnNode emptyReturn = nextMeaningful(secondJump);
            if (emptyReturn == null || emptyReturn.getOpcode() != Opcodes.RETURN) continue;
            if (nextMeaningful(firstJump.label) != nextMeaningful(emptyReturn)) continue;
            return true;
        }
        return false;
    }

    private static void requireMemoryEmptyPathShape(
            String owner, MethodNode method, String expireFieldName,
            String requireFieldName, boolean guarded, MethodInsnNode pauseEngine,
            MethodInsnNode paused, MethodInsnNode workStart, MethodInsnNode getClock,
            MethodInsnNode convert, VarInsnNode convertedStore,
            MethodInsnNode expireIterator, MethodInsnNode values,
            MethodInsnNode requireIterator) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw mismatch("Memory.advance gained exception handlers");
        }
        requireCount("Memory.advance returns", countOpcode(method, Opcodes.RETURN),
                guarded ? 3 : 2);

        AbstractInsnNode pauseJumpInsn = nextMeaningful(paused);
        AbstractInsnNode pauseReturn = nextMeaningful(pauseJumpInsn);
        if (nextMeaningful(pauseEngine) != paused
                || !(pauseJumpInsn instanceof JumpInsnNode pauseJump)
                || pauseJump.getOpcode() != Opcodes.IFEQ
                || pauseReturn == null || pauseReturn.getOpcode() != Opcodes.RETURN) {
            throw mismatch("Memory.advance pause gate changed structurally");
        }
        AbstractInsnNode workEntry = nextMeaningful(pauseReturn);
        if (nextMeaningful(pauseJump.label) != workEntry) {
            throw mismatch("Memory.advance pause branch no longer reaches timed work entry");
        }
        if (guarded) {
            AbstractInsnNode expireLoadInsn = workEntry;
            AbstractInsnNode expireFieldInsn = nextMeaningful(expireLoadInsn);
            AbstractInsnNode expireEmptyInsn = nextMeaningful(expireFieldInsn);
            AbstractInsnNode firstJumpInsn = nextMeaningful(expireEmptyInsn);
            AbstractInsnNode requireLoadInsn = nextMeaningful(firstJumpInsn);
            AbstractInsnNode requireFieldInsn = nextMeaningful(requireLoadInsn);
            AbstractInsnNode requireEmptyInsn = nextMeaningful(requireFieldInsn);
            AbstractInsnNode secondJumpInsn = nextMeaningful(requireEmptyInsn);
            AbstractInsnNode emptyReturn = nextMeaningful(secondJumpInsn);
            if (!(expireLoadInsn instanceof VarInsnNode expireLoad)
                    || expireLoad.getOpcode() != Opcodes.ALOAD || expireLoad.var != 0
                    || !(expireFieldInsn instanceof FieldInsnNode expireField)
                    || expireField.getOpcode() != Opcodes.GETFIELD
                    || !expireField.owner.equals(owner) || !expireField.name.equals(expireFieldName)
                    || !expireField.desc.equals(LIST_DESC)
                    || !(expireEmptyInsn instanceof MethodInsnNode expireEmpty)
                    || !callMatches(expireEmpty, Opcodes.INVOKEINTERFACE,
                    "java/util/List", "isEmpty", "()Z")
                    || !(firstJumpInsn instanceof JumpInsnNode firstJump)
                    || firstJump.getOpcode() != Opcodes.IFEQ
                    || !(requireLoadInsn instanceof VarInsnNode requireLoad)
                    || requireLoad.getOpcode() != Opcodes.ALOAD || requireLoad.var != 0
                    || !(requireFieldInsn instanceof FieldInsnNode requireField)
                    || requireField.getOpcode() != Opcodes.GETFIELD
                    || !requireField.owner.equals(owner)
                    || !requireField.name.equals(requireFieldName)
                    || !requireField.desc.equals(LINKED_MAP_DESC)
                    || !(requireEmptyInsn instanceof MethodInsnNode requireEmpty)
                    || !callMatches(requireEmpty, Opcodes.INVOKEVIRTUAL,
                    "java/util/LinkedHashMap", "isEmpty", "()Z")
                    || !(secondJumpInsn instanceof JumpInsnNode secondJump)
                    || secondJump.getOpcode() != Opcodes.IFEQ
                    || firstJump.label != secondJump.label
                    || emptyReturn == null || emptyReturn.getOpcode() != Opcodes.RETURN
                    || nextMeaningful(firstJump.label) != workStart
                    || nextMeaningful(emptyReturn) != workStart) {
                throw mismatch("Memory.advance empty guard is not the exact post-pause prefix");
            }
        } else if (workEntry != workStart) {
            throw mismatch("Memory.advance has foreign work before clock conversion");
        }

        AbstractInsnNode deltaLoad = nextMeaningful(getClock);
        if (nextMeaningful(workStart) != getClock
                || !(deltaLoad instanceof VarInsnNode load)
                || load.getOpcode() != Opcodes.FLOAD || load.var != 1
                || nextMeaningful(deltaLoad) != convert
                || nextMeaningful(convert) != convertedStore) {
            throw mismatch("Memory.advance clock conversion order changed");
        }

        AbstractInsnNode expireLoadInsn = nextMeaningful(convertedStore);
        AbstractInsnNode expireFieldInsn = nextMeaningful(expireLoadInsn);
        AbstractInsnNode expireStoreInsn = nextMeaningful(expireIterator);
        AbstractInsnNode expireGotoInsn = nextMeaningful(expireStoreInsn);
        if (!(expireLoadInsn instanceof VarInsnNode expireLoad)
                || expireLoad.getOpcode() != Opcodes.ALOAD || expireLoad.var != 0
                || !(expireFieldInsn instanceof FieldInsnNode expireField)
                || expireField.getOpcode() != Opcodes.GETFIELD
                || !expireField.owner.equals(owner) || !expireField.name.equals(expireFieldName)
                || !expireField.desc.equals(LIST_DESC)
                || nextMeaningful(expireFieldInsn) != expireIterator
                || !(expireStoreInsn instanceof VarInsnNode expireStore)
                || expireStore.getOpcode() != Opcodes.ASTORE
                || !(expireGotoInsn instanceof JumpInsnNode expireGoto)
                || expireGoto.getOpcode() != Opcodes.GOTO) {
            throw mismatch("Memory.advance expire loop setup changed");
        }
        AbstractInsnNode expireBody = nextMeaningful(expireGoto);
        AbstractInsnNode expireTestLoadInsn = nextMeaningful(expireGoto.label);
        AbstractInsnNode expireHasNextInsn = nextMeaningful(expireTestLoadInsn);
        AbstractInsnNode expireLoopJumpInsn = nextMeaningful(expireHasNextInsn);
        if (!(expireTestLoadInsn instanceof VarInsnNode expireTestLoad)
                || expireTestLoad.getOpcode() != Opcodes.ALOAD
                || expireTestLoad.var != expireStore.var
                || !(expireHasNextInsn instanceof MethodInsnNode expireHasNext)
                || !callMatches(expireHasNext, Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "hasNext", "()Z")
                || !(expireLoopJumpInsn instanceof JumpInsnNode expireLoopJump)
                || expireLoopJump.getOpcode() != Opcodes.IFNE
                || nextMeaningful(expireLoopJump.label) != expireBody) {
            throw mismatch("Memory.advance expire loop control flow changed");
        }

        AbstractInsnNode requireLoadInsn = nextMeaningful(expireLoopJump);
        AbstractInsnNode requireFieldInsn = nextMeaningful(requireLoadInsn);
        AbstractInsnNode requireStoreInsn = nextMeaningful(requireIterator);
        AbstractInsnNode requireGotoInsn = nextMeaningful(requireStoreInsn);
        if (!(requireLoadInsn instanceof VarInsnNode requireLoad)
                || requireLoad.getOpcode() != Opcodes.ALOAD || requireLoad.var != 0
                || !(requireFieldInsn instanceof FieldInsnNode requireField)
                || requireField.getOpcode() != Opcodes.GETFIELD
                || !requireField.owner.equals(owner) || !requireField.name.equals(requireFieldName)
                || !requireField.desc.equals(LINKED_MAP_DESC)
                || nextMeaningful(requireFieldInsn) != values
                || nextMeaningful(values) != requireIterator
                || !(requireStoreInsn instanceof VarInsnNode requireStore)
                || requireStore.getOpcode() != Opcodes.ASTORE
                || !(requireGotoInsn instanceof JumpInsnNode requireGoto)
                || requireGoto.getOpcode() != Opcodes.GOTO) {
            throw mismatch("Memory.advance require loop setup changed");
        }
        AbstractInsnNode requireBody = nextMeaningful(requireGoto);
        AbstractInsnNode requireTestLoadInsn = nextMeaningful(requireGoto.label);
        AbstractInsnNode requireHasNextInsn = nextMeaningful(requireTestLoadInsn);
        AbstractInsnNode requireLoopJumpInsn = nextMeaningful(requireHasNextInsn);
        AbstractInsnNode terminalReturn = nextMeaningful(requireLoopJumpInsn);
        if (!(requireTestLoadInsn instanceof VarInsnNode requireTestLoad)
                || requireTestLoad.getOpcode() != Opcodes.ALOAD
                || requireTestLoad.var != requireStore.var
                || !(requireHasNextInsn instanceof MethodInsnNode requireHasNext)
                || !callMatches(requireHasNext, Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "hasNext", "()Z")
                || !(requireLoopJumpInsn instanceof JumpInsnNode requireLoopJump)
                || requireLoopJump.getOpcode() != Opcodes.IFNE
                || nextMeaningful(requireLoopJump.label) != requireBody
                || terminalReturn == null || terminalReturn.getOpcode() != Opcodes.RETURN
                || nextMeaningful(terminalReturn) != null) {
            throw mismatch("Memory.advance empty-path terminal control flow changed");
        }
    }

    private PatchReport patchEconomyLocationCache(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        String reach = "com/fs/starfarer/campaign/econ/reach/ReachEconomy";
        String reachDesc = "L" + reach + ";";
        boolean persistent = hasExactSyntheticField(node, ECONOMY_PERSISTENT_STATE_FIELD,
                PERSISTENT_STATE_DESC, persistentStateAccess());
        String hookName = persistent
                ? "updateEconomyLocationMapIfNeededPersistent"
                : "updateEconomyLocationMapIfNeeded";
        String hookDesc = persistent
                ? "(" + reachDesc + PERSISTENT_STATE_DESC + ")V"
                : "(" + reachDesc + ")V";
        List<MethodInsnNode> dirtyCalls = calls(advance, Opcodes.INVOKEVIRTUAL,
                reach, "setLocationCacheNeedsUpdate", "(Z)V");
        List<MethodInsnNode> updateCalls = calls(advance, Opcodes.INVOKEVIRTUAL,
                reach, "updateLocationMap", "()V");
        int hooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc);
        boolean original = dirtyCalls.size() == 1 && updateCalls.size() == 1 && hooks == 0;
        boolean patched = dirtyCalls.isEmpty() && updateCalls.isEmpty() && hooks == 1;
        if (patched) {
            MethodInsnNode hook = only(calls(advance, Opcodes.INVOKESTATIC, HOOKS,
                    hookName, hookDesc), "Economy.advance location-cache hook");
            Frame<SourceValue>[] frames = sourceFrames(node.name, advance);
            FieldInsnNode economyField = optionalSourceField(
                    argumentSource(advance, hook, frames, 0), Opcodes.GETFIELD,
                    node.name, reachDesc);
            if (economyField == null) {
                throw mismatch("Economy.advance location-cache hook economy argument changed");
            }
            if (persistent) {
                FieldInsnNode stateField = optionalSourceField(
                        argumentSource(advance, hook, frames, 1), Opcodes.GETFIELD,
                        node.name, PERSISTENT_STATE_DESC);
                if (stateField == null
                        || !stateField.name.equals(ECONOMY_PERSISTENT_STATE_FIELD)) {
                    throw mismatch("Economy.advance persistent location-cache state changed");
                }
            }
            throw already("Economy.advance location-cache hook and argument postcondition match");
        }
        if (!original) {
            throw mismatch("Economy.advance location-cache sequence is missing, duplicated, or partially patched");
        }

        MethodInsnNode dirty = dirtyCalls.get(0);
        MethodInsnNode update = updateCalls.get(0);
        AbstractInsnNode flag = previousMeaningful(dirty);
        AbstractInsnNode firstFieldInsn = previousMeaningful(flag);
        AbstractInsnNode firstThis = previousMeaningful(firstFieldInsn);
        AbstractInsnNode secondThis = nextMeaningful(dirty);
        AbstractInsnNode secondFieldInsn = nextMeaningful(secondThis);
        AbstractInsnNode secondUpdate = nextMeaningful(secondFieldInsn);
        if (flag == null || flag.getOpcode() != Opcodes.ICONST_1
                || !(firstFieldInsn instanceof FieldInsnNode firstField)
                || firstField.getOpcode() != Opcodes.GETFIELD
                || !firstField.owner.equals(node.name) || !firstField.desc.equals(reachDesc)
                || !(firstThis instanceof VarInsnNode firstLoad)
                || firstLoad.getOpcode() != Opcodes.ALOAD || firstLoad.var != 0
                || !(secondThis instanceof VarInsnNode secondLoad)
                || secondLoad.getOpcode() != Opcodes.ALOAD || secondLoad.var != 0
                || !(secondFieldInsn instanceof FieldInsnNode secondField)
                || secondField.getOpcode() != Opcodes.GETFIELD
                || !secondField.owner.equals(node.name) || !secondField.name.equals(firstField.name)
                || !secondField.desc.equals(reachDesc) || secondUpdate != update) {
            throw mismatch("Economy.advance automatic dirty/update sequence changed structurally");
        }

        advance.instructions.remove(flag);
        advance.instructions.remove(secondThis);
        advance.instructions.remove(secondFieldInsn);
        advance.instructions.remove(update);
        if (persistent) {
            InsnList state = new InsnList();
            state.add(new VarInsnNode(Opcodes.ALOAD, 0));
            state.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                    ECONOMY_PERSISTENT_STATE_FIELD, PERSISTENT_STATE_DESC));
            advance.instructions.insertBefore(dirty, state);
        }
        makeStatic(dirty, HOOKS, hookName, hookDesc);
        requireCount("Economy location-cache hook",
                countCalls(advance, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc), 1);

        PatchReport report = new PatchReport();
        report.add(persistent
                ? "epoch/audit ReachEconomy location-map invalidation"
                : "validated ReachEconomy location-map invalidation", 1);
        return report;
    }

    /**
     * Replaces per-frame clear/addAll scratch snapshots with owner-local,
     * copy-on-write snapshots. A transformed mutator increments the structure
     * epoch immediately; direct live-list edits are detected by a periodic
     * identity/order audit. Old snapshots are never cleared, so a nested advance
     * cannot invalidate an outer iterator.
     */
    private PatchReport patchEconomyPersistentSnapshots(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode paused = requireMethod(node, "advanceMarketConditionsWhenPaused", "(F)V");
        MethodNode constructor = requireMethod(node, "<init>", "(Z)V");
        MethodNode readResolve = requireMethod(node, "readResolve", "()Ljava/lang/Object;");
        MethodNode addMarket = requireMethod(node, "addMarket",
                "(Lcom/fs/starfarer/api/campaign/econ/MarketAPI;Z)V");
        MethodNode removeMarket = requireMethod(node, "removeMarket",
                "(Lcom/fs/starfarer/api/campaign/econ/MarketAPI;)V");
        MethodNode setEcon = requireMethod(node, "setEcon",
                "(Lcom/fs/starfarer/campaign/econ/reach/ReachEconomy;)V");

        boolean fieldPresent = hasExactSyntheticField(node, ECONOMY_PERSISTENT_STATE_FIELD,
                PERSISTENT_STATE_DESC, persistentStateAccess());
        boolean accessorPresent = hasExactPersistentAccessor(node, ECONOMY_PERSISTENT_ACCESSOR,
                "()Ljava/util/List;", true, ECONOMY_PERSISTENT_STATE_FIELD,
                config.economyStructureAuditMs, 0);
        boolean any = fieldPresent || accessorPresent
                || countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                ECONOMY_PERSISTENT_ACCESSOR, "()Ljava/util/List;") > 0
                || countCalls(paused, Opcodes.INVOKEVIRTUAL, node.name,
                ECONOMY_PERSISTENT_ACCESSOR, "()Ljava/util/List;") > 0
                || countPersistentStateInitializers(constructor, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD) > 0
                || countPersistentStateInitializers(readResolve, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD) > 0
                || countPersistentMarks(addMarket, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD) > 0
                || countPersistentMarks(removeMarket, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD) > 0
                || countPersistentMarks(setEcon, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD) > 0;
        if (any) {
            if (!fieldPresent || !accessorPresent) {
                throw mismatch("Economy persistent snapshot fields/accessor are partial or foreign");
            }
            requireCount("Economy.advance persistent market snapshot",
                    countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                            ECONOMY_PERSISTENT_ACCESSOR, "()Ljava/util/List;"), 1);
            requireCount("Economy paused persistent market snapshot",
                    countCalls(paused, Opcodes.INVOKEVIRTUAL, node.name,
                            ECONOMY_PERSISTENT_ACCESSOR, "()Ljava/util/List;"), 1);
            requireCount("Economy.advance retired getMarketsCopy",
                    countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                            "getMarketsCopy", "()Ljava/util/List;"), 0);
            requireCount("Economy paused retired getMarketsCopy",
                    countCalls(paused, Opcodes.INVOKEVIRTUAL, node.name,
                            "getMarketsCopy", "()Ljava/util/List;"), 0);
            requireCount("Economy constructor persistent-state init",
                    countPersistentStateInitializers(constructor, node.name,
                            ECONOMY_PERSISTENT_STATE_FIELD), 1);
            requireCount("Economy readResolve persistent-state init",
                    countPersistentStateInitializers(readResolve, node.name,
                            ECONOMY_PERSISTENT_STATE_FIELD), 1);
            requireCount("Economy addMarket epoch mark",
                    countPersistentMarks(addMarket, node.name,
                            ECONOMY_PERSISTENT_STATE_FIELD), 1);
            requireCount("Economy removeMarket epoch mark",
                    countPersistentMarks(removeMarket, node.name,
                            ECONOMY_PERSISTENT_STATE_FIELD), 1);
            requireCount("Economy setEcon epoch mark",
                    countPersistentMarks(setEcon, node.name,
                            ECONOMY_PERSISTENT_STATE_FIELD), 1);
            PatchState pausedSnapshotState = uniformPatchState(
                    "Economy persistent paused condition snapshot",
                    scratchScopeState(node.name, paused,
                            "Economy.advanceMarketConditionsWhenPaused"),
                    immediateArrayListIteratorSnapshotState(paused, 1,
                            "borrowEconomyCollectionSnapshot",
                            "Economy paused condition snapshot"));
            if (pausedSnapshotState != PatchState.PATCHED) {
                throw mismatch("Economy persistent paused condition snapshot is not patched");
            }
            throw already("Economy persistent snapshot/epoch postcondition matches");
        }

        requireOriginalEconomyMarketSnapshot(advance, "Economy.advance market snapshot");
        requireOriginalEconomyMarketSnapshot(paused, "Economy paused market snapshot");
        node.fields.add(new FieldNode(Opcodes.ASM8, persistentStateAccess(),
                ECONOMY_PERSISTENT_STATE_FIELD, PERSISTENT_STATE_DESC, null, null));
        node.methods.add(newEconomyPersistentAccessor(node.name));
        initializePersistentStateAfterSuper(node, constructor,
                ECONOMY_PERSISTENT_STATE_FIELD, "Economy constructor");
        initializePersistentStateAtEntry(readResolve, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD, "Economy.readResolve");
        replaceEconomyMarketSnapshotWithPersistentAccessor(advance, node.name,
                "Economy.advance market snapshot");
        replaceEconomyMarketSnapshotWithPersistentAccessor(paused, node.name,
                "Economy paused market snapshot");
        boolean pausedScopeInstalled = ensureScratchScope(node.name, paused,
                "Economy.advanceMarketConditionsWhenPaused");
        int pausedConditionSnapshots = replaceImmediateArrayListIteratorSnapshots(
                node.name, paused, 1, "borrowEconomyCollectionSnapshot",
                pausedScopeInstalled, "Economy paused condition snapshot");

        insertPersistentMarkAfterUniqueCall(addMarket, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/econ/reach/ReachEconomy", "addMarket",
                "(Lcom/fs/starfarer/api/campaign/econ/MarketAPI;)V",
                node.name, ECONOMY_PERSISTENT_STATE_FIELD, "Economy.addMarket");
        insertPersistentMarkAfterUniqueCall(removeMarket, Opcodes.INVOKEVIRTUAL,
                "com/fs/starfarer/campaign/econ/reach/ReachEconomy", "removeMarket",
                "(Lcom/fs/starfarer/api/campaign/econ/MarketAPI;)V",
                node.name, ECONOMY_PERSISTENT_STATE_FIELD, "Economy.removeMarket");
        FieldInsnNode econWrite = onlyField(setEcon, Opcodes.PUTFIELD, node.name,
                "econ", "Lcom/fs/starfarer/campaign/econ/reach/ReachEconomy;",
                "Economy.setEcon field write");
        insertPersistentMarkAfter(setEcon, econWrite, node.name,
                ECONOMY_PERSISTENT_STATE_FIELD);

        PatchReport report = new PatchReport();
        report.add("persistent Economy market snapshots, paused condition scratch, and structure epoch",
                8 + pausedConditionSnapshots);
        return report;
    }

    private PatchReport patchMarketPersistentSnapshots(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode constructor = requireMethod(node, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;ILcom/fs/starfarer/campaign/econ/Economy;)V");
        MethodNode readResolve = requireMethod(node, "readResolve", "()Ljava/lang/Object;");
        MethodNode clone = requireMethod(node, "clone", "()Lcom/fs/starfarer/campaign/econ/Market;");

        boolean conditionsField = hasExactSyntheticField(node, MARKET_CONDITIONS_STATE_FIELD,
                PERSISTENT_STATE_DESC, persistentStateAccess());
        boolean industriesField = hasExactSyntheticField(node, MARKET_INDUSTRIES_STATE_FIELD,
                PERSISTENT_STATE_DESC, persistentStateAccess());
        boolean conditionsAccessor = hasExactPersistentAccessor(node, MARKET_CONDITIONS_ACCESSOR,
                "(Ljava/util/Collection;)Ljava/util/ArrayList;", false,
                MARKET_CONDITIONS_STATE_FIELD, config.marketStructureAuditFrames, 1);
        boolean industriesAccessor = hasExactPersistentAccessor(node, MARKET_INDUSTRIES_ACCESSOR,
                "(Ljava/util/Collection;)Ljava/util/ArrayList;", false,
                MARKET_INDUSTRIES_STATE_FIELD, config.marketStructureAuditFrames, 2);
        boolean any = conditionsField || industriesField || conditionsAccessor || industriesAccessor
                || countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                MARKET_CONDITIONS_ACCESSOR,
                "(Ljava/util/Collection;)Ljava/util/ArrayList;") > 0
                || countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                MARKET_INDUSTRIES_ACCESSOR,
                "(Ljava/util/Collection;)Ljava/util/ArrayList;") > 0;
        if (any) {
            if (!conditionsField || !industriesField || !conditionsAccessor || !industriesAccessor) {
                throw mismatch("Market persistent snapshot fields/accessors are partial or foreign");
            }
            requireCount("Market persistent conditions snapshot call",
                    countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                            MARKET_CONDITIONS_ACCESSOR,
                            "(Ljava/util/Collection;)Ljava/util/ArrayList;"), 1);
            requireCount("Market persistent industries snapshot call",
                    countCalls(advance, Opcodes.INVOKEVIRTUAL, node.name,
                            MARKET_INDUSTRIES_ACCESSOR,
                            "(Ljava/util/Collection;)Ljava/util/ArrayList;"), 1);
            requireCount("Market retired ArrayList(Collection) snapshots",
                    countImmediateArrayListIteratorSnapshots(advance), 0);
            requireCount("Market constructor persistent-state initializers",
                    countPersistentStateInitializers(constructor, node.name,
                            MARKET_CONDITIONS_STATE_FIELD)
                            + countPersistentStateInitializers(constructor, node.name,
                            MARKET_INDUSTRIES_STATE_FIELD), 2);
            requireCount("Market readResolve persistent-state initializers",
                    countPersistentStateInitializers(readResolve, node.name,
                            MARKET_CONDITIONS_STATE_FIELD)
                            + countPersistentStateInitializers(readResolve, node.name,
                            MARKET_INDUSTRIES_STATE_FIELD), 2);
            requireCount("Market clone persistent-state initializers",
                    countPersistentStateInitializers(clone, node.name,
                            MARKET_CONDITIONS_STATE_FIELD)
                            + countPersistentStateInitializers(clone, node.name,
                            MARKET_INDUSTRIES_STATE_FIELD), 2);
            requireMarketPersistentMutationMarks(node);
            throw already("Market persistent snapshot/epoch postcondition matches");
        }

        requireCount("Market original condition/industry snapshots",
                countImmediateArrayListIteratorSnapshots(advance), 2);
        node.fields.add(new FieldNode(Opcodes.ASM8, persistentStateAccess(),
                MARKET_CONDITIONS_STATE_FIELD, PERSISTENT_STATE_DESC, null, null));
        node.fields.add(new FieldNode(Opcodes.ASM8, persistentStateAccess(),
                MARKET_INDUSTRIES_STATE_FIELD, PERSISTENT_STATE_DESC, null, null));
        node.methods.add(newMarketPersistentAccessor(node.name, MARKET_CONDITIONS_ACCESSOR,
                MARKET_CONDITIONS_STATE_FIELD, config.marketStructureAuditFrames, 1));
        node.methods.add(newMarketPersistentAccessor(node.name, MARKET_INDUSTRIES_ACCESSOR,
                MARKET_INDUSTRIES_STATE_FIELD, config.marketStructureAuditFrames, 2));
        initializePersistentStateAfterSuper(node, constructor,
                MARKET_CONDITIONS_STATE_FIELD, "Market constructor conditions");
        initializePersistentStateAfterAnchor(constructor,
                lastPersistentStateInitializer(constructor, node.name,
                        MARKET_CONDITIONS_STATE_FIELD), node.name,
                MARKET_INDUSTRIES_STATE_FIELD);
        initializePersistentStateAtEntry(readResolve, node.name,
                MARKET_INDUSTRIES_STATE_FIELD, "Market.readResolve industries");
        initializePersistentStateAtEntry(readResolve, node.name,
                MARKET_CONDITIONS_STATE_FIELD, "Market.readResolve conditions");
        initializeClonePersistentStates(clone, node.name,
                MARKET_CONDITIONS_STATE_FIELD, MARKET_INDUSTRIES_STATE_FIELD);
        replaceMarketAdvanceSnapshots(advance, node.name);
        patchMarketStructureMutators(node);

        PatchReport report = new PatchReport();
        report.add("persistent Market condition/industry snapshots and epochs", 14);
        return report;
    }

    private static int persistentStateAccess() {
        return Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC;
    }

    private static boolean hasExactSyntheticField(ClassNode node, String name,
                                                   String desc, int access) {
        FieldNode found = null;
        for (FieldNode field : node.fields) {
            if (!field.name.equals(name)) continue;
            if (found != null) throw mismatch("duplicate synthetic field " + node.name + "." + name);
            found = field;
        }
        if (found == null) return false;
        if (!found.desc.equals(desc) || found.access != access
                || found.signature != null || found.value != null) {
            throw mismatch("foreign or malformed synthetic field " + node.name + "." + name);
        }
        return true;
    }

    private static boolean hasExactPersistentAccessor(ClassNode node, String name, String desc,
                                                      boolean timed, String stateField,
                                                      int auditValue, int kind) {
        List<MethodNode> methods = methods(node, name, desc);
        if (methods.isEmpty()) return false;
        if (methods.size() != 1) throw mismatch("duplicate persistent accessor " + name + desc);
        MethodNode method = methods.get(0);
        int expectedAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
        if (method.access != expectedAccess || method.signature != null
                || (method.exceptions != null && !method.exceptions.isEmpty())) {
            throw mismatch("foreign persistent accessor metadata " + name + desc);
        }
        List<AbstractInsnNode> code = meaningfulInstructions(method);
        int expected = timed ? 8 : 7;
        requireCount(name + " instruction count", code.size(), expected);
        requireVar(code.get(0), Opcodes.ALOAD, 0, name + " state owner");
        requireField(code.get(1), Opcodes.GETFIELD, node.name, stateField,
                PERSISTENT_STATE_DESC, name + " state field");
        int cursor = 2;
        if (timed) {
            requireVar(code.get(cursor++), Opcodes.ALOAD, 0, name + " source owner");
            requireCall(asMethod(code.get(cursor++), name + " source call"),
                    Opcodes.INVOKEVIRTUAL, node.name, "getMarkets", "()Ljava/util/List;",
                    name + " source call");
        } else {
            requireVar(code.get(cursor++), Opcodes.ALOAD, 1, name + " source argument");
        }
        if (!isIntegerLdc(code.get(cursor++), auditValue)
                || !isIntegerLdc(code.get(cursor++), kind)) {
            throw mismatch(name + " audit/kind constants changed");
        }
        String hookName = timed ? "borrowPersistentSnapshotTimed"
                : "borrowPersistentSnapshotFrames";
        requireCall(asMethod(code.get(cursor++), name + " hook"), Opcodes.INVOKESTATIC,
                HOOKS, hookName,
                "(Ljava/lang/Object;Ljava/util/Collection;II)Ljava/util/ArrayList;",
                name + " hook");
        if (code.get(cursor).getOpcode() != Opcodes.ARETURN) {
            throw mismatch(name + " no longer returns the persistent snapshot");
        }
        return true;
    }

    private MethodNode newEconomyPersistentAccessor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM8,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                ECONOMY_PERSISTENT_ACCESSOR, "()Ljava/util/List;", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner,
                ECONOMY_PERSISTENT_STATE_FIELD, PERSISTENT_STATE_DESC));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner,
                "getMarkets", "()Ljava/util/List;", false));
        method.instructions.add(pushInt(config.economyStructureAuditMs));
        method.instructions.add(pushInt(0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "borrowPersistentSnapshotTimed",
                "(Ljava/lang/Object;Ljava/util/Collection;II)Ljava/util/ArrayList;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode newMarketPersistentAccessor(String owner, String name,
                                                           String stateField,
                                                           int auditFrames, int kind) {
        MethodNode method = new MethodNode(Opcodes.ASM8,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, name,
                "(Ljava/util/Collection;)Ljava/util/ArrayList;", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner,
                stateField, PERSISTENT_STATE_DESC));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(pushInt(auditFrames));
        method.instructions.add(pushInt(kind));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "borrowPersistentSnapshotFrames",
                "(Ljava/lang/Object;Ljava/util/Collection;II)Ljava/util/ArrayList;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(value == -1 ? Opcodes.ICONST_M1 : Opcodes.ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        }
        return new LdcInsnNode(value);
    }

    private static void requireOriginalEconomyMarketSnapshot(MethodNode method, String label) {
        List<MethodInsnNode> calls = calls(method, Opcodes.INVOKEVIRTUAL,
                ECONOMY, "getMarketsCopy", "()Ljava/util/List;");
        requireCount(label, calls.size(), 1);
        MethodInsnNode call = calls.get(0);
        Frame<SourceValue>[] frames = sourceFrames(ECONOMY, method);
        if (!sourceIsLocal(receiverSource(method, call, frames), Opcodes.ALOAD, 0)) {
            throw mismatch(label + " receiver is not this");
        }
        MethodInsnNode iterator = asMethod(nextMeaningful(call), label + " iterator");
        requireCall(iterator, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                "()Ljava/util/Iterator;", label + " iterator");
    }

    private static void replaceEconomyMarketSnapshotWithPersistentAccessor(
            MethodNode method, String owner, String label) {
        MethodInsnNode call = only(calls(method, Opcodes.INVOKEVIRTUAL,
                owner, "getMarketsCopy", "()Ljava/util/List;"), label);
        call.owner = owner;
        call.name = ECONOMY_PERSISTENT_ACCESSOR;
        call.desc = "()Ljava/util/List;";
        call.itf = false;
    }

    private static void initializePersistentStateAfterSuper(ClassNode node,
                                                            MethodNode constructor,
                                                            String stateField,
                                                            String label) {
        List<MethodInsnNode> supers = calls(constructor, Opcodes.INVOKESPECIAL,
                node.superName, "<init>", "()V");
        requireCount(label + " super constructor", supers.size(), 1);
        initializePersistentStateAfterAnchor(constructor, supers.get(0), node.name, stateField);
    }

    private static void initializePersistentStateAtEntry(MethodNode method, String owner,
                                                         String stateField, String label) {
        AbstractInsnNode first = firstMeaningful(method);
        if (first == null) throw mismatch(label + " has no executable body");
        InsnList init = persistentStateInitializer(owner, stateField, 0);
        method.instructions.insertBefore(first, init);
    }

    private static void initializePersistentStateAfterAnchor(MethodNode method,
                                                             AbstractInsnNode anchor,
                                                             String owner, String stateField) {
        method.instructions.insert(anchor, persistentStateInitializer(owner, stateField, 0));
    }

    private static InsnList persistentStateInitializer(String owner, String stateField,
                                                       int ownerLocal) {
        InsnList init = new InsnList();
        init.add(new VarInsnNode(Opcodes.ALOAD, ownerLocal));
        init.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "newPersistentSnapshotState", "()Ljava/lang/Object;", false));
        init.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, stateField,
                PERSISTENT_STATE_DESC));
        return init;
    }

    private static FieldInsnNode lastPersistentStateInitializer(MethodNode method,
                                                                String owner,
                                                                String stateField) {
        FieldInsnNode result = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(owner) && field.name.equals(stateField)
                    && field.desc.equals(PERSISTENT_STATE_DESC)) result = field;
        }
        if (result == null) throw mismatch("missing persistent state initializer " + stateField);
        return result;
    }

    private static int countPersistentStateInitializers(MethodNode method,
                                                        String owner, String stateField) {
        int count = 0;
        for (MethodInsnNode call : calls(method, Opcodes.INVOKESTATIC, HOOKS,
                "newPersistentSnapshotState", "()Ljava/lang/Object;")) {
            AbstractInsnNode next = nextMeaningful(call);
            if (next instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(owner) && field.name.equals(stateField)
                    && field.desc.equals(PERSISTENT_STATE_DESC)) count++;
        }
        return count;
    }

    private static void insertPersistentMarkAfterUniqueCall(MethodNode method, int opcode,
                                                            String callOwner, String callName,
                                                            String callDesc, String owner,
                                                            String stateField, String label) {
        MethodInsnNode call = only(calls(method, opcode, callOwner, callName, callDesc), label);
        AbstractInsnNode anchor = call;
        AbstractInsnNode next = nextMeaningful(call);
        if (next != null && next.getOpcode() == Opcodes.POP) anchor = next;
        insertPersistentMarkAfter(method, anchor, owner, stateField);
    }

    private static void insertPersistentMarkAfter(MethodNode method, AbstractInsnNode anchor,
                                                  String owner, String stateField) {
        InsnList mark = new InsnList();
        mark.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mark.add(new FieldInsnNode(Opcodes.GETFIELD, owner, stateField,
                PERSISTENT_STATE_DESC));
        mark.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "markPersistentSnapshotStructure", "(Ljava/lang/Object;)V", false));
        method.instructions.insert(anchor, mark);
    }

    private static int countPersistentMarks(MethodNode method, String owner,
                                            String stateField) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        int count = 0;
        for (MethodInsnNode call : calls(method, Opcodes.INVOKESTATIC, HOOKS,
                "markPersistentSnapshotStructure", "(Ljava/lang/Object;)V")) {
            FieldInsnNode field = optionalSourceField(argumentSource(method, call, frames, 0),
                    Opcodes.GETFIELD, owner, PERSISTENT_STATE_DESC);
            if (field != null && field.name.equals(stateField)) count++;
        }
        return count;
    }

    private static FieldInsnNode onlyField(MethodNode method, int opcode, String owner,
                                           String name, String desc, String label) {
        List<FieldInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == opcode
                    && field.owner.equals(owner) && field.name.equals(name)
                    && field.desc.equals(desc)) result.add(field);
        }
        if (result.size() != 1) {
            throw mismatch(label + " expected once, found " + result.size());
        }
        return result.get(0);
    }

    private static int countImmediateArrayListIteratorSnapshots(MethodNode method) {
        int count = 0;
        for (MethodInsnNode constructor : calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V")) {
            AbstractInsnNode next = nextMeaningful(constructor);
            if (next instanceof MethodInsnNode iterator
                    && callMatches(iterator, Opcodes.INVOKEVIRTUAL,
                    "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;")) count++;
        }
        return count;
    }

    private static void replaceMarketAdvanceSnapshots(MethodNode method, String owner) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        List<PersistentSnapshotPlan> plans = new ArrayList<>();
        for (MethodInsnNode constructor : calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V")) {
            AbstractInsnNode next = nextMeaningful(constructor);
            if (!(next instanceof MethodInsnNode iterator)
                    || !callMatches(iterator, Opcodes.INVOKEVIRTUAL,
                    "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;")) continue;
            SourceValue source = argumentSource(method, constructor, frames, 0);
            String accessor;
            AbstractInsnNode producer;
            if (sourceIsMethod(source, Opcodes.INVOKEVIRTUAL, owner,
                    "getConditions", "()Ljava/util/List;")) {
                accessor = MARKET_CONDITIONS_ACCESSOR;
                producer = source.insns.iterator().next();
            } else {
                FieldInsnNode field = optionalSourceField(source, Opcodes.GETFIELD,
                        owner, "Ljava/util/List;");
                if (field == null || !field.name.equals("industries")) {
                    throw mismatch("Market.advance snapshot source is not conditions or industries");
                }
                accessor = MARKET_INDUSTRIES_ACCESSOR;
                producer = field;
            }
            AbstractInsnNode receiverLoad = previousMeaningful(producer);
            if (!(receiverLoad instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.ALOAD || load.var != 0) {
                throw mismatch("Market.advance snapshot source receiver is not this");
            }
            plans.add(new PersistentSnapshotPlan(constructor,
                    allocationPair(method, constructor, frames,
                            "java/util/ArrayList", "Market.advance persistent snapshot"),
                    receiverLoad, accessor));
        }
        requireCount("Market.advance persistent snapshot plans", plans.size(), 2);
        Set<String> accessors = new HashSet<>();
        for (PersistentSnapshotPlan plan : plans) accessors.add(plan.accessor);
        if (!accessors.equals(Set.of(MARKET_CONDITIONS_ACCESSOR,
                MARKET_INDUSTRIES_ACCESSOR))) {
            throw mismatch("Market.advance persistent snapshot kinds are incomplete");
        }
        for (PersistentSnapshotPlan plan : plans) {
            method.instructions.remove(plan.allocation.allocation());
            method.instructions.remove(plan.allocation.duplicate());
            method.instructions.insertBefore(plan.sourceReceiver,
                    new VarInsnNode(Opcodes.ALOAD, 0));
            MethodInsnNode call = plan.constructor;
            call.setOpcode(Opcodes.INVOKEVIRTUAL);
            call.owner = owner;
            call.name = plan.accessor;
            call.desc = "(Ljava/util/Collection;)Ljava/util/ArrayList;";
            call.itf = false;
        }
    }

    private static void initializeClonePersistentStates(MethodNode clone, String owner,
                                                        String conditionsState,
                                                        String industriesState) {
        VarInsnNode store = null;
        for (AbstractInsnNode insn : clone.instructions.toArray()) {
            if (!(insn instanceof VarInsnNode var) || var.getOpcode() != Opcodes.ASTORE
                    || var.var != 1) continue;
            AbstractInsnNode previous = previousMeaningful(var);
            if (previous instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                    && cast.desc.equals(owner)) {
                store = var;
                break;
            }
        }
        if (store == null) throw mismatch("Market.clone result local changed");
        InsnList init = new InsnList();
        init.add(persistentStateInitializer(owner, conditionsState, 1));
        init.add(persistentStateInitializer(owner, industriesState, 1));
        clone.instructions.insert(store, init);
    }

    private static void patchMarketStructureMutators(ClassNode node) {
        String list = "java/util/List";
        String iterator = "java/util/Iterator";
        MethodNode addConditionWithParam = requireMethod(node, "addCondition",
                "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;");
        MethodNode addConditionObject = requireMethod(node, "addCondition",
                "(Lcom/fs/starfarer/api/campaign/econ/MarketConditionAPI;)V");
        MethodNode removeCondition = requireMethod(node, "removeCondition",
                "(Ljava/lang/String;)V");
        MethodNode removeSpecific = requireMethod(node, "removeSpecificCondition",
                "(Ljava/lang/String;)V");
        MethodNode addIndustry = requireMethod(node, "addIndustry",
                "(Ljava/lang/String;Ljava/util/List;)V");
        MethodNode removeIndustry = requireMethod(node, "removeIndustry",
                "(Ljava/lang/String;Lcom/fs/starfarer/api/campaign/econ/MarketAPI$MarketInteractionMode;Z)V");

        insertPersistentMarkAfterUniqueCall(addConditionWithParam, Opcodes.INVOKEINTERFACE,
                list, "add", "(Ljava/lang/Object;)Z", node.name,
                MARKET_CONDITIONS_STATE_FIELD, "Market.addCondition(String,Object)");
        insertPersistentMarkAfterUniqueCall(addConditionObject, Opcodes.INVOKEINTERFACE,
                list, "add", "(Ljava/lang/Object;)Z", node.name,
                MARKET_CONDITIONS_STATE_FIELD, "Market.addCondition(MarketConditionAPI)");
        insertPersistentMarkAfterUniqueCall(removeCondition, Opcodes.INVOKEINTERFACE,
                iterator, "remove", "()V", node.name,
                MARKET_CONDITIONS_STATE_FIELD, "Market.removeCondition");
        insertPersistentMarkAfterUniqueCall(removeSpecific, Opcodes.INVOKEINTERFACE,
                iterator, "remove", "()V", node.name,
                MARKET_CONDITIONS_STATE_FIELD, "Market.removeSpecificCondition");
        insertPersistentMarkAfterUniqueCall(addIndustry, Opcodes.INVOKEINTERFACE,
                list, "add", "(Ljava/lang/Object;)Z", node.name,
                MARKET_INDUSTRIES_STATE_FIELD, "Market.addIndustry");
        insertPersistentMarkAfterUniqueCall(removeIndustry, Opcodes.INVOKEINTERFACE,
                iterator, "remove", "()V", node.name,
                MARKET_INDUSTRIES_STATE_FIELD, "Market.removeIndustry");
    }

    private static void requireMarketPersistentMutationMarks(ClassNode node) {
        requireCount("Market condition epoch marks",
                countPersistentMarks(requireMethod(node, "addCondition",
                                "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"),
                        node.name, MARKET_CONDITIONS_STATE_FIELD)
                        + countPersistentMarks(requireMethod(node, "addCondition",
                                "(Lcom/fs/starfarer/api/campaign/econ/MarketConditionAPI;)V"),
                        node.name, MARKET_CONDITIONS_STATE_FIELD)
                        + countPersistentMarks(requireMethod(node, "removeCondition",
                                "(Ljava/lang/String;)V"),
                        node.name, MARKET_CONDITIONS_STATE_FIELD)
                        + countPersistentMarks(requireMethod(node, "removeSpecificCondition",
                                "(Ljava/lang/String;)V"),
                        node.name, MARKET_CONDITIONS_STATE_FIELD), 4);
        requireCount("Market industry epoch marks",
                countPersistentMarks(requireMethod(node, "addIndustry",
                                "(Ljava/lang/String;Ljava/util/List;)V"),
                        node.name, MARKET_INDUSTRIES_STATE_FIELD)
                        + countPersistentMarks(requireMethod(node, "removeIndustry",
                                "(Ljava/lang/String;Lcom/fs/starfarer/api/campaign/econ/MarketAPI$MarketInteractionMode;Z)V"),
                        node.name, MARKET_INDUSTRIES_STATE_FIELD), 2);
    }


    private PatchReport patchEconomySnapshots(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode paused = requireMethod(node, "advanceMarketConditionsWhenPaused", "(F)V");

        PatchState state = uniformPatchState("Economy snapshot reuse",
                scratchScopeState(node.name, advance, "Economy.advance"),
                economyMarketsSnapshotState(advance, "Economy.advance market snapshot"),
                scratchScopeState(node.name, paused,
                        "Economy.advanceMarketConditionsWhenPaused"),
                economyMarketsSnapshotState(paused, "Economy paused market snapshot"),
                immediateArrayListIteratorSnapshotState(paused, 1,
                        "borrowEconomyCollectionSnapshot",
                        "Economy paused condition snapshot"));
        if (state == PatchState.PATCHED) {
            throw already("all Economy snapshot hooks and scopes satisfy their postconditions");
        }

        boolean advanceScopeInstalled = ensureScratchScope(node.name, advance, "Economy.advance");
        int changed = replaceEconomyMarketsSnapshot(advance, advanceScopeInstalled,
                "Economy.advance market snapshot");
        boolean pausedScopeInstalled = ensureScratchScope(node.name, paused,
                "Economy.advanceMarketConditionsWhenPaused");
        changed += replaceEconomyMarketsSnapshot(paused, pausedScopeInstalled,
                "Economy paused market snapshot");
        changed += replaceImmediateArrayListIteratorSnapshots(node.name, paused, 1,
                "borrowEconomyCollectionSnapshot", pausedScopeInstalled,
                "Economy paused condition snapshot");

        PatchReport report = new PatchReport();
        report.add("reusable Economy market/condition snapshots", changed);
        return report;
    }

    private PatchReport patchPlanetConditionMarketAdvanceBridge(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        String market = "com/fs/starfarer/api/campaign/econ/MarketAPI";
        String hookDesc = "(L" + market + ";F)V";
        List<MethodInsnNode> originals = calls(advance, Opcodes.INVOKEINTERFACE,
                market, "advance", "(F)V");
        List<MethodInsnNode> hooks = calls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "advancePlanetConditionMarketScheduled", hookDesc);
        boolean originalState = originals.size() == 1 && hooks.isEmpty();
        boolean patchedState = originals.isEmpty() && hooks.size() == 1;
        if (!originalState && !patchedState) {
            throw mismatch("BaseCampaignEntity planet-condition Market.advance call is missing, "
                    + "duplicated, or partially patched");
        }
        requireCount("BaseCampaignEntity planet-condition predicate",
                countCalls(advance, Opcodes.INVOKEINTERFACE, market,
                        "isPlanetConditionMarketOnly", "()Z"), 1);

        MethodInsnNode target = originalState ? originals.get(0) : hooks.get(0);
        AbstractInsnNode amount = previousMeaningful(target);
        AbstractInsnNode fieldInsn = previousMeaningful(amount);
        AbstractInsnNode owner = previousMeaningful(fieldInsn);
        if (!(amount instanceof VarInsnNode amountLoad)
                || amountLoad.getOpcode() != Opcodes.FLOAD || amountLoad.var != 1
                || !(fieldInsn instanceof FieldInsnNode field)
                || field.getOpcode() != Opcodes.GETFIELD
                || !field.owner.equals(node.name)
                || !field.desc.equals("L" + market + ";")
                || !(owner instanceof VarInsnNode ownerLoad)
                || ownerLoad.getOpcode() != Opcodes.ALOAD || ownerLoad.var != 0) {
            throw mismatch("BaseCampaignEntity planet-condition Market.advance receiver/amount "
                    + "shape changed structurally");
        }

        AbstractInsnNode predicateBranchInsn = previousMeaningful(owner);
        AbstractInsnNode predicateInsn = previousMeaningful(predicateBranchInsn);
        AbstractInsnNode predicateFieldInsn = previousMeaningful(predicateInsn);
        AbstractInsnNode predicateOwnerInsn = previousMeaningful(predicateFieldInsn);
        AbstractInsnNode nullBranchInsn = previousMeaningful(predicateOwnerInsn);
        AbstractInsnNode nullFieldInsn = previousMeaningful(nullBranchInsn);
        AbstractInsnNode nullOwnerInsn = previousMeaningful(nullFieldInsn);
        AbstractInsnNode join = nextMeaningful(target);
        if (!(predicateBranchInsn instanceof JumpInsnNode predicateBranch)
                || predicateBranch.getOpcode() != Opcodes.IFEQ
                || !(predicateInsn instanceof MethodInsnNode predicate)
                || !callMatches(predicate, Opcodes.INVOKEINTERFACE, market,
                        "isPlanetConditionMarketOnly", "()Z")
                || !(predicateFieldInsn instanceof FieldInsnNode predicateField)
                || predicateField.getOpcode() != Opcodes.GETFIELD
                || !predicateField.owner.equals(field.owner)
                || !predicateField.name.equals(field.name)
                || !predicateField.desc.equals(field.desc)
                || !(predicateOwnerInsn instanceof VarInsnNode predicateOwner)
                || predicateOwner.getOpcode() != Opcodes.ALOAD || predicateOwner.var != 0
                || !(nullBranchInsn instanceof JumpInsnNode nullBranch)
                || nullBranch.getOpcode() != Opcodes.IFNULL
                || !(nullFieldInsn instanceof FieldInsnNode nullField)
                || nullField.getOpcode() != Opcodes.GETFIELD
                || !nullField.owner.equals(field.owner)
                || !nullField.name.equals(field.name)
                || !nullField.desc.equals(field.desc)
                || !(nullOwnerInsn instanceof VarInsnNode nullOwner)
                || nullOwner.getOpcode() != Opcodes.ALOAD || nullOwner.var != 0
                || join == null
                || nextMeaningful(predicateBranch.label) != join
                || nextMeaningful(nullBranch.label) != join
                || hasExternalControlFlowEntry(advance, nullOwnerInsn, target)) {
            throw mismatch("BaseCampaignEntity planet-condition predicate/null guards are not "
                    + "the exact control-flow owner of the Market.advance call");
        }

        if (patchedState) {
            throw already("planet-condition Market.advance bridge postcondition matches");
        }

        advance.instructions.set(target, new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "advancePlanetConditionMarketScheduled", hookDesc, false));
        requireCount("planet-condition market bridge",
                countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                        "advancePlanetConditionMarketScheduled", hookDesc), 1);
        requireCount("retired direct planet-condition Market.advance",
                countCalls(advance, Opcodes.INVOKEINTERFACE, market,
                        "advance", "(F)V"), 0);

        PatchReport report = new PatchReport();
        report.add("planet-condition market scheduler/observer bridge", 1);
        return report;
    }

    /**
     * Supplies the shared once-per-Economy-frame clock when the planet-condition
     * scheduler is enabled without the central remote-market scheduler.
     */
    private PatchReport patchPlanetConditionMarketFrameClock(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        PatchState scopeState = scratchScopeState(node.name, advance,
                "Economy.advance planet-condition frame clock");
        int hooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "beginRemoteMarketFrame", "()V");
        if (hooks == 1) {
            if (scopeState != PatchState.PATCHED) {
                throw mismatch("planet-condition frame clock exists without its scratch scope");
            }
            AbstractInsnNode first = firstMeaningful(advance);
            AbstractInsnNode expected = first;
            if (first instanceof MethodInsnNode scratch
                    && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                    "beginScratchScope", "()V")) {
                expected = nextMeaningful(first);
            }
            if (!(expected instanceof MethodInsnNode begin)
                    || !callMatches(begin, Opcodes.INVOKESTATIC, HOOKS,
                    "beginRemoteMarketFrame", "()V")) {
                throw mismatch("planet-condition frame clock is not at Economy.advance entry");
            }
            throw already("planet-condition frame clock postcondition matches");
        }
        requireCount("planet-condition frame clock hooks", hooks, 0);
        boolean scopeInstalled = ensureScratchScope(node.name, advance,
                "Economy.advance planet-condition frame clock");

        MethodInsnNode beginHook = new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "beginRemoteMarketFrame", "()V", false);
        AbstractInsnNode entry = firstMeaningful(advance);
        if (entry instanceof MethodInsnNode scratch
                && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                "beginScratchScope", "()V")) {
            advance.instructions.insert(entry, beginHook);
        } else {
            advance.instructions.insertBefore(entry, beginHook);
        }

        PatchReport report = new PatchReport();
        report.add("planet-condition scheduler frame clock", 1);
        report.add("planet-condition scheduler reentrancy scope", scopeInstalled ? 1 : 0);
        return report;
    }

    private PatchReport patchRemoteMarketScheduler(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        // The scheduler uses the existing re-entrant scratch-scope depth as its
        // central-loop ownership guard. Install it even when economy snapshot
        // reuse is disabled so nested Economy.advance() calls fail open without
        // overwriting the outer scheduling frame.
        PatchState scopeState = scratchScopeState(node.name, advance,
                "Economy.advance remote-market scheduler");
        String market = "com/fs/starfarer/api/campaign/econ/MarketAPI";
        String scheduledDesc = "(L" + market + ";F)V";
        List<MethodInsnNode> originals = calls(advance, Opcodes.INVOKEINTERFACE,
                market, "advance", "(F)V");
        int beginHooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "beginRemoteMarketFrame", "()V");
        int scheduleHooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "advanceMarketScheduled", scheduledDesc);

        boolean patched = originals.isEmpty() && beginHooks == 1 && scheduleHooks == 1;
        if (patched) {
            if (scopeState != PatchState.PATCHED) {
                throw mismatch("Economy remote-market scheduler hooks exist without their "
                        + "scratch scope");
            }
            AbstractInsnNode first = firstMeaningful(advance);
            AbstractInsnNode expected = first;
            if (first instanceof MethodInsnNode scratch
                    && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                    "beginScratchScope", "()V")) {
                expected = nextMeaningful(first);
            }
            if (!(expected instanceof MethodInsnNode begin)
                    || !callMatches(begin, Opcodes.INVOKESTATIC, HOOKS,
                    "beginRemoteMarketFrame", "()V")) {
                throw mismatch("Economy remote-market frame hook is not at the verified entry point");
            }
            requireEconomyMarketAdvanceOperands(only(calls(advance, Opcodes.INVOKESTATIC,
                    HOOKS, "advanceMarketScheduled", scheduledDesc),
                    "Economy scheduled market call"));
            throw already("Economy remote-market scheduler hooks and call-site postcondition match");
        }

        if (originals.size() != 1 || beginHooks != 0 || scheduleHooks != 0) {
            throw mismatch("Economy market-advance call is missing, duplicated, or partially patched");
        }
        requireEconomyMarketAdvanceOperands(originals.get(0));
        boolean scopeInstalled = ensureScratchScope(node.name, advance,
                "Economy.advance remote-market scheduler");

        MethodInsnNode beginHook = new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "beginRemoteMarketFrame", "()V", false);
        AbstractInsnNode entry = firstMeaningful(advance);
        if (entry instanceof MethodInsnNode scratch
                && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                "beginScratchScope", "()V")) {
            advance.instructions.insert(entry, beginHook);
        } else {
            advance.instructions.insertBefore(entry, beginHook);
        }
        MethodInsnNode original = originals.get(0);
        advance.instructions.set(original, new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "advanceMarketScheduled", scheduledDesc, false));

        requireCount("remote-market frame hooks",
                countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                        "beginRemoteMarketFrame", "()V"), 1);
        requireCount("remote-market scheduled calls",
                countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                        "advanceMarketScheduled", scheduledDesc), 1);

        PatchReport report = new PatchReport();
        report.add("staggered central market loop", 1);
        report.add("remote-market reentrancy scope", scopeInstalled ? 1 : 0);
        return report;
    }

    private static void requireEconomyMarketAdvanceOperands(MethodInsnNode call) {
        AbstractInsnNode amount = previousMeaningful(call);
        AbstractInsnNode market = previousMeaningful(amount);
        if (!(amount instanceof VarInsnNode amountLoad)
                || amountLoad.getOpcode() != Opcodes.FLOAD || amountLoad.var != 1
                || !(market instanceof VarInsnNode marketLoad)
                || marketLoad.getOpcode() != Opcodes.ALOAD || marketLoad.var != 2) {
            throw mismatch("Economy market scheduler receiver/amount operands changed structurally");
        }
    }

    private PatchReport patchDirectMarketObservationEntry(ClassNode node) {
        MethodNode method = requireMethod(node, "advance", "(F)V");
        String descriptor = "(Lcom/fs/starfarer/api/campaign/econ/MarketAPI;F)V";
        int existing = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "observeMarketAdvanceEntry", descriptor);
        if (existing == 1) {
            AbstractInsnNode expected = firstMeaningful(method);
            if (expected instanceof MethodInsnNode scratch
                    && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                    "beginScratchScope", "()V")) {
                expected = nextMeaningful(expected);
            }
            if (!(expected instanceof VarInsnNode owner)
                    || owner.getOpcode() != Opcodes.ALOAD || owner.var != 0
                    || !(nextMeaningful(expected) instanceof VarInsnNode amount)
                    || amount.getOpcode() != Opcodes.FLOAD || amount.var != 1
                    || !(nextMeaningful(amount) instanceof MethodInsnNode hook)
                    || !callMatches(hook, Opcodes.INVOKESTATIC, HOOKS,
                    "observeMarketAdvanceEntry", descriptor)) {
                throw mismatch("Market.advance observation entry hook is not at the verified entry point");
            }
            throw already("Market.advance observation entry hook is present");
        }
        requireCount("Market.advance observation entry hook", existing, 0);

        InsnList prologue = new InsnList();
        prologue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prologue.add(new VarInsnNode(Opcodes.FLOAD, 1));
        prologue.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "observeMarketAdvanceEntry", descriptor, false));
        AbstractInsnNode entry = firstMeaningful(method);
        if (entry instanceof MethodInsnNode scratch
                && callMatches(scratch, Opcodes.INVOKESTATIC, HOOKS,
                "beginScratchScope", "()V")) {
            method.instructions.insert(entry, prologue);
        } else {
            method.instructions.insertBefore(entry, prologue);
        }

        PatchReport report = new PatchReport();
        report.add("marketAdvanceEntry", 1);
        return report;
    }

    private PatchReport patchMarketSnapshots(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        PatchState state = uniformPatchState("Market snapshot reuse",
                scratchScopeState(node.name, advance, "Market.advance"),
                immediateArrayListIteratorSnapshotState(advance, 2,
                        "borrowEconomyCollectionSnapshot",
                        "Market condition/industry snapshots"));
        if (state == PatchState.PATCHED) {
            throw already("all Market snapshot hooks and scopes satisfy their postconditions");
        }
        boolean scopeInstalled = ensureScratchScope(node.name, advance, "Market.advance");
        int changed = replaceImmediateArrayListIteratorSnapshots(node.name, advance, 2,
                "borrowEconomyCollectionSnapshot", scopeInstalled,
                "Market condition/industry snapshots");
        PatchReport report = new PatchReport();
        report.add("reusable Market condition/industry snapshots", changed);
        return report;
    }


    /**
     * Replaces Market.advance()'s unconditional full commodity-maintenance loop
     * with an active-set runtime. The original four stat advances and event-mod
     * reapplication remain available as a fail-open path in the hook.
     */
    private PatchReport patchMarketCommodityTemporalFastPath(ClassNode node) {
        final String stateField = "smo$commodityTemporalState";
        final int stateAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        final String hookDesc = "(Ljava/lang/Object;Ljava/lang/Object;F)Ljava/lang/Object;";
        MethodNode advance = requireMethod(node, "advance", "(F)V");

        FieldNode existing = null;
        for (FieldNode field : node.fields) {
            if (!field.name.equals(stateField)) continue;
            if (existing != null) throw mismatch("duplicate Market commodity-temporal state field");
            existing = field;
        }
        if (existing != null) {
            if (!existing.desc.equals("Ljava/lang/Object;") || existing.access != stateAccess
                    || existing.signature != null || existing.value != null) {
                throw mismatch("Market commodity-temporal state field is foreign");
            }
            requireOptimizedMarketCommodityTemporalShape(node, advance, stateField, hookDesc);
            throw already("Market commodity temporal fast-path postcondition matches");
        }
        if (countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "advanceMarketCommodityTemporalState", hookDesc) != 0) {
            throw mismatch("foreign Market commodity-temporal hook without owned state field");
        }

        MarketCommodityLoop loop = requireOriginalMarketCommodityLoop(node, advance);
        node.fields.add(new FieldNode(Opcodes.ASM8, stateAccess,
                stateField, "Ljava/lang/Object;", null, null));

        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0)); // PUTFIELD receiver
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0)); // hook market
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                stateField, "Ljava/lang/Object;"));
        replacement.add(new VarInsnNode(Opcodes.FLOAD, loop.daysLocal));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "advanceMarketCommodityTemporalState", hookDesc, false));
        replacement.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name,
                stateField, "Ljava/lang/Object;"));
        // Keep a cursor into the unchanged industry loop. The first frame after
        // the removed commodity loop was encoded as SAME relative to the
        // commodity-loop iterator frame. Once that predecessor disappears it
        // must become an explicit full frame, or the JVM verifier sees local 5
        // as TOP at the industry-loop ALOAD.
        AbstractInsnNode afterLoop = loop.end.getNext();
        advance.instructions.insertBefore(loop.start, replacement);
        removeInstructionRange(advance, loop.start, loop.end,
                "Market commodity temporal loop");
        repairMarketPostCommodityFrame(node, advance, afterLoop);

        // The removed loop owned local-variable ranges for slots 4/5. They are
        // debug metadata only, and retaining labels no longer in the method would
        // produce malformed classfiles. Runtime semantics are unaffected.
        if (advance.localVariables != null) advance.localVariables.clear();
        if (advance.visibleLocalVariableAnnotations != null) {
            advance.visibleLocalVariableAnnotations.clear();
        }
        if (advance.invisibleLocalVariableAnnotations != null) {
            advance.invisibleLocalVariableAnnotations.clear();
        }

        requireOptimizedMarketCommodityTemporalShape(node, advance, stateField, hookDesc);
        PatchReport report = new PatchReport();
        report.add("commodity temporal active-set loop", 1);
        return report;
    }

    private static MarketCommodityLoop requireOriginalMarketCommodityLoop(
            ClassNode node, MethodNode method) {
        final String commodity = "com/fs/starfarer/campaign/econ/CommodityOnMarket";
        final String stat = "com/fs/starfarer/api/combat/MutableStatWithTempMods";
        List<AbstractInsnNode> code = meaningfulInstructions(method);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + 29 < code.size(); i++) {
            if (!(code.get(i + 1) instanceof MethodInsnNode call)
                    || !callMatches(call, Opcodes.INVOKEVIRTUAL, node.name,
                    "getCommodities", "()Ljava/util/List;")) continue;
            starts.add(i);
        }
        requireCount("Market commodity-maintenance loop candidates", starts.size(), 1);
        int i = starts.get(0);
        requireVar(code.get(i), Opcodes.ALOAD, 0, "commodity-loop Market owner");
        requireCall(asMethod(code.get(i + 1), "commodity list getter"), Opcodes.INVOKEVIRTUAL,
                node.name, "getCommodities", "()Ljava/util/List;", "commodity list getter");
        requireCall(asMethod(code.get(i + 2), "commodity iterator"), Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;", "commodity iterator");
        if (!(code.get(i + 3) instanceof VarInsnNode iteratorStore)
                || iteratorStore.getOpcode() != Opcodes.ASTORE) {
            throw mismatch("commodity iterator local changed");
        }
        int iteratorLocal = iteratorStore.var;
        if (!(code.get(i + 4) instanceof JumpInsnNode initialJump)
                || initialJump.getOpcode() != Opcodes.GOTO) {
            throw mismatch("commodity loop initial jump changed");
        }
        requireVar(code.get(i + 5), Opcodes.ALOAD, iteratorLocal, "commodity next iterator");
        requireCall(asMethod(code.get(i + 6), "commodity iterator next"), Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "next", "()Ljava/lang/Object;",
                "commodity iterator next");
        if (!(code.get(i + 7) instanceof TypeInsnNode cast)
                || cast.getOpcode() != Opcodes.CHECKCAST || !cast.desc.equals(commodity)) {
            throw mismatch("commodity loop cast changed");
        }
        if (!(code.get(i + 8) instanceof VarInsnNode commodityStore)
                || commodityStore.getOpcode() != Opcodes.ASTORE) {
            throw mismatch("commodity local changed");
        }
        int commodityLocal = commodityStore.var;
        String[] getters = {"getAvailableStat", "getTradeMod", "getTradeModPlus", "getTradeModMinus"};
        int daysLocal = -1;
        int cursor = i + 9;
        for (String getter : getters) {
            requireVar(code.get(cursor++), Opcodes.ALOAD, commodityLocal,
                    "commodity stat owner " + getter);
            requireCall(asMethod(code.get(cursor++), "commodity stat getter " + getter),
                    Opcodes.INVOKEVIRTUAL, commodity, getter,
                    "()L" + stat + ";", "commodity stat getter " + getter);
            AbstractInsnNode days = code.get(cursor++);
            if (!(days instanceof VarInsnNode load) || load.getOpcode() != Opcodes.FLOAD) {
                throw mismatch("commodity stat days local changed for " + getter);
            }
            if (daysLocal < 0) daysLocal = load.var;
            else if (daysLocal != load.var) throw mismatch("commodity stat days locals diverged");
            requireCall(asMethod(code.get(cursor++), "commodity stat advance " + getter),
                    Opcodes.INVOKEVIRTUAL, stat, "advance", "(F)V",
                    "commodity stat advance " + getter);
        }
        requireVar(code.get(cursor++), Opcodes.ALOAD, commodityLocal,
                "commodity event-mod owner");
        requireCall(asMethod(code.get(cursor++), "commodity event-mod reapply"),
                Opcodes.INVOKEVIRTUAL, commodity, "reapplyEventMod", "()V",
                "commodity event-mod reapply");
        requireVar(code.get(cursor++), Opcodes.ALOAD, iteratorLocal,
                "commodity hasNext iterator");
        requireCall(asMethod(code.get(cursor++), "commodity iterator hasNext"),
                Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z",
                "commodity iterator hasNext");
        if (!(code.get(cursor) instanceof JumpInsnNode back)
                || back.getOpcode() != Opcodes.IFNE) {
            throw mismatch("commodity loop back-edge changed");
        }
        requireCount("Market commodity-maintenance instruction count", cursor - i + 1, 30);
        if (nextMeaningful(initialJump.label) != code.get(i + 27)
                || nextMeaningful(back.label) != code.get(i + 5)) {
            throw mismatch("commodity loop control-flow targets changed");
        }
        return new MarketCommodityLoop(code.get(i), code.get(cursor), daysLocal);
    }

    private static void repairMarketPostCommodityFrame(
            ClassNode node, MethodNode method, AbstractInsnNode cursor) {
        FrameNode frame = null;
        for (AbstractInsnNode current = cursor; current != null; current = current.getNext()) {
            if (current instanceof FrameNode candidate) {
                frame = candidate;
                break;
            }
        }
        if (frame == null || frame.type != Opcodes.F_SAME) {
            throw mismatch("Market post-commodity industry-loop frame changed");
        }
        FrameNode full = new FrameNode(Opcodes.F_FULL, 6,
                new Object[] {node.name, Opcodes.FLOAT, Opcodes.FLOAT,
                        Opcodes.FLOAT, Opcodes.TOP, "java/util/Iterator"},
                0, new Object[0]);
        method.instructions.set(frame, full);
    }

    private static void requireOptimizedMarketCommodityTemporalShape(
            ClassNode node, MethodNode method, String stateField, String hookDesc) {
        requireCount("Market commodity temporal hook",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                        "advanceMarketCommodityTemporalState", hookDesc), 1);
        requireCount("Market commodity getter after fast path",
                countCalls(method, Opcodes.INVOKEVIRTUAL, node.name,
                        "getCommodities", "()Ljava/util/List;"), 0);
        final String commodity = "com/fs/starfarer/campaign/econ/CommodityOnMarket";
        final String stat = "com/fs/starfarer/api/combat/MutableStatWithTempMods";
        for (String getter : List.of("getAvailableStat", "getTradeMod",
                "getTradeModPlus", "getTradeModMinus")) {
            requireCount("Market fast path residual " + getter,
                    countCalls(method, Opcodes.INVOKEVIRTUAL, commodity, getter,
                            "()L" + stat + ";"), 0);
        }
        requireCount("Market fast path residual reapplyEventMod",
                countCalls(method, Opcodes.INVOKEVIRTUAL, commodity,
                        "reapplyEventMod", "()V"), 0);
        // The market power stat remains the one legitimate per-frame stat advance.
        requireCount("Market fast path remaining MutableStatWithTempMods.advance",
                countCalls(method, Opcodes.INVOKEVIRTUAL, stat, "advance", "(F)V"), 1);

        MethodInsnNode hook = calls(method, Opcodes.INVOKESTATIC, HOOKS,
                "advanceMarketCommodityTemporalState", hookDesc).get(0);
        AbstractInsnNode days = previousMeaningful(hook);
        AbstractInsnNode stateGet = previousMeaningful(days);
        AbstractInsnNode stateOwner = previousMeaningful(stateGet);
        AbstractInsnNode marketArg = previousMeaningful(stateOwner);
        AbstractInsnNode putOwner = previousMeaningful(marketArg);
        AbstractInsnNode statePut = nextMeaningful(hook);
        if (!(days instanceof VarInsnNode daysLoad) || daysLoad.getOpcode() != Opcodes.FLOAD
                || !(stateOwner instanceof VarInsnNode stateLoad)
                || stateLoad.getOpcode() != Opcodes.ALOAD || stateLoad.var != 0
                || !(marketArg instanceof VarInsnNode marketLoad)
                || marketLoad.getOpcode() != Opcodes.ALOAD || marketLoad.var != 0
                || !(putOwner instanceof VarInsnNode receiverLoad)
                || receiverLoad.getOpcode() != Opcodes.ALOAD || receiverLoad.var != 0) {
            throw mismatch("Market commodity temporal hook argument sequence changed");
        }
        requireField(stateGet, Opcodes.GETFIELD, node.name, stateField,
                "Ljava/lang/Object;", "Market commodity state read");
        requireField(statePut, Opcodes.PUTFIELD, node.name, stateField,
                "Ljava/lang/Object;", "Market commodity state write");
    }

    /**
     * Adds two private binding fields to MutableStat and guarded dirty notifications
     * to the concrete mutation methods. Unbound stats pay only one private-field
     * read and a predicted-null branch; no runtime hook is invoked.
     */
    private PatchReport patchCommodityTemporalBaseBinding(ClassNode node) {
        final String ownerField = "spp$commodityTemporalOwner";
        final String roleField = "spp$commodityTemporalRole";
        final int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        final String hookName = "markCommodityTemporalOwnerDirty";
        final String hookDesc = "(Ljava/lang/Object;ILjava/lang/String;)V";
        List<TemporalDirtyMethod> targets = List.of(
                new TemporalDirtyMethod("applyMods",
                        "(Lcom/fs/starfarer/api/combat/MutableStat;)V", -1),
                new TemporalDirtyMethod("applyMods",
                        "(Lcom/fs/starfarer/api/combat/StatBonus;)V", -1),
                new TemporalDirtyMethod("modifyFlat",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("modifyPercent",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("modifyPercentAlways",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("modifyMult",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("modifyMultAlways",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("modifyFlatAlways",
                        "(Ljava/lang/String;FLjava/lang/String;)V", 1),
                new TemporalDirtyMethod("unmodify", "()V", -1),
                new TemporalDirtyMethod("unmodify", "(Ljava/lang/String;)V", 1),
                new TemporalDirtyMethod("unmodifyFlat", "(Ljava/lang/String;)V", 1),
                new TemporalDirtyMethod("unmodifyPercent", "(Ljava/lang/String;)V", 1),
                new TemporalDirtyMethod("unmodifyMult", "(Ljava/lang/String;)V", 1),
                new TemporalDirtyMethod("setBaseValue", "(F)V", -1));

        FieldNode owner = uniqueField(node, ownerField);
        FieldNode role = uniqueField(node, roleField);
        if (owner != null || role != null) {
            requirePrivateSyntheticField(owner, ownerField, "Ljava/lang/Object;", fieldAccess);
            requirePrivateSyntheticField(role, roleField, "I", fieldAccess);
            for (TemporalDirtyMethod target : targets) {
                requireCommodityTemporalBaseDirtyPrologue(node,
                        requireMethod(node, target.name(), target.desc()), target,
                        ownerField, roleField, hookName, hookDesc);
            }
            throw already("MutableStat temporal binding and dirty postcondition match");
        }
        if (uniqueField(node, "smo$commodityTemporalBinding") != null) {
            throw mismatch("legacy commodity temporal binding field is already present");
        }
        for (TemporalDirtyMethod target : targets) {
            MethodNode method = requireMethod(node, target.name(), target.desc());
            requireCount(target.name() + target.desc() + " existing temporal dirty hooks",
                    countCalls(method, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc), 0);
        }

        node.fields.add(new FieldNode(Opcodes.ASM8, fieldAccess,
                ownerField, "Ljava/lang/Object;", null, null));
        node.fields.add(new FieldNode(Opcodes.ASM8, fieldAccess,
                roleField, "I", null, null));
        for (TemporalDirtyMethod target : targets) {
            MethodNode method = requireMethod(node, target.name(), target.desc());
            insertCommodityTemporalBaseDirtyPrologue(node, method, target,
                    ownerField, roleField, hookName, hookDesc);
            requireCommodityTemporalBaseDirtyPrologue(node, method, target,
                    ownerField, roleField, hookName, hookDesc);
        }

        PatchReport report = new PatchReport();
        report.add("bound MutableStat mutation notifications", targets.size());
        report.add("private owner/role binding fields", 2);
        return report;
    }

    private static FieldNode uniqueField(ClassNode node, String name) {
        FieldNode result = null;
        for (FieldNode field : node.fields) {
            if (!field.name.equals(name)) continue;
            if (result != null) throw mismatch("duplicate field " + name);
            result = field;
        }
        return result;
    }

    private static void requirePrivateSyntheticField(FieldNode field, String name,
                                                     String desc, int access) {
        if (field == null || !field.desc.equals(desc) || field.access != access
                || field.signature != null || field.value != null) {
            throw mismatch("foreign or incomplete field " + name);
        }
    }

    private static void insertCommodityTemporalBaseDirtyPrologue(
            ClassNode node, MethodNode method, TemporalDirtyMethod target,
            String ownerField, String roleField, String hookName, String hookDesc) {
        AbstractInsnNode first = firstExecutable(method);
        if (first == null) throw mismatch(target.name() + target.desc() + " has no body");
        LabelNode skip = new LabelNode();
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                ownerField, "Ljava/lang/Object;"));
        code.add(new JumpInsnNode(Opcodes.IFNULL, skip));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name,
                ownerField, "Ljava/lang/Object;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, roleField, "I"));
        if (target.sourceLocal() >= 0) {
            code.add(new VarInsnNode(Opcodes.ALOAD, target.sourceLocal()));
        } else {
            code.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                hookName, hookDesc, false));
        code.add(skip);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insertBefore(first, code);
    }

    private static void requireCommodityTemporalBaseDirtyPrologue(
            ClassNode node, MethodNode method, TemporalDirtyMethod target,
            String ownerField, String roleField, String hookName, String hookDesc) {
        requireCount(target.name() + target.desc() + " temporal dirty hook",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc), 1);
        List<AbstractInsnNode> code = meaningfulInstructions(method);
        if (code.size() < 10) throw mismatch(target.name() + target.desc() + " dirty prologue short");
        requireVar(code.get(0), Opcodes.ALOAD, 0, "dirty owner receiver");
        requireField(code.get(1), Opcodes.GETFIELD, node.name, ownerField,
                "Ljava/lang/Object;", "dirty owner guard");
        if (!(code.get(2) instanceof JumpInsnNode jump)
                || jump.getOpcode() != Opcodes.IFNULL) {
            throw mismatch(target.name() + target.desc() + " dirty null guard changed");
        }
        requireVar(code.get(3), Opcodes.ALOAD, 0, "dirty owner receiver 2");
        requireField(code.get(4), Opcodes.GETFIELD, node.name, ownerField,
                "Ljava/lang/Object;", "dirty owner value");
        requireVar(code.get(5), Opcodes.ALOAD, 0, "dirty role receiver");
        requireField(code.get(6), Opcodes.GETFIELD, node.name, roleField,
                "I", "dirty role value");
        if (target.sourceLocal() >= 0) {
            requireVar(code.get(7), Opcodes.ALOAD, target.sourceLocal(), "dirty source");
        } else if (code.get(7).getOpcode() != Opcodes.ACONST_NULL) {
            throw mismatch(target.name() + target.desc() + " null dirty source changed");
        }
        requireCall(asMethod(code.get(8), "commodity temporal dirty call"),
                Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc,
                "commodity temporal dirty call");
        if (nextMeaningful(jump.label) != code.get(9)) {
            throw mismatch(target.name() + target.desc() + " dirty guard target changed");
        }
    }

    private record TemporalDirtyMethod(String name, String desc, int sourceLocal) {}


    private static AbstractInsnNode firstExecutable(MethodNode method) {
        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        return first;
    }


    private static void removeInstructionRange(MethodNode method, AbstractInsnNode start,
                                               AbstractInsnNode end, String label) {
        Set<AbstractInsnNode> removed = new HashSet<>();
        AbstractInsnNode cursor = start;
        while (cursor != null) {
            removed.add(cursor);
            if (cursor == end) break;
            cursor = cursor.getNext();
        }
        if (cursor == null) throw mismatch(label + " end is not reachable from start");
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (removed.contains(insn)) continue;
            if (insn instanceof JumpInsnNode jump && removed.contains(jump.label)) {
                throw mismatch(label + " has an external jump into the removed range");
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            if (removed.contains(block.start) || removed.contains(block.end)
                    || removed.contains(block.handler)) {
                throw mismatch(label + " crosses an exception-handler boundary");
            }
        }
        cursor = start;
        while (cursor != null) {
            AbstractInsnNode next = cursor.getNext();
            method.instructions.remove(cursor);
            if (cursor == end) break;
            cursor = next;
        }
    }

    private record MarketCommodityLoop(AbstractInsnNode start, AbstractInsnNode end,
                                       int daysLocal) {}


    // ---------------------------------------------------------------------
    // Dormant inherited BaseIndustry.advance() fast path
    // ---------------------------------------------------------------------

    private static final String BASE_INDUSTRY_STATE_FIELD = "spp$marketNoOpState";
    private static final String BASE_INDUSTRY_COUNTDOWN_FIELD = "spp$marketNoOpCountdown";
    private static final String BASE_INDUSTRY_RAW_ADVANCE = "spp$baseIndustryRawAdvance";
    private static final String BASE_INDUSTRY_TEMPLATE_OWNER =
            "com/starsector/prepatcher/agent/templates/BaseIndustryDormantTemplate";
    private static final String BASE_INDUSTRY_TEMPLATE_RESOURCE =
            "/" + BASE_INDUSTRY_TEMPLATE_OWNER + ".class";
    private static final int BASE_INDUSTRY_AUDIT_PLACEHOLDER = 0x5A17CAFE;

    /**
     * Optimizes only the empty inherited BaseIndustry.advance() steady state.
     * MarketConditionPlugin dispatch is intentionally untouched: benchmarking
     * showed that classifying an empty condition costs more than the empty
     * virtual callback itself. The complete original BaseIndustry method is
     * retained and periodically audited.
     */
    private PatchReport patchBaseIndustryDormantFastPath(ClassNode node) {
        final int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        int fieldsPresent = 0;
        for (String fieldName : List.of(BASE_INDUSTRY_STATE_FIELD,
                BASE_INDUSTRY_COUNTDOWN_FIELD)) {
            List<FieldNode> matches = node.fields.stream()
                    .filter(field -> field.name.equals(fieldName)).toList();
            if (!matches.isEmpty()) {
                fieldsPresent++;
                requireCount("BaseIndustry dormant field " + fieldName, matches.size(), 1);
                FieldNode field = matches.get(0);
                if (!field.desc.equals("I") || field.access != fieldAccess
                        || field.signature != null || field.value != null) {
                    throw mismatch("foreign BaseIndustry dormant field " + fieldName);
                }
            }
        }
        if (fieldsPresent != 0) {
            requireCount("complete BaseIndustry dormant field set", fieldsPresent, 2);
            requireOptimizedBaseIndustryDormantShape(node);
            throw already("direct dormant BaseIndustry fast path postcondition matches");
        }

        requireOriginalBaseIndustryDormantShape(node);
        node.fields.add(new FieldNode(Opcodes.ASM8, fieldAccess,
                BASE_INDUSTRY_STATE_FIELD, "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ASM8, fieldAccess,
                BASE_INDUSTRY_COUNTDOWN_FIELD, "I", null, null));

        MethodNode original = requireMethod(node, "advance", "(F)V");
        MethodNode wrapper = renameForPrivateOriginal(node, original,
                BASE_INDUSTRY_RAW_ADVANCE);
        ClassNode template = loadBaseIndustryDormantTemplate();
        installBaseIndustryTemplateBody(template, node.name, wrapper);
        replaceBaseIndustryAuditPlaceholder(wrapper,
                config.marketNoOpIndustryAuditFrames);

        MethodNode setDisrupted = requireMethod(node, "setDisrupted", "(FZ)V");
        AbstractInsnNode first = firstMeaningful(setDisrupted);
        if (first == null) throw mismatch("BaseIndustry.setDisrupted has no code");
        InsnList wake = new InsnList();
        wake.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wake.add(new InsnNode(Opcodes.ICONST_0));
        wake.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name,
                BASE_INDUSTRY_STATE_FIELD, "I"));
        wake.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wake.add(new InsnNode(Opcodes.ICONST_0));
        wake.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name,
                BASE_INDUSTRY_COUNTDOWN_FIELD, "I"));
        setDisrupted.instructions.insertBefore(first, wake);

        requireOptimizedBaseIndustryDormantShape(node);
        PatchReport report = new PatchReport();
        report.add("direct dormant inherited BaseIndustry fast path", 1);
        return report;
    }

    private static void requireOriginalBaseIndustryDormantShape(ClassNode node) {
        for (FieldNode field : node.fields) {
            if (field.name.startsWith("spp$marketNoOp")) {
                throw mismatch("foreign/partial BaseIndustry dormant field " + field.name);
            }
        }
        requireCount("foreign BaseIndustry raw advance method",
                (int) node.methods.stream().filter(method ->
                        method.name.equals(BASE_INDUSTRY_RAW_ADVANCE)
                                && method.desc.equals("(F)V")).count(), 0);
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        requireCount("BaseIndustry.advance isDisrupted", countCalls(advance,
                Opcodes.INVOKEVIRTUAL, node.name, "isDisrupted", "()Z"), 1);
        requireCount("BaseIndustry.advance disruptionFinished", countCalls(advance,
                Opcodes.INVOKEVIRTUAL, node.name, "disruptionFinished", "()V"), 1);
        requireCount("BaseIndustry.advance building reads", countFields(advance,
                Opcodes.GETFIELD, node.name, "building", "Z"), 1);
        requireCount("BaseIndustry.advance wasDisrupted reads", countFields(advance,
                Opcodes.GETFIELD, node.name, "wasDisrupted", "Z"), 1);
        requireCount("BaseIndustry.advance wasDisrupted writes", countFields(advance,
                Opcodes.PUTFIELD, node.name, "wasDisrupted", "Z"), 1);

        MethodNode setDisrupted = requireMethod(node, "setDisrupted", "(FZ)V");
        requireCount("BaseIndustry.setDisrupted canBeDisrupted", countCalls(setDisrupted,
                Opcodes.INVOKEVIRTUAL, node.name, "canBeDisrupted", "()Z"), 1);
        requireCount("BaseIndustry.setDisrupted dormant state writes", countFields(setDisrupted,
                Opcodes.PUTFIELD, node.name, BASE_INDUSTRY_STATE_FIELD, "I"), 0);
        requireCount("BaseIndustry.setDisrupted dormant countdown writes", countFields(setDisrupted,
                Opcodes.PUTFIELD, node.name, BASE_INDUSTRY_COUNTDOWN_FIELD, "I"), 0);
    }

    private static void requireOptimizedBaseIndustryDormantShape(ClassNode node) {
        final int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        FieldNode state = requireField(node, BASE_INDUSTRY_STATE_FIELD, "I");
        FieldNode countdown = requireField(node, BASE_INDUSTRY_COUNTDOWN_FIELD, "I");
        if (state.access != fieldAccess || state.signature != null || state.value != null
                || countdown.access != fieldAccess || countdown.signature != null
                || countdown.value != null) {
            throw mismatch("BaseIndustry dormant field metadata changed");
        }

        MethodNode wrapper = requireMethod(node, "advance", "(F)V");
        MethodNode raw = requireMethod(node, BASE_INDUSTRY_RAW_ADVANCE, "(F)V");
        int rawAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
        if ((raw.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) != rawAccess) {
            throw mismatch("BaseIndustry raw advance access changed");
        }
        requireCount("BaseIndustry wrapper raw call", countCalls(wrapper,
                -1, node.name, BASE_INDUSTRY_RAW_ADVANCE, "(F)V"), 1);
        requireCount("BaseIndustry wrapper eligibility call", countCalls(wrapper,
                Opcodes.INVOKESTATIC, HOOKS,
                "isBaseIndustryDormantFastPathEligible", "(Ljava/lang/Object;)Z"), 1);
        requireCount("BaseIndustry raw isDisrupted", countCalls(raw,
                Opcodes.INVOKEVIRTUAL, node.name, "isDisrupted", "()Z"), 1);
        requireCount("BaseIndustry raw disruptionFinished", countCalls(raw,
                Opcodes.INVOKEVIRTUAL, node.name, "disruptionFinished", "()V"), 1);

        for (AbstractInsnNode insn : wrapper.instructions.toArray()) {
            if (insn instanceof LdcInsnNode ldc
                    && Integer.valueOf(BASE_INDUSTRY_AUDIT_PLACEHOLDER).equals(ldc.cst)) {
                throw mismatch("BaseIndustry audit placeholder leaked into target");
            }
        }

        MethodNode setDisrupted = requireMethod(node, "setDisrupted", "(FZ)V");
        List<AbstractInsnNode> code = meaningfulInstructions(setDisrupted);
        if (code.size() < 6) throw mismatch("BaseIndustry.setDisrupted wake prologue is short");
        requireVar(code.get(0), Opcodes.ALOAD, 0, "BaseIndustry wake receiver 1");
        if (code.get(1).getOpcode() != Opcodes.ICONST_0) {
            throw mismatch("BaseIndustry wake state constant changed");
        }
        requireField(code.get(2), Opcodes.PUTFIELD, node.name,
                BASE_INDUSTRY_STATE_FIELD, "I", "BaseIndustry wake state");
        requireVar(code.get(3), Opcodes.ALOAD, 0, "BaseIndustry wake receiver 2");
        if (code.get(4).getOpcode() != Opcodes.ICONST_0) {
            throw mismatch("BaseIndustry wake countdown constant changed");
        }
        requireField(code.get(5), Opcodes.PUTFIELD, node.name,
                BASE_INDUSTRY_COUNTDOWN_FIELD, "I", "BaseIndustry wake countdown");
        requireNoBaseIndustryTemplateReferences(node);
    }

    private static ClassNode loadBaseIndustryDormantTemplate() {
        try (InputStream input = PrepatcherTransformer.class
                .getResourceAsStream(BASE_INDUSTRY_TEMPLATE_RESOURCE)) {
            if (input == null) throw mismatch("missing BaseIndustry dormant template resource");
            ClassNode template = new ClassNode(Opcodes.ASM8);
            new ClassReader(input).accept(template, 0);
            if (!template.name.equals(BASE_INDUSTRY_TEMPLATE_OWNER)) {
                throw mismatch("BaseIndustry dormant template owner changed: " + template.name);
            }
            return template;
        } catch (IOException ex) {
            throw mismatch("unable to read BaseIndustry dormant template: " + ex.getMessage());
        }
    }

    private static void installBaseIndustryTemplateBody(ClassNode template,
                                                         String targetOwner,
                                                         MethodNode target) {
        MethodNode source = requireMethod(template, "advance", "(F)V");
        MethodNode copy = cloneAndRemapBaseIndustryTemplateMethod(source, targetOwner);
        target.instructions.clear();
        target.instructions.add(copy.instructions);
        target.tryCatchBlocks.clear();
        target.tryCatchBlocks.addAll(copy.tryCatchBlocks);
        target.localVariables = copy.localVariables;
        target.visibleLocalVariableAnnotations = copy.visibleLocalVariableAnnotations;
        target.invisibleLocalVariableAnnotations = copy.invisibleLocalVariableAnnotations;
        target.maxLocals = copy.maxLocals;
        target.maxStack = copy.maxStack;
    }

    private static MethodNode cloneAndRemapBaseIndustryTemplateMethod(MethodNode source,
                                                                       String targetOwner) {
        MethodNode copy = new MethodNode(Opcodes.ASM8, source.access, source.name,
                source.desc, source.signature, exceptions(source));
        source.accept(copy);
        remapBaseIndustryTemplateMethod(copy, targetOwner);
        return copy;
    }

    private static void remapBaseIndustryTemplateMethod(MethodNode method,
                                                         String targetOwner) {
        method.desc = remapBaseIndustryTemplateText(method.desc, targetOwner);
        method.signature = remapBaseIndustryTemplateText(method.signature, targetOwner);
        if (method.exceptions != null) {
            for (int i = 0; i < method.exceptions.size(); i++) {
                method.exceptions.set(i,
                        remapBaseIndustryTemplateText(method.exceptions.get(i), targetOwner));
            }
        }
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field) {
                field.owner = remapBaseIndustryTemplateText(field.owner, targetOwner);
                field.desc = remapBaseIndustryTemplateText(field.desc, targetOwner);
            } else if (insn instanceof MethodInsnNode call) {
                call.owner = remapBaseIndustryTemplateText(call.owner, targetOwner);
                call.desc = remapBaseIndustryTemplateText(call.desc, targetOwner);
            } else if (insn instanceof TypeInsnNode type) {
                type.desc = remapBaseIndustryTemplateText(type.desc, targetOwner);
            } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                ldc.cst = Type.getType(remapBaseIndustryTemplateText(
                        type.getDescriptor(), targetOwner));
            } else if (insn instanceof MultiANewArrayInsnNode array) {
                array.desc = remapBaseIndustryTemplateText(array.desc, targetOwner);
            } else if (insn instanceof FrameNode frame) {
                remapBaseIndustryFrameValues(frame.local, targetOwner);
                remapBaseIndustryFrameValues(frame.stack, targetOwner);
            } else if (insn instanceof InvokeDynamicInsnNode) {
                throw mismatch("unexpected invokedynamic in BaseIndustry dormant template");
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            block.type = remapBaseIndustryTemplateText(block.type, targetOwner);
        }
        if (method.localVariables != null) {
            for (LocalVariableNode local : method.localVariables) {
                local.desc = remapBaseIndustryTemplateText(local.desc, targetOwner);
                local.signature = remapBaseIndustryTemplateText(local.signature, targetOwner);
            }
        }
    }

    private static void remapBaseIndustryFrameValues(List<Object> values,
                                                       String targetOwner) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof String text) {
                values.set(i, remapBaseIndustryTemplateText(text, targetOwner));
            }
        }
    }

    private static String remapBaseIndustryTemplateText(String value,
                                                         String targetOwner) {
        if (value == null) return null;
        return value.replace(BASE_INDUSTRY_TEMPLATE_OWNER, targetOwner);
    }

    private static void replaceBaseIndustryAuditPlaceholder(MethodNode method,
                                                             int auditFrames) {
        int replacements = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode ldc
                    && Integer.valueOf(BASE_INDUSTRY_AUDIT_PLACEHOLDER).equals(ldc.cst)) {
                ldc.cst = Integer.valueOf(auditFrames);
                replacements++;
            }
        }
        requireCount("BaseIndustry audit-frame placeholder", replacements, 1);
    }

    private static void requireNoBaseIndustryTemplateReferences(ClassNode node) {
        for (FieldNode field : node.fields) {
            if ((field.desc != null && field.desc.contains(BASE_INDUSTRY_TEMPLATE_OWNER))
                    || (field.signature != null
                    && field.signature.contains(BASE_INDUSTRY_TEMPLATE_OWNER))) {
                throw mismatch("BaseIndustry template field reference leaked into target");
            }
        }
        for (MethodNode method : node.methods) {
            if ((method.desc != null && method.desc.contains(BASE_INDUSTRY_TEMPLATE_OWNER))
                    || (method.signature != null
                    && method.signature.contains(BASE_INDUSTRY_TEMPLATE_OWNER))) {
                throw mismatch("BaseIndustry template method signature leaked into target");
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field
                        && (field.owner.contains(BASE_INDUSTRY_TEMPLATE_OWNER)
                        || field.desc.contains(BASE_INDUSTRY_TEMPLATE_OWNER))) {
                    throw mismatch("BaseIndustry template field instruction leaked into target");
                }
                if (insn instanceof MethodInsnNode call
                        && (call.owner.contains(BASE_INDUSTRY_TEMPLATE_OWNER)
                        || call.desc.contains(BASE_INDUSTRY_TEMPLATE_OWNER))) {
                    throw mismatch("BaseIndustry template method instruction leaked into target");
                }
                if (insn instanceof TypeInsnNode type
                        && type.desc.contains(BASE_INDUSTRY_TEMPLATE_OWNER)) {
                    throw mismatch("BaseIndustry template type instruction leaked into target");
                }
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type
                        && type.getDescriptor().contains(BASE_INDUSTRY_TEMPLATE_OWNER)) {
                    throw mismatch("BaseIndustry template class literal leaked into target");
                }
            }
        }
    }


    // ---------------------------------------------------------------------
    // MutableStatWithTempMods direct expiry-aware hybrid scheduler
    // ---------------------------------------------------------------------

    private static final String TEMP_MOD_LEGACY_STATE_FIELD = "spp$tempModExpiryState";
    private static final String TEMP_MOD_TEMPLATE_OWNER =
            "com/fs/starfarer/api/combat/StarsectorPrepatcherTempModHybridTemplate";
    private static final String TEMP_MOD_TEMPLATE_RESOURCE =
            "/" + TEMP_MOD_TEMPLATE_OWNER + ".class";
    private static final String TEMP_MOD_RAW_ADVANCE = "spp$originalTempModAdvance";
    private static final String TEMP_MOD_RAW_GET_MODS = "spp$rawGetMods";
    private static final String TEMP_MOD_RAW_GET_MOD = "spp$rawGetMod";
    private static final String TEMP_MOD_RAW_REMOVE = "spp$rawRemoveTemporaryMod";
    private static final String TEMP_MOD_RAW_HAS = "spp$rawHasMod";
    private static final String TEMP_MOD_RAW_WRITE = "spp$rawWriteReplace";

    private static final String[][] TEMP_MOD_HYBRID_FIELDS = {
            {"spp$tempModHybridDeferredDays", "F"},
            {"spp$tempModHybridTimeToNext", "F"},
            {"spp$tempModHybridScheduledMin", "F"},
            {"spp$tempModHybridScheduledMinCount", "I"},
            {"spp$tempModHybridKnownSize", "I"},
            {"spp$tempModHybridFlags", "I"}
    };

    private static final String[][] TEMP_MOD_HYBRID_HELPERS = {
            {"spp$tempModHybridUseVanilla", "()Z"},
            {"spp$tempModHybridSynchronize", "()V"},
            {"spp$tempModHybridAuditForCommodity", "()V"},
            {"spp$tempModHybridDisableAndFlush", "()V"},
            {"spp$tempModHybridApplyElapsed", "(FZZ)V"},
            {"spp$tempModHybridRecomputeSchedule", "()V"},
            {"spp$tempModHybridAfterSet", "(ZFF)V"},
            {"spp$tempModHybridAfterRemoval", "(F)V"},
            {"spp$tempModHybridSetSoleMinimum", "(F)V"},
            {"spp$tempModHybridResetForEmpty", "()V"},
            {"spp$tempModHybridTrackable", "(F)Z"}
    };

    /**
     * Keeps the unified transform/guard/fail-open architecture, but moves the hot scheduler state
     * directly into the game class. The nearest expiry is driven by repeated float subtraction,
     * matching vanilla's frame of expiry, while all surviving modifiers are materialized only at
     * a deadline or an externally observable mutation/read/save boundary.
     */
    private PatchReport patchTempModExpiryScheduler(ClassNode node) {
        final int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        for (FieldNode field : node.fields) {
            if (field.name.equals(TEMP_MOD_LEGACY_STATE_FIELD)) {
                throw mismatch("legacy 0.9.1 temp-mod state field is already present");
            }
        }

        int hybridFieldsPresent = 0;
        for (String[] spec : TEMP_MOD_HYBRID_FIELDS) {
            List<FieldNode> matches = new ArrayList<>();
            for (FieldNode field : node.fields) {
                if (field.name.equals(spec[0])) matches.add(field);
            }
            if (!matches.isEmpty()) {
                hybridFieldsPresent++;
                requireCount("temp-mod hybrid field " + spec[0], matches.size(), 1);
                FieldNode field = matches.get(0);
                if (!field.desc.equals(spec[1]) || field.access != fieldAccess
                        || field.signature != null || field.value != null) {
                    throw mismatch("foreign temp-mod hybrid field " + spec[0]);
                }
            }
        }
        if (hybridFieldsPresent != 0) {
            requireCount("complete temp-mod hybrid field set", hybridFieldsPresent,
                    TEMP_MOD_HYBRID_FIELDS.length);
            requireOptimizedTempModSchedulerShape(node);
            throw already("direct temp-mod hybrid scheduler postcondition matches");
        }

        requireOriginalTempModSchedulerShape(node);
        for (String[] spec : TEMP_MOD_HYBRID_FIELDS) {
            node.fields.add(new FieldNode(Opcodes.ASM8, fieldAccess,
                    spec[0], spec[1], null, null));
        }

        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode getMods = requireMethod(node, "getMods", "()Ljava/util/Map;");
        MethodNode getMod = requireMethod(node, "getMod",
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;");
        MethodNode remove = requireMethod(node, "removeTemporaryMod", "(Ljava/lang/String;)V");
        MethodNode has = requireMethod(node, "hasMod", "(Ljava/lang/String;)Z");
        MethodNode write = requireMethod(node, "writeReplace", "()Ljava/lang/Object;");

        MethodNode advanceWrapper = renameForPrivateOriginal(node, advance, TEMP_MOD_RAW_ADVANCE);
        MethodNode getModsWrapper = renameForPrivateOriginal(node, getMods, TEMP_MOD_RAW_GET_MODS);
        MethodNode getModWrapper = renameForPrivateOriginal(node, getMod, TEMP_MOD_RAW_GET_MOD);
        MethodNode removeWrapper = renameForPrivateOriginal(node, remove, TEMP_MOD_RAW_REMOVE);
        MethodNode hasWrapper = renameForPrivateOriginal(node, has, TEMP_MOD_RAW_HAS);
        MethodNode writeWrapper = renameForPrivateOriginal(node, write, TEMP_MOD_RAW_WRITE);

        int rawGetModsCalls = 0;
        for (MethodNode raw : List.of(advance, getMod, remove, has, write)) {
            for (AbstractInsnNode insn : raw.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !call.owner.equals(node.name)
                        || !call.name.equals("getMods")
                        || !call.desc.equals("()Ljava/util/Map;")) continue;
                call.name = TEMP_MOD_RAW_GET_MODS;
                call.setOpcode(Opcodes.INVOKESPECIAL);
                call.itf = false;
                rawGetModsCalls++;
            }
        }
        requireCount("MutableStatWithTempMods raw getMods rewrites", rawGetModsCalls, 7);

        ClassNode template = loadTempModHybridTemplate();
        installTempModTemplateBody(template, node.name, "advance", "(F)V", advanceWrapper);
        installTempModTemplateBody(template, node.name, "getMods", "()Ljava/util/Map;",
                getModsWrapper);
        installTempModTemplateBody(template, node.name, "getMod",
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;",
                getModWrapper);
        installTempModTemplateBody(template, node.name, "removeTemporaryMod",
                "(Ljava/lang/String;)V", removeWrapper);
        installTempModTemplateBody(template, node.name, "hasMod",
                "(Ljava/lang/String;)Z", hasWrapper);
        installTempModTemplateBody(template, node.name, "writeReplace",
                "()Ljava/lang/Object;", writeWrapper);
        for (String[] helper : TEMP_MOD_HYBRID_HELPERS) {
            node.methods.add(copyTempModTemplateMethod(template, node.name, helper[0], helper[1]));
        }

        requireOptimizedTempModSchedulerShape(node);
        PatchReport report = new PatchReport();
        report.add("direct float-countdown MutableStatWithTempMods hybrid scheduler", 1);
        return report;
    }

    private static void requireOriginalTempModSchedulerShape(ClassNode node) {
        requireField(node, "tempMods", "Ljava/util/LinkedHashMap;");
        for (FieldNode field : node.fields) {
            if (field.name.startsWith("spp$tempModHybrid")
                    || field.name.equals(TEMP_MOD_LEGACY_STATE_FIELD)) {
                throw mismatch("foreign/partial temp-mod scheduler field " + field.name);
            }
        }
        for (String raw : List.of(TEMP_MOD_RAW_ADVANCE, TEMP_MOD_RAW_GET_MODS,
                TEMP_MOD_RAW_GET_MOD, TEMP_MOD_RAW_REMOVE, TEMP_MOD_RAW_HAS,
                TEMP_MOD_RAW_WRITE)) {
            for (MethodNode method : node.methods) {
                if (method.name.equals(raw)) {
                    throw mismatch("foreign/partial temp-mod scheduler method " + raw);
                }
            }
        }
        for (String[] helper : TEMP_MOD_HYBRID_HELPERS) {
            if (!methods(node, helper[0], helper[1]).isEmpty()) {
                throw mismatch("foreign/partial temp-mod hybrid helper " + helper[0]);
            }
        }

        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode getMods = requireMethod(node, "getMods", "()Ljava/util/Map;");
        MethodNode getMod = requireMethod(node, "getMod",
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;");
        MethodNode remove = requireMethod(node, "removeTemporaryMod", "(Ljava/lang/String;)V");
        MethodNode has = requireMethod(node, "hasMod", "(Ljava/lang/String;)Z");
        MethodNode write = requireMethod(node, "writeReplace", "()Ljava/lang/Object;");

        requireCount("MutableStatWithTempMods.advance getMods calls",
                countCalls(advance, -1, node.name, "getMods", "()Ljava/util/Map;"), 2);
        requireCount("MutableStatWithTempMods.advance iterator removals",
                countCalls(advance, Opcodes.INVOKEINTERFACE, "java/util/Iterator",
                        "remove", "()V"), 1);
        requireCount("MutableStatWithTempMods.advance unmodify calls",
                countCalls(advance, Opcodes.INVOKEVIRTUAL,
                        null, "unmodify", "(Ljava/lang/String;)V"), 1);
        requireCount("MutableStatWithTempMods.getMods map construction",
                countNew(getMods, "java/util/LinkedHashMap"), 1);
        requireCount("MutableStatWithTempMods.getMod getMods calls",
                countCalls(getMod, -1, node.name, "getMods", "()Ljava/util/Map;"), 2);
        requireCount("MutableStatWithTempMods.remove getMods calls",
                countCalls(remove, -1, node.name, "getMods", "()Ljava/util/Map;"), 1);
        requireCount("MutableStatWithTempMods.has getMods calls",
                countCalls(has, -1, node.name, "getMods", "()Ljava/util/Map;"), 1);
        requireCount("MutableStatWithTempMods.write getMods calls",
                countCalls(write, -1, node.name, "getMods", "()Ljava/util/Map;"), 1);
    }

    private static void requireOptimizedTempModSchedulerShape(ClassNode node) {
        final int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        for (String[] spec : TEMP_MOD_HYBRID_FIELDS) {
            FieldNode field = requireField(node, spec[0], spec[1]);
            if (field.access != fieldAccess || field.signature != null || field.value != null) {
                throw mismatch("temp-mod hybrid field shape changed: " + spec[0]);
            }
        }
        for (FieldNode field : node.fields) {
            if (field.name.equals(TEMP_MOD_LEGACY_STATE_FIELD)) {
                throw mismatch("legacy temp-mod scheduler field coexists with hybrid");
            }
        }

        requireCount("temp-mod public advance wrapper", methods(node, "advance", "(F)V").size(), 1);
        requireCount("temp-mod raw advance", methods(node, TEMP_MOD_RAW_ADVANCE, "(F)V").size(), 1);
        requireCount("temp-mod public getMods wrapper",
                methods(node, "getMods", "()Ljava/util/Map;").size(), 1);
        requireCount("temp-mod raw getMods",
                methods(node, TEMP_MOD_RAW_GET_MODS, "()Ljava/util/Map;").size(), 1);
        requireCount("temp-mod private getMod wrapper", methods(node, "getMod",
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;").size(), 1);
        requireCount("temp-mod raw getMod", methods(node, TEMP_MOD_RAW_GET_MOD,
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;").size(), 1);
        requireCount("temp-mod remove wrapper",
                methods(node, "removeTemporaryMod", "(Ljava/lang/String;)V").size(), 1);
        requireCount("temp-mod raw remove",
                methods(node, TEMP_MOD_RAW_REMOVE, "(Ljava/lang/String;)V").size(), 1);
        requireCount("temp-mod has wrapper",
                methods(node, "hasMod", "(Ljava/lang/String;)Z").size(), 1);
        requireCount("temp-mod raw has",
                methods(node, TEMP_MOD_RAW_HAS, "(Ljava/lang/String;)Z").size(), 1);
        requireCount("temp-mod write wrapper",
                methods(node, "writeReplace", "()Ljava/lang/Object;").size(), 1);
        requireCount("temp-mod raw write",
                methods(node, TEMP_MOD_RAW_WRITE, "()Ljava/lang/Object;").size(), 1);
        for (String[] helper : TEMP_MOD_HYBRID_HELPERS) {
            MethodNode method = requireMethod(node, helper[0], helper[1]);
            if ((method.access & Opcodes.ACC_PRIVATE) == 0
                    || (method.access & Opcodes.ACC_SYNTHETIC) == 0) {
                throw mismatch("temp-mod hybrid helper access changed: " + helper[0]);
            }
        }

        MethodNode advance = requireMethod(node, "advance", "(F)V");
        MethodNode getMods = requireMethod(node, "getMods", "()Ljava/util/Map;");
        MethodNode getMod = requireMethod(node, "getMod",
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;");
        MethodNode remove = requireMethod(node, "removeTemporaryMod", "(Ljava/lang/String;)V");
        MethodNode has = requireMethod(node, "hasMod", "(Ljava/lang/String;)Z");
        MethodNode write = requireMethod(node, "writeReplace", "()Ljava/lang/Object;");
        MethodNode apply = requireMethod(node, "spp$tempModHybridApplyElapsed", "(FZZ)V");

        requireCountAtLeast("temp-mod advance raw fallback",
                countCalls(advance, -1, node.name, TEMP_MOD_RAW_ADVANCE, "(F)V"), 1);
        requireCountAtLeast("temp-mod advance direct apply",
                countCalls(advance, -1, node.name, "spp$tempModHybridApplyElapsed", "(FZZ)V"), 2);
        requireCountAtLeast("temp-mod getMods raw path",
                countCalls(getMods, -1, node.name, TEMP_MOD_RAW_GET_MODS, "()Ljava/util/Map;"), 1);
        requireCountAtLeast("temp-mod getMod raw path",
                countCalls(getMod, -1, node.name, TEMP_MOD_RAW_GET_MOD,
                        "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;"), 1);
        requireCountAtLeast("temp-mod remove raw path",
                countCalls(remove, -1, node.name, TEMP_MOD_RAW_REMOVE, "(Ljava/lang/String;)V"), 1);
        requireCount("temp-mod has direct raw path",
                countCalls(has, -1, node.name, TEMP_MOD_RAW_HAS, "(Ljava/lang/String;)Z"), 1);
        requireCountAtLeast("temp-mod write raw path",
                countCalls(write, -1, node.name, TEMP_MOD_RAW_WRITE, "()Ljava/lang/Object;"), 1);
        requireCount("temp-mod direct iterator removal",
                countCalls(apply, Opcodes.INVOKEINTERFACE, "java/util/Iterator", "remove", "()V"), 1);
        requireCount("temp-mod direct unmodify",
                countCalls(apply, Opcodes.INVOKEVIRTUAL, null, "unmodify", "(Ljava/lang/String;)V"), 1);

        requireCount("temp-mod hybrid initial telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridInitialSweep", "(II)V"), 1);
        requireCount("temp-mod hybrid expiry telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridExpirySweep", "(II)V"), 1);
        requireCount("temp-mod hybrid synchronization telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridSynchronizationSweep", "(II)V"), 1);
        requireCount("temp-mod hybrid rebuild telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridScheduleRebuild", "(I)V"), 1);
        requireCount("temp-mod hybrid exposure telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridExternalExposure", "()V"), 1);
        requireCount("temp-mod hybrid subclass telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridSubclassFallback", "()V"), 1);
        requireCount("temp-mod hybrid failure telemetry", countCallsInClass(node,
                TEMP_MOD_HOOKS, "recordHybridFailureFallback", "()V"), 1);
        requireCount("legacy per-frame temp-mod hook removed", countCallsInClass(node,
                TEMP_MOD_HOOKS, "advance", null), 0);

        MethodNode rawAdvance = requireMethod(node, TEMP_MOD_RAW_ADVANCE, "(F)V");
        MethodNode rawGetMod = requireMethod(node, TEMP_MOD_RAW_GET_MOD,
                "(Ljava/lang/String;F)Lcom/fs/starfarer/api/combat/MutableStatWithTempMods$TemporaryStatMod;");
        MethodNode rawRemove = requireMethod(node, TEMP_MOD_RAW_REMOVE, "(Ljava/lang/String;)V");
        MethodNode rawHas = requireMethod(node, TEMP_MOD_RAW_HAS, "(Ljava/lang/String;)Z");
        MethodNode rawWrite = requireMethod(node, TEMP_MOD_RAW_WRITE, "()Ljava/lang/Object;");
        int rawCalls = 0;
        for (MethodNode raw : List.of(rawAdvance, rawGetMod, rawRemove, rawHas, rawWrite)) {
            rawCalls += countCalls(raw, -1, node.name,
                    TEMP_MOD_RAW_GET_MODS, "()Ljava/util/Map;");
            requireCount("raw temp-mod method calls public getMods",
                    countCalls(raw, -1, node.name, "getMods", "()Ljava/util/Map;"), 0);
        }
        requireCount("raw temp-mod getMods call count", rawCalls, 7);
        requireNoTempModTemplateReferences(node);
    }

    private static int countCallsInClass(ClassNode node, String owner, String name, String desc) {
        int count = 0;
        for (MethodNode method : node.methods) {
            count += countCalls(method, -1, owner, name, desc);
        }
        return count;
    }

    private static MethodNode renameForPrivateOriginal(ClassNode node, MethodNode original,
                                                        String rawName) {
        String wrapperName = original.name;
        MethodNode wrapper = new MethodNode(Opcodes.ASM8, original.access, wrapperName,
                original.desc, original.signature, exceptions(original));
        moveWrapperMetadata(original, wrapper, node.name + "." + wrapperName + original.desc);
        original.name = rawName;
        makePrivateSynthetic(original);
        node.methods.add(wrapper);
        return wrapper;
    }

    private static ClassNode loadTempModHybridTemplate() {
        try (InputStream input = PrepatcherTransformer.class
                .getResourceAsStream(TEMP_MOD_TEMPLATE_RESOURCE)) {
            if (input == null) throw mismatch("missing temp-mod hybrid template resource");
            ClassNode template = new ClassNode(Opcodes.ASM8);
            new ClassReader(input).accept(template, 0);
            if (!template.name.equals(TEMP_MOD_TEMPLATE_OWNER)) {
                throw mismatch("temp-mod hybrid template owner changed: " + template.name);
            }
            return template;
        } catch (IOException ex) {
            throw mismatch("unable to read temp-mod hybrid template: " + ex.getMessage());
        }
    }

    private static void installTempModTemplateBody(ClassNode template, String targetOwner,
                                                    String name, String desc, MethodNode target) {
        MethodNode source = requireMethod(template, name, desc);
        MethodNode copy = cloneAndRemapTempModTemplateMethod(source, targetOwner);
        target.instructions.clear();
        target.instructions.add(copy.instructions);
        target.tryCatchBlocks.clear();
        target.tryCatchBlocks.addAll(copy.tryCatchBlocks);
        target.localVariables = copy.localVariables;
        target.visibleLocalVariableAnnotations = copy.visibleLocalVariableAnnotations;
        target.invisibleLocalVariableAnnotations = copy.invisibleLocalVariableAnnotations;
        target.maxLocals = copy.maxLocals;
        target.maxStack = copy.maxStack;
    }

    private static MethodNode copyTempModTemplateMethod(ClassNode template, String targetOwner,
                                                         String name, String desc) {
        MethodNode source = requireMethod(template, name, desc);
        MethodNode copy = cloneAndRemapTempModTemplateMethod(source, targetOwner);
        copy.access = (copy.access & Opcodes.ACC_STATIC)
                | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
        return copy;
    }

    private static MethodNode cloneAndRemapTempModTemplateMethod(MethodNode source,
                                                                  String targetOwner) {
        MethodNode copy = new MethodNode(Opcodes.ASM8, source.access, source.name,
                source.desc, source.signature, exceptions(source));
        source.accept(copy);
        remapTempModTemplateMethod(copy, targetOwner);
        return copy;
    }

    private static void remapTempModTemplateMethod(MethodNode method, String targetOwner) {
        method.desc = remapTempModTemplateText(method.desc, targetOwner);
        method.signature = remapTempModTemplateText(method.signature, targetOwner);
        if (method.exceptions != null) {
            for (int i = 0; i < method.exceptions.size(); i++) {
                method.exceptions.set(i, remapTempModTemplateText(method.exceptions.get(i), targetOwner));
            }
        }
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field) {
                field.owner = remapTempModTemplateText(field.owner, targetOwner);
                field.desc = remapTempModTemplateText(field.desc, targetOwner);
            } else if (insn instanceof MethodInsnNode call) {
                call.owner = remapTempModTemplateText(call.owner, targetOwner);
                call.desc = remapTempModTemplateText(call.desc, targetOwner);
            } else if (insn instanceof TypeInsnNode type) {
                type.desc = remapTempModTemplateText(type.desc, targetOwner);
            } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                ldc.cst = Type.getType(remapTempModTemplateText(type.getDescriptor(), targetOwner));
            } else if (insn instanceof MultiANewArrayInsnNode array) {
                array.desc = remapTempModTemplateText(array.desc, targetOwner);
            } else if (insn instanceof FrameNode frame) {
                remapTempModFrameValues(frame.local, targetOwner);
                remapTempModFrameValues(frame.stack, targetOwner);
            } else if (insn instanceof InvokeDynamicInsnNode) {
                throw mismatch("unexpected invokedynamic in temp-mod hybrid template");
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            block.type = remapTempModTemplateText(block.type, targetOwner);
        }
        if (method.localVariables != null) {
            for (LocalVariableNode local : method.localVariables) {
                local.desc = remapTempModTemplateText(local.desc, targetOwner);
                local.signature = remapTempModTemplateText(local.signature, targetOwner);
            }
        }
    }

    private static void remapTempModFrameValues(List<Object> values, String targetOwner) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof String text) {
                values.set(i, remapTempModTemplateText(text, targetOwner));
            }
        }
    }

    private static String remapTempModTemplateText(String value, String targetOwner) {
        if (value == null) return null;
        return value.replace(TEMP_MOD_TEMPLATE_OWNER, targetOwner);
    }

    private static void requireNoTempModTemplateReferences(ClassNode node) {
        for (FieldNode field : node.fields) {
            if ((field.desc != null && field.desc.contains(TEMP_MOD_TEMPLATE_OWNER))
                    || (field.signature != null && field.signature.contains(TEMP_MOD_TEMPLATE_OWNER))) {
                throw mismatch("temp-mod template field reference leaked into target");
            }
        }
        for (MethodNode method : node.methods) {
            if ((method.desc != null && method.desc.contains(TEMP_MOD_TEMPLATE_OWNER))
                    || (method.signature != null && method.signature.contains(TEMP_MOD_TEMPLATE_OWNER))) {
                throw mismatch("temp-mod template method signature leaked into target");
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field
                        && (field.owner.contains(TEMP_MOD_TEMPLATE_OWNER)
                        || field.desc.contains(TEMP_MOD_TEMPLATE_OWNER))) {
                    throw mismatch("temp-mod template field instruction leaked into target");
                }
                if (insn instanceof MethodInsnNode call
                        && (call.owner.contains(TEMP_MOD_TEMPLATE_OWNER)
                        || call.desc.contains(TEMP_MOD_TEMPLATE_OWNER))) {
                    throw mismatch("temp-mod template method instruction leaked into target");
                }
                if (insn instanceof TypeInsnNode type
                        && type.desc.contains(TEMP_MOD_TEMPLATE_OWNER)) {
                    throw mismatch("temp-mod template type instruction leaked into target");
                }
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type
                        && type.getDescriptor().contains(TEMP_MOD_TEMPLATE_OWNER)) {
                    throw mismatch("temp-mod template class literal leaked into target");
                }
            }
        }
    }


    private static void replaceMethodBody(MethodNode method, InsnList code, int maxLocals) {
        method.instructions.clear();
        method.instructions.add(code);
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
        if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
        method.maxLocals = maxLocals;
        method.maxStack = 8;
    }

    private static int countNew(MethodNode method, String type) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                    && allocation.desc.equals(type)) count++;
        }
        return count;
    }

    /**
     * CommodityOnMarket.reapplyEventMod() normally calls unmodifyFlat("eMod")
     * for every commodity every campaign frame, including the overwhelmingly
     * common case where the combined recent-trade quantity is zero and the
     * modifier was already removed on a previous frame.
     *
     * Only that proven-absent zero path is cached. The nonzero path retains the
     * complete vanilla remove -> calculate -> conditional add sequence and its
     * original float behavior. The private flag is transient, is set only after
     * a successful removal, and is cleared before every nonzero update so loads,
     * transitions, and exceptional exits all fail open.
     */
    private PatchReport patchCommodityEventModDirtyCache(ClassNode node) {
        final String knownAbsentField = "smo$commodityEventModKnownAbsent";
        final int stateAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT
                | Opcodes.ACC_SYNTHETIC;
        MethodNode method = requireMethod(node, "reapplyEventMod", "()V");

        FieldNode knownAbsent = null;
        for (FieldNode field : node.fields) {
            if (!field.name.equals(knownAbsentField)) continue;
            if (knownAbsent != null) {
                throw mismatch("duplicate commodity event-mod known-absent field");
            }
            knownAbsent = field;
        }
        if (knownAbsent != null) {
            if (!knownAbsent.desc.equals("Z") || knownAbsent.access != stateAccess
                    || knownAbsent.signature != null || knownAbsent.value != null) {
                throw mismatch("commodity event-mod known-absent field is foreign");
            }
            requireOptimizedCommodityEventModShape(node, method, knownAbsentField);
            throw already("commodity event-mod zero-path cache postcondition matches");
        }

        String availableField = requireOriginalCommodityEventModShape(node, method);
        node.fields.add(new FieldNode(Opcodes.ASM8, stateAccess,
                knownAbsentField, "Z", null, null));
        rewriteCommodityEventMod(method, node.name, availableField, knownAbsentField);
        requireOptimizedCommodityEventModShape(node, method, knownAbsentField);

        PatchReport report = new PatchReport();
        report.add("proven-absent commodity event-mod removals", 1);
        return report;
    }

    private static String requireOriginalCommodityEventModShape(ClassNode node,
                                                                 MethodNode method) {
        final String stat = "com/fs/starfarer/api/combat/MutableStatWithTempMods";
        List<AbstractInsnNode> code = meaningfulInstructions(method);
        requireCount("CommodityOnMarket.reapplyEventMod original instruction count",
                code.size(), 31);
        requireVar(code.get(0), Opcodes.ALOAD, 0, "event-mod quantity owner");
        requireCall(asMethod(code.get(1), "event-mod quantity call"), Opcodes.INVOKEVIRTUAL,
                node.name, "getCombinedTradeModQuantity", "()F", "event-mod quantity call");
        requireVar(code.get(2), Opcodes.FSTORE, 1, "event-mod quantity local");
        requireVar(code.get(3), Opcodes.FLOAD, 1, "event-mod quantity test");
        if (code.get(4).getOpcode() != Opcodes.FCONST_0
                || code.get(5).getOpcode() != Opcodes.FCMPL
                || !(code.get(6) instanceof JumpInsnNode zeroQuantity)
                || zeroQuantity.getOpcode() != Opcodes.IFEQ) {
            throw mismatch("event-mod zero-quantity branch changed");
        }

        requireVar(code.get(7), Opcodes.ALOAD, 0, "event-mod available owner");
        if (!(code.get(8) instanceof FieldInsnNode available)
                || available.getOpcode() != Opcodes.GETFIELD
                || !available.owner.equals(node.name) || !available.desc.equals("L" + stat + ";")) {
            throw mismatch("event-mod available-stat field changed");
        }
        requireString(code.get(9), "eMod", "event-mod removal key");
        requireCall(asMethod(code.get(10), "event-mod removal"), Opcodes.INVOKEVIRTUAL,
                stat, "unmodifyFlat", "(Ljava/lang/String;)V", "event-mod removal");
        requireVar(code.get(11), Opcodes.ALOAD, 0, "event-mod value owner");
        requireVar(code.get(12), Opcodes.FLOAD, 1, "event-mod quantity argument");
        requireCall(asMethod(code.get(13), "event-mod value call"), Opcodes.INVOKEVIRTUAL,
                node.name, "getModValueForQuantity", "(F)F", "event-mod value call");
        requireVar(code.get(14), Opcodes.FSTORE, 2, "event-mod value local");
        requireVar(code.get(15), Opcodes.FLOAD, 2, "event-mod value test");
        if (code.get(16).getOpcode() != Opcodes.FCONST_0
                || code.get(17).getOpcode() != Opcodes.FCMPL
                || !(code.get(18) instanceof JumpInsnNode zeroValue)
                || zeroValue.getOpcode() != Opcodes.IFEQ) {
            throw mismatch("event-mod zero-value branch changed");
        }

        requireVar(code.get(19), Opcodes.ALOAD, 0, "event-mod modification owner");
        requireField(code.get(20), Opcodes.GETFIELD, node.name, available.name,
                available.desc, "event-mod modification available field");
        requireString(code.get(21), "eMod", "event-mod modification key");
        requireVar(code.get(22), Opcodes.FLOAD, 2, "event-mod modification value");
        requireString(code.get(23), "Recent trade/events", "event-mod description");
        requireCall(asMethod(code.get(24), "event-mod modification"), Opcodes.INVOKEVIRTUAL,
                stat, "modifyFlat", "(Ljava/lang/String;FLjava/lang/String;)V",
                "event-mod modification");
        if (!(code.get(25) instanceof JumpInsnNode done) || done.getOpcode() != Opcodes.GOTO) {
            throw mismatch("event-mod nonzero completion branch changed");
        }

        requireVar(code.get(26), Opcodes.ALOAD, 0, "zero event-mod available owner");
        requireField(code.get(27), Opcodes.GETFIELD, node.name, available.name,
                available.desc, "zero event-mod available field");
        requireString(code.get(28), "eMod", "zero event-mod removal key");
        requireCall(asMethod(code.get(29), "zero event-mod removal"), Opcodes.INVOKEVIRTUAL,
                stat, "unmodifyFlat", "(Ljava/lang/String;)V", "zero event-mod removal");
        if (code.get(30).getOpcode() != Opcodes.RETURN
                || nextMeaningful(zeroQuantity.label) != code.get(26)
                || nextMeaningful(zeroValue.label) != code.get(30)
                || nextMeaningful(done.label) != code.get(30)
                || !method.tryCatchBlocks.isEmpty()) {
            throw mismatch("event-mod original control flow changed");
        }
        return available.name;
    }

    private static void rewriteCommodityEventMod(MethodNode method, String owner,
                                                  String availableField,
                                                  String knownAbsentField) {
        final String stat = "com/fs/starfarer/api/combat/MutableStatWithTempMods";
        LabelNode zeroQuantity = new LabelNode();
        LabelNode applyZeroRemoval = new LabelNode();
        LabelNode done = new LabelNode();
        InsnList code = new InsnList();

        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner,
                "getCombinedTradeModQuantity", "()F", false));
        code.add(new VarInsnNode(Opcodes.FSTORE, 1));
        code.add(new VarInsnNode(Opcodes.FLOAD, 1));
        code.add(new InsnNode(Opcodes.FCONST_0));
        code.add(new InsnNode(Opcodes.FCMPL));
        code.add(new JumpInsnNode(Opcodes.IFEQ, zeroQuantity));

        // The nonzero path is the original implementation, with only a
        // fail-open state clear inserted before the first operation that can throw.
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, knownAbsentField, "Z"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, owner, availableField, "L" + stat + ";"));
        code.add(new LdcInsnNode("eMod"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, stat,
                "unmodifyFlat", "(Ljava/lang/String;)V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.FLOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner,
                "getModValueForQuantity", "(F)F", false));
        code.add(new VarInsnNode(Opcodes.FSTORE, 2));
        code.add(new VarInsnNode(Opcodes.FLOAD, 2));
        code.add(new InsnNode(Opcodes.FCONST_0));
        code.add(new InsnNode(Opcodes.FCMPL));
        code.add(new JumpInsnNode(Opcodes.IFEQ, done));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, owner, availableField, "L" + stat + ";"));
        code.add(new LdcInsnNode("eMod"));
        code.add(new VarInsnNode(Opcodes.FLOAD, 2));
        code.add(new LdcInsnNode("Recent trade/events"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, stat,
                "modifyFlat", "(Ljava/lang/String;FLjava/lang/String;)V", false));
        code.add(new JumpInsnNode(Opcodes.GOTO, done));

        // Zero quantity always means that eMod must be absent. Once a successful
        // removal established that invariant, repeated absent HashMap.remove()
        // calls are unnecessary until a nonzero quantity clears the proof.
        code.add(zeroQuantity);
        code.add(new FrameNode(Opcodes.F_FULL, 2,
                new Object[]{owner, Opcodes.FLOAT}, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, owner, knownAbsentField, "Z"));
        code.add(new JumpInsnNode(Opcodes.IFEQ, applyZeroRemoval));
        code.add(new InsnNode(Opcodes.RETURN));
        code.add(applyZeroRemoval);
        code.add(new FrameNode(Opcodes.F_FULL, 2,
                new Object[]{owner, Opcodes.FLOAT}, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, owner, availableField, "L" + stat + ";"));
        code.add(new LdcInsnNode("eMod"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, stat,
                "unmodifyFlat", "(Ljava/lang/String;)V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new InsnNode(Opcodes.ICONST_1));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, knownAbsentField, "Z"));
        code.add(done);
        code.add(new FrameNode(Opcodes.F_FULL, 2,
                new Object[]{owner, Opcodes.FLOAT}, 0, null));
        code.add(new InsnNode(Opcodes.RETURN));

        method.instructions.clear();
        method.instructions.add(code);
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        if (method.visibleLocalVariableAnnotations != null) {
            method.visibleLocalVariableAnnotations.clear();
        }
        if (method.invisibleLocalVariableAnnotations != null) {
            method.invisibleLocalVariableAnnotations.clear();
        }
        method.maxLocals = 3;
        method.maxStack = 4;
    }

    private static void requireOptimizedCommodityEventModShape(ClassNode node,
                                                                MethodNode method,
                                                                String knownAbsentField) {
        final String stat = "com/fs/starfarer/api/combat/MutableStatWithTempMods";
        List<AbstractInsnNode> code = meaningfulInstructions(method);
        requireCount("CommodityOnMarket.reapplyEventMod optimized instruction count",
                code.size(), 41);
        requireVar(code.get(0), Opcodes.ALOAD, 0, "cached event-mod quantity owner");
        requireCall(asMethod(code.get(1), "cached event-mod quantity call"),
                Opcodes.INVOKEVIRTUAL, node.name, "getCombinedTradeModQuantity", "()F",
                "cached event-mod quantity call");
        requireVar(code.get(2), Opcodes.FSTORE, 1, "cached event-mod quantity local");
        requireVar(code.get(3), Opcodes.FLOAD, 1, "cached event-mod quantity test");
        if (code.get(4).getOpcode() != Opcodes.FCONST_0
                || code.get(5).getOpcode() != Opcodes.FCMPL
                || !(code.get(6) instanceof JumpInsnNode zeroQuantity)
                || zeroQuantity.getOpcode() != Opcodes.IFEQ) {
            throw mismatch("cached event-mod zero-quantity branch changed");
        }

        requireVar(code.get(7), Opcodes.ALOAD, 0, "nonzero cache-state owner");
        if (code.get(8).getOpcode() != Opcodes.ICONST_0) {
            throw mismatch("nonzero event-mod cache clear value changed");
        }
        requireField(code.get(9), Opcodes.PUTFIELD, node.name, knownAbsentField, "Z",
                "nonzero event-mod cache clear");
        requireVar(code.get(10), Opcodes.ALOAD, 0, "cached event-mod available owner");
        if (!(code.get(11) instanceof FieldInsnNode available)
                || available.getOpcode() != Opcodes.GETFIELD
                || !available.owner.equals(node.name) || !available.desc.equals("L" + stat + ";")) {
            throw mismatch("cached event-mod available-stat field changed");
        }
        requireString(code.get(12), "eMod", "cached event-mod removal key");
        requireCall(asMethod(code.get(13), "cached event-mod nonzero removal"),
                Opcodes.INVOKEVIRTUAL, stat, "unmodifyFlat", "(Ljava/lang/String;)V",
                "cached event-mod nonzero removal");
        requireVar(code.get(14), Opcodes.ALOAD, 0, "cached event-mod value owner");
        requireVar(code.get(15), Opcodes.FLOAD, 1, "cached event-mod value argument");
        requireCall(asMethod(code.get(16), "cached event-mod value call"),
                Opcodes.INVOKEVIRTUAL, node.name, "getModValueForQuantity", "(F)F",
                "cached event-mod value call");
        requireVar(code.get(17), Opcodes.FSTORE, 2, "cached event-mod value local");
        requireVar(code.get(18), Opcodes.FLOAD, 2, "cached event-mod zero test");
        if (code.get(19).getOpcode() != Opcodes.FCONST_0
                || code.get(20).getOpcode() != Opcodes.FCMPL
                || !(code.get(21) instanceof JumpInsnNode zeroValue)
                || zeroValue.getOpcode() != Opcodes.IFEQ) {
            throw mismatch("cached event-mod zero-value branch changed");
        }
        requireVar(code.get(22), Opcodes.ALOAD, 0, "cached event-mod modification owner");
        requireField(code.get(23), Opcodes.GETFIELD, node.name, available.name,
                available.desc, "cached event-mod modification available field");
        requireString(code.get(24), "eMod", "cached event-mod modification key");
        requireVar(code.get(25), Opcodes.FLOAD, 2, "cached event-mod modification value");
        requireString(code.get(26), "Recent trade/events", "cached event-mod description");
        requireCall(asMethod(code.get(27), "cached event-mod modification"),
                Opcodes.INVOKEVIRTUAL, stat, "modifyFlat",
                "(Ljava/lang/String;FLjava/lang/String;)V", "cached event-mod modification");
        if (!(code.get(28) instanceof JumpInsnNode nonzeroDone)
                || nonzeroDone.getOpcode() != Opcodes.GOTO) {
            throw mismatch("cached event-mod nonzero completion branch changed");
        }

        requireVar(code.get(29), Opcodes.ALOAD, 0, "zero event-mod cache owner");
        requireField(code.get(30), Opcodes.GETFIELD, node.name, knownAbsentField, "Z",
                "zero event-mod cache read");
        if (!(code.get(31) instanceof JumpInsnNode unknown)
                || unknown.getOpcode() != Opcodes.IFEQ
                || code.get(32).getOpcode() != Opcodes.RETURN) {
            throw mismatch("zero event-mod cache guard changed");
        }
        requireVar(code.get(33), Opcodes.ALOAD, 0, "zero event-mod removal owner");
        requireField(code.get(34), Opcodes.GETFIELD, node.name, available.name,
                available.desc, "zero event-mod removal available field");
        requireString(code.get(35), "eMod", "zero event-mod removal key");
        requireCall(asMethod(code.get(36), "zero event-mod removal"),
                Opcodes.INVOKEVIRTUAL, stat, "unmodifyFlat", "(Ljava/lang/String;)V",
                "zero event-mod removal");
        requireVar(code.get(37), Opcodes.ALOAD, 0, "zero event-mod proof owner");
        if (code.get(38).getOpcode() != Opcodes.ICONST_1) {
            throw mismatch("zero event-mod proof value changed");
        }
        requireField(code.get(39), Opcodes.PUTFIELD, node.name, knownAbsentField, "Z",
                "zero event-mod proof write");
        if (code.get(40).getOpcode() != Opcodes.RETURN
                || nextMeaningful(zeroQuantity.label) != code.get(29)
                || nextMeaningful(unknown.label) != code.get(33)
                || nextMeaningful(zeroValue.label) != code.get(40)
                || nextMeaningful(nonzeroDone.label) != code.get(40)
                || !method.tryCatchBlocks.isEmpty()) {
            throw mismatch("cached event-mod optimized control flow changed");
        }
    }

    private static void requireString(AbstractInsnNode insn, String expected, String label) {
        if (!(insn instanceof LdcInsnNode ldc) || !expected.equals(ldc.cst)) {
            throw mismatch(label + " changed");
        }
    }

    private static int replaceEconomyMarketsSnapshot(MethodNode method, boolean scopeInstalled,
                                                       String label) {
        String hookDesc = "(L" + ECONOMY + ";)Ljava/util/List;";
        List<MethodInsnNode> originals = calls(method, Opcodes.INVOKEVIRTUAL,
                ECONOMY, "getMarketsCopy", "()Ljava/util/List;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "borrowEconomyMarketsSnapshot", hookDesc);
        boolean original = originals.size() == 1 && hooks == 0;
        boolean patched = originals.isEmpty() && hooks == 1;
        if (patched) {
            if (scopeInstalled) throw mismatch(label + " hook exists without its scratch scope");
            throw already(label + " hook is already present");
        }
        if (!original) throw mismatch(label + " is missing, duplicated, or partially patched");
        MethodInsnNode call = originals.get(0);
        Frame<SourceValue>[] frames = sourceFrames(ECONOMY, method);
        if (!sourceIsLocal(receiverSource(method, call, frames), Opcodes.ALOAD, 0)) {
            throw mismatch(label + " receiver is not this");
        }
        MethodInsnNode iterator = asMethod(nextMeaningful(call), label + " iterator");
        requireCall(iterator, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                "()Ljava/util/Iterator;", label + " iterator");
        makeStatic(call, HOOKS, "borrowEconomyMarketsSnapshot", hookDesc);
        return 1;
    }

    private static int replaceImmediateArrayListIteratorSnapshots(String owner, MethodNode method,
                                                                   int expected, String hookName,
                                                                   boolean scopeInstalled,
                                                                   String label) {
        String hookDesc = "(Ljava/util/Collection;)Ljava/util/ArrayList;";
        List<MethodInsnNode> originals = new ArrayList<>();
        for (MethodInsnNode constructor : calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V")) {
            AbstractInsnNode next = nextMeaningful(constructor);
            if (next instanceof MethodInsnNode iterator
                    && callMatches(iterator, Opcodes.INVOKEVIRTUAL,
                    "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;")) {
                originals.add(constructor);
            }
        }
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, hookName, hookDesc);
        for (MethodInsnNode hook : hooks) {
            MethodInsnNode iterator = asMethod(nextMeaningful(hook), label + " patched iterator");
            requireCall(iterator, Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "iterator",
                    "()Ljava/util/Iterator;", label + " patched iterator");
        }
        boolean original = originals.size() == expected && hooks.isEmpty();
        boolean patched = originals.isEmpty() && hooks.size() == expected;
        if (patched) {
            if (scopeInstalled) throw mismatch(label + " hooks exist without their scratch scope");
            throw already(label + " hooks are already present");
        }
        if (!original) throw mismatch(label + " has mixed, missing, or ambiguous sites");

        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        for (MethodInsnNode constructor : originals) {
            AllocationPair pair = allocationPair(method, constructor, frames,
                    "java/util/ArrayList", label);
            method.instructions.remove(pair.allocation);
            method.instructions.remove(pair.duplicate);
            makeStatic(constructor, HOOKS, hookName, hookDesc);
        }
        requireCount(label + " hook count",
                countCalls(method, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc), expected);
        return expected;
    }

    private PatchReport patchShipAdvanceScratch(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        PatchState state = uniformPatchState("Ship.advance scratch reuse",
                scratchScopeState(node.name, advance, "Ship.advance"),
                storedSimpleAllocationState(advance,
                        "java/util/ArrayList", "()V", "borrowShipList",
                        "()Ljava/util/ArrayList;", 3,
                        "Ship.advance command/group lists"),
                storedSimpleAllocationState(advance,
                        "java/util/HashSet", "()V", "borrowShipSet",
                        "()Ljava/util/HashSet;", 2,
                        "Ship.advance command/weapon sets"),
                shipCommandSnapshotState(node.name, advance));
        if (state == PatchState.PATCHED) {
            throw already("all Ship.advance scratch hooks and scopes satisfy their postconditions");
        }
        boolean scopeInstalled = ensureScratchScope(node.name, advance, "Ship.advance");
        int changed = 0;
        changed += replaceStoredSimpleAllocations(node.name, advance,
                "java/util/ArrayList", "()V", "borrowShipList", "()Ljava/util/ArrayList;",
                3, scopeInstalled, "Ship.advance command/group lists");
        changed += replaceStoredSimpleAllocations(node.name, advance,
                "java/util/HashSet", "()V", "borrowShipSet", "()Ljava/util/HashSet;",
                2, false, "Ship.advance command/weapon sets");
        changed += replaceShipCommandSnapshot(node.name, advance);

        PatchReport report = new PatchReport();
        report.add("reusable Ship.advance command/group collections", changed);
        return report;
    }

    private static int replaceStoredSimpleAllocations(String owner, MethodNode method,
                                                       String type, String constructorDesc,
                                                       String hookName, String hookDesc,
                                                       int expected, boolean scopeInstalled,
                                                       String label) {
        List<MethodInsnNode> constructors = calls(method, Opcodes.INVOKESPECIAL,
                type, "<init>", constructorDesc);
        List<MethodInsnNode> hookCalls = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, hookName, hookDesc);
        boolean original = constructors.size() == expected && hookCalls.isEmpty();
        boolean patched = constructors.isEmpty() && hookCalls.size() == expected;
        if (patched) {
            if (scopeInstalled) throw mismatch(label + " hooks exist without their scratch scope");
            for (MethodInsnNode hook : hookCalls) {
                AbstractInsnNode stored = nextMeaningful(hook);
                if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                    throw mismatch(label + " patched result is not stored directly in a local");
                }
            }
            throw already(label + " hooks are already present");
        }
        if (!original) throw mismatch(label + " has mixed, missing, or ambiguous allocations");
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        for (MethodInsnNode constructor : constructors) {
            AbstractInsnNode stored = nextMeaningful(constructor);
            if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(label + " allocation result is not stored directly in a local");
            }
            AllocationPair pair = constructorDesc.equals("()V")
                    ? immediateNoArgAllocationPair(constructor, type, label)
                    : allocationPair(method, constructor, frames, type, label);
            method.instructions.remove(pair.allocation);
            method.instructions.remove(pair.duplicate);
            makeStatic(constructor, HOOKS, hookName, hookDesc);
        }
        return expected;
    }

    private static int replaceShipCommandSnapshot(String owner, MethodNode method) {
        String hookDesc = "(Ljava/util/Collection;)Ljava/util/ArrayList;";
        List<MethodInsnNode> raw = calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V");
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, "borrowShipListSnapshot", hookDesc);
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        List<MethodInsnNode> directFieldSnapshots = new ArrayList<>();
        List<MethodInsnNode> listenerSnapshots = new ArrayList<>();
        for (MethodInsnNode constructor : raw) {
            SourceValue argument = argumentSource(method, constructor, frames, 0);
            FieldInsnNode field = optionalSourceField(argument, Opcodes.GETFIELD, owner, LIST_DESC);
            if (field != null) {
                MethodInsnNode iterator = asMethod(nextMeaningful(constructor),
                        "Ship.advance command snapshot iterator");
                requireCall(iterator, Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "iterator",
                        "()Ljava/util/Iterator;", "Ship.advance command snapshot iterator");
                directFieldSnapshots.add(constructor);
                continue;
            }
            if (sourceIsMethod(argument, Opcodes.INVOKEINTERFACE,
                    "com/fs/starfarer/api/combat/listeners/CombatListenerManagerAPI",
                    "getListeners", "(Ljava/lang/Class;)Ljava/util/List;")) {
                listenerSnapshots.add(constructor);
            }
        }
        boolean original = directFieldSnapshots.size() == 1 && listenerSnapshots.size() == 1
                && raw.size() == 2 && hooks.isEmpty();
        boolean patched = directFieldSnapshots.isEmpty() && listenerSnapshots.size() == 1
                && raw.size() == 1 && hooks.size() == 1;
        if (patched) throw already("Ship.advance command snapshot hook is already present");
        if (!original) {
            throw mismatch("Ship.advance listener/command snapshot ownership changed structurally");
        }
        MethodInsnNode constructor = directFieldSnapshots.get(0);
        AllocationPair pair = allocationPair(method, constructor, frames,
                "java/util/ArrayList", "Ship.advance command snapshot");
        method.instructions.remove(pair.allocation);
        method.instructions.remove(pair.duplicate);
        makeStatic(constructor, HOOKS, "borrowShipListSnapshot", hookDesc);
        return 1;
    }

    private PatchReport patchDynamicParticleCleanup(ClassNode node) {
        MethodNode advance = requireMethod(node, "advance", "(F)V");
        PatchState state = uniformPatchState("DynamicParticleGroup cleanup",
                scratchScopeState(node.name, advance, "DynamicParticleGroup.advance"),
                storedSimpleAllocationState(advance,
                        "java/util/ArrayList", "()V", "borrowParticleRemovalList",
                        "()Ljava/util/ArrayList;", 1,
                        "DynamicParticleGroup expired list"),
                particleCleanupState(advance));
        if (state == PatchState.PATCHED) {
            throw already("all DynamicParticleGroup cleanup hooks and scope postconditions match");
        }
        boolean scopeInstalled = ensureScratchScope(node.name, advance,
                "DynamicParticleGroup.advance");
        int changed = replaceStoredSimpleAllocations(node.name, advance,
                "java/util/ArrayList", "()V", "borrowParticleRemovalList",
                "()Ljava/util/ArrayList;", 1, scopeInstalled,
                "DynamicParticleGroup expired list");

        String hookDesc = "(Ljava/util/LinkedList;Ljava/util/Collection;)Z";
        List<MethodInsnNode> raw = calls(advance, Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedList", "removeAll", "(Ljava/util/Collection;)Z");
        int hooks = countCalls(advance, Opcodes.INVOKESTATIC, HOOKS,
                "removeExpiredParticles", hookDesc);
        boolean original = raw.size() == 1 && hooks == 0;
        boolean patched = raw.isEmpty() && hooks == 1;
        if (patched) {
            if (changed != 0) throw mismatch("DynamicParticleGroup cleanup is partially patched");
            throw already("DynamicParticleGroup cleanup hook is already present");
        }
        if (!original) throw mismatch("DynamicParticleGroup removeAll site changed structurally");
        MethodInsnNode removeAll = raw.get(0);
        if (nextMeaningful(removeAll) == null || nextMeaningful(removeAll).getOpcode() != Opcodes.POP) {
            throw mismatch("DynamicParticleGroup removeAll result is not discarded");
        }
        makeStatic(removeAll, HOOKS, "removeExpiredParticles", hookDesc);
        changed++;

        PatchReport report = new PatchReport();
        report.add("reusable particle expiry buffer and linear cleanup", changed);
        return report;
    }

    private static PatchState economyMarketsSnapshotState(MethodNode method, String label) {
        String hookDesc = "(L" + ECONOMY + ";)Ljava/util/List;";
        List<MethodInsnNode> originals = calls(method, Opcodes.INVOKEVIRTUAL,
                ECONOMY, "getMarketsCopy", "()Ljava/util/List;");
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC, HOOKS,
                "borrowEconomyMarketsSnapshot", hookDesc);
        boolean original = originals.size() == 1 && hooks.isEmpty();
        boolean patched = originals.isEmpty() && hooks.size() == 1;
        if (!original && !patched) {
            throw mismatch(label + " is missing, duplicated, or partially patched");
        }

        Frame<SourceValue>[] frames = sourceFrames(ECONOMY, method);
        MethodInsnNode snapshot = original ? originals.get(0) : hooks.get(0);
        SourceValue economy = original
                ? receiverSource(method, snapshot, frames)
                : argumentSource(method, snapshot, frames, 0);
        if (!sourceIsLocal(economy, Opcodes.ALOAD, 0)) {
            throw mismatch(label + " Economy source is not this");
        }
        MethodInsnNode iterator = asMethod(nextMeaningful(snapshot), label + " iterator");
        requireCall(iterator, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                "()Ljava/util/Iterator;", label + " iterator");
        return original ? PatchState.ORIGINAL : PatchState.PATCHED;
    }

    private static PatchState immediateArrayListIteratorSnapshotState(MethodNode method,
                                                                        int expected,
                                                                        String hookName,
                                                                        String label) {
        String hookDesc = "(Ljava/util/Collection;)Ljava/util/ArrayList;";
        List<MethodInsnNode> originals = new ArrayList<>();
        for (MethodInsnNode constructor : calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V")) {
            AbstractInsnNode next = nextMeaningful(constructor);
            if (next instanceof MethodInsnNode iterator
                    && callMatches(iterator, Opcodes.INVOKEVIRTUAL,
                    "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;")) {
                originals.add(constructor);
            }
        }
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, hookName, hookDesc);
        for (MethodInsnNode hook : hooks) {
            MethodInsnNode iterator = asMethod(nextMeaningful(hook), label + " patched iterator");
            requireCall(iterator, Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "iterator",
                    "()Ljava/util/Iterator;", label + " patched iterator");
        }
        boolean original = originals.size() == expected && hooks.isEmpty();
        boolean patched = originals.isEmpty() && hooks.size() == expected;
        if (!original && !patched) {
            throw mismatch(label + " has mixed, missing, or ambiguous sites");
        }
        return original ? PatchState.ORIGINAL : PatchState.PATCHED;
    }

    private static PatchState storedSimpleAllocationState(MethodNode method,
                                                           String type, String constructorDesc,
                                                           String hookName, String hookDesc,
                                                           int expected, String label) {
        List<MethodInsnNode> constructors = calls(method, Opcodes.INVOKESPECIAL,
                type, "<init>", constructorDesc);
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, hookName, hookDesc);
        boolean original = constructors.size() == expected && hooks.isEmpty();
        boolean patched = constructors.isEmpty() && hooks.size() == expected;
        if (!original && !patched) {
            throw mismatch(label + " has mixed, missing, or ambiguous allocations");
        }

        for (MethodInsnNode result : original ? constructors : hooks) {
            AbstractInsnNode stored = nextMeaningful(result);
            if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(label + " result is not stored directly in a local");
            }
            if (original && constructorDesc.equals("()V")) {
                immediateNoArgAllocationPair(result, type, label);
            }
        }
        return original ? PatchState.ORIGINAL : PatchState.PATCHED;
    }

    private static PatchState shipCommandSnapshotState(String owner, MethodNode method) {
        String label = "Ship.advance command snapshot";
        String hookDesc = "(Ljava/util/Collection;)Ljava/util/ArrayList;";
        List<MethodInsnNode> raw = calls(method, Opcodes.INVOKESPECIAL,
                "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V");
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC,
                HOOKS, "borrowShipListSnapshot", hookDesc);
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        List<MethodInsnNode> directFieldSnapshots = new ArrayList<>();
        List<MethodInsnNode> listenerSnapshots = new ArrayList<>();
        for (MethodInsnNode constructor : raw) {
            SourceValue argument = argumentSource(method, constructor, frames, 0);
            FieldInsnNode field = optionalSourceField(argument, Opcodes.GETFIELD, owner, LIST_DESC);
            if (field != null) {
                requireThisFieldReceiver(field, label);
                MethodInsnNode iterator = asMethod(nextMeaningful(constructor), label + " iterator");
                requireCall(iterator, Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "iterator",
                        "()Ljava/util/Iterator;", label + " iterator");
                directFieldSnapshots.add(constructor);
            } else if (sourceIsMethod(argument, Opcodes.INVOKEINTERFACE,
                    "com/fs/starfarer/api/combat/listeners/CombatListenerManagerAPI",
                    "getListeners", "(Ljava/lang/Class;)Ljava/util/List;")) {
                listenerSnapshots.add(constructor);
            }
        }
        for (MethodInsnNode hook : hooks) {
            FieldInsnNode field = optionalSourceField(argumentSource(method, hook, frames, 0),
                    Opcodes.GETFIELD, owner, LIST_DESC);
            if (field == null) throw mismatch(label + " hook input is not the owned command field");
            requireThisFieldReceiver(field, label + " hook");
            MethodInsnNode iterator = asMethod(nextMeaningful(hook), label + " patched iterator");
            requireCall(iterator, Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "iterator",
                    "()Ljava/util/Iterator;", label + " patched iterator");
        }

        boolean original = directFieldSnapshots.size() == 1 && listenerSnapshots.size() == 1
                && raw.size() == 2 && hooks.isEmpty();
        boolean patched = directFieldSnapshots.isEmpty() && listenerSnapshots.size() == 1
                && raw.size() == 1 && hooks.size() == 1;
        if (!original && !patched) {
            throw mismatch("Ship.advance listener/command snapshot ownership changed structurally");
        }
        return original ? PatchState.ORIGINAL : PatchState.PATCHED;
    }

    private static void requireThisFieldReceiver(FieldInsnNode field, String label) {
        AbstractInsnNode receiver = previousMeaningful(field);
        if (!(receiver instanceof VarInsnNode load)
                || load.getOpcode() != Opcodes.ALOAD || load.var != 0) {
            throw mismatch(label + " field receiver is not this");
        }
    }

    private static PatchState particleCleanupState(MethodNode method) {
        String label = "DynamicParticleGroup removeAll cleanup";
        String hookDesc = "(Ljava/util/LinkedList;Ljava/util/Collection;)Z";
        List<MethodInsnNode> raw = calls(method, Opcodes.INVOKEVIRTUAL,
                "java/util/LinkedList", "removeAll", "(Ljava/util/Collection;)Z");
        List<MethodInsnNode> hooks = calls(method, Opcodes.INVOKESTATIC, HOOKS,
                "removeExpiredParticles", hookDesc);
        boolean original = raw.size() == 1 && hooks.isEmpty();
        boolean patched = raw.isEmpty() && hooks.size() == 1;
        if (!original && !patched) {
            throw mismatch(label + " is missing, duplicated, or partially patched");
        }
        MethodInsnNode cleanup = original ? raw.get(0) : hooks.get(0);
        AbstractInsnNode discarded = nextMeaningful(cleanup);
        if (discarded == null || discarded.getOpcode() != Opcodes.POP) {
            throw mismatch(label + " result is not discarded");
        }
        return original ? PatchState.ORIGINAL : PatchState.PATCHED;
    }

    private static PatchState uniformPatchState(String label, PatchState... states) {
        boolean original = true;
        boolean patched = true;
        StringBuilder observed = new StringBuilder();
        for (PatchState state : states) {
            original &= state == PatchState.ORIGINAL;
            patched &= state == PatchState.PATCHED;
            if (observed.length() > 0) observed.append(',');
            observed.append(state);
        }
        if (original) return PatchState.ORIGINAL;
        if (patched) return PatchState.PATCHED;
        throw mismatch(label + " has mixed original/patched postconditions: " + observed);
    }

    private PatchReport patchCommRelaySystemIndex(ClassNode node) {
        String desc = "(" + ENTITY_TOKEN_DESC + ")" + ENTITY_TOKEN_DESC;
        MethodNode method = requireMethod(node, "findNearestCommRelayToReceive", desc);
        boolean scopeInstalled = ensureScratchScope(node.name, method,
                "IntelManager.findNearestCommRelayToReceive");
        String hookDesc = "(L" + CAMPAIGN_ENGINE
                + ";Lorg/lwjgl/util/vector/Vector2f;FF)Ljava/util/List;";
        List<MethodInsnNode> raw = calls(method, Opcodes.INVOKEVIRTUAL,
                CAMPAIGN_ENGINE, "getStarSystems", "()Ljava/util/List;");
        int hooks = countCalls(method, Opcodes.INVOKESTATIC, HOOKS,
                "commRelayCandidateSystems", hookDesc);
        boolean original = raw.size() == 1 && hooks == 0;
        boolean patched = raw.isEmpty() && hooks == 1;
        if (patched) {
            if (scopeInstalled) throw mismatch("comm relay hook exists without its scratch scope");
            throw already("comm relay system-index hook is already present");
        }
        if (!original) throw mismatch("IntelManager comm-relay system scan changed structurally");

        MethodInsnNode getSystems = raw.get(0);
        Frame<SourceValue>[] frames = sourceFrames(node.name, method);
        requireSourceMethod(receiverSource(method, getSystems, frames), Opcodes.INVOKESTATIC,
                CAMPAIGN_ENGINE, "getInstance", "()L" + CAMPAIGN_ENGINE + ";",
                "comm-relay CampaignEngine source");
        requireStoredLocalProducer(method, 2, Opcodes.ASTORE,
                Opcodes.INVOKEINTERFACE,
                "com/fs/starfarer/api/campaign/SectorEntityToken",
                "getLocationInHyperspace", "()Lorg/lwjgl/util/vector/Vector2f;",
                getSystems, "comm-relay player hyperspace location");
        requireFloatLocalProducer(method, 3, getSystems,
                "comm-relay hyperspace units-per-light-year");
        requireCount("MAX_RANGE_AROUND_SYSTEM field",
                countFields(method, Opcodes.GETSTATIC, node.name,
                        "MAX_RANGE_AROUND_SYSTEM", "F"), 2);

        method.instructions.insertBefore(getSystems, new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.insertBefore(getSystems, new VarInsnNode(Opcodes.FLOAD, 3));
        method.instructions.insertBefore(getSystems, new FieldInsnNode(Opcodes.GETSTATIC,
                node.name, "MAX_RANGE_AROUND_SYSTEM", "F"));
        makeStatic(getSystems, HOOKS, "commRelayCandidateSystems", hookDesc);

        PatchReport report = new PatchReport();
        report.add("ordered hyperspace comm-relay system spatial index", 1);
        return report;
    }

    private static void requireStoredLocalProducer(MethodNode method, int local, int storeOpcode,
                                                   int callOpcode, String callOwner,
                                                   String callName, String callDesc,
                                                   AbstractInsnNode before, String label) {
        int matches = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof VarInsnNode store) || store.getOpcode() != storeOpcode
                    || store.var != local || method.instructions.indexOf(store)
                    >= method.instructions.indexOf(before)) continue;
            AbstractInsnNode producer = previousMeaningful(store);
            if (producer instanceof MethodInsnNode call
                    && callMatches(call, callOpcode, callOwner, callName, callDesc)) matches++;
        }
        requireCount(label, matches, 1);
    }

    private static void requireFloatLocalProducer(MethodNode method, int local,
                                                   AbstractInsnNode before, String label) {
        int matches = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof VarInsnNode store) || store.getOpcode() != Opcodes.FSTORE
                    || store.var != local || method.instructions.indexOf(store)
                    >= method.instructions.indexOf(before)) continue;
            AbstractInsnNode producer = previousMeaningful(store);
            if (producer instanceof MethodInsnNode call
                    && Type.getReturnType(call.desc).getSort() == Type.FLOAT) matches++;
        }
        requireCount(label, matches, 1);
    }

    private static AllocationPair immediateNoArgAllocationPair(MethodInsnNode constructor,
                                                                  String type, String label) {
        AbstractInsnNode duplicate = previousMeaningful(constructor);
        AbstractInsnNode allocation = previousMeaningful(duplicate);
        if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP
                || !(allocation instanceof TypeInsnNode typed)
                || typed.getOpcode() != Opcodes.NEW || !typed.desc.equals(type)) {
            throw mismatch(label + " no-arg allocation is not NEW/DUP/<init>");
        }
        return new AllocationPair(typed, duplicate);
    }

    private static AllocationPair allocationPair(MethodNode method, MethodInsnNode constructor,
                                                  Frame<SourceValue>[] frames, String type,
                                                  String label) {
        SourceValue receiver = receiverSource(method, constructor, frames);
        TypeInsnNode allocation = null;
        AbstractInsnNode duplicate = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof TypeInsnNode candidate)
                    || candidate.getOpcode() != Opcodes.NEW || !candidate.desc.equals(type)) continue;
            AbstractInsnNode dup = nextMeaningful(candidate);
            if (dup == null || dup.getOpcode() != Opcodes.DUP
                    || (!sourceContains(receiver, dup) && !sourceContains(receiver, candidate))) continue;
            if (allocation != null) throw mismatch(label + " has ambiguous allocation ownership");
            allocation = candidate;
            duplicate = dup;
        }
        if (allocation == null) throw mismatch(label + " allocation owner was not found");
        return new AllocationPair(allocation, duplicate);
    }

    // ---------------------------------------------------------------------
    // Structural matcher and verifier helpers
    // ---------------------------------------------------------------------

    private static boolean ensureScratchScope(String owner, MethodNode method, String label) {
        if (scratchScopeState(owner, method, label) == PatchState.ORIGINAL) {
            installScratchScope(owner, method, label);
            return true;
        }
        return false;
    }

    private static PatchState scratchScopeState(String owner, MethodNode method, String label) {
        String beginDesc = "()V";
        int begins = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "beginScratchScope", beginDesc);
        int ends = countCalls(method, Opcodes.INVOKESTATIC, HOOKS, "endScratchScope", beginDesc);
        if (begins == 0 && ends == 0) {
            return PatchState.ORIGINAL;
        }
        requireCount(label + " scratch-scope begin hooks", begins, 1);
        int returns = countReturnOpcodes(method);
        requireCount(label + " scratch-scope end hooks", ends, returns + 1);

        MethodInsnNode begin = only(calls(method, Opcodes.INVOKESTATIC,
                HOOKS, "beginScratchScope", beginDesc), label + " scratch begin");
        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first != begin) throw mismatch(label + " scratch scope does not begin at method entry");

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isReturnOpcode(insn.getOpcode())) continue;
            AbstractInsnNode previous = previousMeaningful(insn);
            if (!(previous instanceof MethodInsnNode call)
                    || !callMatches(call, Opcodes.INVOKESTATIC, HOOKS, "endScratchScope", beginDesc)) {
                throw mismatch(label + " return is not protected by endScratchScope");
            }
        }

        int handlers = 0;
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            if (block.type != null) continue;
            AbstractInsnNode handlerStart = nextMeaningful(block.handler);
            if (!(handlerStart instanceof MethodInsnNode call)
                    || !callMatches(call, Opcodes.INVOKESTATIC, HOOKS, "endScratchScope", beginDesc)) continue;
            AbstractInsnNode rethrow = nextMeaningful(call);
            if (rethrow != null && rethrow.getOpcode() == Opcodes.ATHROW) handlers++;
        }
        requireCount(label + " scratch-scope catch-all", handlers, 1);
        return PatchState.PATCHED;
    }

    private static boolean hasExternalControlFlowEntry(
            MethodNode method, AbstractInsnNode start, AbstractInsnNode end) {
        Set<AbstractInsnNode> guarded = Collections.newSetFromMap(new IdentityHashMap<>());
        AbstractInsnNode cursor = start;
        while (cursor != null) {
            guarded.add(cursor);
            if (cursor == end) break;
            cursor = nextMeaningful(cursor);
        }
        if (cursor == null) return true;

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (guarded.contains(insn)) continue;
            if (insn instanceof JumpInsnNode jump) {
                AbstractInsnNode entry = nextMeaningful(jump.label);
                if (entry != start && guarded.contains(entry)) return true;
            } else if (insn instanceof LookupSwitchInsnNode lookup) {
                AbstractInsnNode defaultEntry = nextMeaningful(lookup.dflt);
                if (defaultEntry != start && guarded.contains(defaultEntry)) return true;
                for (LabelNode label : lookup.labels) {
                    AbstractInsnNode entry = nextMeaningful(label);
                    if (entry != start && guarded.contains(entry)) return true;
                }
            } else if (insn instanceof TableSwitchInsnNode table) {
                AbstractInsnNode defaultEntry = nextMeaningful(table.dflt);
                if (defaultEntry != start && guarded.contains(defaultEntry)) return true;
                for (LabelNode label : table.labels) {
                    AbstractInsnNode entry = nextMeaningful(label);
                    if (entry != start && guarded.contains(entry)) return true;
                }
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            AbstractInsnNode entry = nextMeaningful(block.handler);
            if (entry != start && guarded.contains(entry)) return true;
        }
        return false;
    }

    private static void installScratchScope(String owner, MethodNode method, String label) {
        if (method.instructions == null || method.instructions.getFirst() == null) {
            throw mismatch(label + " has no code for a scratch scope");
        }
        String desc = "()V";
        if (method.name.equals("<init>")) throw mismatch(label + " constructor scopes are unsupported");
        AbstractInsnNode oldFirst = method.instructions.getFirst();
        LabelNode entry = new LabelNode();
        LabelNode start = new LabelNode();
        method.instructions.insertBefore(oldFirst, entry);
        MethodInsnNode begin = new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "beginScratchScope", desc, false);
        method.instructions.insert(entry, begin);
        method.instructions.insert(begin, start);

        int returns = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isReturnOpcode(insn.getOpcode())) continue;
            method.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    HOOKS, "endScratchScope", desc, false));
            returns++;
        }
        if (returns == 0) throw mismatch(label + " has no normal return");

        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(end);
        method.instructions.add(handler);
        Object[] initialLocals = initialFrameLocals(owner, method);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, initialLocals.length, initialLocals,
                1, new Object[] {"java/lang/Throwable"}));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOKS, "endScratchScope", desc, false));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
        ensureScratchScope(owner, method, label);
    }

    private static Object[] initialFrameLocals(String owner, MethodNode method) {
        List<Object> locals = new ArrayList<>();
        if ((method.access & Opcodes.ACC_STATIC) == 0) locals.add(owner);
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            switch (argument.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> locals.add(Opcodes.INTEGER);
                case Type.FLOAT -> locals.add(Opcodes.FLOAT);
                case Type.LONG -> locals.add(Opcodes.LONG);
                case Type.DOUBLE -> locals.add(Opcodes.DOUBLE);
                case Type.ARRAY -> locals.add(argument.getDescriptor());
                case Type.OBJECT -> locals.add(argument.getInternalName());
                default -> throw mismatch("unsupported scratch-scope argument type " + argument);
            }
        }
        return locals.toArray();
    }

    private static int countReturnOpcodes(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isReturnOpcode(insn.getOpcode())) count++;
        }
        return count;
    }

    private static boolean isReturnOpcode(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
    }

    /**
     * Replaces defensive-copy allocations whose compiler locals are reused
     * later in the same large method. The ordinary scratch matcher intentionally
     * requires a single assignment per local; this variant proves ownership from
     * the SourceInterpreter value of each individual ASTORE instead.
     */
    private static int replaceSnapshotAllocationGroup(String owner, MethodNode method,
                                                       AllocationSpec spec,
                                                       boolean scopeInstalled, String label) {
        List<SnapshotAllocationPlan> original = snapshotAllocationPlans(owner, method, spec);
        List<SnapshotHookPlan> patched = snapshotHookPlans(method, spec);
        boolean originalState = original.size() == spec.expected && patched.isEmpty();
        boolean patchedState = original.isEmpty() && patched.size() == spec.expected;
        if (patchedState) {
            if (scopeInstalled) {
                throw mismatch(label + " hooks exist without their required scratch scope");
            }
            for (int i = 0; i < patched.size(); i++) {
                requireSnapshotUseContract(owner, method, patched.get(i).store,
                        expectedSnapshotIterators(spec.hookName, i), label);
            }
            throw already(label + " hooks and assignment-scoped non-escape contracts are already present");
        }
        if (!originalState) {
            throw mismatch(label + " has mixed, missing, or ambiguous allocation/hook sites");
        }

        for (int i = 0; i < original.size(); i++) {
            SnapshotAllocationPlan plan = original.get(i);
            requireSnapshotUseContract(owner, method, plan.store,
                    expectedSnapshotIterators(spec.hookName, i), label);
            method.instructions.remove(plan.allocation);
            method.instructions.remove(plan.duplicate);
            makeStatic(plan.constructor, HOOKS, spec.hookName, spec.hookDesc);
        }
        requireCount(label + " postcondition " + spec.hookName,
                countCalls(method, Opcodes.INVOKESTATIC,
                        HOOKS, spec.hookName, spec.hookDesc), spec.expected);
        return original.size();
    }

    private static List<SnapshotAllocationPlan> snapshotAllocationPlans(
            String owner, MethodNode method, AllocationSpec spec) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        List<SnapshotAllocationPlan> result = new ArrayList<>();
        int rawConstructors = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(spec.type) && call.name.equals("<init>")
                    && call.desc.equals(spec.constructorDesc)) rawConstructors++;
            if (!(insn instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW
                    || !allocation.desc.equals(spec.type)) continue;
            AbstractInsnNode duplicate = nextMeaningful(allocation);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) continue;
            MethodInsnNode constructor = null;
            for (AbstractInsnNode cursor = duplicate.getNext(); cursor != null; cursor = cursor.getNext()) {
                if (cursor instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals(spec.type) && call.name.equals("<init>")
                        && call.desc.equals(spec.constructorDesc)) {
                    SourceValue receiver = receiverSource(method, call, frames);
                    if (sourceContains(receiver, duplicate)) constructor = call;
                    break;
                }
                if (cursor.getOpcode() == Opcodes.NEW && cursor != allocation) break;
            }
            if (constructor == null) continue;
            AbstractInsnNode stored = nextMeaningful(constructor);
            if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(spec.hookName + " snapshot result is not stored in a local");
            }
            result.add(new SnapshotAllocationPlan(allocation, duplicate, constructor, store));
        }
        if (rawConstructors != result.size()) {
            throw mismatch(spec.hookName
                    + " has constructors not paired by uninitialized-value data-flow");
        }
        return result;
    }

    private static List<SnapshotHookPlan> snapshotHookPlans(MethodNode method,
                                                            AllocationSpec spec) {
        List<SnapshotHookPlan> result = new ArrayList<>();
        for (MethodInsnNode hook : calls(method, Opcodes.INVOKESTATIC,
                HOOKS, spec.hookName, spec.hookDesc)) {
            AbstractInsnNode stored = nextMeaningful(hook);
            if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(spec.hookName + " result is not stored directly in a local");
            }
            result.add(new SnapshotHookPlan(hook, store));
        }
        return result;
    }

    private static int expectedSnapshotIterators(String hookName, int ordinal) {
        if (hookName.equals("borrowPausedLocationSnapshot") && ordinal == 0) return 2;
        if (hookName.equals("borrowLocationAdvanceSnapshot")
                || hookName.equals("borrowPausedLocationSnapshot")
                || hookName.equals("borrowEntityScriptSnapshot")) return 1;
        throw mismatch("no snapshot iterator contract for " + hookName);
    }

    private static void requireSnapshotUseContract(String owner, MethodNode method,
                                                   VarInsnNode assignment, int expectedIterators,
                                                   String label) {
        int local = assignment.var;
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Set<AbstractInsnNode> accountedLoads = new HashSet<>();
        List<MethodInsnNode> iterators = new ArrayList<>();
        Map<String, Integer> observed = new LinkedHashMap<>();

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    && recordSnapshotUse(method, receiverSource(method, call, frames),
                    local, assignment, frames, accountedLoads)) {
                String key = call.getOpcode() + "|" + call.owner + "|" + call.name
                        + "|" + call.desc + "|receiver";
                observed.merge(key, 1, Integer::sum);
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals("java/util/List") && call.name.equals("iterator")
                        && call.desc.equals("()Ljava/util/Iterator;")) {
                    iterators.add(call);
                }
            }
            Type[] args = Type.getArgumentTypes(call.desc);
            for (int i = 0; i < args.length; i++) {
                if (recordSnapshotUse(method, argumentSource(method, call, frames, i),
                        local, assignment, frames, accountedLoads)) {
                    String key = call.getOpcode() + "|" + call.owner + "|" + call.name
                            + "|" + call.desc + "|arg" + i;
                    observed.merge(key, 1, Integer::sum);
                }
            }
        }

        Map<String, Integer> expected = new LinkedHashMap<>();
        expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                "()Ljava/util/Iterator;", -1, expectedIterators);
        if (!observed.equals(expected)) {
            throw mismatch(label + " assignment to local " + local
                    + " use contract changed: expected " + expected + ", found " + observed);
        }

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD
                    || load.var != local) continue;
            SourceValue origin = frameBefore(method, load, frames).getLocal(local);
            if (!sourceContains(origin, assignment)) continue;
            if (origin.insns.size() != 1) {
                throw mismatch(label + " local " + local
                        + " merges its snapshot assignment with another value");
            }
            if (!accountedLoads.contains(load)) {
                throw mismatch(label + " local " + local
                        + " has an unclassified alias/escape at instruction "
                        + method.instructions.indexOf(load));
            }
        }
        for (MethodInsnNode iterator : iterators) {
            requireIteratorLifetime(owner, method, iterator,
                    label + " assignment to local " + local);
        }
    }

    private static boolean recordSnapshotUse(MethodNode method, SourceValue source,
                                             int local, VarInsnNode assignment,
                                             Frame<SourceValue>[] frames,
                                             Set<AbstractInsnNode> accountedLoads) {
        if (source == null || source.insns == null || source.insns.isEmpty()) return false;
        boolean fromAssignment = false;
        for (AbstractInsnNode producer : source.insns) {
            if (!(producer instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.ALOAD || load.var != local) continue;
            SourceValue origin = frameBefore(method, load, frames).getLocal(local);
            if (!sourceContains(origin, assignment)) continue;
            if (origin.insns.size() != 1) {
                throw mismatch("snapshot local " + local
                        + " merges its proven assignment with another value");
            }
            accountedLoads.add(load);
            fromAssignment = true;
        }
        return fromAssignment;
    }

    private static int replaceAllocationGroup(String owner, MethodNode method,
                                              List<AllocationSpec> specs, boolean scopeInstalled,
                                              String label) {
        boolean allOriginal = true;
        boolean allPatched = true;
        List<List<AllocationPlan>> plans = new ArrayList<>();
        List<List<Integer>> hookedLocals = new ArrayList<>();
        for (AllocationSpec spec : specs) {
            List<AllocationPlan> found = allocationPlans(owner, method, spec);
            List<Integer> locals = storedHookResultLocals(method, spec);
            boolean originalState = found.size() == spec.expected && locals.isEmpty();
            boolean patchedState = found.isEmpty() && locals.size() == spec.expected;
            allOriginal &= originalState;
            allPatched &= patchedState;
            plans.add(found);
            hookedLocals.add(locals);
        }
        if (allPatched) {
            if (scopeInstalled) {
                throw mismatch(label + " hooks exist without their required scratch scope");
            }
            for (int i = 0; i < specs.size(); i++) {
                for (int j = 0; j < hookedLocals.get(i).size(); j++) {
                    requireScratchUseContract(owner, method, hookedLocals.get(i).get(j),
                            specs.get(i), j, label);
                }
            }
            throw already(label + " hooks and non-escape contracts are already present");
        }
        if (!allOriginal) {
            throw mismatch(label + " has mixed, missing, or ambiguous allocation/hook sites");
        }

        int changed = 0;
        for (int i = 0; i < specs.size(); i++) {
            AllocationSpec spec = specs.get(i);
            for (int j = 0; j < plans.get(i).size(); j++) {
                AllocationPlan plan = plans.get(i).get(j);
                requireScratchUseContract(owner, method, plan.local, spec, j, label);
                method.instructions.remove(plan.allocation);
                method.instructions.remove(plan.duplicate);
                makeStatic(plan.constructor, HOOKS, spec.hookName, spec.hookDesc);
                changed++;
            }
        }
        for (AllocationSpec spec : specs) {
            requireCount(label + " postcondition " + spec.hookName,
                    countCalls(method, Opcodes.INVOKESTATIC, HOOKS, spec.hookName, spec.hookDesc), spec.expected);
        }
        return changed;
    }

    private static List<AllocationPlan> allocationPlans(String owner, MethodNode method, AllocationSpec spec) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        List<AllocationPlan> result = new ArrayList<>();
        int rawConstructors = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(spec.type) && call.name.equals("<init>")
                    && call.desc.equals(spec.constructorDesc)) rawConstructors++;
            if (!(insn instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW || !allocation.desc.equals(spec.type)) continue;
            AbstractInsnNode duplicate = nextMeaningful(allocation);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) continue;
            MethodInsnNode constructor = null;
            for (AbstractInsnNode cursor = duplicate.getNext(); cursor != null; cursor = cursor.getNext()) {
                if (cursor instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals(spec.type) && call.name.equals("<init>")
                        && call.desc.equals(spec.constructorDesc)) {
                    SourceValue receiver = receiverSource(method, call, frames);
                    if (sourceContains(receiver, duplicate)) constructor = call;
                    break;
                }
                if (cursor.getOpcode() == Opcodes.NEW && cursor != allocation) break;
            }
            if (constructor == null) continue;
            AbstractInsnNode storeInsn = nextMeaningful(constructor);
            if (!(storeInsn instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(spec.hookName + " allocation result is not stored in a local");
            }
            if (countStores(method, store.var) != 1) {
                throw mismatch(spec.hookName + " scratch local " + store.var + " is assigned more than once");
            }
            result.add(new AllocationPlan(allocation, duplicate, constructor, store.var));
        }
        if (rawConstructors != result.size()) {
            throw mismatch(spec.hookName + " has constructor calls not paired by uninitialized-value data-flow");
        }
        return result;
    }

    private static List<Integer> storedHookResultLocals(MethodNode method, AllocationSpec spec) {
        List<Integer> result = new ArrayList<>();
        for (MethodInsnNode hook : calls(method, Opcodes.INVOKESTATIC,
                HOOKS, spec.hookName, spec.hookDesc)) {
            AbstractInsnNode next = nextMeaningful(hook);
            if (!(next instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
                throw mismatch(spec.hookName + " result is not stored directly in one local");
            }
            if (countStores(method, store.var) != 1) {
                throw mismatch(spec.hookName + " result local " + store.var + " is reassigned");
            }
            result.add(store.var);
        }
        return result;
    }

    private static void requireScratchUseContract(String owner, MethodNode method, int local,
                                                  AllocationSpec spec, int ordinal, String label) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Map<String, Integer> observed = new LinkedHashMap<>();
        Set<AbstractInsnNode> accountedLoads = new HashSet<>();
        List<MethodInsnNode> iterators = new ArrayList<>();

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                SourceValue receiver = receiverSource(method, call, frames);
                if (recordScratchUse(receiver, local,
                        scratchUseKey(spec, call, -1), observed, accountedLoads)
                        && call.owner.equals("java/util/List") && call.name.equals("iterator")
                        && call.desc.equals("()Ljava/util/Iterator;")) {
                    iterators.add(call);
                }
            }
            Type[] args = Type.getArgumentTypes(call.desc);
            for (int i = 0; i < args.length; i++) {
                recordScratchUse(argumentSource(method, call, frames, i), local,
                        scratchUseKey(spec, call, i), observed, accountedLoads);
            }
        }

        Set<AbstractInsnNode> rmwLoads = validateArrowVectorRmw(method, local, spec, ordinal);
        accountedLoads.addAll(rmwLoads);
        Map<String, Integer> expected = expectedScratchUses(owner, spec, ordinal);
        if (!scratchUsesMatch(expected, observed)) {
            throw mismatch(label + " local " + local + " use contract changed: expected "
                    + expected + ", found " + observed);
        }

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                    && load.var == local && !accountedLoads.contains(load)) {
                throw mismatch(label + " local " + local
                        + " has an unclassified alias/escape at instruction "
                        + method.instructions.indexOf(load));
            }
        }
        for (MethodInsnNode iterator : iterators) {
            requireIteratorLifetime(owner, method, iterator, label + " local " + local);
        }
    }

    private static boolean recordScratchUse(SourceValue source, int local, String key,
                                             Map<String, Integer> uses,
                                             Set<AbstractInsnNode> accountedLoads) {
        Integer loaded = optionalLoadedLocal(source, Opcodes.ALOAD);
        if (loaded == null || loaded != local) return false;
        uses.merge(key, 1, Integer::sum);
        for (AbstractInsnNode producer : source.insns) {
            if (producer instanceof VarInsnNode load
                    && load.getOpcode() == Opcodes.ALOAD && load.var == local) {
                accountedLoads.add(load);
            }
        }
        return true;
    }

    private static String scratchUseKey(AllocationSpec spec, MethodInsnNode call, int position) {
        if (spec.hookName.equals("borrowEntityList")) {
            boolean collectionOperation = call.getOpcode() == Opcodes.INVOKEINTERFACE
                    && call.owner.equals("java/util/Set")
                    && (call.name.equals("retainAll") || call.name.equals("removeAll"))
                    && call.desc.equals("(Ljava/util/Collection;)Z") && position == 0;
            boolean optimizedOperation = call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(HOOKS) && call.name.equals("retainAllFast")
                    && call.desc.equals("(Ljava/util/Set;Ljava/util/Collection;Ljava/lang/Object;)Z")
                    && position == 1;
            if (collectionOperation || optimizedOperation) return "safe-collection-membership-consumer";
        }
        String name = call.name;
        String vectorDesc = "(Lorg/lwjgl/util/vector/Vector2f;Lorg/lwjgl/util/vector/Vector2f;)F";
        if (spec.hookName.equals("borrowHitPoint")
                && call.owner.equals("com/fs/starfarer/prototype/Utils")
                && call.desc.equals(vectorDesc)) {
            name = "*";
        }
        return call.getOpcode() + "|" + call.owner + "|" + name + "|" + call.desc
                + "|" + (position < 0 ? "receiver" : "arg" + position);
    }

    private static Map<String, Integer> expectedScratchUses(String owner,
                                                            AllocationSpec spec, int ordinal) {
        Map<String, Integer> expected = new LinkedHashMap<>();
        switch (spec.hookName) {
            case "borrowEntityList" -> {
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "addAll",
                        "(Ljava/util/Collection;)Z", -1, 3);
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                        "(Ljava/lang/Object;)Z", -1, 1);
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                        "()Ljava/util/Iterator;", -1, 1);
                expected.put("safe-collection-membership-consumer", -1);
            }
            case "borrowClassSet" -> {
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/Set", "add",
                        "(Ljava/lang/Object;)Z", -1, 7);
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/Set", "contains",
                        "(Ljava/lang/Object;)Z", -1, 1);
            }
            case "borrowHitList" -> {
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "addAll",
                        "(Ljava/util/Collection;)Z", -1, 1);
                expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                        "()Ljava/util/Iterator;", -1, 1);
            }
            case "borrowLocationAdvanceSnapshot", "borrowEntityScriptSnapshot" ->
                    expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                            "()Ljava/util/Iterator;", -1, 1);
            case "borrowPausedLocationSnapshot" ->
                    expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/List", "iterator",
                            "()Ljava/util/Iterator;", -1, ordinal == 0 ? 2 : 1);
            case "borrowHitPoint" -> expectUse(expected, Opcodes.INVOKESTATIC,
                    "com/fs/starfarer/prototype/Utils", "*",
                    "(Lorg/lwjgl/util/vector/Vector2f;Lorg/lwjgl/util/vector/Vector2f;)F", 0, 2);
            case "borrowArrowVector" -> {
                int position = ordinal;
                String twoVectors = "(Lorg/lwjgl/util/vector/Vector2f;"
                        + "Lorg/lwjgl/util/vector/Vector2f;)F";
                expectUse(expected, Opcodes.INVOKESTATIC, "com/fs/starfarer/api/util/Misc",
                        "getDistance", twoVectors, position, 1);
                expectUse(expected, Opcodes.INVOKESTATIC, "com/fs/starfarer/api/util/Misc",
                        "getAngleInDegrees", twoVectors, position, 1);
                expectUse(expected, Opcodes.INVOKESTATIC, owner, "o00000",
                        "(Lorg/lwjgl/util/vector/Vector2f;Lorg/lwjgl/util/vector/Vector2f;"
                                + "FLjava/awt/Color;F)V", position, 1);
            }
            default -> throw mismatch("no scratch use contract for " + spec.hookName);
        }
        return expected;
    }

    private static boolean scratchUsesMatch(Map<String, Integer> expected,
                                            Map<String, Integer> observed) {
        if (!expected.keySet().equals(observed.keySet())) return false;
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            int actual = observed.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() < 0) {
                if (actual < 1) return false;
            } else if (actual != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void expectUse(Map<String, Integer> expected, int opcode,
                                  String owner, String name, String desc,
                                  int position, int count) {
        String key = opcode + "|" + owner + "|" + name + "|" + desc + "|"
                + (position < 0 ? "receiver" : "arg" + position);
        expected.put(key, count);
    }

    private static Set<AbstractInsnNode> validateArrowVectorRmw(MethodNode method, int local,
                                                                AllocationSpec spec, int ordinal) {
        Set<AbstractInsnNode> starts = new HashSet<>();
        if (!spec.hookName.equals("borrowArrowVector")) return starts;
        Set<String> fields = new HashSet<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTFIELD
                    || !put.owner.equals("org/lwjgl/util/vector/Vector2f")
                    || !put.desc.equals("F")) continue;
            AbstractInsnNode start = null;
            AbstractInsnNode get = null;
            int seen = 0;
            for (AbstractInsnNode cursor = put.getPrevious(); cursor != null && seen < 10;
                 cursor = cursor.getPrevious()) {
                if (cursor.getOpcode() < 0) continue;
                seen++;
                if (get == null && cursor instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals(put.owner) && field.name.equals(put.name)
                        && field.desc.equals(put.desc)) {
                    get = field;
                }
                if (cursor instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                        && load.var == local && nextMeaningful(load) != null
                        && nextMeaningful(load).getOpcode() == Opcodes.DUP) {
                    start = load;
                    break;
                }
            }
            if (start != null && get != null) {
                starts.add(start);
                fields.add(put.name);
            }
        }
        if (starts.size() != 2 || !fields.equals(Set.of("x", "y"))) {
            throw mismatch("arrow vector local " + local + " (ordinal " + ordinal
                    + ") does not have exactly one self-RMW chain for x and y");
        }
        return starts;
    }

    private static void requireIteratorLifetime(String owner, MethodNode method,
                                                MethodInsnNode iteratorCall, String label) {
        AbstractInsnNode stored = nextMeaningful(iteratorCall);
        if (!(stored instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
            throw mismatch(label + " iterator is not stored directly in a local");
        }
        AbstractInsnNode boundary = null;
        for (AbstractInsnNode cursor = store.getNext(); cursor != null; cursor = cursor.getNext()) {
            if (cursor instanceof VarInsnNode nextStore && nextStore.getOpcode() == Opcodes.ASTORE
                    && nextStore.var == store.var) {
                boundary = cursor;
                break;
            }
        }
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Map<String, Integer> observed = new LinkedHashMap<>();
        Set<AbstractInsnNode> accounted = new HashSet<>();
        for (AbstractInsnNode insn = store.getNext(); insn != null && insn != boundary; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                recordScratchUse(receiverSource(method, call, frames), store.var,
                        call.getOpcode() + "|" + call.owner + "|" + call.name + "|" + call.desc
                                + "|receiver", observed, accounted);
            }
            Type[] args = Type.getArgumentTypes(call.desc);
            for (int i = 0; i < args.length; i++) {
                recordScratchUse(argumentSource(method, call, frames, i), store.var,
                        call.getOpcode() + "|" + call.owner + "|" + call.name + "|" + call.desc
                                + "|arg" + i, observed, accounted);
            }
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next",
                "()Ljava/lang/Object;", -1, 1);
        expectUse(expected, Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", -1, 1);
        if (!observed.equals(expected)) {
            throw mismatch(label + " iterator use contract changed: expected " + expected
                    + ", found " + observed);
        }
        for (AbstractInsnNode insn = store.getNext(); insn != null && insn != boundary; insn = insn.getNext()) {
            if (insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                    && load.var == store.var && !accounted.contains(load)) {
                throw mismatch(label + " iterator local has an unclassified escape");
            }
        }
    }

    private static void requireLocalConstructor(MethodNode method, int local, String type,
                                                String desc, String label) {
        int matches = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !call.owner.equals(type) || !call.name.equals("<init>") || !call.desc.equals(desc)) continue;
            AbstractInsnNode store = nextMeaningful(call);
            if (store instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE && var.var == local) matches++;
        }
        requireCount(label + " constructor assignment", matches, 1);
    }

    private static MethodNode requireWrapperSource(ClassNode node, String publicName, String desc,
                                                   String renamedName, String hookName, String hookDesc) {
        List<MethodNode> publicMethods = methods(node, publicName, desc);
        List<MethodNode> renamedMethods = methods(node, renamedName, desc);
        int hookCount = countCalls(node, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc);
        if (publicMethods.size() == 1 && renamedMethods.size() == 1 && hookCount == 1) {
            MethodNode wrapper = publicMethods.get(0);
            MethodNode original = renamedMethods.get(0);
            int expectedOriginalAccess = wrapper.access;
            expectedOriginalAccess &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
            expectedOriginalAccess |= Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
            if (countCalls(wrapper, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc) == 1
                    && original.access == expectedOriginalAccess) {
                requireStraightWrapper(wrapper, hookName, hookDesc, publicName);
                return original;
            }
            throw mismatch(publicName + " has an invalid partial wrapper state");
        }
        if (publicMethods.size() != 1 || !renamedMethods.isEmpty() || hookCount != 0) {
            throw mismatch(publicName + " wrapper collision/mixed state: public=" + publicMethods.size()
                    + ", renamed=" + renamedMethods.size() + ", hooks=" + hookCount);
        }
        return publicMethods.get(0);
    }

    private static void requireWrapperPostcondition(ClassNode node, String publicName, String desc,
                                                    String renamedName, String hookName, String hookDesc) {
        requireCount(publicName + " wrapper count", methods(node, publicName, desc).size(), 1);
        List<MethodNode> renamed = methods(node, renamedName, desc);
        requireCount(renamedName + " original count", renamed.size(), 1);
        if ((renamed.get(0).access & Opcodes.ACC_PRIVATE) == 0) {
            throw mismatch(renamedName + " is not private");
        }
        requireCount(publicName + " wrapper hook count",
                countCalls(methods(node, publicName, desc).get(0), Opcodes.INVOKESTATIC,
                        HOOKS, hookName, hookDesc), 1);
        requireStraightWrapper(methods(node, publicName, desc).get(0), hookName, hookDesc, publicName);
    }

    private static void requireStraightWrapper(MethodNode wrapper, String hookName,
                                               String hookDesc, String label) {
        if (wrapper.tryCatchBlocks != null && !wrapper.tryCatchBlocks.isEmpty()) {
            throw mismatch(label + " wrapper unexpectedly contains exception handlers");
        }
        int returns = 0;
        for (AbstractInsnNode insn : wrapper.instructions.toArray()) {
            int opcode = insn.getOpcode();
            if (opcode < 0) continue;
            if (insn instanceof MethodInsnNode call) {
                if (!callMatches(call, Opcodes.INVOKESTATIC, HOOKS, hookName, hookDesc)) {
                    throw mismatch(label + " wrapper contains a foreign method call");
                }
                continue;
            }
            if (opcode == Opcodes.ARETURN || opcode == Opcodes.RETURN) {
                returns++;
                continue;
            }
            if (opcode != Opcodes.ALOAD && opcode != Opcodes.FLOAD
                    && opcode != Opcodes.GETFIELD && opcode != Opcodes.CHECKCAST) {
                throw mismatch(label + " wrapper contains unexpected opcode " + opcode);
            }
        }
        requireCount(label + " wrapper returns", returns, 1);
        AbstractInsnNode tail = wrapper.instructions.getLast();
        while (tail != null && tail.getOpcode() < 0) tail = tail.getPrevious();
        if (tail == null || (tail.getOpcode() != Opcodes.ARETURN && tail.getOpcode() != Opcodes.RETURN)) {
            throw mismatch(label + " wrapper does not end in its only return");
        }
    }

    private static void requireIntelEntityWrapper(ClassNode node, String desc,
                                                  String listField, String hookDesc) {
        MethodNode wrapper = requireMethod(node, "getIntelIconEntity", desc);
        List<AbstractInsnNode> code = meaningfulInstructions(wrapper);
        requireCount("getIntelIconEntity wrapper instruction count", code.size(), 7);
        requireVar(code.get(0), Opcodes.ALOAD, 0, "Intel wrapper owner");
        requireVar(code.get(1), Opcodes.ALOAD, 0, "Intel wrapper field owner");
        requireField(code.get(2), Opcodes.GETFIELD, node.name, listField, LIST_DESC,
                "Intel wrapper List field");
        requireVar(code.get(3), Opcodes.ALOAD, argumentLocal(wrapper, 0), "Intel wrapper plugin");
        requireCall(asMethod(code.get(4), "Intel wrapper hook"), Opcodes.INVOKESTATIC,
                HOOKS, "getIntelIconEntityIndexed", hookDesc, "Intel wrapper hook");
        if (!(code.get(5) instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST
                || !cast.desc.equals("com/fs/starfarer/campaign/CustomCampaignEntity")) {
            throw mismatch("Intel wrapper result cast changed");
        }
        if (code.get(6).getOpcode() != Opcodes.ARETURN) throw mismatch("Intel wrapper return changed");
    }

    private static void requireSystemNebulaWrapper(ClassNode node, String desc,
                                                   List<String> fields, String hookDesc) {
        MethodNode wrapper = requireMethod(node, "updateSystemNebulas", desc);
        List<AbstractInsnNode> code = meaningfulInstructions(wrapper);
        requireCount("updateSystemNebulas wrapper instruction count", code.size(), 9);
        requireVar(code.get(0), Opcodes.ALOAD, 0, "nebula wrapper owner");
        for (int i = 0; i < fields.size(); i++) {
            requireVar(code.get(1 + i * 2), Opcodes.ALOAD, 0, "nebula wrapper field owner " + i);
            requireField(code.get(2 + i * 2), Opcodes.GETFIELD, node.name, fields.get(i), LIST_DESC,
                    "nebula wrapper field " + i);
        }
        requireCall(asMethod(code.get(7), "nebula wrapper hook"), Opcodes.INVOKESTATIC,
                HOOKS, "updateSystemNebulasCached", hookDesc, "nebula wrapper hook");
        if (code.get(8).getOpcode() != Opcodes.RETURN) throw mismatch("nebula wrapper return changed");
    }

    private static void requireHitTestWrapper(ClassNode node, String desc, String hookDesc) {
        MethodNode wrapper = requireMethod(node, "OO0000", desc);
        List<AbstractInsnNode> code = meaningfulInstructions(wrapper);
        requireCount("OO0000 wrapper instruction count", code.size(), 6);
        requireVar(code.get(0), Opcodes.ALOAD, 0, "hit-test wrapper owner");
        requireVar(code.get(1), Opcodes.FLOAD, argumentLocal(wrapper, 0), "hit-test wrapper x");
        requireVar(code.get(2), Opcodes.FLOAD, argumentLocal(wrapper, 1), "hit-test wrapper y");
        requireVar(code.get(3), Opcodes.FLOAD, argumentLocal(wrapper, 2), "hit-test wrapper radius");
        requireCall(asMethod(code.get(4), "hit-test wrapper hook"), Opcodes.INVOKESTATIC,
                HOOKS, "hitTestCached", hookDesc, "hit-test wrapper hook");
        if (code.get(5).getOpcode() != Opcodes.ARETURN) throw mismatch("hit-test wrapper return changed");
    }

    private static List<AbstractInsnNode> meaningfulInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) result.add(insn);
        }
        return result;
    }

    private static void requireVar(AbstractInsnNode insn, int opcode, int local, String label) {
        if (!(insn instanceof VarInsnNode var) || var.getOpcode() != opcode || var.var != local) {
            throw mismatch(label + " changed");
        }
    }

    private static void requireField(AbstractInsnNode insn, int opcode, String owner,
                                     String name, String desc, String label) {
        if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != opcode
                || !field.owner.equals(owner) || !field.name.equals(name) || !field.desc.equals(desc)) {
            throw mismatch(label + " changed");
        }
    }

    private static FieldNode requireField(ClassNode node, String name, String desc) {
        FieldNode match = null;
        for (FieldNode field : node.fields) {
            if (!field.name.equals(name) || !field.desc.equals(desc)) continue;
            if (match != null) throw mismatch("duplicate field " + node.name + "." + name + desc);
            match = field;
        }
        if (match == null) throw mismatch("missing field " + node.name + "." + name + desc);
        return match;
    }

    private static void moveWrapperMetadata(MethodNode original, MethodNode wrapper, String label) {
        if (original.attrs != null && !original.attrs.isEmpty()) {
            throw mismatch(label + " has unknown method attributes that cannot be moved safely");
        }
        wrapper.parameters = original.parameters;
        original.parameters = null;
        wrapper.annotationDefault = original.annotationDefault;
        original.annotationDefault = null;
        wrapper.visibleAnnotations = original.visibleAnnotations;
        original.visibleAnnotations = null;
        wrapper.invisibleAnnotations = original.invisibleAnnotations;
        original.invisibleAnnotations = null;
        wrapper.visibleTypeAnnotations = original.visibleTypeAnnotations;
        original.visibleTypeAnnotations = null;
        wrapper.invisibleTypeAnnotations = original.invisibleTypeAnnotations;
        original.invisibleTypeAnnotations = null;
        wrapper.visibleAnnotableParameterCount = original.visibleAnnotableParameterCount;
        original.visibleAnnotableParameterCount = 0;
        wrapper.visibleParameterAnnotations = original.visibleParameterAnnotations;
        original.visibleParameterAnnotations = null;
        wrapper.invisibleAnnotableParameterCount = original.invisibleAnnotableParameterCount;
        original.invisibleAnnotableParameterCount = 0;
        wrapper.invisibleParameterAnnotations = original.invisibleParameterAnnotations;
        original.invisibleParameterAnnotations = null;
    }

    private static void makePrivateSynthetic(MethodNode method) {
        method.access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
        method.access |= Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
    }

    private static boolean hasPatchMarker(ClassNode node, String patchId) {
        String name = PATCH_MARKER_PREFIX + patchId;
        List<FieldNode> matches = new ArrayList<>();
        for (FieldNode field : node.fields) {
            if (field.name.equals(name)) matches.add(field);
        }
        if (matches.isEmpty()) return false;
        if (matches.size() != 1) throw mismatch("duplicate optimizer patch marker " + name);
        FieldNode marker = matches.get(0);
        int expectedAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
        if (!marker.desc.equals(PATCH_MARKER_DESC)
                || marker.access != expectedAccess
                || !PATCH_MARKER_VALUE_PREFIX.concat(patchId).equals(marker.value)) {
            throw mismatch("foreign or malformed optimizer patch marker " + name);
        }
        return true;
    }

    private static void addPatchMarker(ClassNode node, String patchId) {
        if (hasPatchMarker(node, patchId)) throw mismatch("patch marker already exists");
        node.fields.add(new FieldNode(Opcodes.ASM8,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                PATCH_MARKER_PREFIX + patchId, PATCH_MARKER_DESC, null,
                PATCH_MARKER_VALUE_PREFIX + patchId));
    }

    private static int findComparedLocationLocal(String owner, MethodNode method,
                                                 MethodInsnNode from, AbstractInsnNode boundary) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Set<Integer> locals = new LinkedHashSet<>();
        for (AbstractInsnNode insn = from.getNext(); insn != null && insn != boundary; insn = insn.getNext()) {
            if (!(insn instanceof JumpInsnNode jump)
                    || (jump.getOpcode() != Opcodes.IF_ACMPEQ && jump.getOpcode() != Opcodes.IF_ACMPNE)) continue;
            Frame<SourceValue> frame = frameBefore(method, jump, frames);
            if (frame.getStackSize() < 2) continue;
            SourceValue left = frame.getStack(frame.getStackSize() - 2);
            SourceValue right = frame.getStack(frame.getStackSize() - 1);
            Integer local = null;
            if (sourceIsMethod(left, -1, "com/fs/starfarer/api/campaign/SectorEntityToken",
                    "getContainingLocation", "()" + LOCATION_DESC)) {
                local = optionalLoadedLocal(right, Opcodes.ALOAD);
            } else if (sourceIsMethod(right, -1, "com/fs/starfarer/api/campaign/SectorEntityToken",
                    "getContainingLocation", "()" + LOCATION_DESC)) {
                local = optionalLoadedLocal(left, Opcodes.ALOAD);
            }
            if (local != null) locals.add(local);
        }
        if (locals.size() != 1) {
            throw mismatch("expected one destination-containing-location comparison local, found " + locals);
        }
        return locals.iterator().next();
    }

    private static int findAnchorComparisonLocal(String owner, MethodNode method, MethodInsnNode from) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Set<Integer> locals = new LinkedHashSet<>();
        int meaningful = 0;
        for (AbstractInsnNode insn = from.getNext(); insn != null && meaningful < 100; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) meaningful++;
            if (!(insn instanceof JumpInsnNode jump)
                    || (jump.getOpcode() != Opcodes.IF_ACMPEQ && jump.getOpcode() != Opcodes.IF_ACMPNE)) continue;
            Frame<SourceValue> frame = frameBefore(method, jump, frames);
            if (frame.getStackSize() < 2) continue;
            SourceValue left = frame.getStack(frame.getStackSize() - 2);
            SourceValue right = frame.getStack(frame.getStackSize() - 1);
            Integer local = null;
            if (sourceIsMethod(left, -1, "com/fs/starfarer/api/campaign/StarSystemAPI",
                    "getHyperspaceAnchor", "()" + ENTITY_TOKEN_DESC)) {
                local = optionalLoadedLocal(right, Opcodes.ALOAD);
            } else if (sourceIsMethod(right, -1, "com/fs/starfarer/api/campaign/StarSystemAPI",
                    "getHyperspaceAnchor", "()" + ENTITY_TOKEN_DESC)) {
                local = optionalLoadedLocal(left, Opcodes.ALOAD);
            }
            if (local != null) locals.add(local);
        }
        if (locals.size() != 1) throw mismatch("expected one hyperspace-anchor comparison local, found " + locals);
        return locals.iterator().next();
    }

    private static void requireSystemLocalProducer(MethodNode method, int local,
                                                   AbstractInsnNode before, String label) {
        int valid = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null && insn != before;
             insn = insn.getNext()) {
            if (!(insn instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE || store.var != local) continue;
            AbstractInsnNode producer = previousMeaningful(store);
            if (producer instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                    && (cast.desc.equals("com/fs/starfarer/campaign/StarSystem")
                    || cast.desc.equals("com/fs/starfarer/api/campaign/StarSystemAPI"))) {
                valid++;
            } else if (producer instanceof MethodInsnNode call
                    && Type.getReturnType(call.desc).getDescriptor().equals(STAR_SYSTEM_DESC)) {
                valid++;
            }
        }
        requireCountAtLeast(label + " system-local producers", valid, 1);
    }

    private static int requireStoredCallResultLocal(MethodNode method, int opcode, String owner,
                                                    String name, String desc, String label) {
        MethodInsnNode call = only(calls(method, opcode, owner, name, desc), label);
        AbstractInsnNode next = nextMeaningful(call);
        if (!(next instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
            throw mismatch(label + " is not stored in a local");
        }
        return store.var;
    }

    private static void requireStoredCallResult(MethodNode method, int local, int opcode, String owner,
                                                String name, String desc, String label) {
        int matches = 0;
        for (MethodInsnNode call : calls(method, opcode, owner, name, desc)) {
            AbstractInsnNode next = nextMeaningful(call);
            if (next instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE && store.var == local) matches++;
        }
        requireCount(label + " assignment", matches, 1);
    }

    private static int countIdentityComparisons(MethodNode method, Frame<SourceValue>[] frames,
                                                AbstractInsnNode after, int local,
                                                String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = after.getNext(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof JumpInsnNode jump)
                    || (jump.getOpcode() != Opcodes.IF_ACMPEQ && jump.getOpcode() != Opcodes.IF_ACMPNE)) continue;
            Frame<SourceValue> frame = frameBefore(method, jump, frames);
            if (frame.getStackSize() < 2) continue;
            SourceValue left = frame.getStack(frame.getStackSize() - 2);
            SourceValue right = frame.getStack(frame.getStackSize() - 1);
            if ((sourceIsMethod(left, -1, owner, name, desc) && sourceIsLocal(right, Opcodes.ALOAD, local))
                    || (sourceIsMethod(right, -1, owner, name, desc)
                    && sourceIsLocal(left, Opcodes.ALOAD, local))) count++;
        }
        return count;
    }

    private static boolean hasMutatorAfter(String owner, MethodNode method,
                                           AbstractInsnNode after, int receiverLocal) {
        Frame<SourceValue>[] frames = sourceFrames(owner, method);
        Set<String> mutators = Set.of("add", "addAll", "clear", "remove", "removeAll", "retainAll", "set");
        for (AbstractInsnNode insn = after.getNext(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call) || !mutators.contains(call.name)) continue;
            if (call.getOpcode() == Opcodes.INVOKESTATIC) continue;
            if (sourceIsLocal(receiverSource(method, call, frames), Opcodes.ALOAD, receiverLocal)) return true;
        }
        return false;
    }

    private static boolean hasNullGuard(MethodNode method, AbstractInsnNode after,
                                        AbstractInsnNode before, int local) {
        for (AbstractInsnNode insn = after.getNext(); insn != null && insn != before; insn = insn.getNext()) {
            if (!(insn instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != local) continue;
            AbstractInsnNode next = nextMeaningful(load);
            if (next != null && (next.getOpcode() == Opcodes.IFNULL || next.getOpcode() == Opcodes.IFNONNULL)) return true;
        }
        return false;
    }

    private static boolean hasCallImmediatelyBefore(AbstractInsnNode target, int opcode,
                                                    String owner, String name, String desc, int maxMeaningful) {
        int seen = 0;
        for (AbstractInsnNode insn = target.getPrevious(); insn != null && seen < maxMeaningful; insn = insn.getPrevious()) {
            if (insn.getOpcode() < 0) continue;
            seen++;
            if (insn instanceof MethodInsnNode call && callMatches(call, opcode, owner, name, desc)) return true;
        }
        return false;
    }

    private static String uniqueRole(String current, String candidate, String label) {
        if (current != null && !current.equals(candidate)) throw mismatch("multiple fields match " + label);
        return candidate;
    }

    private static int countCallsWithFieldReceiver(String owner, MethodNode method,
                                                   Frame<SourceValue>[] frames, int opcode,
                                                   String callOwner, String name, String desc,
                                                   String fieldName, String fieldDesc) {
        int count = 0;
        for (MethodInsnNode call : calls(method, opcode, callOwner, name, desc)) {
            FieldInsnNode field = optionalSourceField(receiverSource(method, call, frames),
                    Opcodes.GETFIELD, owner, fieldDesc);
            if (field != null && field.name.equals(fieldName)) count++;
        }
        return count;
    }

    private static void requireIconsGetter(ClassNode node, String fieldName) {
        MethodNode getter = requireMethod(node, "getIcons", "()Ljava/util/LinkedHashMap;");
        requireCount("getIcons backing field", countFields(getter, Opcodes.GETFIELD,
                node.name, fieldName, LINKED_MAP_DESC), 1);
        requireCount("getIcons ARETURN", countOpcode(getter, Opcodes.ARETURN), 1);
    }

    private static void requireNullReturn(MethodNode method, String label) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.ARETURN
                    && previousMeaningful(insn) != null
                    && previousMeaningful(insn).getOpcode() == Opcodes.ACONST_NULL) count++;
        }
        requireCount(label + " null return", count, 1);
    }

    private static boolean containsWrite(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isStateWriteOpcode(insn.getOpcode())) return true;
        }
        return false;
    }

    private static boolean containsWriteOrMonitor(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            int opcode = insn.getOpcode();
            if (isStateWriteOpcode(opcode)
                    || opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT
                    || opcode == Opcodes.INVOKEDYNAMIC) return true;
        }
        return false;
    }

    private static boolean isStateWriteOpcode(int opcode) {
        return opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC
                || (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE);
    }

    private static int totalMethodCalls(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) count++;
        }
        return count;
    }

    private static int argumentLocal(MethodNode method, int argumentIndex) {
        Type[] args = Type.getArgumentTypes(method.desc);
        if (argumentIndex < 0 || argumentIndex >= args.length) throw mismatch("invalid argument index " + argumentIndex);
        int local = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (int i = 0; i < argumentIndex; i++) local += args[i].getSize();
        return local;
    }

    private static Frame<SourceValue>[] sourceFrames(String owner, MethodNode method) {
        try {
            return new Analyzer<SourceValue>(new SourceInterpreter()).analyze(owner, method);
        } catch (AnalyzerException ex) {
            throw mismatch("data-flow analysis failed for " + owner + "." + method.name + method.desc
                    + ": " + ex.getMessage());
        }
    }

    private static Frame<SourceValue> frameBefore(MethodNode method, AbstractInsnNode insn,
                                                  Frame<SourceValue>[] frames) {
        int index = method.instructions.indexOf(insn);
        if (index < 0 || index >= frames.length || frames[index] == null) {
            throw mismatch("no reachable data-flow frame at " + method.name + method.desc);
        }
        return frames[index];
    }

    private static SourceValue receiverSource(MethodNode method, MethodInsnNode call,
                                              Frame<SourceValue>[] frames) {
        if (call.getOpcode() == Opcodes.INVOKESTATIC) throw mismatch("static call has no receiver: " + call.name);
        Frame<SourceValue> frame = frameBefore(method, call, frames);
        int arguments = Type.getArgumentTypes(call.desc).length;
        int index = frame.getStackSize() - arguments - 1;
        if (index < 0) throw mismatch("invalid receiver stack for " + call.owner + "." + call.name + call.desc);
        return frame.getStack(index);
    }

    private static SourceValue argumentSource(MethodNode method, MethodInsnNode call,
                                              Frame<SourceValue>[] frames, int argumentIndex) {
        Type[] args = Type.getArgumentTypes(call.desc);
        if (argumentIndex < 0 || argumentIndex >= args.length) throw mismatch("invalid call argument index");
        Frame<SourceValue> frame = frameBefore(method, call, frames);
        int index = frame.getStackSize() - args.length + argumentIndex;
        if (index < 0) throw mismatch("invalid argument stack for " + call.owner + "." + call.name + call.desc);
        return frame.getStack(index);
    }

    private static MethodInsnNode requireSourceMethod(SourceValue value, int opcode,
                                                      String owner, String name, String desc, String label) {
        if (value == null || value.insns == null || value.insns.size() != 1) {
            throw mismatch(label + " has ambiguous producer set");
        }
        AbstractInsnNode source = value.insns.iterator().next();
        if (!(source instanceof MethodInsnNode call) || !callMatches(call, opcode, owner, name, desc)) {
            throw mismatch(label + " is not produced by " + owner + "." + name + desc);
        }
        return call;
    }

    private static FieldInsnNode requireSourceField(SourceValue value, int opcode,
                                                    String owner, String desc, String label) {
        FieldInsnNode field = optionalSourceField(value, opcode, owner, desc);
        if (field == null) throw mismatch(label + " is not produced by the expected field read");
        return field;
    }

    private static FieldInsnNode optionalSourceField(SourceValue value, int opcode,
                                                     String owner, String desc) {
        if (value == null || value.insns == null || value.insns.size() != 1) return null;
        AbstractInsnNode source = value.insns.iterator().next();
        if (source instanceof FieldInsnNode field && field.getOpcode() == opcode
                && field.owner.equals(owner) && field.desc.equals(desc)) return field;
        return null;
    }

    private static int requireLoadedLocal(SourceValue value, int opcode, String label) {
        Integer local = optionalLoadedLocal(value, opcode);
        if (local == null) throw mismatch(label + " is not loaded unambiguously from one local");
        return local;
    }

    private static Integer optionalLoadedLocal(SourceValue value, int opcode) {
        if (value == null || value.insns == null || value.insns.isEmpty()) return null;
        Integer local = null;
        for (AbstractInsnNode source : value.insns) {
            if (!(source instanceof VarInsnNode var) || var.getOpcode() != opcode) return null;
            if (local != null && local != var.var) return null;
            local = var.var;
        }
        return local;
    }

    private static boolean sourceIsLocal(SourceValue value, int opcode, int local) {
        Integer found = optionalLoadedLocal(value, opcode);
        return found != null && found == local;
    }

    private static boolean sourceIsOpcode(SourceValue value, int opcode) {
        if (value == null || value.insns == null || value.insns.size() != 1) return false;
        return value.insns.iterator().next().getOpcode() == opcode;
    }

    private static boolean sourceIsFieldDesc(SourceValue value, String desc) {
        if (value == null || value.insns == null || value.insns.size() != 1) return false;
        AbstractInsnNode source = value.insns.iterator().next();
        return source instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.desc.equals(desc);
    }

    private static boolean sourceIsMethod(SourceValue value, int opcode,
                                          String owner, String name, String desc) {
        if (value == null || value.insns == null || value.insns.size() != 1) return false;
        AbstractInsnNode source = value.insns.iterator().next();
        return source instanceof MethodInsnNode call && callMatches(call, opcode, owner, name, desc);
    }

    private static boolean sourceContains(SourceValue value, AbstractInsnNode source) {
        return value != null && value.insns != null && value.insns.contains(source);
    }

    private static MethodNode requireMethod(ClassNode node, String name, String desc) {
        List<MethodNode> found = methods(node, name, desc);
        if (found.size() != 1) {
            throw mismatch("expected one method " + node.name + "." + name + desc + ", found " + found.size());
        }
        return found.get(0);
    }

    private static List<MethodNode> methods(ClassNode node, String name, String desc) {
        List<MethodNode> result = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) result.add(method);
        }
        return result;
    }

    private static List<FieldNode> fields(ClassNode node, String desc) {
        List<FieldNode> result = new ArrayList<>();
        for (FieldNode field : node.fields) if (field.desc.equals(desc)) result.add(field);
        return result;
    }

    private static List<MethodInsnNode> calls(MethodNode method, int opcode,
                                             String owner, String name, String desc) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call && callMatches(call, opcode, owner, name, desc)) result.add(call);
        }
        return result;
    }

    private static boolean callMatches(MethodInsnNode call, int opcode,
                                       String owner, String name, String desc) {
        return (opcode < 0 || call.getOpcode() == opcode)
                && (owner == null || call.owner.equals(owner))
                && (name == null || call.name.equals(name))
                && (desc == null || call.desc.equals(desc));
    }

    private static int countCalls(MethodNode method, int opcode,
                                  String owner, String name, String desc) {
        return calls(method, opcode, owner, name, desc).size();
    }

    private static int countCalls(ClassNode node, int opcode,
                                  String owner, String name, String desc) {
        int count = 0;
        for (MethodNode method : node.methods) count += countCalls(method, opcode, owner, name, desc);
        return count;
    }

    private static int countFields(MethodNode method, int opcode,
                                   String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field && (opcode < 0 || field.getOpcode() == opcode)
                    && (owner == null || field.owner.equals(owner))
                    && (name == null || field.name.equals(name))
                    && (desc == null || field.desc.equals(desc))) count++;
        }
        return count;
    }

    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == opcode) count++;
        return count;
    }

    private static int countStores(MethodNode method, int local) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE && var.var == local) count++;
        }
        return count;
    }

    private static MethodInsnNode asMethod(AbstractInsnNode insn, String label) {
        if (insn instanceof MethodInsnNode method) return method;
        throw mismatch(label + " is not a method invocation");
    }

    private static void requireCall(MethodInsnNode call, int opcode, String owner,
                                    String name, String desc, String label) {
        if (!callMatches(call, opcode, owner, name, desc)) {
            throw mismatch(label + " does not match " + owner + "." + name + desc);
        }
    }

    private static <T> T only(List<T> values, String label) {
        if (values.size() != 1) throw mismatch(label + ": expected one site, found " + values.size());
        return values.get(0);
    }

    private static void requireUnpatchedOrAlready(String label, int originals, int expectedOriginals,
                                                  int hooks, int expectedHooks) {
        if (originals == 0 && hooks == expectedHooks) throw already(label + " hook postcondition matches");
        if (originals != expectedOriginals || hooks != 0) {
            throw mismatch(label + ": expected original=" + expectedOriginals + ", hooks=0; found original="
                    + originals + ", hooks=" + hooks);
        }
    }

    private static void requireCount(String label, int actual, int expected) {
        if (actual != expected) throw mismatch(label + ": expected " + expected + ", found " + actual);
    }

    private static void requireCountAtLeast(String label, int actual, int minimum) {
        if (actual < minimum) throw mismatch(label + ": expected at least " + minimum + ", found " + actual);
    }

    private static void makeStatic(MethodInsnNode call, String owner, String name, String desc) {
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = owner;
        call.name = name;
        call.desc = desc;
        call.itf = false;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode current = node.getNext();
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private static AbstractInsnNode firstMeaningful(MethodNode method) {
        AbstractInsnNode current = method.instructions.getFirst();
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode current = node.getPrevious();
        while (current != null && current.getOpcode() < 0) current = current.getPrevious();
        return current;
    }

    private static boolean isFloat(LdcInsnNode ldc, float expected) {
        return ldc.cst instanceof Float value
                && Float.floatToIntBits(value) == Float.floatToIntBits(expected);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private static String[] exceptions(MethodNode method) {
        if (method.exceptions == null) return null;
        return method.exceptions.toArray(new String[0]);
    }

    private static Set<String> publicApi(ClassNode node) {
        Set<String> api = new HashSet<>();
        for (FieldNode field : node.fields) {
            if ((field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                api.add("F:" + field.name + ":" + field.desc + ":" + (field.access & apiAccessMask()));
            }
        }
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                api.add("M:" + method.name + method.desc + ":" + (method.access & apiAccessMask()));
            }
        }
        return api;
    }

    private static int apiAccessMask() {
        return Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_NATIVE | Opcodes.ACC_VARARGS;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM8);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void verifyClass(byte[] bytes) {
        ClassNode node = readClass(bytes);
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
            } catch (AnalyzerException ex) {
                throw mismatch("BasicVerifier rejected " + node.name + "." + method.name + method.desc
                        + ": " + ex.getMessage());
            }
        }
    }

    private static StructuralMismatchException mismatch(String message) {
        return new StructuralMismatchException(message);
    }

    private static AlreadyPatchedException already(String message) {
        return new AlreadyPatchedException(message);
    }

    @FunctionalInterface
    private interface PatchAction {
        PatchReport apply(ClassNode node);
    }

    @FunctionalInterface
    private interface CountPatchAction {
        int apply(ClassNode node);
    }

    private static final class TransformState {
        final String className;
        byte[] bytes;
        boolean changed;
        final List<String> appliedDetails = new ArrayList<>();

        TransformState(String className, byte[] bytes) {
            this.className = className;
            this.bytes = bytes;
        }
    }

    private record LogBlock(MethodNode method, AbstractInsnNode start, AbstractInsnNode end) {}

    private record BufferConstruction(AbstractInsnNode start, AbstractInsnNode end) {}

    private record AllocationSpec(String type, String constructorDesc,
                                  String hookName, String hookDesc, int expected) {}

    private record AllocationPlan(TypeInsnNode allocation, AbstractInsnNode duplicate,
                                  MethodInsnNode constructor, int local) {}

    private record SnapshotAllocationPlan(TypeInsnNode allocation, AbstractInsnNode duplicate,
                                          MethodInsnNode constructor, VarInsnNode store) {}

    private record SnapshotHookPlan(MethodInsnNode hook, VarInsnNode store) {}
    private record AllocationPair(TypeInsnNode allocation, AbstractInsnNode duplicate) {}
    private record PersistentSnapshotPlan(MethodInsnNode constructor, AllocationPair allocation,
                                          AbstractInsnNode sourceReceiver, String accessor) {}

    private enum PatchState { ORIGINAL, PATCHED }

    private static final class PatchReport {
        int total;
        final List<String> details = new ArrayList<>();

        void add(String label, int count) {
            if (count <= 0) return;
            total += count;
            details.add(label + "=" + count);
        }

        @Override
        public String toString() {
            return String.join(", ", details);
        }
    }

    private static final class StructuralMismatchException extends RuntimeException {
        StructuralMismatchException(String message) { super(message); }
    }

    private static final class AlreadyPatchedException extends RuntimeException {
        AlreadyPatchedException(String message) { super(message); }
    }
}
