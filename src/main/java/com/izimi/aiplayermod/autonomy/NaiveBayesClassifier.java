package com.izimi.aiplayermod.autonomy;

import com.izimi.aiplayermod.character.ThresholdConfig;
import com.izimi.aiplayermod.learning.BehaviorEvent;

import java.util.*;

public class NaiveBayesClassifier {

    public record Classification(
            String bestAction,
            double confidence,
            boolean meetsThreshold
    ) {
        public String toTaskGoal() {
            return switch (bestAction) {
                case "dig" -> "挖矿";
                case "attack" -> "打怪";
                case "use_item" -> "使用物品";
                case "place_block" -> "建造";
                default -> bestAction;
            };
        }
    }

    private final ThresholdConfig thresholdConfig;

    public NaiveBayesClassifier(ThresholdConfig thresholdConfig) {
        this.thresholdConfig = thresholdConfig;
    }

    public Classification classify(Map<String, Deque<BehaviorEvent>> playerWindows,
                                    FamiliarityTracker familiarity) {
        if (playerWindows.isEmpty()) return null;

        Map<String, Double> actionScores = new HashMap<>();
        double totalWeight = 0.0;
        int contributingPlayers = 0;

        for (var entry : playerWindows.entrySet()) {
            String playerName = entry.getKey();
            Deque<BehaviorEvent> events = entry.getValue();

            if (events.isEmpty()) continue;

            double playerWeight = familiarity.getWeight(playerName);
            contributingPlayers++;

            for (BehaviorEvent event : events) {
                actionScores.merge(event.action(), playerWeight, Double::sum);
                totalWeight += playerWeight;
            }
        }

        if (totalWeight == 0 || actionScores.isEmpty()) return null;

        String bestAction = null;
        double bestScore = 0.0;
        for (var entry : actionScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestAction = entry.getKey();
            }
        }

        double confidence = bestScore / totalWeight;
        double effectiveThreshold = thresholdConfig.getEffectiveThreshold();
        boolean meets = contributingPlayers >= thresholdConfig.minObservations
                && confidence >= effectiveThreshold;

        return new Classification(bestAction, confidence, meets);
    }
}
