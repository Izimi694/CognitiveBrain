package com.izimi.aiplayermod.cortex.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.character.PersonalityStress;
import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.state.PlayerState;
import com.izimi.aiplayermod.cortex.task.Task;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DeepSeekClient implements AIClient {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final AIConfig config;
    private volatile long lastCallTime = 0;
    private static final long MIN_CALL_INTERVAL_MS = 2000;

    public DeepSeekClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.config = AIConfig.load();
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public boolean testConnection() {
        try {
            var response = sendMessage(List.of(
                    AIMessage.system("回复'ok'"),
                    AIMessage.user("ping")
            )).get(10, java.util.concurrent.TimeUnit.SECONDS);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[DeepSeekClient] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void setApiKey(String key) {
        config.setApiKey(key);
    }

    @Override
    public CompletableFuture<AIResponse> planTask(String playerMessage, PlayerState state,
                                                   Task activeTask, List<MemoryEntry> recentMemories,
                                                   Map<String, Double> preferences, PersonalityStress stress) {
        List<AIMessage> messages = AIRequest.buildPlanningRequest(
                playerMessage, state, activeTask, recentMemories, preferences, stress);
        return sendMessage(messages);
    }

    @Override
    public CompletableFuture<AIResponse> generateMemory(Task completedTask, PlayerState state) {
        List<AIMessage> messages = AIRequest.buildMemoryRequest(completedTask, state);
        return sendMessage(messages);
    }

    @Override
    public CompletableFuture<AIResponse> sendMessage(List<AIMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long waitTime = MIN_CALL_INTERVAL_MS - (now - lastCallTime);
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
                lastCallTime = System.currentTimeMillis();

                JsonObject body = new JsonObject();
                body.addProperty("model", config.apiModel);

                JsonArray msgArray = new JsonArray();
                for (AIMessage msg : messages) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("role", msg.role);
                    obj.addProperty("content", msg.content);
                    msgArray.add(obj);
                }
                body.add("messages", msgArray);

                JsonObject responseFormat = new JsonObject();
                responseFormat.addProperty("type", "json_object");
                body.add("response_format", responseFormat);

                body.addProperty("temperature", 0.7);
                body.addProperty("max_tokens", 1024);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getFullEndpoint()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .timeout(Duration.ofSeconds(60))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    AIPlayerMod.LOGGER.error("[DeepSeekClient] API 返回错误 {}: {}", response.statusCode(), response.body());
                    return AIResponse.empty();
                }

                return parseResponse(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AIResponse.empty();
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("[DeepSeekClient] 请求失败: {}", e.getMessage());
                return AIResponse.empty();
            }
        });
    }

    private AIResponse parseResponse(String body) {
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                AIPlayerMod.LOGGER.warn("[DeepSeekClient] 响应无choices");
                return AIResponse.empty();
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            content = content.trim();
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();

            return GSON.fromJson(content, AIResponse.class);
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[DeepSeekClient] 解析响应失败: {} -- body: {}",
                    e.getMessage(), body.substring(0, Math.min(200, body.length())));
            return AIResponse.empty();
        }
    }
}
