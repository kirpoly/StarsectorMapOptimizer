package com.starsector.mapoptimizer.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TypeInsnNode;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class MapOptimizerTransformer implements ClassFileTransformer {
    private static final String HOOKS = "com/starsector/mapoptimizer/runtime/MapOptimizerHooks";
    private static final String H = "com/fs/starfarer/coreui/A/H";
    private static final String A = "com/fs/starfarer/coreui/A/A";
    private static final String Z = "com/fs/starfarer/coreui/A/Z";
    private static final String EVENTS = "com/fs/starfarer/campaign/comms/v2/EventsPanel";
    private static final String CAMPAIGN_ENGINE = "com/fs/starfarer/campaign/CampaignEngine";
    private static final String COURSE_WIDGET = "com/fs/starfarer/coreui/A/O0Oo";
    private static final String ENTITY_TOKEN_DESC = "Lcom/fs/starfarer/api/campaign/SectorEntityToken;";
    private static final String MAP_API_DESC = "Lcom/fs/starfarer/api/ui/SectorMapAPI;";
    private static final String INTEL_DESC = "Lcom/fs/starfarer/api/campaign/comm/IntelInfoPlugin;";

    private static final Map<String, String> EXPECTED_HASHES = new HashMap<>();
    static {
        EXPECTED_HASHES.put(H, "3bad0296ca21b7c1de04ec091fa35ba868903d00185501d2d60053182c304d14");
        EXPECTED_HASHES.put(A, "bd9e3fbe425ff18199f5f0c5ec654996c836326224ff6bc2efdfcaf41162cb2f");
        EXPECTED_HASHES.put(Z, "f584eb5cd28216f8be97cf47a2ba9141ad7f0671e9b16c235e5dc8c6787cce98");
        EXPECTED_HASHES.put(EVENTS, "3924d42d8e8ceab19a147ed9db03161773333203752f3430518bf87231ff6aa1");
        EXPECTED_HASHES.put(CAMPAIGN_ENGINE, "9888ba1d2493f8d1d41106b57d9843c266f7073355aa9d2c41e73c14c3527cab");
        EXPECTED_HASHES.put(COURSE_WIDGET, "31ba933e63240f793eb7e40706d0ed155d0e226f80f90c0f4813f46f2d7ac222");
    }

    private final OptimizerConfig config;
    private final boolean knownBuild;
    private final AtomicInteger patchedClasses = new AtomicInteger();

    public MapOptimizerTransformer(OptimizerConfig config, boolean knownBuild) {
        this.config = config;
        this.knownBuild = knownBuild;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!EXPECTED_HASHES.containsKey(className) || !isTargetEnabled(className)) return null;
        try {
            String hash = sha256(classfileBuffer);
            String expected = EXPECTED_HASHES.get(className);
            if (!expected.equalsIgnoreCase(hash)) {
                OptimizerLog.warn("Skipping " + className + ": class hash mismatch. expected="
                        + expected + ", actual=" + hash);
                return null;
            }

            ClassNode node = new ClassNode(Opcodes.ASM8);
            new ClassReader(classfileBuffer).accept(node, 0);
            PatchReport report;
            switch (className) {
                case H: report = patchH(node); break;
                case A: report = patchA(node); break;
                case Z: report = patchZ(node); break;
                case EVENTS: report = patchEvents(node); break;
                case CAMPAIGN_ENGINE: report = patchCampaignEngine(node); break;
                case COURSE_WIDGET: report = patchCourseWidget(node); break;
                default: return null;
            }
            if (report.total == 0) {
                OptimizerLog.warn("No patch sites changed in " + className + "; returning vanilla bytes.");
                return null;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] transformed = writer.toByteArray();
            int count = patchedClasses.incrementAndGet();
            System.setProperty("starsector.mapoptimizer.patchedClasses", Integer.toString(count));
            OptimizerLog.info("Patched " + className + ": " + report + ", bytes "
                    + classfileBuffer.length + " -> " + transformed.length);
            return transformed;
        } catch (Throwable ex) {
            OptimizerLog.error("Failed to transform " + className + "; vanilla class will be used.", ex);
            return null;
        }
    }


    private boolean isTargetEnabled(String className) {
        return switch (className) {
            case H -> config.retainAll || config.scratchCollections || config.labelSpatialCandidates
                    || config.intelEntityIndex || config.systemNebulaCache
                    || config.sampleCacheClearThrottle || config.gridLineCap;
            case A -> config.scratchCollections || config.hoverHitTestCache || config.intelCallbackCache;
            case Z -> config.intelCallbackCache || config.arrowVectorPool;
            case EVENTS -> config.intelCallbackCache || config.intelFastContains
                    || config.intelExistingIconLookup;
            case CAMPAIGN_ENGINE -> config.campaignListenerThrottle;
            case COURSE_WIDGET -> config.routeJumpPointIndex;
            default -> false;
        };
    }


    private PatchReport patchCampaignEngine(ClassNode node) {
        PatchReport report = new PatchReport();
        if (config.campaignListenerThrottle) {
            MethodNode advance = requireMethod(node, "advance", "(FLcom/fs/starfarer/util/A/new;)V");
            int count = 0;
            for (AbstractInsnNode insn : advance.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(CAMPAIGN_ENGINE)
                        && call.name.equals("readdChangeListeners")
                        && call.desc.equals("()V")) {
                    makeStatic(call, HOOKS, "readdChangeListenersIfNeeded", "(Ljava/lang/Object;)V");
                    count++;
                }
            }
            requireCount("CampaignEngine.advance readdChangeListeners", count, 1);
            report.add("incremental campaign repository-listener refresh", count);
        }
        return report;
    }

    private PatchReport patchCourseWidget(ClassNode node) {
        PatchReport report = new PatchReport();
        if (!config.routeJumpPointIndex) return report;

        MethodNode next = requireMethod(node, "getNextStep",
                "(" + ENTITY_TOKEN_DESC + ")" + ENTITY_TOKEN_DESC);
        int jumpCalls = 0;
        int systemCalls = 0;
        for (AbstractInsnNode insn : next.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                    && call.owner.equals("com/fs/starfarer/api/campaign/LocationAPI")
                    && call.name.equals("getJumpPoints")
                    && call.desc.equals("()Ljava/util/List;")) {
                next.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 4));
                makeStatic(call, HOOKS, "routeJumpPointsForSystem",
                        "(Lcom/fs/starfarer/api/campaign/LocationAPI;Ljava/lang/Object;)Ljava/util/List;");
                jumpCalls++;
            } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(CAMPAIGN_ENGINE)
                    && call.name.equals("getStarSystems")
                    && call.desc.equals("()Ljava/util/List;")) {
                next.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                makeStatic(call, HOOKS, "routeSystemsForAnchor",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;");
                systemCalls++;
            }
        }
        requireCount("O0Oo.getNextStep hyperspace jump-point scans", jumpCalls, 2);
        requireCount("O0Oo.getNextStep star-system anchor scan", systemCalls, 1);

        MethodNode distance = requireMethod(node, "getLastLegDistance",
                "(" + ENTITY_TOKEN_DESC + ")F");
        int distanceCalls = 0;
        for (AbstractInsnNode insn : distance.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEINTERFACE
                    && call.owner.equals("com/fs/starfarer/api/campaign/LocationAPI")
                    && call.name.equals("getJumpPoints")
                    && call.desc.equals("()Ljava/util/List;")) {
                distance.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 2));
                makeStatic(call, HOOKS, "routeJumpPointsForSystem",
                        "(Lcom/fs/starfarer/api/campaign/LocationAPI;Ljava/lang/Object;)Ljava/util/List;");
                distanceCalls++;
            }
        }
        requireCount("O0Oo.getLastLegDistance hyperspace jump-point scan", distanceCalls, 1);
        report.add("route jump-point system index", jumpCalls + distanceCalls);
        report.add("route hyperspace-anchor system index", systemCalls);
        return report;
    }

    private PatchReport patchH(ClassNode node) {
        PatchReport report = new PatchReport();

        MethodNode renderStuff = requireMethod(node, "renderStuff", "(FZ)V");
        if (config.scratchCollections) {
            int lists = replaceConstructors(renderStuff, "java/util/ArrayList", "(Ljava/util/Collection;)V",
                    "borrowEntityList", "(Ljava/util/Collection;)Ljava/util/ArrayList;", 1);
            int sets = replaceConstructors(renderStuff, "java/util/HashSet", "()V",
                    "borrowClassSet", "()Ljava/util/HashSet;", 1);
            report.add("render scratch collections", lists + sets);
        }
        if (config.retainAll) {
            int count = 0;
            for (AbstractInsnNode insn : renderStuff.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals("java/util/Set")
                        && call.name.equals("retainAll")
                        && call.desc.equals("(Ljava/util/Collection;)Z")) {
                    renderStuff.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                    makeStatic(call, HOOKS, "retainAllFast",
                            "(Ljava/util/Set;Ljava/util/Collection;Ljava/lang/Object;)Z");
                    count++;
                }
            }
            requireCount("H.renderStuff retainAll", count, 1);
            report.add("O(K*E) retainAll -> linear identity/equality reconciliation", count);
        }

        if (config.labelSpatialCandidates) {
            MethodNode labels = requireMethod(node, "getTextAlignmentFor",
                    "(Lcom/fs/starfarer/coreui/A/ooOO;)Lcom/fs/starfarer/api/ui/Alignment;");
            int count = 0;
            for (AbstractInsnNode insn : labels.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.owner.equals("java/util/LinkedHashMap")
                        && call.name.equals("values")
                        && call.desc.equals("()Ljava/util/Collection;")) {
                    labels.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                    labels.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                    makeStatic(call, HOOKS, "nearbyLabelIcons",
                            "(Ljava/util/LinkedHashMap;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Collection;");
                    count++;
                }
            }
            requireCount("H.getTextAlignmentFor values", count, 1);
            report.add("label spatial candidates", count);
        }

        if (config.intelEntityIndex) {
            MethodNode original = requireMethod(node, "getIntelIconEntity",
                    "(" + INTEL_DESC + ")Lcom/fs/starfarer/campaign/CustomCampaignEntity;");
            Set<String> fields = new LinkedHashSet<>();
            for (AbstractInsnNode insn : original.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals(node.name)
                        && field.desc.equals("Ljava/util/List;")) {
                    fields.add(field.name);
                }
            }
            if (fields.size() != 1) {
                throw new IllegalStateException("Expected one Intel-data list field, found " + fields);
            }
            String listField = fields.iterator().next();
            int access = original.access;
            String signature = original.signature;
            @SuppressWarnings("unchecked")
            List<String> exceptions = original.exceptions == null ? null : new ArrayList<>(original.exceptions);
            original.name = "smo$originalGetIntelIconEntity";
            MethodNode wrapper = new MethodNode(Opcodes.ASM8, access, "getIntelIconEntity",
                    "(" + INTEL_DESC + ")Lcom/fs/starfarer/campaign/CustomCampaignEntity;",
                    signature, exceptions == null ? null : exceptions.toArray(new String[0]));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            wrapper.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, listField, "Ljava/util/List;"));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "getIntelIconEntityIndexed",
                    "(Ljava/lang/Object;Ljava/util/List;" + INTEL_DESC + ")Ljava/lang/Object;", false));
            wrapper.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
                    "com/fs/starfarer/campaign/CustomCampaignEntity"));
            wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(wrapper);
            report.add("Intel entity identity index", 1);
        }

        if (config.systemNebulaCache) {
            MethodNode original = requireMethod(node, "updateSystemNebulas", "()V");
            Set<String> fields = new LinkedHashSet<>();
            for (AbstractInsnNode insn : original.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals(node.name)
                        && field.desc.equals("Ljava/util/List;")) {
                    fields.add(field.name);
                }
            }
            if (fields.size() != 3) {
                throw new IllegalStateException("Expected 3 system-nebula list fields, found " + fields);
            }
            List<String> listFields = new ArrayList<>(fields);
            int originalAccess = original.access;
            String originalSignature = original.signature;
            @SuppressWarnings("unchecked")
            List<String> originalExceptions = original.exceptions == null
                    ? null : new ArrayList<>(original.exceptions);
            original.name = "smo$originalUpdateSystemNebulas";

            MethodNode wrapper = new MethodNode(Opcodes.ASM8, originalAccess, "updateSystemNebulas", "()V",
                    originalSignature, originalExceptions == null ? null : originalExceptions.toArray(new String[0]));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (String fieldName : listFields) {
                wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                wrapper.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, fieldName, "Ljava/util/List;"));
            }
            wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "updateSystemNebulasCached",
                    "(Ljava/lang/Object;Ljava/util/List;Ljava/util/List;Ljava/util/List;)V", false));
            wrapper.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(wrapper);
            report.add("system-nebula preprocessing cache", 1);
        }

        if (config.sampleCacheClearThrottle) {
            MethodNode constructor = requireMethod(node, "<init>", "(Z)V");
            int count = 0;
            for (AbstractInsnNode insn : constructor.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.name.equals("forceClearSampleCache")
                        && call.desc.equals("()V")) {
                    makeStatic(call, HOOKS, "forceClearSampleCacheThrottled", "(Ljava/lang/Object;)V");
                    count++;
                }
            }
            requireCount("H constructor forceClearSampleCache", count, 1);
            report.add("hyperspace sample-cache clear throttle", count);
        }

        if (config.gridLineCap) {
            MethodNode grid = requireMethod(node, "null", "(F)V");
            boolean afterStarscapeMarker = false;
            int count = 0;
            for (AbstractInsnNode insn : grid.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.owner.equals(node.name)
                        && call.name.equals("isStarscapeMode")
                        && call.desc.equals("()Z")) {
                    afterStarscapeMarker = true;
                    continue;
                }
                if (afterStarscapeMarker && insn instanceof LdcInsnNode ldc
                        && ldc.cst instanceof Float
                        && Float.floatToIntBits((Float) ldc.cst) == Float.floatToIntBits(2000f)) {
                    grid.instructions.set(ldc, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                            "gridSpacing", "()F", false));
                    count++;
                }
            }
            if (count < 5) throw new IllegalStateException("Expected at least 5 global-grid 2000f constants; found " + count);
            report.add("large-sector grid line cap", count);
        }
        return report;
    }

    private PatchReport patchA(ClassNode node) {
        PatchReport report = new PatchReport();
        MethodNode hit = requireMethod(node, "OO0000", "(FFF)" + ENTITY_TOKEN_DESC);
        if (config.scratchCollections) {
            int lists = replaceConstructors(hit, "java/util/ArrayList", "(Ljava/util/Collection;)V",
                    "borrowHitList", "(Ljava/util/Collection;)Ljava/util/ArrayList;", 1);
            int vectors = replaceConstructors(hit, "org/lwjgl/util/vector/Vector2f", "(FF)V",
                    "borrowHitPoint", "(FF)Lorg/lwjgl/util/vector/Vector2f;", 1);
            report.add("hit-test scratch allocations", lists + vectors);
        }
        if (config.hoverHitTestCache) {
            int access = hit.access;
            String signature = hit.signature;
            @SuppressWarnings("unchecked")
            List<String> exceptions = hit.exceptions == null ? null : new ArrayList<>(hit.exceptions);
            hit.name = "smo$originalHitTest";
            MethodNode wrapper = new MethodNode(Opcodes.ASM8, access, "OO0000", "(FFF)" + ENTITY_TOKEN_DESC,
                    signature, exceptions == null ? null : exceptions.toArray(new String[0]));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
            wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, 2));
            wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD, 3));
            wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "hitTestCached",
                    "(Ljava/lang/Object;FFF)" + ENTITY_TOKEN_DESC, false));
            wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(wrapper);
            report.add("hyperspace/map hover hit-test cache", 1);
        }
        if (config.intelCallbackCache) {
            int count = replaceIntelMapLocationCalls(node);
            report.add("Intel map-location callback cache", count);
        }
        return report;
    }

    private PatchReport patchZ(ClassNode node) {
        PatchReport report = new PatchReport();
        MethodNode method = requireMethod(node, "o00000", "(FF)V");
        if (config.intelCallbackCache) {
            int count = 0;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isIntelArrowDataCall(insn)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    makeStatic(call, HOOKS, "getArrowDataCached",
                            "(" + INTEL_DESC + MAP_API_DESC + ")Ljava/util/List;");
                    count++;
                }
            }
            requireCount("Z arrow-data callback", count, 1);
            report.add("Intel arrow-data callback cache", count);
        }
        if (config.arrowVectorPool) {
            int count = replaceConstructors(method, "org/lwjgl/util/vector/Vector2f", "(FF)V",
                    "borrowArrowVector", "(FF)Lorg/lwjgl/util/vector/Vector2f;", 2);
            report.add("Intel arrow vector pool", count);
        }
        return report;
    }

    private PatchReport patchEvents(ClassNode node) {
        PatchReport report = new PatchReport();
        if (config.intelCallbackCache) {
            int count = replaceIntelMapLocationCalls(node);
            if (count < 4) throw new IllegalStateException("Expected at least 4 EventsPanel getMapLocation calls; found " + count);
            report.add("EventsPanel map-location callback cache", count);
        }

        MethodNode missing = requireMethod(node, "addMissingIconsAndRows", "()V");
        if (config.intelFastContains) {
            int count = 0;
            for (AbstractInsnNode insn : missing.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals("java/util/List")
                        && call.name.equals("contains")
                        && call.desc.equals("(Ljava/lang/Object;)Z")) {
                    makeStatic(call, HOOKS, "fastContains", "(Ljava/util/Collection;Ljava/lang/Object;)Z");
                    count++;
                }
            }
            requireCount("EventsPanel missing-list contains", count, 1);
            report.add("Intel missing-list hash contains", count);
        }

        if (config.intelExistingIconLookup) {
            int count = 0;
            for (AbstractInsnNode insn : missing.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && call.owner.equals("java/util/LinkedHashMap")
                        && call.name.equals("values")
                        && call.desc.equals("()Ljava/util/Collection;")) {
                    missing.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 6));
                    makeStatic(call, HOOKS, "existingIntelIconCandidates",
                            "(Ljava/util/LinkedHashMap;Ljava/lang/Object;)Ljava/util/Collection;");
                    count++;
                }
            }
            requireCount("EventsPanel existing Intel icon values scan", count, 1);
            report.add("Intel direct existing-icon lookup", count);
        }
        return report;
    }

    private int replaceIntelMapLocationCalls(ClassNode node) {
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isIntelMapLocationCall(insn)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    makeStatic(call, HOOKS, "getMapLocationCached",
                            "(" + INTEL_DESC + MAP_API_DESC + ")" + ENTITY_TOKEN_DESC);
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isIntelMapLocationCall(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEINTERFACE
                && call.owner.equals("com/fs/starfarer/api/campaign/comm/IntelInfoPlugin")
                && call.name.equals("getMapLocation")
                && call.desc.equals("(" + MAP_API_DESC + ")" + ENTITY_TOKEN_DESC);
    }

    private static boolean isIntelArrowDataCall(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEINTERFACE
                && call.owner.equals("com/fs/starfarer/api/campaign/comm/IntelInfoPlugin")
                && call.name.equals("getArrowData")
                && call.desc.equals("(" + MAP_API_DESC + ")Ljava/util/List;");
    }

    private static int replaceConstructors(MethodNode method, String type, String constructorDesc,
                                           String hookName, String hookDesc, int expected) {
        int count = 0;
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode insn = instructions[i];
            if (!(insn instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW || !allocation.desc.equals(type)) continue;
            AbstractInsnNode duplicate = nextMeaningful(insn);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) continue;
            MethodInsnNode constructor = null;
            for (AbstractInsnNode cursor = duplicate.getNext(); cursor != null; cursor = cursor.getNext()) {
                if (cursor instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals(type)
                        && call.name.equals("<init>")
                        && call.desc.equals(constructorDesc)) {
                    constructor = call;
                    break;
                }
                if (cursor.getOpcode() == Opcodes.NEW && cursor != allocation) {
                    // The target constructors used here have no nested allocations in their arguments.
                    break;
                }
            }
            if (constructor == null) continue;
            method.instructions.remove(allocation);
            method.instructions.remove(duplicate);
            makeStatic(constructor, HOOKS, hookName, hookDesc);
            count++;
        }
        requireCount(method.name + method.desc + " constructor " + type + constructorDesc, count, expected);
        return count;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        AbstractInsnNode current = node.getNext();
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private static void makeStatic(MethodInsnNode call, String owner, String name, String desc) {
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = owner;
        call.name = name;
        call.desc = desc;
        call.itf = false;
    }

    private static MethodNode requireMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        throw new IllegalStateException("Method not found: " + node.name + "." + name + desc);
    }

    private static void requireCount(String label, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException(label + ": expected " + expected + " patch site(s), found " + actual);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) builder.append(String.format("%02x", value & 0xff));
        return builder.toString();
    }

    private static final class PatchReport {
        int total;
        final List<String> details = new ArrayList<>();
        void add(String label, int count) {
            if (count <= 0) return;
            total += count;
            details.add(label + "=" + count);
        }
        @Override public String toString() { return String.join(", ", details); }
    }
}
