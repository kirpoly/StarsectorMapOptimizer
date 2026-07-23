package com.starsector.prepatcher.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Early, side-effect-free discovery of an AoTD fork candidate.
 *
 * <p>The scan is intentionally diagnostic only: a directory can exist while the
 * mod is disabled. Runtime capabilities become active solely after the loaded
 * fork performs the ABI handshake.</p>
 */
final class AoTDForkMarkerScanner {
    private static final String MOD_ID = "aotd_theory_of_toolbox";
    private static final String MARKER_ENTRY =
            "data/kaysaar/aotd/tot/compat/PrepatcherContract.class";

    private AoTDForkMarkerScanner() {}

    static Result scan(Path prepatcherModRoot) {
        if (prepatcherModRoot == null || prepatcherModRoot.getParent() == null) {
            return new Result(Status.UNAVAILABLE, null, null, "mods root is unavailable");
        }
        Path modsRoot = prepatcherModRoot.getParent().toAbsolutePath().normalize();
        if (!Files.isDirectory(modsRoot)) {
            return new Result(Status.UNAVAILABLE, null, null,
                    "mods root is not a directory: " + modsRoot);
        }

        List<Candidate> markerCandidates = new ArrayList<>();
        try (var directories = Files.list(modsRoot)) {
            directories.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(directory -> inspectDirectory(directory, markerCandidates));
        } catch (IOException ex) {
            return new Result(Status.ERROR, null, null,
                    ex.getClass().getSimpleName() + ": " + safeMessage(ex));
        }

        if (markerCandidates.isEmpty()) {
            return new Result(Status.NOT_FOUND, null, null,
                    "no AoTD native contract marker found");
        }
        if (markerCandidates.size() > 1) {
            StringBuilder detail = new StringBuilder("multiple marker candidates: ");
            for (int i = 0; i < markerCandidates.size(); i++) {
                if (i > 0) detail.append(", ");
                detail.append(markerCandidates.get(i).jar());
            }
            return new Result(Status.AMBIGUOUS, null, null, detail.toString());
        }
        Candidate candidate = markerCandidates.get(0);
        return new Result(Status.CANDIDATE_FOUND, candidate.directory(), candidate.jar(),
                "marker found; runtime handshake is still required");
    }

    private static void inspectDirectory(Path directory, List<Candidate> result) {
        Path modInfo = directory.resolve("mod_info.json");
        if (!Files.isRegularFile(modInfo)) return;
        String json;
        try {
            json = Files.readString(modInfo, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }
        if (!containsModId(json, MOD_ID)) return;

        try (var files = Files.walk(directory, 3)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jar -> {
                        try (JarFile file = new JarFile(jar.toFile())) {
                            if (file.getJarEntry(MARKER_ENTRY) != null) {
                                result.add(new Candidate(directory, jar));
                            }
                        } catch (IOException ignored) {
                            // A malformed unrelated JAR cannot make startup fail.
                        }
                    });
        } catch (IOException ignored) {
            // Runtime handshake remains authoritative if early discovery fails.
        }
    }

    private static boolean containsModId(String json, String expected) {
        int index = 0;
        while ((index = json.indexOf("\"id\"", index)) >= 0) {
            int colon = json.indexOf(':', index + 4);
            if (colon < 0) return false;
            int firstQuote = json.indexOf('"', colon + 1);
            if (firstQuote < 0) return false;
            int secondQuote = json.indexOf('"', firstQuote + 1);
            if (secondQuote < 0) return false;
            if (expected.equals(json.substring(firstQuote + 1, secondQuote))) return true;
            index = secondQuote + 1;
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "no message" : message;
    }

    enum Status {
        CANDIDATE_FOUND,
        NOT_FOUND,
        AMBIGUOUS,
        UNAVAILABLE,
        ERROR
    }

    record Result(Status status, Path modDirectory, Path markerJar, String detail) {}
    private record Candidate(Path directory, Path jar) {}
}
