package com.izimi.aiplayermod.cortex.api;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;

public class AIConfig {
    private static final Path KEY_FILE = FileUtil.getConfigDir().resolve("api_key.json");

    public String apiEndpoint;
    public String apiModel;
    public String apiKey;
    public boolean enabled;

    private static AIConfig instance;

    public AIConfig() {
        this.apiEndpoint = "https://api.deepseek.com";
        this.apiModel = "deepseek-chat";
        this.apiKey = "";
        this.enabled = false;
    }

    public static AIConfig load() {
        if (instance != null) return instance;

        AIConfig config = JsonUtil.readFromFileSafe(KEY_FILE, AIConfig.class);
        if (config == null) {
            config = new AIConfig();
            config.save();
        }
        instance = config;
        return instance;
    }

    public void save() {
        JsonUtil.writeToFileSafe(KEY_FILE, this);
        instance = this;
    }

    public void setApiKey(String key) {
        this.apiKey = key != null ? key.trim() : "";
        this.enabled = !this.apiKey.isEmpty();
        save();
        AIPlayerMod.LOGGER.info("[AIConfig] API Key 已{}", enabled ? "设置" : "清除");
    }

    public void setApiModel(String model) {
        if (model != null && !model.trim().isEmpty()) {
            this.apiModel = model.trim();
            save();
            AIPlayerMod.LOGGER.info("[AIConfig] AI模型已设置为: {}", this.apiModel);
        }
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    public String getFullEndpoint() {
        String endpoint = apiEndpoint;
        if (!endpoint.endsWith("/")) endpoint += "/";
        return endpoint + "v1/chat/completions";
    }

    public static AIConfig getInstance() {
        return instance != null ? instance : load();
    }
}
