package com.izimi.aiplayermod.cortex.api;

import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.state.PlayerState;
import com.izimi.aiplayermod.cortex.task.Task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AIClient {

    CompletableFuture<AIResponse> sendMessage(List<AIMessage> messages);

    CompletableFuture<AIResponse> planTask(String playerMessage, PlayerState state,
                                           Task activeTask, List<MemoryEntry> recentMemories,
                                           Map<String, Double> preferences);

    CompletableFuture<AIResponse> generateMemory(Task completedTask, PlayerState state);

    boolean isConfigured();

    boolean testConnection();

    void setApiKey(String key);

    void setApiModel(String model);
}
