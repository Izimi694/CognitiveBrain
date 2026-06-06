package com.izimi.aiplayermod.character;

import com.izimi.aiplayermod.AIPlayerMod;

import java.util.regex.Pattern;

public class EvaluationCycle {

    private static final Pattern EVAL_PATTERN = Pattern.compile(
            "(?:你[很太真假好笨]|你真|你有点|你是个|你是)[^，。！？\n]{1,15}"
    );

    private static final Pattern POSITIVE_PATTERN = Pattern.compile(
            ".*(?:好|棒|厉害|聪明|牛|强|不错|靠谱).*"
    );

    private static final Pattern NEGATIVE_PATTERN = Pattern.compile(
            ".*(?:笨|蠢|傻|差|弱|没用|废物|垃圾|不行|拉胯).*"
    );

    private static final long EVAL_PERIOD_MS = 30L * 24 * 3600 * 1000;

    private final ThresholdConfig config;
    private final CharacterManager characterManager;

    public EvaluationCycle(ThresholdConfig config, CharacterManager characterManager) {
        this.config = config;
        this.characterManager = characterManager;
    }

    public String checkMessage(String message) {
        if (message == null || message.isEmpty()) return null;

        if (!EVAL_PATTERN.matcher(message).find()) return null;

        if (POSITIVE_PATTERN.matcher(message).find()) {
            recordFeedback(true);
            return "positive";
        }

        if (NEGATIVE_PATTERN.matcher(message).find()) {
            recordFeedback(false);
            return "negative";
        }

        return null;
    }

    public void onTick() {
        if (config.lastEvaluation == 0) {
            config.lastEvaluation = System.currentTimeMillis();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - config.lastEvaluation >= EVAL_PERIOD_MS) {
            performEvaluation();
            config.lastEvaluation = now;
            config.save();
        }
    }

    private void recordFeedback(boolean positive) {
        if (positive) {
            config.positiveFeedbackCount++;
        } else {
            config.negativeFeedbackCount++;
            config.conformityCoefficient = Math.min(1.0,
                    config.conformityCoefficient + 0.02);
            config.markAdjusted();

            if (config.consecutiveNegativeMonths >= 2) {
                AIPlayerMod.LOGGER.info("[EvaluationCycle] 持续差评 → 从众倾向上升: {:.2f}",
                        config.conformityCoefficient);
            }
        }
        config.save();
    }

    private void performEvaluation() {
        int neg = config.negativeFeedbackCount;
        int pos = config.positiveFeedbackCount;

        AIPlayerMod.LOGGER.info("[EvaluationCycle] 月度评估: 好评{}次, 差评{}次, 从众系数={:.2f}",
                pos, neg, config.conformityCoefficient);

        if (neg > pos) {
            config.consecutiveNegativeMonths++;
            config.consecutivePositiveMonths = 0;

            double increase = config.consecutiveNegativeMonths >= 3 ? 0.15 : 0.10;
            config.conformityCoefficient = Math.min(1.0, config.conformityCoefficient + increase);
            config.stabilityFactor = Math.min(1.0, config.stabilityFactor + 0.02);
            config.personalityTrend = "declining";

            if (characterManager != null) {
                characterManager.updatePreference("友善", -0.1, "月度评估");
                characterManager.updatePreference("冷漠", 0.05, "月度评估");
            }

            if (config.consecutiveNegativeMonths >= 3) {
                AIPlayerMod.LOGGER.warn("[EvaluationCycle] 连续3月差评! 从众倾向激增: {:.2f}",
                        config.conformityCoefficient);
            }

        } else if (pos > neg) {
            config.consecutivePositiveMonths++;
            config.consecutiveNegativeMonths = 0;
            config.personalityTrend = "improving";

            if (config.consecutivePositiveMonths >= 3) {
                config.conformityCoefficient = Math.max(0.0,
                        config.conformityCoefficient - 0.05);
                AIPlayerMod.LOGGER.info("[EvaluationCycle] 持续3月好评，从众倾向缓慢恢复: {:.2f}",
                        config.conformityCoefficient);
            }

            if (characterManager != null) {
                characterManager.updatePreference("友善", 0.05, "月度评估");
            }

        } else {
            config.stabilityFactor = Math.min(1.0, config.stabilityFactor + 0.01);
            config.personalityTrend = "stable";
        }

        config.negativeFeedbackCount = 0;
        config.positiveFeedbackCount = 0;
        config.markAdjusted();
        config.save();

        AIPlayerMod.LOGGER.info("[EvaluationCycle] 评估完成: trend={}, conformity={:.2f}, stability={:.2f}",
                config.personalityTrend, config.conformityCoefficient, config.stabilityFactor);
    }
}
