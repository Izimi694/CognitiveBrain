package com.izimi.aiplayermod.brainstem.bot;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class GenomeArchivist {

    public static void saveGenome(UUID botId, BotParams params, String name, String cause) {
        Path genomeFile = FileUtil.getBotGenomeDir().resolve(botId + ".json");
        Map<String, Object> genome = new LinkedHashMap<>();
        genome.put("botId", botId.toString());
        genome.put("name", name);
        genome.put("alpha", params.getAlpha());
        genome.put("beta", params.getBeta());
        genome.put("temperature", params.getTemperature());
        genome.put("generation", params.getGeneration());
        genome.put("parentId", params.getParentId() != null ? params.getParentId().toString() : null);
        genome.put("cause", cause);
        genome.put("diedAt", System.currentTimeMillis());
        JsonUtil.writeToFileSafeAtomic(genomeFile, genome);
        AIPlayerMod.LOGGER.info("[Genome] 存档基因组: {} ({}, gen={}, cause={})", name, botId, params.getGeneration(), cause);
    }

    public static List<GenomeRecord> listGenomes() {
        Path dir = FileUtil.getBotGenomeDir();
        if (!Files.exists(dir)) return Collections.emptyList();

        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        Map<String, Object> data = JsonUtil.readMapFromFileSafe(p);
                        if (data == null) return null;
                        return new GenomeRecord(
                                UUID.fromString((String) data.get("botId")),
                                (String) data.get("name"),
                                ((Number) data.get("generation")).intValue(),
                                (String) data.get("cause"),
                                ((Number) data.get("diedAt")).longValue(),
                                (String) data.get("parentId") != null
                                        ? UUID.fromString((String) data.get("parentId")) : null
                        );
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> Long.compare(b.diedAt, a.diedAt))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static BotParams loadGenomeParams(UUID botId) {
        Path genomeFile = FileUtil.getBotGenomeDir().resolve(botId + ".json");
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(genomeFile);
        if (data == null) return null;
        BotParams params = new BotParams(
                ((Number) data.get("alpha")).doubleValue(),
                ((Number) data.get("beta")).doubleValue(),
                ((Number) data.get("temperature")).doubleValue()
        );
        params.withGeneration(((Number) data.get("generation")).intValue());
        String parentStr = (String) data.get("parentId");
        if (parentStr != null) params.withParentId(UUID.fromString(parentStr));
        params.withBotId(botId);
        return params;
    }

    public static BotParams getLatestGenomeParams() {
        List<GenomeRecord> records = listGenomes();
        if (records.isEmpty()) return null;
        return loadGenomeParams(records.get(0).botId);
    }

    public static boolean hasGenomes() {
        Path dir = FileUtil.getBotGenomeDir();
        if (!Files.exists(dir)) return false;
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    public static int getGenomeCount() {
        Path dir = FileUtil.getBotGenomeDir();
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(p -> p.toString().endsWith(".json")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public static boolean genomeExists(UUID botId) {
        return Files.exists(FileUtil.getBotGenomeDir().resolve(botId + ".json"));
    }

    public record GenomeRecord(UUID botId, String name, int generation, String cause, long diedAt, UUID parentId) {}
}
