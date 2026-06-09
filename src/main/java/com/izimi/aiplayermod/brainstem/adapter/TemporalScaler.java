package com.izimi.aiplayermod.brainstem.adapter;

import com.izimi.aiplayermod.brainstem.HormonalSystem;
import com.izimi.aiplayermod.brainstem.scheduler.UrgencyClassifier;
import net.minecraft.server.network.ServerPlayerEntity;

public class TemporalScaler {

    private float globalSpeed = 1.0f;
    private UrgencyClassifier classifier;
    private UrgencyClassifier.UrgencyLabel lastLabel = UrgencyClassifier.UrgencyLabel.NORMAL;

    public TemporalScaler() {
        this.classifier = new UrgencyClassifier();
    }

    public TemporalScaler(UrgencyClassifier classifier) {
        this.classifier = classifier;
    }

    public void update(HormonalSystem hormones, ServerPlayerEntity bot, int ticksInState) {
        UrgencyClassifier.UrgencyLabel label = classifier.classify(hormones, bot, ticksInState);
        lastLabel = label;

        switch (label) {
            case CRITICAL -> globalSpeed = 2.0f;
            case HIGH     -> globalSpeed = 1.5f;
            case NORMAL   -> globalSpeed = 1.0f;
            case LOW      -> globalSpeed = 0.8f;
            case OBSERVE  -> globalSpeed = 0.5f;
        }
    }

    public int scaleDuration(int baseTicks) {
        return Math.max(1, Math.round(baseTicks / globalSpeed));
    }

    public double scaleVelocity(double baseVelocity) {
        return baseVelocity * globalSpeed;
    }

    public float getSpeed() {
        return globalSpeed;
    }

    public UrgencyClassifier.UrgencyLabel getLastLabel() {
        return lastLabel;
    }
}
