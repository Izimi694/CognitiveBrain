package com.izimi.aiplayermod.autonomy;

import com.izimi.aiplayermod.learning.BehaviorEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SocialObserver {

    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<BehaviorEvent>> playerWindows = new ConcurrentHashMap<>();
    private final Set<String> nearbyPlayers = ConcurrentHashMap.newKeySet();
    private final FamiliarityTracker familiarityTracker;

    public SocialObserver(FamiliarityTracker familiarityTracker) {
        this.familiarityTracker = familiarityTracker;
    }

    public void onEvent(BehaviorEvent event) {
        if (event.playerName() == null || event.playerName().isEmpty()) return;

        playerWindows.computeIfAbsent(event.playerName(), k -> new ArrayDeque<>()).addLast(event);
        prunePlayerWindow(event.playerName());
        familiarityTracker.recordInteraction(event.playerName());
    }

    public void markNearbyPlayers(Collection<String> playerNames) {
        nearbyPlayers.clear();
        nearbyPlayers.addAll(playerNames);
        for (String name : playerNames) {
            familiarityTracker.recordPresence(name);
        }
    }

    public Map<String, Deque<BehaviorEvent>> getPlayerWindows() {
        return Collections.unmodifiableMap(playerWindows);
    }

    public int getObservedPlayerCount() {
        return (int) playerWindows.values().stream().filter(w -> !w.isEmpty()).count();
    }

    public int getNearbyPlayerCount() {
        return nearbyPlayers.size();
    }

    private void prunePlayerWindow(String playerName) {
        Deque<BehaviorEvent> window = playerWindows.get(playerName);
        if (window == null) return;
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!window.isEmpty() && window.peekFirst().timestamp() < cutoff) {
            window.pollFirst();
        }
        if (window.isEmpty()) {
            playerWindows.remove(playerName);
        }
    }
}
