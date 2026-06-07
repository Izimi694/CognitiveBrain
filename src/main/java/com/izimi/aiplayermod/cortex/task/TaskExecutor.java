package com.izimi.aiplayermod.cortex.task;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.log.ExecutionLogger;
import com.izimi.aiplayermod.brainstem.skill.Skill;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.state.StateManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class TaskExecutor {
    private final TaskManager taskManager;
    private final SkillManager skillManager;
    private final StateManager stateManager;
    private final ExecutionLogger executionLogger;

    private int executionTick = 0;
    private int retryCount = 0;
    private static final int SKILL_TIMEOUT_TICKS = 6000;

    public TaskExecutor(TaskManager taskManager, SkillManager skillManager,
                        StateManager stateManager, ExecutionLogger executionLogger) {
        this.taskManager = taskManager;
        this.skillManager = skillManager;
        this.stateManager = stateManager;
        this.executionLogger = executionLogger;
    }

    public void executeTask(ServerPlayerEntity bot, Task task) {
        if (bot == null || task == null) return;

        executionTick++;

        if (executionTick > SKILL_TIMEOUT_TICKS) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 任务超时: {}", task.getGoal());
            taskManager.cancelActiveTask();
            executionTick = 0;
            return;
        }

        Task.SubTask current = getCurrentSubTask(task);
        if (current == null) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 无待处理子任务: {}", task.getGoal());
            taskManager.completeTask();
            executionTick = 0;
            return;
        }

        Skill skill = skillManager.getSkill(current.skillId);
        if (skill == null) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 技能未找到: {}", current.skillId);
            current.status = "skipped";
            return;
        }

        Map<String, Object> context = new HashMap<>();
        context.put("goal", current.goal);
        context.put("task", task);

        try {
            if (skill.canExecute(bot.getServerWorld(), bot, context)) {
                Skill.SkillResult result = skill.execute(bot.getServerWorld(), bot, context);

                executionLogger.logAction(
                        skill.getSkillId(),
                        context,
                        result.success() ? "success" : "fail",
                        result.effectiveness()
                );

                if (result.success()) {
                    retryCount = 0;
                    current.status = "success";
                    task.progress.completedCount++;
                    taskManager.saveActiveTask();

                    AIPlayerMod.LOGGER.info("[TaskExecutor] 子任务完成: {} ({}/{})",
                            current.goal, task.progress.completedCount, task.progress.targetCount);

                    if (task.progress.completedCount >= task.progress.targetCount) {
                        taskManager.completeTask();
                        executionTick = 0;
                    }
                } else {
                    current.attemptCount++;

                    if (!result.executed()) {
                        AIPlayerMod.LOGGER.warn("[TaskExecutor] 确定无法执行，跳过: {} (goal={})",
                                current.skillId, current.goal);
                        current.status = "skipped";
                        retryCount = 0;
                        return;
                    }

                    retryCount++;
                    int maxRetries = getMaxRetries();
                    if (current.attemptCount >= maxRetries) {
                        AIPlayerMod.LOGGER.warn("[TaskExecutor] 子任务失败{}次: {} (goal={})",
                                maxRetries, current.skillId, current.goal);
                        current.status = "skipped";
                        retryCount = 0;
                    }
                }
            }
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[TaskExecutor] 技能执行异常: " + current.skillId, e);
            executionLogger.logAction(current.skillId, context, "error", 0.0);
        }
    }

    private Task.SubTask getCurrentSubTask(Task task) {
        if (task.subTasks == null || task.subTasks.isEmpty()) return null;
        for (Task.SubTask st : task.subTasks) {
            if ("pending".equals(st.status) || "running".equals(st.status)) {
                st.status = "running";
                return st;
            }
        }
        return null;
    }

    public void resetExecutionTick() {
        executionTick = 0;
        retryCount = 0;
    }

    private int getMaxRetries() {
        var stress = AIPlayerMod.getPersonalityStress();
        if (stress == null) return 3;
        double s = stress.getCurrentStress();
        if (s > 0.7) return 1;
        if (s > 0.3) return 3;
        return 5;
    }
}
