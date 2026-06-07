package com.izimi.aiplayermod.cortex.api;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.character.PersonalityStress;
import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.state.PlayerState;
import com.izimi.aiplayermod.cortex.task.Task;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AITaskPlanner {
    private final AIClient aiClient;
    private final Deque<AIResponse> pendingResults = new ArrayDeque<>();
    private CompletableFuture<AIResponse> currentRequest = null;

    public AITaskPlanner(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public void planTask(String playerMessage, PlayerState state, Task activeTask,
                         List<MemoryEntry> recentMemories, Map<String, Double> preferences,
                         PersonalityStress stress) {
        if (!aiClient.isConfigured()) return;

        if (currentRequest != null && !currentRequest.isDone()) {
            return;
        }

        currentRequest = aiClient.planTask(playerMessage, state, activeTask,
                recentMemories, preferences, stress);

        currentRequest.thenAccept(response -> {
            if (response != null && !response.isEmpty()) {
                synchronized (pendingResults) {
                    pendingResults.add(response);
                }
            }
        }).exceptionally(e -> {
            AIPlayerMod.LOGGER.error("[AITaskPlanner] 规划失败: {}", e.getMessage());
            return null;
        });
    }

    public AIResponse pollResult() {
        synchronized (pendingResults) {
            return pendingResults.poll();
        }
    }

    public boolean hasPendingResult() {
        synchronized (pendingResults) {
            return !pendingResults.isEmpty();
        }
    }

    public boolean isBusy() {
        return currentRequest != null && !currentRequest.isDone();
    }
}
