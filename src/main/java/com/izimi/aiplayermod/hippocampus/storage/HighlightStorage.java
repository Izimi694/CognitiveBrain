package com.izimi.aiplayermod.hippocampus.storage;

import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HighlightStorage {

    private final Path highlightsDir;

    public HighlightStorage(Path botDir) {
        this.highlightsDir = botDir.resolve("memory").resolve("highlights");
        try {
            Files.createDirectories(highlightsDir);
        } catch (IOException ignored) {}
    }

    public void save(MemoryEntry entry) {
        Path path = highlightsDir.resolve(entry.id + ".json");
        JsonUtil.writeToFileSafeAtomic(path, entry);
    }

    public List<MemoryEntry> loadAll() {
        List<MemoryEntry> result = new ArrayList<>();
        try (var stream = Files.list(highlightsDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        MemoryEntry entry = JsonUtil.readFromFileSafe(p, MemoryEntry.class);
                        if (entry != null) result.add(entry);
                    });
        } catch (IOException ignored) {}
        result.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return result;
    }

    public boolean delete(String id) {
        Path path = highlightsDir.resolve(id + ".json");
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
}
