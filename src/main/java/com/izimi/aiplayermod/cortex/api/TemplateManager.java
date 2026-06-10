package com.izimi.aiplayermod.cortex.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.izimi.aiplayermod.AIPlayerMod;

public class TemplateManager {

    public enum TemplateType {
        REFLEX_CREATE,
        TASK_PLAN,
        EVALUATION_BATCH,
        FAILURE_CLASSIFY,
        CHAT_DIRECTION
    }

    private static final Gson GSON = new Gson();
    private static final long GLOBAL_COOLDOWN_MS = 5000;
    private static volatile long lastGlobalCall = 0;

    private final AIClient aiClient;
    private final Map<TemplateType, List<Consumer<JsonObject>>> postFillHooks = new HashMap<>();

    public TemplateManager(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public void registerPostFillHook(TemplateType type, Consumer<JsonObject> hook) {
        postFillHooks.computeIfAbsent(type, k -> new ArrayList<>()).add(hook);
    }

    public CompletableFuture<JsonObject> fill(TemplateType type, Map<String, Object> context) {
        if (!tryAcquireLLMSlot()) {
            return CompletableFuture.completedFuture(null);
        }

        String template = buildTemplate(type, context);

        List<AIMessage> messages = List.of(
                new AIMessage("system", buildSystemPrompt(type)),
                new AIMessage("user", template)
        );

        return aiClient.sendMessage(messages).thenApply(response -> {
            if (response == null || response.getMessage() == null || response.getMessage().isEmpty()) {
                return null;
            }
            try {
                JsonObject result = GSON.fromJson(response.getMessage(), JsonObject.class);
                if (result != null) {
                    List<Consumer<JsonObject>> hooks = postFillHooks.get(type);
                    if (hooks != null) {
                        for (Consumer<JsonObject> hook : hooks) {
                            hook.accept(result);
                        }
                    }
                }
                return result;
            } catch (JsonSyntaxException | NullPointerException e) {
                AIPlayerMod.LOGGER.warn("[TemplateManager] 填空解析失败: {} - {}", type, e.getMessage());
                return null;
            }
        });
    }

    private boolean tryAcquireLLMSlot() {
        long now = System.currentTimeMillis();
        if (now - lastGlobalCall < GLOBAL_COOLDOWN_MS) {
            return false;
        }
        lastGlobalCall = now;
        return true;
    }

    private String buildSystemPrompt(TemplateType type) {
        return switch (type) {
            case REFLEX_CREATE -> "你是一个Minecraft AI助手。请填空以下JSON模板，生成一个新的反射技能。";
            case TASK_PLAN -> "你是一个Minecraft AI助手。请填空以下JSON模板，生成任务执行步骤。";
            case EVALUATION_BATCH -> "你是一个行为评价系统。请填空以下JSON模板，输出评价结果。";
            case FAILURE_CLASSIFY -> "你是一个错误分析系统。请填空以下JSON模板，分析失败原因。";
            case CHAT_DIRECTION -> "你是一个对话方向系统。请填空以下JSON模板，提供对话方向。";
        };
    }

    private String buildTemplate(TemplateType type, Map<String, Object> context) {
        return switch (type) {
            case REFLEX_CREATE -> {
                String skill = (String) context.getOrDefault("skill", "unknown");
                String target = (String) context.getOrDefault("target", "unknown");
                yield String.format("""
                        填空以下JSON:
                        {
                          "reflex_id": "reflex_%s_%s",
                          "display_name": "{填显示名称}",
                          "steps": [
                            {"action": "{填动作1}", "target": "{填目标1}"},
                            {"action": "{填动作2}", "target": "{填目标2}"}
                          ]
                        }""", skill, target);
            }
            case TASK_PLAN -> {
                String goal = (String) context.getOrDefault("goal", "");
                yield String.format("""
                        任务: %s
                        填空以下JSON:
                        {
                          "steps": [
                            {"action": "{填动作}", "target": "{填目标}", "params": {}}
                          ],
                          "target_count": {填数量}
                        }""", goal);
            }
            case EVALUATION_BATCH -> {
                String evaluations = (String) context.getOrDefault("evaluations", "");
                String reflexContext = (String) context.getOrDefault("reflexContext", "");
                yield String.format("""
                        评价:
                        %s
                        最近执行:
                        %s
                        填空以下JSON:
                        [
                          {"reflex_id": "reflex_{填技能}_{填目标}", "delta": 0.0}
                        ]""", evaluations, reflexContext);
            }
            case FAILURE_CLASSIFY -> {
                String failureInfo = (String) context.getOrDefault("failureInfo", "");
                yield String.format("""
                        失败信息:
                        %s
                        填空以下JSON:
                        {
                          "reflex_id": "reflex_{填技能}_{填目标}",
                          "feature_key": "{填环境特征}",
                          "feature_value": true,
                          "outcome": "failure"
                        }""", failureInfo);
            }
            case CHAT_DIRECTION -> {
                String directionHint = (String) context.getOrDefault("directionHint", "");
                yield String.format("""
                        当前方向:
                        %s
                        填空以下JSON:
                        {
                          "perspective": "{视角: SURVIVAL|TASK|SOCIAL|CURIOUS|CAUTIOUS}",
                          "priority": 0.5,
                          "suggested_focus": "{建议关注内容}"
                        }""", directionHint);
            }
        };
    }
}
