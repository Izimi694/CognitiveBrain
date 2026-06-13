package com.izimi.eagent.brainstem.bot;

import com.izimi.eagent.bayesian.BayesianModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.hippocampus.MemoryGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ReflexPackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int PACK_VERSION = 1;

    private final UUID botId;
    private final BayesianModule bayesianModule;
    private MemoryGraph memoryGraph;

    public ReflexPackManager(UUID botId, BayesianModule bayesianModule) {
        this.botId = botId;
        this.bayesianModule = bayesianModule;
    }

    public void setMemoryGraph(MemoryGraph memoryGraph) {
        this.memoryGraph = memoryGraph;
    }

    public boolean exportPack(String packName, boolean includePrior) {
        Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
        Path conditionedDir = FileUtil.getBotConditionedDir(botId);

        if (!Files.exists(conditionedDir)) {
            LOGGER.warn("[ReflexPack] 没有反射可以导出: bot={}", botId);
            return false;
        }

        try {
            Map<String, Object> pack = buildPack(conditionedDir, includePrior);
            JsonUtil.writeToFileSafeAtomic(packFile, pack);
            Object refs = pack.get("reflexes");
            int count = refs instanceof Map ? ((Map<?, ?>) refs).size() : 0;
            LOGGER.info("[ReflexPack] 导出成功: {} ({} 反射, includePrior={})",
                    packName, count, includePrior);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ReflexPack] 导出失败: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildPack(Path conditionedDir, boolean includePrior) throws IOException {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("version", PACK_VERSION);
        pack.put("source", "E-Agent");
        pack.put("source_bot", botId.toString());
        pack.put("exported_at", Instant.now().toString());
        pack.put("include_prior", includePrior);

        Map<String, Object> reflexes = new LinkedHashMap<>();
        try (var stream = Files.list(conditionedDir)) {
            for (Path reflexFile : stream.toList()) {
                if (!reflexFile.toString().endsWith(".json")) continue;
                Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexFile);
                if (data != null) {
                    String skillId = (String) data.getOrDefault("skillId",
                            reflexFile.getFileName().toString().replace(".json", ""));
                    reflexes.put(skillId, data);
                }
            }
        }
        pack.put("reflexes", reflexes);

        if (includePrior) {
            Map<String, Double> prior = bayesianModule.getSharedPrior();
            Map<String, Double> filteredPrior = new HashMap<>();
            for (String reflexId : reflexes.keySet()) {
                if (prior.containsKey(reflexId)) {
                    filteredPrior.put(reflexId, prior.get(reflexId));
                }
            }
            pack.put("bayesian_prior", filteredPrior);
        }

        if (memoryGraph != null) {
            pack.put("memory_graph", memoryGraph.exportSkeleton());
        }

        return pack;
    }

    @SuppressWarnings("unchecked")
    public boolean importPack(String packName, boolean reset) {
        Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
        Path conditionedDir = FileUtil.getBotConditionedDir(botId);

        Map<String, Object> pack = JsonUtil.readMapFromFileSafe(packFile);
        if (pack == null) {
            LOGGER.warn("[ReflexPack] 包不存在: {}", packName);
            return false;
        }

        Map<String, Object> incomingReflexes = (Map<String, Object>) pack.get("reflexes");
        if (incomingReflexes == null || incomingReflexes.isEmpty()) {
            LOGGER.warn("[ReflexPack] 包内没有反射: {}", packName);
            return false;
        }

        try {
            Files.createDirectories(conditionedDir);

            int imported = 0;
            int skipped = 0;

            for (var entry : incomingReflexes.entrySet()) {
                String reflexId = entry.getKey();
                Map<String, Object> incomingData = (Map<String, Object>) entry.getValue();

                Path reflexFile = conditionedDir.resolve(reflexId + ".json");

                if (!reset && Files.exists(reflexFile)) {
                    Map<String, Object> existingData = JsonUtil.readMapFromFileSafe(reflexFile);
                    if (existingData != null) {
                        long existingTime = getLastUpdated(existingData);
                        long incomingTime = getLastUpdated(incomingData);
                        if (existingTime >= incomingTime) {
                            skipped++;
                            continue;
                        }
                    }
                }

                JsonUtil.writeToFileSafeAtomic(reflexFile, incomingData);
                imported++;
            }

            if (pack.containsKey("bayesian_prior")) {
                Object rawPrior = pack.get("bayesian_prior");
                if (rawPrior instanceof Map) {
                    for (var entry : ((Map<String, Object>) rawPrior).entrySet()) {
                        BayesianModule.setPrior(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                    BayesianModule.saveSharedPrior();
                }
            }

            if (pack.containsKey("memory_graph") && memoryGraph != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> skeleton = (Map<String, Object>) pack.get("memory_graph");
                memoryGraph.importSkeleton(skeleton);
            }

            LOGGER.info("[ReflexPack] 导入完成: {} ({} 个, 跳过 {} 个, reset={})",
                    packName, imported, skipped, reset);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ReflexPack] 导入失败: {}", e.getMessage());
            return false;
        }
    }

    public static List<String> listPacks() {
        Path packsDir = FileUtil.getReflexPacksDir();
        if (!Files.exists(packsDir)) return Collections.emptyList();

        try (var stream = Files.list(packsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> describePack(p))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static String describePack(Path path) {
        String name = path.getFileName().toString().replace(".json", "");
        Map<String, Object> meta = JsonUtil.readMapFromFileSafe(path);
        if (meta == null) return name + " (无法读取)";
        Object refs = meta.get("reflexes");
        int count = refs instanceof Map ? ((Map<String, Object>) refs).size() : 0;
        boolean hasPrior = meta.containsKey("bayesian_prior")
                && meta.get("bayesian_prior") instanceof Map
                && !((Map<String, Object>) meta.get("bayesian_prior")).isEmpty();
        return name + " (" + count + " 反射" + (hasPrior ? ", 含先验" : "") + ")";
    }

    public static boolean deletePack(String packName) {
        Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
        if (!Files.exists(packFile)) return false;
        boolean deleted = FileUtil.deleteIfExists(packFile);
        if (deleted) {
            LOGGER.info("[ReflexPack] 删除成功: {}", packName);
        }
        return deleted;
    }

    private static long getLastUpdated(Map<String, Object> data) {
        Object val = data.get("lastUpdated");
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { }
        }
        Object solidified = data.get("solidifiedAt");
        if (solidified instanceof Number) return ((Number) solidified).longValue();
        return 0L;
    }
}
