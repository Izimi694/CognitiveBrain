package com.izimi.aiplayermod.brainstem.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

public abstract class Skill {
    protected final String skillId;
    protected final String name;
    protected final String type;

    public Skill(String skillId, String name, String type) {
        this.skillId = skillId;
        this.name = name;
        this.type = type;
    }

    public String getSkillId() { return skillId; }
    public String getName() { return name; }
    public String getType() { return type; }

    public abstract boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context);

    public abstract SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context);

    public record SkillResult(boolean executed, boolean success, double effectiveness, String message) {
        public static SkillResult success(String message) {
            return new SkillResult(true, true, 1.0, message);
        }
        public static SkillResult fail(String message) {
            return new SkillResult(true, false, 0.0, message);
        }
        public static SkillResult partial(double effectiveness, String message) {
            return new SkillResult(true, false, effectiveness, message);
        }
        public static SkillResult unable(String message) {
            return new SkillResult(false, false, 0.0, message);
        }
    }
}
