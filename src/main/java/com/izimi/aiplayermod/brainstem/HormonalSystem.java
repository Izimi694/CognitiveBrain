package com.izimi.aiplayermod.brainstem;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class HormonalSystem {

    private static final double STRESS_DECAY = 0.001;
    private static final double AGGRESSION_DECAY = 0.002;
    private static final double CURIOSITY_DECAY_PER_TICK = 0.000019;
    private static final double CLAMP_MIN = 0.0;
    private static final double CLAMP_MAX = 1.0;
    private static final double NOVELTY_BOOST = 0.3;
    private static final double NOVELTY_CAP = 0.95;
    private static final double FAMILIAR_SUPPRESS = 0.95;
    private static final double STRESS_SUPPRESS_FACTOR = 1.5;

    private double stress;
    private double aggression;
    private double curiosity;
    private final Map<UUID, Double> intimacy = new HashMap<>();

    public HormonalSystem() {
        this.stress = 0.1;
        this.aggression = 0.2;
        this.curiosity = 0.3;
    }

    public HormonalSystem(double initialStress, double initialAggression, double initialCuriosity) {
        this.stress = clamp(initialStress);
        this.aggression = clamp(initialAggression);
        this.curiosity = clamp(initialCuriosity);
    }

    public void onTakeDamage()          { stress = clamp(stress + 0.1); }
    public void onCombatWin()           { aggression = clamp(aggression + 0.05); stress = max(stress - 0.02, 0); }
    public void onCombatLoss()          { stress = clamp(stress + 0.15); aggression = clamp(aggression - 0.1); }
    public void onNovelDiscovery()      { curiosity = Math.min(NOVELTY_CAP, curiosity + NOVELTY_BOOST); }
    public void onEnterNewBiome()       { curiosity = Math.min(NOVELTY_CAP, curiosity + 0.2); }
    public void onTaskSuccess()         { stress = max(stress - 0.02, 0); }

    public void onPlayerPraise(UUID playerId) {
        intimacy.merge(playerId, 0.05, Math::min);
        stress = max(stress - 0.02, 0);
    }

    public void onPlayerCriticize(UUID playerId) {
        intimacy.merge(playerId, -0.03, Double::sum);
        intimacy.put(playerId, max(intimacy.getOrDefault(playerId, 0.5), 0.0));
        stress = clamp(stress + 0.03);
    }

    public void onPlayerNearby(UUID playerId) {
        intimacy.putIfAbsent(playerId, 0.5);
    }

    public double getIntimacy(UUID playerId) {
        return intimacy.getOrDefault(playerId, 0.5);
    }

    public void tick() {
        stress     = max(stress - STRESS_DECAY, 0);
        aggression = max(aggression - AGGRESSION_DECAY, 0);
        curiosity  = curiosity * (1 - CURIOSITY_DECAY_PER_TICK);
        curiosity  = Math.max(0, Math.min(NOVELTY_CAP, curiosity));
    }

    public void tickWithFamiliarity(boolean familiar) {
        tick();
        if (familiar) {
            curiosity *= FAMILIAR_SUPPRESS;
        }
    }

    public double getCuriosityThreshold(double beta, double stress) {
        double threshold = 0.5 + beta * 10;
        if (stress > 0.5) threshold *= STRESS_SUPPRESS_FACTOR;
        return Math.min(1.0, threshold);
    }

    public double getStress()     { return stress; }
    public double getAggression() { return aggression; }
    public double getCuriosity()  { return curiosity; }
    public Map<UUID, Double> getIntimacyMap() { return new HashMap<>(intimacy); }

    public String summary() {
        return String.format("stress=%.2f aggression=%.2f curiosity=%.2f intimacy=%d players",
                stress, aggression, curiosity, intimacy.size());
    }

    private static double clamp(double val) {
        return Math.max(CLAMP_MIN, Math.min(CLAMP_MAX, val));
    }

    private static double max(double a, double b) {
        return Math.max(a, b);
    }

    public HormonalState snapshot() {
        return new HormonalState(stress, aggression, curiosity, new HashMap<>(intimacy));
    }

    public record HormonalState(double stress, double aggression, double curiosity, Map<UUID, Double> intimacy) {}
}
