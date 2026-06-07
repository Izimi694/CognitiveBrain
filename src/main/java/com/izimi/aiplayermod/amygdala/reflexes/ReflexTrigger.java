package com.izimi.aiplayermod.amygdala.reflexes;

public record ReflexTrigger(TriggerType type, double value, int range) {

    public enum TriggerType {
        HEALTH_BELOW,
        HUNGER_BELOW,
        MONSTER_NEARBY,
        LAVA_NEARBY,
        TIME_OF_DAY,
        ITEM_NEARBY,
        CHAT_PRESENCE
    }
}
