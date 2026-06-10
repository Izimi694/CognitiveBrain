package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

public class BotParams {
    private double alpha;
    private double beta;
    private double temperature;
    private long generatedAt;
    private int generation = 0;
    private UUID parentId;
    private UUID botId;

    public BotParams() {}

    public BotParams(double alpha, double beta, double temperature) {
        this.alpha = alpha;
        this.beta = beta;
        this.temperature = temperature;
        this.generatedAt = System.currentTimeMillis();
    }

    public static BotParams load() {
        BotParams params = JsonUtil.readFromFileSafe(FileUtil.getBotParamsPath(), BotParams.class);
        if (params == null || params.alpha <= 0) {
            params = generate();
            params.save();
        }
        if (params.temperature <= 0) {
            params.temperature = 0.4;
        }
        return params;
    }

    public static BotParams generate() {
        Random rng = new Random();
        double alpha = clampNormal(0.3, 0.1, rng, 0.1, 0.6);
        double beta = clampNormal(0.01, 0.005, rng, 0.002, 0.03);
        double baseTemp = clampNormal(0.4, 0.15, rng, 0.2, 0.7);
        double betaFactor = 1.0 - (beta * 5);
        double temperature = Math.max(0.15, Math.min(0.8, baseTemp * betaFactor));
        BotParams p = new BotParams(alpha, beta, temperature);
        p.generation = 0;
        return p;
    }

    // ── Phase 7: Three-rule inheritance ──

    public static BotParams inherit(BotParams parent) {
        return inherit(parent, null);
    }

    public static BotParams inherit(BotParams p1, BotParams p2) {
        Random rng = new Random();

        // Rule 1: Intersection average
        double alpha = p2 != null ? (p1.alpha + p2.alpha) / 2 : p1.alpha;
        double beta = p2 != null ? (p1.beta + p2.beta) / 2 : p1.beta;
        double temp = p2 != null ? (p1.temperature + p2.temperature) / 2 : p1.temperature;

        // Rule 2: Random halving
        if (rng.nextBoolean()) alpha /= 2;
        if (rng.nextBoolean()) beta /= 2;
        if (rng.nextBoolean()) temp /= 2;

        // Rule 3: Mutation (small gaussian noise)
        alpha += rng.nextGaussian() * 0.05;
        beta += rng.nextGaussian() * 0.003;
        temp += rng.nextGaussian() * 0.08;

        alpha = Math.max(0.1, Math.min(0.6, alpha));
        beta = Math.max(0.002, Math.min(0.03, beta));
        temp = Math.max(0.15, Math.min(0.8, temp));

        BotParams child = new BotParams(alpha, beta, temp);
        child.generation = (p2 != null
                ? Math.max(p1.generation, p2.generation)
                : p1.generation) + 1;
        child.parentId = p1.botId != null ? p1.botId : p1.getUUID();
        return child;
    }

    // ── Persistence ──

    public void save() {
        JsonUtil.writeToFileSafeAtomic(FileUtil.getBotParamsPath(), this);
    }

    public void saveToPath(Path path) {
        JsonUtil.writeToFileSafeAtomic(path, this);
    }

    public static BotParams loadFromPath(Path path) {
        return JsonUtil.readFromFileSafe(path, BotParams.class);
    }

    // ── Setters (for genome restore) ──

    public BotParams withBotId(UUID id) { this.botId = id; return this; }
    public BotParams withGeneration(int gen) { this.generation = gen; return this; }
    public BotParams withParentId(UUID id) { this.parentId = id; return this; }

    // ── Getters ──

    public double getAlpha() { return alpha; }
    public double getBeta() { return beta; }
    public double getTemperature() { return temperature; }
    public long getGeneratedAt() { return generatedAt; }
    public int getGeneration() { return generation; }
    public UUID getParentId() { return parentId; }
    public UUID getBotId() { return botId; }

    public UUID getUUID() {
        if (botId != null) return botId;
        // Deterministic UUID from params for legacy compatibility
        return UUID.nameUUIDFromBytes((alpha + "," + beta + "," + temperature).getBytes());
    }

    // ── Internal ──

    private static double clampNormal(double mean, double std, Random rng, double min, double max) {
        double val = mean + rng.nextGaussian() * std;
        return Math.max(min, Math.min(max, val));
    }
}
