package com.izimi.aiplayermod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class JsonUtil {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T fromJson(String json, TypeToken<T> typeToken) {
        return GSON.fromJson(json, typeToken.getType());
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T readFromFile(Path path, Class<T> clazz) throws IOException {
        if (!Files.exists(path)) return null;
        String content = Files.readString(path);
        return GSON.fromJson(content, clazz);
    }

    public static void writeToFile(Path path, Object obj) throws IOException {
        Files.createDirectories(path.getParent());
        String content = GSON.toJson(obj);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public static <T> T readFromFileSafe(Path path, Class<T> clazz) {
        try {
            return readFromFile(path, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    public static <T> T readFromFileSafe(Path path, Type type) {
        try {
            if (!Files.exists(path)) return null;
            String content = Files.readString(path);
            return GSON.fromJson(content, type);
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeToFileSafe(Path path, Object obj) {
        try {
            writeToFile(path, obj);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToFileAtomic(Path path, Object obj) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmpPath = path.resolveSibling(path.getFileName() + ".tmp");
        String content = GSON.toJson(obj);
        Files.writeString(tmpPath, content, StandardCharsets.UTF_8);

        String written = Files.readString(tmpPath);
        try {
            GSON.fromJson(written, Map.class);
        } catch (JsonSyntaxException e) {
            Files.deleteIfExists(tmpPath);
            throw new IOException("原子写入校验失败: JSON格式不合法 " + path.getFileName(), e);
        }

        Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeToFileSafeAtomic(Path path, Object obj) {
        try {
            writeToFileAtomic(path, obj);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".tmp"));
            } catch (IOException ignored) {}
            e.printStackTrace();
        }
    }
}
