package com.izimi.aiplayermod.cortex.inhibitor;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.character.BehaviorStats;
import com.izimi.aiplayermod.brainstem.innate.InnateReflex;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Set;

public class InhibitoryControl {

    private static final int SAFETY_DISTANCE_THRESHOLD = 10;
    private static final float HEALTH_SAFE_THRESHOLD = 15.0f;
    private static final int FALL_DANGER_HEIGHT = 5;

    private static final Set<EntityType<?>> WEAK_HOSTILES = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER
    );

    private int vetoSafetyCount;
    private int vetoImitationCount;

    public boolean shouldVetoSafety(InnateReflex reflex, ServerPlayerEntity bot,
                                    MinecraftServer server, BehaviorStats stats) {
        if (reflex == null || bot == null) return false;

        if (!"flee".equals(reflex.action().type())) return false;

        HostileEntity nearest = findNearestHostile(bot, 16);
        if (nearest == null) {
            logVeto("安全反射-flee", "附近无敌对生物");
            vetoSafetyCount++;
            return true;
        }

        double distance = bot.squaredDistanceTo(nearest);
        if (distance > SAFETY_DISTANCE_THRESHOLD * SAFETY_DISTANCE_THRESHOLD) {
            logVeto("安全反射-flee", "敌对生物距离过远: " + String.format("%.1f", Math.sqrt(distance)));
            vetoSafetyCount++;
            return true;
        }

        if (bot.getHealth() > HEALTH_SAFE_THRESHOLD
                && WEAK_HOSTILES.contains(nearest.getType())) {
            logVeto("安全反射-flee", "血量充足(" + String.format("%.0f", bot.getHealth())
                    + ") vs 弱敌(" + nearest.getType().getUntranslatedName() + ")");
            vetoSafetyCount++;
            return true;
        }

        if (hasSolidBarrier(bot, nearest)) {
            logVeto("安全反射-flee", "与敌对生物之间有固体屏障");
            vetoSafetyCount++;
            return true;
        }

        return false;
    }

    public boolean shouldVetoImitation(String actionType, ServerPlayerEntity bot) {
        if (actionType == null || bot == null) return false;

        World world = bot.getServerWorld();
        BlockPos pos = bot.getBlockPos();

        switch (actionType) {
            case "moveTo": {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (world.getBlockState(pos.add(dx, 0, dz)).isOf(Blocks.LAVA)) {
                            logVeto("模仿-moveTo", "目标位置附近有岩浆");
                            vetoImitationCount++;
                            return true;
                        }
                    }
                }
                break;
            }
            case "jump": {
                BlockPos below = pos.down();
                for (int dy = 1; dy <= FALL_DANGER_HEIGHT; dy++) {
                    if (world.getBlockState(below.down(dy)).isAir()) {
                        if (!world.getBlockState(below.down(dy + 1)).isAir()) {
                            logVeto("模仿-jump", "潜在坠落高度 > " + FALL_DANGER_HEIGHT);
                            vetoImitationCount++;
                            return true;
                        }
                    }
                }
                break;
            }
            case "attack": {
                LivingEntity target = findNearestAttackTarget(bot, 5);
                if (target instanceof VillagerEntity) {
                    logVeto("模仿-attack", "目标为村民，否决");
                    vetoImitationCount++;
                    return true;
                }
                break;
            }
        }

        return false;
    }

    public String getReport() {
        return String.format("共否决: 安全反射%d次 / 模仿%d次",
                vetoSafetyCount, vetoImitationCount);
    }

    private HostileEntity findNearestHostile(ServerPlayerEntity bot, int range) {
        World world = bot.getServerWorld();
        HostileEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entity : world.getEntitiesByClass(HostileEntity.class,
                bot.getBoundingBox().expand(range), e -> true)) {
            double dist = bot.squaredDistanceTo(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private LivingEntity findNearestAttackTarget(ServerPlayerEntity bot, int range) {
        World world = bot.getServerWorld();
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entity : world.getEntitiesByClass(LivingEntity.class,
                bot.getBoundingBox().expand(range), e -> e != bot)) {
            double dist = bot.squaredDistanceTo(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean hasSolidBarrier(ServerPlayerEntity bot, HostileEntity enemy) {
        BlockPos botPos = bot.getBlockPos();
        BlockPos enemyPos = enemy.getBlockPos();

        int dx = Integer.signum(enemyPos.getX() - botPos.getX());
        int dy = Integer.signum(enemyPos.getY() - botPos.getY());
        int dz = Integer.signum(enemyPos.getZ() - botPos.getZ());

        int steps = Math.max(Math.abs(enemyPos.getX() - botPos.getX()),
                Math.max(Math.abs(enemyPos.getY() - botPos.getY()),
                        Math.abs(enemyPos.getZ() - botPos.getZ())));

        if (steps <= 1) return false;

        World world = bot.getServerWorld();
        for (int i = 1; i < steps; i++) {
            BlockPos check = new BlockPos(
                    botPos.getX() + dx * i,
                    botPos.getY() + dy * i,
                    botPos.getZ() + dz * i);
            if (world.getBlockState(check).isSolidBlock(world, check)) {
                return true;
            }
        }
        return false;
    }

    private void logVeto(String category, String reason) {
        AIPlayerMod.LOGGER.info("[InhibitoryControl] 否决-{}: {}", category, reason);
    }
}
