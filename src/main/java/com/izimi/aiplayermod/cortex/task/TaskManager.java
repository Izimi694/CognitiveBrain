package com.izimi.aiplayermod.cortex.task;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TaskManager {
    private Task activeTask;
    private Task lastTask;

    public TaskManager() {
        activeTask = loadActiveTask();
        lastTask = loadLastTask();
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

        var evaluationCycle = AIPlayerMod.getEvaluationCycle();
        if (evaluationCycle != null) {
            evaluationCycle.onTaskCompleted();
        }

        lastTask = null;
        saveLastTask();
        deleteActiveTask();
        activeTask = null;
    }

    public void saveActiveTask() {
        if (activeTask != null) {
            JsonUtil.writeToFileSafeAtomic(FileUtil.getActiveTaskPath(), activeTask);
        } else {
            FileUtil.deleteIfExists(FileUtil.getActiveTaskPath());
        }
    }

    private void saveLastTask() {
        if (lastTask != null) {
            JsonUtil.writeToFileSafeAtomic(FileUtil.getLastTaskPath(), lastTask);
        } else {
            FileUtil.deleteIfExists(FileUtil.getLastTaskPath());
        }
    }

    private void deleteActiveTask() {
        FileUtil.deleteIfExists(FileUtil.getActiveTaskPath());
    }

    private Task loadActiveTask() {
        return JsonUtil.readFromFileSafe(FileUtil.getActiveTaskPath(), Task.class);
    }

    private Task loadLastTask() {
        return JsonUtil.readFromFileSafe(FileUtil.getLastTaskPath(), Task.class);
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
