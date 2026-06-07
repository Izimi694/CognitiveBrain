package com.izimi.aiplayermod.cortex.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Plan {
    public String taskId;
    public String goal;
    public String status;
    public int currentSubTaskIndex;
    public long createdAt;
    public List<PlanStep> subSteps;
    public String source;

    public Plan() {
        this.subSteps = new ArrayList<>();
        this.currentSubTaskIndex = 0;
        this.createdAt = System.currentTimeMillis();
        this.status = "created";
        this.source = "local";
    }

    public Plan(String taskId, String goal) {
        this();
        this.taskId = taskId;
        this.goal = goal;
    }

    public boolean isComplete() {
        return currentSubTaskIndex >= subSteps.size();
    }

    public PlanStep getCurrentStep() {
        if (currentSubTaskIndex >= 0 && currentSubTaskIndex < subSteps.size()) {
            return subSteps.get(currentSubTaskIndex);
        }
        return null;
    }

    public void advanceToNextStep() {
        currentSubTaskIndex++;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("goal", goal);
        map.put("status", status);
        map.put("currentSubTaskIndex", currentSubTaskIndex);
        map.put("createdAt", createdAt);
        map.put("source", source);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (PlanStep step : subSteps) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("skillId", step.skillId);
            sm.put("action", step.action);
            sm.put("target", step.target);
            sm.put("status", step.status);
            sm.put("amount", step.amount);
            steps.add(sm);
        }
        map.put("subSteps", steps);

        return map;
    }

    @SuppressWarnings("unchecked")
    public static Plan fromMap(Map<String, Object> map) {
        Plan plan = new Plan();
        plan.taskId = (String) map.get("taskId");
        plan.goal = (String) map.get("goal");
        plan.status = (String) map.getOrDefault("status", "created");
        plan.currentSubTaskIndex = ((Number) map.getOrDefault("currentSubTaskIndex", 0)).intValue();
        plan.createdAt = ((Number) map.getOrDefault("createdAt", System.currentTimeMillis())).longValue();
        plan.source = (String) map.getOrDefault("source", "local");

        List<Map<String, Object>> steps = (List<Map<String, Object>>) map.get("subSteps");
        if (steps != null) {
            for (Map<String, Object> sm : steps) {
                PlanStep step = new PlanStep();
                step.skillId = (String) sm.get("skillId");
                step.action = (String) sm.get("action");
                step.target = (String) sm.get("target");
                step.status = (String) sm.getOrDefault("status", "pending");
                step.amount = ((Number) sm.getOrDefault("amount", 1)).intValue();
                plan.subSteps.add(step);
            }
        }

        return plan;
    }

    public static class PlanStep {
        public String skillId;
        public String action;
        public String target;
        public String status;
        public int amount;

        public PlanStep() {
            this.status = "pending";
            this.amount = 1;
        }

        public PlanStep(String skillId, String action, String target) {
            this();
            this.skillId = skillId;
            this.action = action;
            this.target = target;
        }
    }
}
