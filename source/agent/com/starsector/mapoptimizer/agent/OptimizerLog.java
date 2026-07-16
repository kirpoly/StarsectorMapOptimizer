package com.starsector.mapoptimizer.agent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class OptimizerLog {
    private static final Object LOCK = new Object();
    private static volatile BufferedWriter writer;
    private static volatile Path logPath;

    private OptimizerLog() {}

    public static void initialize(Path path) {
        synchronized (LOCK) {
            logPath = path;
            try {
                Files.createDirectories(path.getParent());
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            } catch (IOException ex) {
                System.err.println("[StarsectorMapOptimizer] Unable to open log " + path + ": " + ex);
                writer = null;
            }
        }
    }

    public static Path getLogPath() {
        return logPath;
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void error(String message, Throwable throwable) {
        write("ERROR", message, throwable);
    }

    private static void write(String level, String message, Throwable throwable) {
        String line = Instant.now() + " [" + level + "] " + message;
        synchronized (LOCK) {
            System.err.println("[StarsectorMapOptimizer] " + line);
            BufferedWriter out = writer;
            if (out == null) return;
            try {
                out.write(line);
                out.newLine();
                if (throwable != null) {
                    out.write(throwable.toString());
                    out.newLine();
                    for (StackTraceElement element : throwable.getStackTrace()) {
                        out.write("    at " + element);
                        out.newLine();
                    }
                }
                out.flush();
            } catch (IOException ignored) {
                writer = null;
            }
        }
    }
}
