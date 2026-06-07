package com.izimi.aiplayermod.brainstem.innate;

import com.izimi.aiplayermod.brainstem.skill.Skill;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Map;

public class AttackSkill extends Skill {
    private int attackCooldown = 0;
    private static final int ATTACK_COOLDOWN_TICKS = 10;

    public AttackSkill() {
        super("attack", "攻击", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        if (bot == null || world == null) return false;
        return findAttackTarget(world, bot) != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        Entity target = findAttackTarget(world, bot);
        if (target == null) {
            return SkillResult.success("附近没有可攻击的目标");
        }

        if (attackCooldown > 0) {
            attackCooldown--;
            return SkillResult.partial(0.5, "攻击冷却中");
        }

        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);

        bot.attack(target);
        bot.swingHand(Hand.MAIN_HAND);
        attackCooldown = ATTACK_COOLDOWN_TICKS;

        if (!target.isAlive()) {
            return SkillResult.success("已击杀目标");
        }

        if (target instanceof LivingEntity living) {
            float healthPercent = living.getHealth() / living.getMaxHealth();
            return SkillResult.partial(1.0 - healthPercent, "正在攻击: " + target.getName().getString());
        }

        return SkillResult.partial(0.5, "正在攻击: " + target.getName().getString());
    }

    private Entity findAttackTarget(ServerWorld world, ServerPlayerEntity bot) {
        BlockPos botPos = bot.getBlockPos();
        Box searchBox = new Box(botPos).expand(10);

        var entities = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && e != bot);

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            if (entity instanceof HostileEntity || entity instanceof AnimalEntity) {
                double dist = entity.squaredDistanceTo(bot);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }

        return closest;
    }
}
