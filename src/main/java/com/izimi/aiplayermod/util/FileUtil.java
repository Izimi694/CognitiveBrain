package com.izimi.aiplayermod.util;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileUtil {
    private static final String AI_MEMORY_DIR = "ai_memory";

    public static Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path getAIMemoryDir() {
        return getGameDir().resolve(AI_MEMORY_DIR);
    }

    public static Path getTasksDir() {
        return getAIMemoryDir().resolve("tasks");
    }

    public static Path getMemoriesDir() {
        return getAIMemoryDir().resolve("memory");
    }

    public static Path getSkillsDir() {
        return getAIMemoryDir().resolve("skills");
    }

    public static Path getInnateSkillsDir() {
        return getSkillsDir().resolve("innate");
    }

    public static Path getConditionedSkillsDir() {
        return getSkillsDir().resolve("conditioned");
    }

    public static Path getCharacterDir() {
        return getSkillsDir().resolve("character");
    }

    public static Path getPlansDir() {
        return getAIMemoryDir().resolve("plans");
    }

    public static Path getStateDir() {
        return getAIMemoryDir().resolve("state");
    }

    public static Path getExecutionLogsDir() {
        return getAIMemoryDir().resolve("execution_logs");
    }

    public static Path getTrialsDir() {
        return getMemoriesDir().resolve("trials");
    }

    public static Path getHighlightsDir() {
        return getMemoriesDir().resolve("highlights");
    }

    public static Path getEvaluationsDir() {
        return getAIMemoryDir().resolve("evaluations");
    }

    public static Path getConditionedDir() {
        return getAIMemoryDir().resolve("conditioned");
    }

    public static Path getThresholdsDir() {
        return getCharacterDir().resolve("thresholds");
    }

    public static Path getConfigDir() {
        return getAIMemoryDir().resolve("config");
    }

    public static Path getActiveTaskPath() {
        return getTasksDir().resolve("active_task.json");
    }

    public static Path getLastTaskPath() {
        return getTasksDir().resolve("last_task.json");
    }

    public static Path getStatePath() {
        return getStateDir().resolve("current.json");
    }

    public static Path getConfigPath() {
        return getConfigDir().resolve("config.json");
    }

    public static Path getInnateReflexesPath() {
        return getConfigDir().resolve("innate_reflexes.json");
    }

    public static Path getPreferencesPath() {
        return getCharacterDir().resolve("preferences.json");
    }

    public static Path getPersonalityTagsPath() {
        return getCharacterDir().resolve("personality_tags.json");
    }

    public static Path getHabitsPath() {
        return getCharacterDir().resolve("habits.json");
    }

    public static Path getRiskTolerancePath() {
        return getCharacterDir().resolve("risk_tolerance.json");
    }

    public static Path getLatestMemoryPath() {
        return getMemoriesDir().resolve("latest.mem");
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(getTasksDir());
        Files.createDirectories(getMemoriesDir());
        Files.createDirectories(getInnateSkillsDir());
        Files.createDirectories(getConditionedSkillsDir());
        Files.createDirectories(getCharacterDir());
        Files.createDirectories(getPlansDir());
        Files.createDirectories(getStateDir());
        Files.createDirectories(getExecutionLogsDir());
        Files.createDirectories(getConfigDir());
        Files.createDirectories(getTrialsDir());
        Files.createDirectories(getHighlightsDir());
        Files.createDirectories(getEvaluationsDir());
        Files.createDirectories(getConditionedDir());
        Files.createDirectories(getThresholdsDir());
    }

    public static boolean deleteIfExists(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    public static List<Path> getAllDataDirs() {
        return Arrays.asList(
                getConditionedDir(), getTasksDir(), getMemoriesDir(),
                getTrialsDir(), getEvaluationsDir(), getCharacterDir(),
                getPlansDir(), getStateDir(), getExecutionLogsDir(),
                getConfigDir(), getThresholdsDir(),
                getHighlightsDir()
        );
    }

    public static void cleanupTempFiles() {
        for (Path dir : getAllDataDirs()) {
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.toString().endsWith(".tmp"))
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {}
                            });
                } catch (IOException ignored) {}
            }
        }
    }
}
