package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;

import java.util.HashMap;
import java.util.Map;

public class BehaviorAnalyzer {

    private static final int EVOLVE_THRESHOLD = 10;

    private final CharacterManager characterManager;
    private final PersonalityStress personalityStress;

    private final Map<String, Double> pendingBehaviorUpdates = new HashMap<>();
    private final Map<String, Double> pendingChatUpdates = new HashMap<>();
    private int observationCount = 0;

    public BehaviorAnalyzer(CharacterManager characterManager, PersonalityStress personalityStress) {
        this.characterManager = characterManager;
        this.personalityStress = personalityStress;
    }

    public void recordEvent(String target, double behaviorDelta, double chatDelta, double stressDelta) {
        if (behaviorDelta != 0) {
            pendingBehaviorUpdates.merge(target, behaviorDelta, Double::sum);
        }
        if (chatDelta != 0) {
            pendingChatUpdates.merge(target, chatDelta, Double::sum);
        }
        observationCount++;
        if (personalityStress != null) {
            personalityStress.onPlayerInteraction(stressDelta);
        }
        checkEvolution();
    }

    private void checkEvolution() {
        if (observationCount >= EVOLVE_THRESHOLD) {
            triggerEvolution();
            observationCount = 0;
        }
    }

    private void triggerEvolution() {
        if (pendingBehaviorUpdates.isEmpty() && pendingChatUpdates.isEmpty()) return;

        Map<String, Double> behaviorUpdates = new HashMap<>(pendingBehaviorUpdates);
        Map<String, Double> chatUpdates = new HashMap<>(pendingChatUpdates);

        characterManager.evolvePreferences(behaviorUpdates, new HashMap<>(), chatUpdates);

        pendingBehaviorUpdates.clear();
        pendingChatUpdates.clear();

        if (personalityStress != null && personalityStress.checkAndTrigger()) {
            characterManager.triggerStressEvolution();
            AIPlayerMod.LOGGER.info("[BehaviorAnalyzer] 压力触发性格修改!");
        }

        AIPlayerMod.LOGGER.info("[BehaviorAnalyzer] 性格演化已触发");
    }

    public int getObservationCount() { return observationCount; }
    public int getPendingCount() { return pendingBehaviorUpdates.size() + pendingChatUpdates.size(); }
}
