package com.izimi.aiplayermod.autonomy;

import java.util.*;

public class FamiliarityTracker {

    private static final double BASE_WEIGHT = 1.0;
    private static final double MAX_WEIGHT = 2.0;
    private static final double INTERACTION_INCREMENT = 0.01;
    private static final double DECAY_PER_HOUR = 0.05;

    private final Map<String, Double> familiarityMap = new HashMap<>();
    private final Map<String, Long> lastInteraction = new HashMap<>();

    public void recordInteraction(String playerName) {
        familiarityMap.merge(playerName, INTERACTION_INCREMENT,
                (old, inc) -> Math.min(MAX_WEIGHT, old + inc));
        lastInteraction.put(playerName, System.currentTimeMillis());
    }

    public void recordPresence(String playerName) {
        lastInteraction.putIfAbsent(playerName, System.currentTimeMillis());
        familiarityMap.putIfAbsent(playerName, BASE_WEIGHT);
    }

    public double getWeight(String playerName) {
        applyDecay(playerName);
        return familiarityMap.getOrDefault(playerName, BASE_WEIGHT);
    }

    public double getFamiliarity(String playerName) {
        return familiarityMap.getOrDefault(playerName, BASE_WEIGHT);
    }

    public boolean isFamiliar(String playerName) {
        return getWeight(playerName) >= 1.3;
    }

    public List<String> getFamiliarPlayers() {
        List<String> result = new ArrayList<>();
        for (var entry : familiarityMap.entrySet()) {
            if (getWeight(entry.getKey()) >= 1.2) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private void applyDecay(String playerName) {
        Long last = lastInteraction.get(playerName);
        if (last == null) return;

        long hoursSinceLast = (System.currentTimeMillis() - last) / 3_600_000;
        if (hoursSinceLast > 0) {
            double current = familiarityMap.getOrDefault(playerName, BASE_WEIGHT);
            current = Math.max(BASE_WEIGHT, current - DECAY_PER_HOUR * hoursSinceLast);
            familiarityMap.put(playerName, current);
            lastInteraction.put(playerName, System.currentTimeMillis());
        }
    }
}
