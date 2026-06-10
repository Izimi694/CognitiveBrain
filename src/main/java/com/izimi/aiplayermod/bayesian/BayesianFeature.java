package com.izimi.aiplayermod.bayesian;

public class BayesianFeature {
    private final String key;
    private final boolean value;
    private final double confidence;

    public BayesianFeature(String key, boolean value) {
        this(key, value, 1.0);
    }

    public BayesianFeature(String key, boolean value, double confidence) {
        this.key = key;
        this.value = value;
        this.confidence = Math.max(0, Math.min(1, confidence));
    }

    public String key() { return key; }
    public boolean value() { return value; }
    public double confidence() { return confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BayesianFeature that)) return false;
        return value == that.value && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + Boolean.hashCode(value);
    }

    @Override
    public String toString() {
        return key + "=" + value + " (conf=" + String.format("%.2f", confidence) + ")";
    }
}
