package com.izimi.aiplayermod.util.api;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public interface FileSystem {
    String readString(Path path);
    void writeString(Path path, String content);
    boolean exists(Path path);
    long lastModified(Path path);
    Stream<Path> list(Path dir);
    void createDirectories(Path dir);
    Map<String, Object> readJsonMap(Path path);
    void writeJson(Path path, Object data);
}
