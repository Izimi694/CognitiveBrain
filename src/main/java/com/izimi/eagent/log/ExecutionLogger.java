package com.izimi.eagent.log;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExecutionLogger {
    private final List<LogEntry> recentLogs = new ArrayList<>();
    private int logCounter = 0;
    private static final int MAX_RECENT = 100;
    private static final int BATCH_SIZE = 10;

    public void logAction(String action, Map<String, Object> context, String outcome, double effectiveness) {
        LogEntry entry = new LogEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.action = action;
        entry.context = context != null ? new HashMap<>(context) : new HashMap<>();
        entry.outcome = outcome;
        entry.effectiveness = effectiveness;

        recentLogs.add(entry);
        logCounter++;

        if (recentLogs.size() > MAX_RECENT) {
            recentLogs.remove(0);
        }

        if (logCounter >= BATCH_SIZE) {
            flushToFile();
            logCounter = 0;
        }
    }

    private void flushToFile() {
        if (recentLogs.isEmpty()) return;

        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path logPath = FileUtil.getExecutionLogsDir().resolve("log_" + date + ".json");
        List<LogEntry> toWrite = new ArrayList<>();
        int count = Math.min(BATCH_SIZE, recentLogs.size());
        for (int i = recentLogs.size() - count; i < recentLogs.size(); i++) {
            toWrite.add(recentLogs.get(i));
        }

        JsonUtil.writeToFileSafe(logPath, toWrite);
    }

    public List<LogEntry> getRecentLogs() {
        return new ArrayList<>(recentLogs);
    }

    public Map<String, List<Double>> analyzeEffectiveness() {
        Map<String, List<Double>> analysis = new HashMap<>();
        loadFileLogs(analysis);
        for (LogEntry entry : recentLogs) {
            analysis.computeIfAbsent(entry.action, k -> new ArrayList<>()).add(entry.effectiveness);
        }
        return analysis;
    }

    private void loadFileLogs(Map<String, List<Double>> analysis) {
        var dir = FileUtil.getExecutionLogsDir();
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> loadLogFile(p, analysis));
        } catch (IOException ignored) {}
    }

    private void loadLogFile(Path p, Map<String, List<Double>> analysis) {
        try {
            LogEntry[] entries = JsonUtil.readFromFileSafe(p, LogEntry[].class);
            if (entries == null) return;
            for (LogEntry entry : entries) {
                analysis.computeIfAbsent(entry.action, k -> new ArrayList<>()).add(entry.effectiveness);
            }
        } catch (Exception ignored) {}
    }

    public static class LogEntry {
        public long timestamp;
        public String action;
        public Map<String, Object> context = new HashMap<>();
        public String outcome;
        public double effectiveness;
    }
}
