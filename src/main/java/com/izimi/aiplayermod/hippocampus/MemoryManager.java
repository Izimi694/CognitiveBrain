package com.izimi.aiplayermod.hippocampus;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MemoryManager {
    private final ModConfig config;
    private final List<MemoryEntry> memoryCache = new ArrayList<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_MS = 60000;
    private int currentGameDay = 1;

    public MemoryManager(ModConfig config) {
        this.config = config;
        refreshCache();
    }

    public void setCurrentGameDay(int day) {
        this.currentGameDay = day;
    }

    public MemoryEntry generateMemory(Task task) {
        if (task == null) return null;

        String id = "mem_" + task.getTaskId().replace("task_", "");
        MemoryEntry memory = new MemoryEntry(id, generateSummary(task));
        memory.keyLearnings = generateLearnings(task);
        memory.relatedSkills = extractSkillIds(task);
        memory.preferencesUpdated = new HashMap<>();
        memory.gameDay = currentGameDay;

        saveMemory(memory);
        memoryCache.add(0, memory);
        updateLatest(memory);

        AIPlayerMod.LOGGER.info("[MemoryManager] 记忆已生成: {}", id);
        return memory;
    }

    public boolean deleteMemory(String id) {
        Path dayFile = getDayFile(currentGameDay);
        try {
            List<MemoryEntry> entries = loadDayMemories(dayFile);
            entries.removeIf(m -> m.id.equals(id));
            JsonUtil.writeToFile(dayFile, entries);
            memoryCache.removeIf(m -> m.id.equals(id));
            AIPlayerMod.LOGGER.info("[MemoryManager] 记忆已删除: {}", id);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public List<MemoryEntry> getRecentMemories() {
        refreshCacheIfNeeded();
        long cutoff = System.currentTimeMillis() - (long) config.memoryWindowDays * 86400000L;
        return memoryCache.stream()
                .filter(m -> m.timestamp >= cutoff)
                .collect(Collectors.toList());
    }

    public List<MemoryEntry> searchMemories(String query) {
        if (query == null || query.isEmpty()) return getRecentMemories();
        String lower = query.toLowerCase();
        return memoryCache.stream()
                .filter(m -> matchesQuery(m, lower))
                .collect(Collectors.toList());
    }

    public List<MemoryEntry> searchByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();

        return memoryCache.stream()
                .filter(m -> {
                    StringBuilder textBuilder = new StringBuilder();
                    if (m.summary != null) textBuilder.append(m.summary);
                    if (m.keyLearnings != null) textBuilder.append(" ").append(String.join(" ", m.keyLearnings));
                    String text = textBuilder.toString().toLowerCase();
                    for (String k : keywords) {
                        if (text.contains(k.toLowerCase())) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    private void saveMemory(MemoryEntry memory) {
        Path dayFile = getDayFile(currentGameDay);
        try {
            List<MemoryEntry> entries = loadDayMemories(dayFile);
            entries.add(memory);
            JsonUtil.writeToFile(dayFile, entries);
        } catch (IOException e) {
            AIPlayerMod.LOGGER.error("[MemoryManager] 保存记忆失败", e);
        }
    }

    private void updateLatest(MemoryEntry memory) {
        JsonUtil.writeToFileSafeAtomic(FileUtil.getLatestMemoryPath(), memory);
    }

    private Path getDayFile(int gameDay) {
        return FileUtil.getMemoriesDir().resolve(String.format("day_%03d.mem", gameDay));
    }

    private List<MemoryEntry> loadDayMemories(Path path) throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        MemoryEntry[] arr = JsonUtil.readFromFile(path, MemoryEntry[].class);
        return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
    }

    private void refreshCache() {
        memoryCache.clear();
        Path dir = FileUtil.getMemoriesDir();
        if (!Files.exists(dir)) return;

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".mem") && !p.getFileName().toString().equals("latest.mem"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            MemoryEntry[] arr = JsonUtil.readFromFile(p, MemoryEntry[].class);
                            if (arr != null) {
                                memoryCache.addAll(Arrays.asList(arr));
                            }
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}

        memoryCache.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        lastCacheUpdate = System.currentTimeMillis();
    }

    private void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_REFRESH_MS) {
            refreshCache();
        }
    }

    private boolean matchesQuery(MemoryEntry memory, String query) {
        if (memory.summary != null && memory.summary.toLowerCase().contains(query)) return true;
        if (memory.keyLearnings != null) {
            for (String learning : memory.keyLearnings) {
                if (learning.toLowerCase().contains(query)) return true;
            }
        }
        if (memory.relatedSkills != null) {
            for (String skill : memory.relatedSkills) {
                if (skill.toLowerCase().contains(query)) return true;
            }
        }
        return false;
    }

    private String generateSummary(Task task) {
        String goal = task.getGoal();
        int completed = task.progress != null ? task.progress.completedCount : 0;
        int target = task.progress != null ? task.progress.targetCount : 0;
        return "完成任务: " + goal + " (进度: " + completed + "/" + target + ")";
    }

    private List<String> generateLearnings(Task task) {
        List<String> learnings = new ArrayList<>();
        String goal = task.getGoal().toLowerCase();

        if (goal.contains("矿")) learnings.add("记录了矿产分布信息");
        if (goal.contains("攻击") || goal.contains("杀")) learnings.add("记录了战斗经验");
        if (goal.contains("合成") || goal.contains("制作")) learnings.add("记录了合成配方");
        if (goal.contains("探索") || goal.contains("走")) learnings.add("记录了地形特征");

        return learnings.isEmpty() ? List.of("执行了操作: " + task.getGoal()) : learnings;
    }

    private List<String> extractSkillIds(Task task) {
        String goal = task.getGoal().toLowerCase();
        List<String> skills = new ArrayList<>();

        if (goal.contains("矿") || goal.contains("挖")) skills.add("mine");
        if (goal.contains("攻击") || goal.contains("杀")) skills.add("combat");
        if (goal.contains("合成") || goal.contains("制作")) skills.add("craft");
        if (goal.contains("探索") || goal.contains("走")) skills.add("explore");
        if (goal.contains("矿洞") || goal.contains("洞穴")) skills.add("navigate_cave");

        return skills.isEmpty() ? List.of("general") : skills;
    }
}
