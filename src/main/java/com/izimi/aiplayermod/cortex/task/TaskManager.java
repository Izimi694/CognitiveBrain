package com.izimi.aiplayermod.cortex.task;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.cortex.planner.Plan;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class TaskManager {
    private Task activeTask;
    private Task lastTask;
    private final UUID botId;

    public TaskManager() {
        this(null);
    }

    public TaskManager(UUID botId) {
        this.botId = botId;
        activeTask = loadActiveTask();
        lastTask = loadLastTask();
    }

    private Path activeTaskPath() {
        return botId != null
                ? FileUtil.getBotTasksDir(botId).resolve("active_task.json")
                : FileUtil.getActiveTaskPath();
    }

    private Path lastTaskPath() {
        return botId != null
                ? FileUtil.getBotTasksDir(botId).resolve("last_task.json")
                : FileUtil.getLastTaskPath();
    }

    public Task getActiveTask() {
        return activeTask;
    }

    public String createTask(String goal) {
        cancelActiveTask();

        String taskId = generateTaskId();
        String type = determineTaskType(goal);

        activeTask = new Task(taskId, type, goal);
        decomposeTask(activeTask);
        saveActiveTask();
        AIPlayerMod.LOGGER.info("[TaskManager] 任务创建: {} - {} ({}子任务)", taskId, goal, activeTask.subTasks.size());
        return taskId;
    }

    public String createTaskFromPlan(String goal, Plan plan) {
        cancelActiveTask();

        String taskId = generateTaskId();
        activeTask = new Task(taskId, "plan", goal);
        activeTask.progress.targetCount = plan.subSteps.size();

        for (Plan.PlanStep step : plan.subSteps) {
            String subGoal = step.target != null && !step.target.isEmpty()
                    ? step.action + "_" + step.target : step.action;
            activeTask.subTasks.add(new Task.SubTask(subGoal, step.action));
        }

        saveActivePlanToTask();
        AIPlayerMod.LOGGER.info("[TaskManager] 从Plan创建任务: {} → {}步", goal, plan.subSteps.size());
        return taskId;
    }

    private void saveActivePlanToTask() {
        if (activeTask != null) {
            JsonUtil.writeToFileSafeAtomic(activeTaskPath(), activeTask);
        }
    }

    private void decomposeTask(Task task) {
        int count = Task.extractCount(task.goal);
        String action = Task.extractAction(task.goal);
        String target = Task.extractTarget(task.goal);

        task.progress.targetCount = count;
        task.progress.currentStep = "decomposed";

        if (count <= 1 && task.goal.contains("探索")) {
            task.subTasks.add(new Task.SubTask("自由探索", action));
            return;
        }

        for (int i = 0; i < count; i++) {
            String subGoal = target.isEmpty() ? action : action + "_" + target;
            task.subTasks.add(new Task.SubTask(subGoal, action));
        }

        AIPlayerMod.LOGGER.info("[TaskManager] 任务拆解: {} → {}×{} ({})",
                task.goal, count, task.subTasks.get(0).goal,
                task.subTasks.get(0).skillId);
    }

    public String createExploreTask() {
        return createTask("自由探索");
    }

    public void cancelActiveTask() {
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            activeTask.status = "paused";
            saveActiveTask();
            lastTask = activeTask;
            saveLastTask();
            activeTask = null;
            saveActiveTask();
            AIPlayerMod.LOGGER.info("[TaskManager] 任务已中断");
        }
    }

    public boolean resumeLastTask() {
        if (lastTask == null) return false;
        if (activeTask != null) {
            cancelActiveTask();
        }
        activeTask = lastTask;
        activeTask.status = "running";
        lastTask = null;
        saveActiveTask();
        saveLastTask();
        AIPlayerMod.LOGGER.info("[TaskManager] 任务已恢复: {}", activeTask.getGoal());
        return true;
    }

    public void completeTask() {
        if (activeTask == null) return;
        activeTask.status = "completed";
        AIPlayerMod.LOGGER.info("[TaskManager] 任务完成: {}", activeTask.getGoal());

        var memoryManager = AIPlayerMod.getMemoryManager();
        if (memoryManager != null) {
            memoryManager.generateMemory(activeTask);
        }

        lastTask = null;
        saveLastTask();
        deleteActiveTask();
        activeTask = null;
    }

    public void saveActiveTask() {
        if (activeTask != null) {
            JsonUtil.writeToFileSafeAtomic(activeTaskPath(), activeTask);
        } else {
            FileUtil.deleteIfExists(activeTaskPath());
        }
    }

    private void saveLastTask() {
        if (lastTask != null) {
            JsonUtil.writeToFileSafeAtomic(lastTaskPath(), lastTask);
        } else {
            FileUtil.deleteIfExists(lastTaskPath());
        }
    }

    private void deleteActiveTask() {
        FileUtil.deleteIfExists(activeTaskPath());
    }

    private Task loadActiveTask() {
        return JsonUtil.readFromFileSafe(activeTaskPath(), Task.class);
    }

    private Task loadLastTask() {
        return JsonUtil.readFromFileSafe(lastTaskPath(), Task.class);
    }

    private String generateTaskId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int count = 1;
        Path memDir = FileUtil.getMemoriesDir();
        try {
            count = (int) java.nio.file.Files.list(memDir).count() + 1;
        } catch (IOException ignored) {}
        return "task_" + date + "_" + String.format("%03d", count);
    }

    private String determineTaskType(String goal) {
        if (goal.contains("计划") || goal.contains("plan") || goal.length() > 20) {
            return "plan";
        }
        if (goal.contains("探索") || goal.contains("逛") || goal.contains("探")) {
            return "instant";
        }
        return "sustained";
    }
}
