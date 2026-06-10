package com.izimi.aiplayermod.util.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MemoryFileSystem implements FileSystem {

    private final Map<String, String> files = new ConcurrentHashMap<>();
    private final Map<String, Long> mtimes = new ConcurrentHashMap<>();

    private String key(Path path) {
        return path.toString().replace('\\', '/');
    }

    @Override
    public String readString(Path path) {
        return files.get(key(path));
    }

    @Override
    public void writeString(Path path, String content) {
        files.put(key(path), content);
        mtimes.put(key(path), System.currentTimeMillis());
    }

    @Override
    public boolean exists(Path path) {
        return files.containsKey(key(path));
    }

    @Override
    public long lastModified(Path path) {
        return mtimes.getOrDefault(key(path), 0L);
    }

    @Override
    public Stream<Path> list(Path dir) {
        String prefix = key(dir) + "/";
        return files.keySet().stream()
                .filter(k -> k.startsWith(prefix) && !k.substring(prefix.length()).contains("/"))
                .map(k -> Paths.get(k));
    }

    @Override
    public void createDirectories(Path dir) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> readJsonMap(Path path) {
        String json = files.get(key(path));
        if (json == null) return null;
        return new HashMap<>((Map<String, Object>) new com.google.gson.Gson().fromJson(json, Map.class));
    }

    @Override
    public void writeJson(Path path, Object data) {
        writeString(path, new com.google.gson.Gson().toJson(data));
    }

    public void clear() {
        files.clear();
        mtimes.clear();
    }

    public int fileCount() {
        return files.size();
    }
}
