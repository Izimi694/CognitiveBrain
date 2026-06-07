package com.izimi.aiplayermod.cortex.api;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.character.PersonalityStress;
import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.state.PlayerState;
import com.izimi.aiplayermod.cortex.task.Task;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AIChatHandler {
    private final AIClient aiClient;
    private final Deque<AIResponse> pendingResponses = new ArrayDeque<>();
    private CompletableFuture<AIResponse> currentRequest = null;

    public AIChatHandler(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public void handleChat(String playerMessage, PlayerState state, Task activeTask,
                           List<MemoryEntry> recentMemories, Map<String, Double> preferences,
                           PersonalityStress stress) {
        if (!aiClient.isConfigured()) return;
        if (currentRequest != null && !currentRequest.isDone()) return;

        currentRequest = aiClient.planTask(playerMessage, state, activeTask,
                recentMemories, preferences, stress);

        currentRequest.thenAccept(response -> {
            if (response != null) {
                synchronized (pendingResponses) {
                    pendingResponses.add(response);
                }
            }
        }).exceptionally(e -> {
            AIPlayerMod.LOGGER.error("[AIChatHandler] 对话失败: {}", e.getMessage());
            return null;
        });
    }

    public AIResponse pollResponse() {
        synchronized (pendingResponses) {
            return pendingResponses.poll();
        }
    }

    public boolean hasPendingResponse() {
        synchronized (pendingResponses) {
            return !pendingResponses.isEmpty();
        }
    }
}
