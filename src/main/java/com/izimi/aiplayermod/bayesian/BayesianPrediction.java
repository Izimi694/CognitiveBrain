package com.izimi.aiplayermod.bayesian;

import java.util.Collections;
import java.util.List;

public class BayesianPrediction {
    private final String reflexId;
    private final double successProbability;
    private final List<FeatureContribution> contributions;

    public BayesianPrediction(String reflexId, double successProbability, List<FeatureContribution> contributions) {
        this.reflexId = reflexId;
        this.successProbability = Math.max(0, Math.min(1, successProbability));
        this.contributions = contributions != null
                ? Collections.unmodifiableList(contributions)
                : Collections.emptyList();
    }

    public String reflexId() { return reflexId; }
    public double successProbability() { return successProbability; }
    public List<FeatureContribution> contributions() { return contributions; }

    public boolean isRecommended() {
        return successProbability > 0.5;
    }

    public record FeatureContribution(String featureKey, boolean featureValue, double impact) {
        public String summary() {
            return String.format("%s=%s (%.2f)", featureKey, featureValue, impact);
        }
    }

    @Override
    public String toString() {
        return String.format("BayesianPrediction{reflexId='%s', prob=%.3f, contributions=%d}",
                reflexId, successProbability, contributions.size());
    }
}
