package com.izimi.aiplayermod.character;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;

public class ThresholdConfig {

    public double conformityCoefficient = 0.3;
    public double stabilityFactor = 0.1;
    public double minConfidence = 0.6;
    public int minObservations = 3;
    public int evaluationPeriodDays = 30;
    public long lastEvaluation = 0;
    public int negativeFeedbackCount = 0;
    public int positiveFeedbackCount = 0;
    public int consecutivePositiveMonths = 0;
    public int consecutiveNegativeMonths = 0;
    public String personalityTrend = "stable";
    public long lastAdjusted = 0;

    public double getEffectiveThreshold() {
        return minConfidence * (1.0 - conformityCoefficient * 0.5);
    }

    public void markAdjusted() {
        lastAdjusted = System.currentTimeMillis();
    }

    public static ThresholdConfig load() {
        ThresholdConfig config = JsonUtil.readFromFileSafe(getPath(), ThresholdConfig.class);
        if (config == null) {
            config = new ThresholdConfig();
            config.save();
        }
        return config;
    }

    public void save() {
        JsonUtil.writeToFileSafe(getPath(), this);
    }

    public static Path getPath() {
        return FileUtil.getThresholdsDir().resolve("thresholds.json");
    }
}
