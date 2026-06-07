package com.izimi.aiplayermod.amygdala.character;

public class Preference {
    public String target;
    public double valence;
    public String origin;
    public int reinforcementCount;

    public Preference() {}

    public Preference(String target, double valence, String origin) {
        this.target = target;
        this.valence = Math.max(-1.0, Math.min(1.0, valence));
        this.origin = origin;
        this.reinforcementCount = 1;
    }
}
