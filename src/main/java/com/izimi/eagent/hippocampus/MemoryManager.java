package com.izimi.eagent.hippocampus;

import com.izimi.eagent.bayesian.BayesianModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.cortex.task.Task;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MemoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final ModConfig config;
    private final UUID botId;
    private final List<MemoryEntry> memoryCache = new ArrayList<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_MS = 60000;
    private int currentGameDay = 1;
    private MemoryGraph memoryGraph;

    public MemoryManager(ModConfig config) {
        this(config, null);
    }

    public MemoryManager(ModConfig config, UUID botId) {
        this.config = config;
        this.botId = botId;
        refreshCache();
    }

    private Path memoriesDir() {
        return botId != null
                ? FileUtil.getBotMemoriesDir(botId)
                : FileUtil.getMemoriesDir();
    }

    private Path latestMemoryPath() {
        return memoriesDir().resolve("latest.mem");
    }

    public void setCurrentGameDay(int day) {
        this.currentGameDay = day;
    }

    public void setMemoryGraph(MemoryGraph memoryGraph) {
        this.memoryGraph = memoryGraph;
        if (memoryGraph != null) {
            memoryGraph.load(memoriesDir().resolve("memory_graph.json"));
            memoryGraph.rebuildReflexToNodesIndex(this);
        }
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

        if (memoryGraph != null) {
            memoryGraph.addNode(memory);
            memoryGraph.updateTimestamps(memory.timestamp);
            memoryGraph.inferEdges(getRecentMemories(), memory, null);
            memoryGraph.save(memoriesDir().resolve("memory_graph.json"));
        }

        LOGGER.info("[MemoryManager] 记忆已生成: {}", id);
        return memory;
    }

    public boolean deleteMemory(String id) {
        Path dayFile = getDayFile(currentGameDay);
        try {
            List<MemoryEntry> entries = loadDayMemories(dayFile);
            entries.removeIf(m -> m.id.equals(id));
            JsonUtil.writeToFile(dayFile, entries);
            memoryCache.removeIf(m -> m.id.equals(id));
            if (memoryGraph != null) {
                memoryGraph.removeNode(id);
                memoryGraph.save(memoriesDir().resolve("memory_graph.json"));
            }
            LOGGER.info("[MemoryManager] 记忆已删除: {}", id);
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

    public MemoryEntry getEntry(String id) {
        if (id == null) return null;
        refreshCacheIfNeeded();
        return memoryCache.stream()
                .filter(m -> id.equals(m.id))
                .findFirst().orElse(null);
    }

    public MemoryGraph getMemoryGraph() {
        return memoryGraph;
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

    public List<MemoryEntry> retrieve(String query, HormonalSystem hormones, BayesianModule bayes, int topK) {
        if (query == null) return getRecentMemories();
        List<MemoryEntry> all = getRecentMemories();
        if (all.isEmpty()) return all;

        // 阶段1: 激素粗筛 (curiosity 决定时间窗口阈值)
        double curiosity = hormones != null ? hormones.getCuriosity() : 0.5;
        long maxAgeMs = (long) (curiosity * 86400000L * 7); // curiosity 比例决定回溯天数
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        List<MemoryEntry> coarse = all.stream()
                .filter(m -> m.timestamp >= cutoff)
                .collect(Collectors.toList());
        if (coarse.isEmpty()) coarse = all;

        // 阶段2: 贝叶斯精排
        if (bayes != null) {
            coarse.sort((a, b) -> {
                String textA = a.summary != null ? a.summary : "";
                String textB = b.summary != null ? b.summary : "";
                double scoreA = bayes.predictRelevance(query, textA);
                double scoreB = bayes.predictRelevance(query, textB);
                return Double.compare(scoreB, scoreA);
            });
        }

        // 阶段3: 取 topK
        return coarse.stream().limit(topK > 0 ? topK : 5).collect(Collectors.toList());
    }

    public boolean hasNotSeenRecently(String entityType, long maxAgeMs) {
        refreshCacheIfNeeded();
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        String lower = entityType.toLowerCase();
        for (MemoryEntry m : memoryCache) {
            if (m.timestamp >= cutoff) {
                String text = (m.summary != null ? m.summary : "") +
                        (m.keyLearnings != null ? " " + String.join(" ", m.keyLearnings) : "");
                if (text.toLowerCase().contains(lower)) return false;
            }
        }
        return true;
    }

    private void saveMemory(MemoryEntry memory) {
        Path dayFile = getDayFile(currentGameDay);
        try {
            List<MemoryEntry> entries = loadDayMemories(dayFile);
            entries.add(memory);
            JsonUtil.writeToFile(dayFile, entries);
        } catch (IOException e) {
            LOGGER.error("[MemoryManager] 保存记忆失败", e);
        }
    }

    private void updateLatest(MemoryEntry memory) {
        JsonUtil.writeToFileSafeAtomic(latestMemoryPath(), memory);
    }

    private Path getDayFile(int gameDay) {
        return memoriesDir().resolve(String.format("day_%03d.mem", gameDay));
    }

    private List<MemoryEntry> loadDayMemories(Path path) throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        MemoryEntry[] arr = JsonUtil.readFromFile(path, MemoryEntry[].class);
        return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
    }

    private void refreshCache() {
        memoryCache.clear();
        Path dir = memoriesDir();
        if (!Files.exists(dir)) return;

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".mem") && !p.getFileName().toString().equals("latest.mem"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::loadMemoryFile);
        } catch (IOException ignored) {}

        memoryCache.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        lastCacheUpdate = System.currentTimeMillis();
    }

    private void loadMemoryFile(Path p) {
        try {
            MemoryEntry[] arr = JsonUtil.readFromFile(p, MemoryEntry[].class);
            if (arr != null) {
                memoryCache.addAll(Arrays.asList(arr));
            }
        } catch (Exception ignored) {}
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
