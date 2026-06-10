package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.api.BotContext;
import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

public class MotivationEngine {

    private static final double W_SURVIVAL = 1.0;
    private static final double W_STRESS = 0.8;
    private static final double W_FEAR = 0.6;
    private static final double W_TASK_PROG = 0.7;
    private static final double W_TASK_BASE = 0.3;
    private static final double W_SOCIAL = 0.5;
    private static final double W_LONELY = 0.4;
    private static final double W_CAUTIOUS = 0.5;
    private static final double W_RECENT_FAIL = 0.6;
    private static final double W_STRESS_CAUTIOUS = 0.3;

    private static final double CROSS_INHIBITION_RATIO = 0.7;
    private static final int INHIBITION_WINDOW = 5;
    private static final double EXPLORE_THRESHOLD = 1.0 / Math.E;

    public static boolean shouldExplore(HormonalSystem hormones) {
        if (hormones == null) return false;
        double maxCuriosity = 0.95;
        double exploreBias = hormones.getCuriosity() / maxCuriosity;
        return exploreBias > EXPLORE_THRESHOLD;
    }

    private final Random rng = new Random();
    private Perspective lastWinner = null;
    private int inhibitionTicks = 0;

    public DriveState computeDrives(BotContext ctx, WorldContext world, ServerPlayerEntity bot) {
        HormonalSystem h = ctx.hormonalSystem();
        BotParams params = ctx.botParams();

        double survivalUrgency = computeSurvivalDrive(ctx, world, bot, h);
        double taskUrgency = computeTaskDrive(ctx, bot);
        double socialUrgency = computeSocialDrive(ctx, world, h);
        double curiosityUrgency = computeCuriosityDrive(ctx, h);
        double cautiousUrgency = computeCautiousDrive(ctx, h, params);

        return new DriveState(survivalUrgency, taskUrgency, socialUrgency, curiosityUrgency, cautiousUrgency);
    }

    public Perspective select(BotContext ctx, DriveState drives) {
        Perspective[] values = Perspective.values();
        double[] activations = new double[values.length];

        for (int i = 0; i < values.length; i++) {
            activations[i] = drives.get(values[i]);
            if (lastWinner != null && inhibitionTicks > 0 && values[i] != lastWinner) {
                activations[i] *= CROSS_INHIBITION_RATIO;
            }
        }

        double temperature = Math.max(0.05, ctx.botParams().getTemperature());
        Perspective winner = boltzmannSample(activations, values, temperature);

        if (lastWinner == winner) {
            inhibitionTicks = inhibitionTicks > 0 ? inhibitionTicks - 1 : 0;
        } else {
            lastWinner = winner;
            inhibitionTicks = INHIBITION_WINDOW;
        }

        return winner;
    }

    private Perspective boltzmannSample(double[] activations, Perspective[] values, double temperature) {
        double maxActivation = 0;
        for (double a : activations) {
            if (a > maxActivation) maxActivation = a;
        }

        double[] probs = new double[activations.length];
        double sum = 0;
        for (int i = 0; i < activations.length; i++) {
            probs[i] = Math.exp((activations[i] - maxActivation) / temperature);
            sum += probs[i];
        }

        double rand = rng.nextDouble() * sum;
        double cumulative = 0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (rand <= cumulative) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private double computeSurvivalDrive(BotContext ctx, WorldContext world, ServerPlayerEntity bot, HormonalSystem h) {
        double drive = 0;
        if (bot != null) {
            double healthRatio = 1.0 - (bot.getHealth() / bot.getMaxHealth());
            drive += W_SURVIVAL * healthRatio;
            if (h.getStress() > 0.3) drive += W_STRESS * h.getStress();
            if (ctx.alarmSystem() != null && ctx.alarmSystem().hasThreatMatchNearby(bot)) drive += W_FEAR;
            InnateReflexRegistry innate = world.brainstem().innateReflexes();
            if (innate != null && innate.highest(bot, 0) != null) drive += 0.4;
        }
        if (h.getStress() > 0.7) drive += 0.3;
        return Math.min(1.0, drive);
    }

    private double computeTaskDrive(BotContext ctx, ServerPlayerEntity bot) {
        double drive = 0;
        var tm = ctx.taskManager();
        Task task = tm != null ? tm.getActiveTask() : null;
        if (task != null && "running".equals(task.getStatus())) {
            drive += W_TASK_BASE;
            double prog = (double) task.progress.completedCount / Math.max(1, task.progress.targetCount);
            drive += W_TASK_PROG * prog;
        }
        if (ctx.conditionedReflex() != null && ctx.conditionedReflex().getHighestProficiency() >= 0.8) {
            drive += 0.2;
        }
        return Math.min(1.0, drive);
    }

    private double computeSocialDrive(BotContext ctx, WorldContext world, HormonalSystem h) {
        double drive = 0;
        var social = world.amygdala().socialObserver();
        if (social != null && social.getNearbyPlayerCount() > 1) {
            drive += W_SOCIAL;
        }
        double avgIntimacy = 0;
        int cnt = 0;
        for (double v : h.getIntimacyMap().values()) {
            avgIntimacy += v;
            cnt++;
        }
        if (cnt > 0) avgIntimacy /= cnt;
        double loneliness = 1.0 - Math.min(1.0, avgIntimacy);
        if (loneliness > 0.3) drive += W_LONELY * loneliness;
        return Math.min(1.0, drive);
    }

    private double computeCuriosityDrive(BotContext ctx, HormonalSystem h) {
        double curiosity = h.getCuriosity();
        double threshold = h.getCuriosityThreshold(ctx.botParams().getBeta(), h.getStress());
        if (curiosity > threshold) {
            return Math.min(1.0, curiosity);
        }
        return curiosity * 0.3;
    }

    private double computeCautiousDrive(BotContext ctx, HormonalSystem h, BotParams params) {
        double drive = 0;
        drive += W_CAUTIOUS * params.getBeta() * 10;
        var conditioned = ctx.conditionedReflex();
        if (conditioned != null) {
            int fails = conditioned.getConsecutiveFailures();
            if (fails >= 2) drive += W_RECENT_FAIL * Math.min(1.0, fails * 0.2);
        }
        drive += W_STRESS_CAUTIOUS * h.getStress();
        return Math.min(1.0, drive);
    }
}
