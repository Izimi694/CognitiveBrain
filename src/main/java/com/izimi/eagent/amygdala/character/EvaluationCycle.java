package com.izimi.eagent.amygdala.character;

import com.izimi.eagent.amygdala.ConditionedReflex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.cortex.api.AIClient;
import com.izimi.eagent.cortex.api.AIMessage;
import com.izimi.eagent.cortex.api.AIResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EvaluationCycle {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final Pattern EVAL_PATTERN = Pattern.compile(
            "(?:你[很太真假好笨]|你真|你有点|你是个|你是)[^，。！？\n]{1,15}"
    );

    private static final int BATCH_THRESHOLD = 10;
    private static final long BATCH_INTERVAL_MS = 30L * 60 * 1000;

    private final ConditionedReflex conditionedReflex;
    private final AIClient aiClient;
    private final List<String> pendingEvaluations = new ArrayList<>();
    private long lastBatchTime = 0;

    public EvaluationCycle(ConditionedReflex conditionedReflex, AIClient aiClient) {
        this.conditionedReflex = conditionedReflex;
        this.aiClient = aiClient;
    }

    public String checkMessage(String message) {
        if (message == null || message.isEmpty()) return null;
        if (!EVAL_PATTERN.matcher(message).find()) return null;

        pendingEvaluations.add(message);
        return "evaluation";
    }

    public void onTick() {
        long now = System.currentTimeMillis();
        if (pendingEvaluations.size() >= BATCH_THRESHOLD ||
                (!pendingEvaluations.isEmpty() && now - lastBatchTime >= BATCH_INTERVAL_MS)) {
            batchReinforce();
        }
    }

    private void batchReinforce() {
        if (aiClient == null || !aiClient.isConfigured()) {
            pendingEvaluations.clear();
            return;
        }

        List<String> batch = new ArrayList<>(pendingEvaluations);
        pendingEvaluations.clear();
        lastBatchTime = System.currentTimeMillis();

        String joined = String.join("\n", batch);
        String reflexContext = conditionedReflex.getLastReflexSummary();

        AIMessage systemMsg = new AIMessage("system", """
                你是一个游戏角色的行为强化系统。以下是玩家对bot最近的评价列表。
                判断每条评价是鼓励还是批评哪种行为。
                如果评价明确指向bot最近执行的反射，请直接使用完整的reflexId。
                输出JSON数组格式: [{"reflexId":"reflex_dig_iron_ore","delta":0.15}, ...]
                delta范围[-0.2, 0.2]。正面评价正delta，负面评价负delta。
                如果无明确指向，输出{"reflexId":"","delta":0}。
                只输出JSON数组，不要其他内容。""");
        AIMessage userMsg = new AIMessage("user",
                "评价:\n" + joined + "\n\n" + reflexContext);

        try {
            aiClient.sendMessage(List.of(systemMsg, userMsg))
                    .thenAccept(this::processBatchResponse);
        } catch (Exception e) {
            LOGGER.warn("[EvaluationCycle] 批量强化失败: {}", e.getMessage());
        }
    }

    private void processBatchResponse(AIResponse response) {
        if (response == null || response.getMessage() == null || response.getMessage().isEmpty()) return;
        try {
            var array = new com.google.gson.Gson().fromJson(response.getMessage(), com.google.gson.JsonArray.class);
            if (array == null) return;
            for (int i = 0; i < array.size(); i++) {
                applyEvaluationEntry(array.get(i).getAsJsonObject());
            }
        } catch (Exception e) {
            LOGGER.warn("[EvaluationCycle] JSON解析失败: {}", e.getMessage());
        }
    }

    private void applyEvaluationEntry(com.google.gson.JsonObject obj) {
        String hint = resolveReflexHint(obj);
        double delta = obj.has("delta") ? obj.get("delta").getAsDouble() : 0;
        if (hint.isEmpty() || delta == 0) return;
        String matchedId = conditionedReflex.matchReflexIdByHint(hint);
        if (matchedId != null) {
            conditionedReflex.reinforceWithSpill(matchedId, delta);
        }
    }

    private String resolveReflexHint(com.google.gson.JsonObject obj) {
        if (obj.has("reflexId") && !obj.get("reflexId").isJsonNull()) {
            return obj.get("reflexId").getAsString();
        }
        if (obj.has("reflexHint") && !obj.get("reflexHint").isJsonNull()) {
            return obj.get("reflexHint").getAsString();
        }
        return "";
    }
}
