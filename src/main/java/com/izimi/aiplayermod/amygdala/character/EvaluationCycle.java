package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.cortex.api.AIClient;
import com.izimi.aiplayermod.cortex.api.AIMessage;

import java.util.ArrayList;
import java.util.List;
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
    private static final long TAG_ANALYSIS_PERIOD_MS = 30L * 60 * 1000;
    private static final int BATCH_SUMMARIZE_THRESHOLD = 10;
    private static final int TASK_COMPLETION_THRESHOLD = 5;

    private final ThresholdConfig config;
    private final CharacterManager characterManager;
    private final List<String> pendingEvaluations = new ArrayList<>();
    private final PersonalityTag personalityTags;
    private int evalBatchCount = 0;
    private int taskCompletionCount = 0;
    private long lastTagAnalysis = 0;

    public EvaluationCycle(ThresholdConfig config, CharacterManager characterManager) {
        this.config = config;
        this.characterManager = characterManager;
        this.personalityTags = PersonalityTag.load();
        if (personalityTags.isEmpty()) {
            AIPlayerMod.LOGGER.info("[EvaluationCycle] 未找到已有性格标签，将在首次归纳后生成");
        } else {
            AIPlayerMod.LOGGER.info("[EvaluationCycle] 已加载性格标签: {}", personalityTags.format());
        }
    }

    public String checkMessage(String message) {
        if (message == null || message.isEmpty()) return null;

        if (!EVAL_PATTERN.matcher(message).find()) return null;

        pendingEvaluations.add(message);
        evalBatchCount++;

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

        if (evalBatchCount >= BATCH_SUMMARIZE_THRESHOLD) {
            tryBatchSummarize();
        }

        if (lastTagAnalysis == 0 || taskCompletionCount >= TASK_COMPLETION_THRESHOLD
                || (now - lastTagAnalysis >= TAG_ANALYSIS_PERIOD_MS && !personalityTags.isEmpty())) {
            triggerPersonalityAnalysis();
        }
    }

    public void onTaskCompleted() {
        taskCompletionCount++;
    }

    private void tryBatchSummarize() {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) {
            pendingEvaluations.clear();
            evalBatchCount = 0;
            return;
        }

        List<String> batch = new ArrayList<>(pendingEvaluations);
        pendingEvaluations.clear();
        evalBatchCount = 0;

        String joined = String.join("\n", batch);
        AIMessage msg = new AIMessage("system", """
                你是一个游戏角色的性格分析器。以下是玩家对你最近的评价列表。
                请用1-3个中文标签归纳这些评价反映的玩家对你的整体看法。
                只输出标签，用逗号分隔，不要其他内容。""");
        AIMessage userMsg = new AIMessage("user", "评价:\n" + joined);

        try {
            aiClient.sendMessage(List.of(msg, userMsg)).thenAccept(response -> {
                if (response != null && !response.isEmpty()) {
                    List<String> newTags = new ArrayList<>();
                    for (String tag : response.getMessage() != null ? response.getMessage().split("[,，]") : new String[0]) {
                        String trimmed = tag.trim();
                        if (!trimmed.isEmpty()) {
                            newTags.add(trimmed);
                            if (characterManager != null) {
                                characterManager.updatePreference(trimmed, 0.1, "批量评价归纳");
                            }
                        }
                    }
                    if (!newTags.isEmpty()) {
                        personalityTags.mergeTags(newTags, 0.1);
                        personalityTags.save();
                        AIPlayerMod.LOGGER.info("[EvaluationCycle] 批量API归纳完成: {}条评价 → tags={}",
                                batch.size(), personalityTags.format());
                    }
                }
            });
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[EvaluationCycle] 批量归纳失败: {}", e.getMessage());
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

    private void triggerPersonalityAnalysis() {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) {
            taskCompletionCount = 0;
            lastTagAnalysis = System.currentTimeMillis();
            return;
        }

        taskCompletionCount = 0;
        lastTagAnalysis = System.currentTimeMillis();

        String existingTags = personalityTags.isEmpty() ? "无" : personalityTags.format();
        AIMessage msg = new AIMessage("system", """
                你是一个游戏角色的性格分析器。根据角色的行为统计和现有标签，输出更新后的性格标签列表。
                规则：
                1. 如果某个标签不再符合当前行为模式，移除它。
                2. 如果出现了新的行为倾向，新增对应标签。
                3. 标签用中文，逗号分隔，最多5个。
                4. 只输出标签列表，不要其他内容。""");
        AIMessage userMsg = new AIMessage("user",
                "现有标签: " + existingTags
                + "\n已完成任务数(本轮): " + taskCompletionCount
                + "\n当前性格趋势: " + config.personalityTrend
                + "\n从众系数: " + String.format("%.2f", config.conformityCoefficient)
                + "\n稳定性: " + String.format("%.2f", config.stabilityFactor));

        try {
            aiClient.sendMessage(List.of(msg, userMsg)).thenAccept(response -> {
                if (response != null && !response.isEmpty() && response.getMessage() != null) {
                    List<String> newTags = new ArrayList<>();
                    for (String tag : response.getMessage().split("[,，]")) {
                        String trimmed = tag.trim();
                        if (!trimmed.isEmpty()) {
                            newTags.add(trimmed);
                        }
                    }
                    if (!newTags.isEmpty()) {
                        personalityTags.updateTags(newTags);
                        personalityTags.save();
                        AIPlayerMod.LOGGER.info("[EvaluationCycle] 性格标签分析完成: {}",
                                personalityTags.format());
                    }
                }
            });
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[EvaluationCycle] 性格标签分析失败: {}", e.getMessage());
        }
    }

    public PersonalityTag getPersonalityTags() {
        return personalityTags;
    }
}
