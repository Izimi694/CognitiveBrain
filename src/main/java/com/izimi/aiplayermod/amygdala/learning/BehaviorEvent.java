package com.izimi.aiplayermod.amygdala.learning;

public record BehaviorEvent(
        String playerName,
        String action,
        String target,
        long timestamp,
        String heldItem,
        String timeOfDay
) {
    public static BehaviorEvent of(String playerName, String action, String target,
                                    String heldItem, String timeOfDay) {
        return new BehaviorEvent(playerName, action, target, System.currentTimeMillis(),
                heldItem != null ? heldItem : "unknown", timeOfDay != null ? timeOfDay : "any");
    }
}
