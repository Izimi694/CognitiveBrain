package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.hormonal.HormonalSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

import java.util.List;

public class UrgencyClassifier {

    public enum UrgencyLabel {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW,
        OBSERVE
    }

    private static final double THREAT_WEIGHT = 0.6;
    private static final double TIME_WEIGHT = 0.4;
    private static final int TIME_MAX_TICKS = 600;
    private static final double TIME_FULL_ESCALATION = 1.0;

    public UrgencyLabel classify(HormonalSystem hormones, ServerPlayerEntity bot, int ticksInState) {
        double urgency = computeUrgency(hormones, bot, ticksInState);
        return labelFromUrgency(urgency);
    }

    public static UrgencyLabel labelFromUrgency(double urgency) {
        if (urgency > 0.85) return UrgencyLabel.CRITICAL;
        if (urgency > 0.65) return UrgencyLabel.HIGH;
        if (urgency > 0.35) return UrgencyLabel.NORMAL;
        if (urgency > 0.15) return UrgencyLabel.LOW;
        return UrgencyLabel.OBSERVE;
    }

    public double computeUrgency(HormonalSystem hormones, ServerPlayerEntity bot, int ticksInState) {
        double threatProximity = computeThreatProximity(hormones, bot);
        double timePressure = Math.min(TIME_FULL_ESCALATION, (double) ticksInState / TIME_MAX_TICKS);
        double urgency = threatProximity * THREAT_WEIGHT + timePressure * TIME_WEIGHT;
        return Math.min(1.0, Math.max(0.0, urgency));
    }

    public double computeThreatProximity(HormonalSystem hormones, ServerPlayerEntity bot) {
        double stimulus = computeStimulus(hormones, bot);
        double inhibition = computeInhibition(hormones, bot);

        if (Math.abs(inhibition) < 0.001) return stimulus > 0.1 ? 1.0 : 0.0;

        double ratio = stimulus / Math.max(0.01, inhibition);
        return Math.min(1.0, Math.max(0.0, ratio));
    }

    private double computeStimulus(HormonalSystem hormones, ServerPlayerEntity bot) {
        double aggression = hormones != null ? hormones.getAggression() : 0;
        double curiosity = hormones != null ? hormones.getCuriosity() : 0;
        double intimacy = hormones != null ? hormones.getIntimacy(bot != null ? bot.getUuid() : null) : 0;

        double score = 0;
        score += aggression * 0.4;
        score += curiosity * 0.3;
        score += intimacy * 0.1;

        if (bot != null) {
            ServerWorld world = bot.getServerWorld();
            if (world != null) {
                boolean hasLava = findBlockNearby(world, bot.getBlockPos(), Blocks.LAVA, 3);
                if (hasLava) score += 0.8;
            }
        }

        return score;
    }

    private double computeInhibition(HormonalSystem hormones, ServerPlayerEntity bot) {
        double stress = hormones != null ? hormones.getStress() : 0;

        double score = 0;
        score += stress * 0.5;

        if (bot != null) {
            double health = bot.getHealth();
            if (health < 4) score += 0.8;
            else if (health < 8) score += 0.4;

            ServerWorld world = bot.getServerWorld();
            if (world != null) {
                List<HostileEntity> hostiles = world.getEntitiesByClass(
                        HostileEntity.class,
                        bot.getBoundingBox().expand(8),
                        e -> e.isAlive()
                );
                if (!hostiles.isEmpty()) score += 0.3;

                boolean hasVoid = bot.getY() < -10;
                if (hasVoid) score += 0.9;
            }
        }

        return score;
    }

    private boolean findBlockNearby(ServerWorld world, BlockPos origin, net.minecraft.block.Block block, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (world.getBlockState(origin.add(dx, dy, dz)).isOf(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
