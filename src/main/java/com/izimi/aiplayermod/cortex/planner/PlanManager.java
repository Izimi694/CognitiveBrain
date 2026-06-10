package com.izimi.aiplayermod.cortex.planner;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.cortex.api.AITaskPlanner;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class PlanManager {

    private Plan activePlan;
    private final AITaskPlanner aiTaskPlanner;
    private final UUID botId;

    // Legacy constructor (no UUID → global path)
    public PlanManager(AITaskPlanner aiTaskPlanner) {
        this(aiTaskPlanner, null);
    }

    public PlanManager(AITaskPlanner aiTaskPlanner, UUID botId) {
        this.aiTaskPlanner = aiTaskPlanner;
        this.botId = botId;
        this.activePlan = loadActivePlan();
    }

    public Plan getActivePlan() {
        return activePlan;
    }

    public Plan createPlan(String taskId, String goal) {
        activePlan = new Plan(taskId, goal);

        int count = Task.extractCount(goal);
        String action = Task.extractAction(goal);
        String target = Task.extractTarget(goal);

        for (int i = 0; i < count; i++) {
            activePlan.subSteps.add(new Plan.PlanStep(action, action, target));
        }
        activePlan.status = "active";
        activePlan.source = "local";

        saveActivePlan();

        AIPlayerMod.LOGGER.info("[PlanManager] 本地计划创建: {} → {}×{}", goal, count, action);

        if (aiTaskPlanner != null) {
            tryEnrichFromAPI(taskId, goal);
        }

        return activePlan;
    }

    @SuppressWarnings("deprecation")
    private void tryEnrichFromAPI(String taskId, String goal) {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) {
            AIPlayerMod.LOGGER.info("[PlanManager] API不可用，使用本地计划");
            return;
        }

        aiTaskPlanner.planTask(goal,
                botId != null ? AIPlayerMod.getStateManager(botId).loadState() : AIPlayerMod.getStateManager().loadState(),
                null,
                botId != null ? AIPlayerMod.getMemoryManager(botId).getRecentMemories() : AIPlayerMod.getMemoryManager().getRecentMemories(),
                null);

        AIPlayerMod.LOGGER.info("[PlanManager] 已请求API计划富化: {}", goal);
    }

    public void integrateAIResult(Task enrichedTask) {
        if (enrichedTask == null || activePlan == null) return;

        if (enrichedTask.subTasks != null && !enrichedTask.subTasks.isEmpty()) {
            activePlan.subSteps.clear();
            for (Task.SubTask st : enrichedTask.subTasks) {
                Plan.PlanStep step = new Plan.PlanStep();
                step.skillId = st.skillId;
                step.action = st.skillId;
                step.target = st.goal;
                step.status = "pending";
                activePlan.subSteps.add(step);
            }
            activePlan.source = "api";
            AIPlayerMod.LOGGER.info("[PlanManager] API计划已集成: {} → {}子步骤",
                    activePlan.goal, activePlan.subSteps.size());
        }

        saveActivePlan();
    }

    public void markStepComplete(int stepIndex) {
        if (activePlan == null) return;
        if (stepIndex >= 0 && stepIndex < activePlan.subSteps.size()) {
            activePlan.subSteps.get(stepIndex).status = "completed";
        }
        activePlan.advanceToNextStep();
        saveActivePlan();
    }

    public void markStepSkipped(int stepIndex) {
        if (activePlan == null) return;
        if (stepIndex >= 0 && stepIndex < activePlan.subSteps.size()) {
            activePlan.subSteps.get(stepIndex).status = "skipped";
        }
        activePlan.advanceToNextStep();
        saveActivePlan();
    }

    public void completePlan() {
        if (activePlan != null) {
            activePlan.status = "completed";
            saveActivePlan();
            AIPlayerMod.LOGGER.info("[PlanManager] 计划完成: {}", activePlan.goal);
        }
        activePlan = null;
        deleteActivePlan();
    }

    public void cancelPlan() {
        if (activePlan != null) {
            activePlan.status = "cancelled";
            saveActivePlan();

            Path backup = getActivePlanPath().resolveSibling("last_plan_" + activePlan.taskId + ".json");
            saveTo(backup);

            AIPlayerMod.LOGGER.info("[PlanManager] 计划取消: {}", activePlan.goal);
        }
        activePlan = null;
        deleteActivePlan();
    }

    private void saveActivePlan() {
        if (activePlan != null) {
            saveTo(getActivePlanPath());
        }
    }

    private void saveTo(Path path) {
        if (activePlan != null) {
            JsonUtil.writeToFileSafeAtomic(path, activePlan.toMap());
        }
    }

    private void deleteActivePlan() {
        FileUtil.deleteIfExists(getActivePlanPath());
    }

    private Plan loadActivePlan() {
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(getActivePlanPath());
        if (data == null) return null;
        return Plan.fromMap(data);
    }

    private Path getActivePlanPath() {
        if (botId != null) {
            return FileUtil.getBotPlansDir(botId).resolve("active_plan.json");
        }
        return FileUtil.getPlansDir().resolve("active_plan.json");
    }
}
