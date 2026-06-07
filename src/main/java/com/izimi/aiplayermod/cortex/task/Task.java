package com.izimi.aiplayermod.cortex.task;

import java.util.*;

public class Task {
    public String taskId;
    public String type;
    public String goal;
    public String status;
    public Progress progress;
    public Context context;
    public String parentTaskId;
    public String preemptPolicy;
    public List<SubTask> subTasks;

    public Task() {
        this.progress = new Progress();
        this.context = new Context();
        this.preemptPolicy = "ask";
        this.subTasks = new ArrayList<>();
    }

    public Task(String taskId, String type, String goal) {
        this();
        this.taskId = taskId;
        this.type = type;
        this.goal = goal;
        this.status = "running";
    }

    public static class Progress {
        public String currentStep;
        public int completedCount;
        public int targetCount;

        public Progress() {
            this.currentStep = "init";
            this.completedCount = 0;
            this.targetCount = 1;
        }
    }

    public static class Context {
        public int[] position;
        public Map<String, Integer> inventorySnapshot = new HashMap<>();
        public long startTime;

        public Context() {
            this.startTime = System.currentTimeMillis();
        }
    }

    public static class SubTask {
        public String goal;
        public String skillId;
        public String status;
        public int attemptCount;

        public SubTask() {}

        public SubTask(String goal, String skillId) {
            this.goal = goal;
            this.skillId = skillId;
            this.status = "pending";
            this.attemptCount = 0;
        }
    }

    public String getGoal() { return goal; }
    public String getStatus() { return status; }
    public String getTaskId() { return taskId; }

    public String getProgressSummary() {
        if (progress == null) return "0/0";
        return progress.completedCount + "/" + progress.targetCount + " (" + progress.currentStep + ")";
    }

    public static int extractCount(String goal) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher m = p.matcher(goal);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    public static String extractAction(String goal) {
        if (containsAny(goal, "挖", "矿", "采集", "收集", "砍", "伐", "mine", "dig", "collect"))
            return "dig";
        if (containsAny(goal, "攻", "杀", "打", "击败", "消灭", "attack", "kill", "fight"))
            return "attack";
        if (containsAny(goal, "合成", "制作", "造", "做", "craft", "make", "build"))
            return "craft";
        if (containsAny(goal, "去", "到", "走", "移动", "探索", "探", "逛", "move", "go", "explore", "walk"))
            return "move";
        return "move";
    }

    public static String extractTarget(String goal) {
        String t = goal.replaceAll("\\d+", "")
                .replaceAll("[挖砍伐采打杀攻合造做去走到移动探索逛采矿集击败灭成作制]", "")
                .replaceAll("个|块|只|颗|棵|下|次", "")
                .trim();
        if (t.isEmpty()) t = goal.trim();
        return t;
    }

    private static boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
