package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

public class PersonalityStress {
    private double currentStress;
    private final double stressThreshold;
    private double decayRate;
    private double interactionDurationWeight;
    private final double interactionCoefficient;
    private int conditionedReflexChanges;
    private final double changeCoefficient;
    private long lastStressEvent;
    private int totalStressEvents;
    private long lastTickTime;

    public PersonalityStress(ModConfig config) {
        this.stressThreshold = 1.0;
        this.decayRate = 0.02;
        this.interactionDurationWeight = 0;
        this.interactionCoefficient = 0.05;
        this.conditionedReflexChanges = 0;
        this.changeCoefficient = 0.2;
        this.currentStress = 0;
        this.lastStressEvent = 0;
        this.totalStressEvents = 0;
        this.lastTickTime = System.currentTimeMillis();
        load();
    }

    public void onTick() {
        long now = System.currentTimeMillis();
        double deltaMinutes = (now - lastTickTime) / 60000.0;
        lastTickTime = now;

        decay(deltaMinutes);

        currentStress = interactionDurationWeight * interactionCoefficient
                + conditionedReflexChanges * changeCoefficient;
    }

    public void onPlayerInteraction(double durationMinutes) {
        interactionDurationWeight += durationMinutes;
        AIPlayerMod.LOGGER.debug("[PersonalityStress] 互动: +{}min, 累计互动权重: {}",
                durationMinutes, interactionDurationWeight);
    }

    public void onReflexChange() {
        conditionedReflexChanges++;
        AIPlayerMod.LOGGER.debug("[PersonalityStress] 反射变化: +1, 累计反射变化: {}",
                conditionedReflexChanges);
    }

    public boolean checkAndTrigger() {
        if (currentStress >= stressThreshold) {
            triggerStressEvent();
            return true;
        }
        return false;
    }

    private void triggerStressEvent() {
        AIPlayerMod.LOGGER.info("[PersonalityStress] 压力触发! stress={} >= threshold={}, 事件次数={}",
                currentStress, stressThreshold, totalStressEvents + 1);

        currentStress = 0;
        interactionDurationWeight = 0;
        conditionedReflexChanges = 0;
        totalStressEvents++;
        lastStressEvent = System.currentTimeMillis();

        save();
    }

    private void decay(double deltaMinutes) {
        double decay = decayRate * deltaMinutes;
        interactionDurationWeight = Math.max(0, interactionDurationWeight - decay * 10);
        if (conditionedReflexChanges > 0 && Math.random() < decay * 0.05) {
            conditionedReflexChanges = Math.max(0, conditionedReflexChanges - 1);
        }
    }

    public void save() {
        JsonUtil.writeToFileSafeAtomic(FileUtil.getCharacterDir().resolve("personality_stress.json"), this);
    }

    private void load() {
        PersonalityStress loaded = JsonUtil.readFromFileSafe(
                FileUtil.getCharacterDir().resolve("personality_stress.json"), PersonalityStress.class);
        if (loaded != null) {
            this.currentStress = loaded.currentStress;
            this.interactionDurationWeight = loaded.interactionDurationWeight;
            this.conditionedReflexChanges = loaded.conditionedReflexChanges;
            this.totalStressEvents = loaded.totalStressEvents;
            this.lastStressEvent = loaded.lastStressEvent;
        }
    }

    public double getCurrentStress() { return currentStress; }
    public double getStressThreshold() { return stressThreshold; }
    public int getTotalStressEvents() { return totalStressEvents; }
    public long getLastStressEvent() { return lastStressEvent; }
    public double getInteractionDurationWeight() { return interactionDurationWeight; }
    public int getConditionedReflexChanges() { return conditionedReflexChanges; }
}
