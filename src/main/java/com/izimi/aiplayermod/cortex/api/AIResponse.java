package com.izimi.aiplayermod.cortex.api;

import com.google.gson.annotations.SerializedName;

public class AIResponse {
    public String action;
    public String skill;
    public AIResponseParams params;
    public String message;
    @SerializedName("memory_save")
    public String memorySave;
    @SerializedName("personality_delta")
    public java.util.Map<String, Double> personalityDelta;

    public static class AIResponseParams {
        public String target;
        public int amount;
        public int[] position;
        public String direction;
        public String item;
    }

    public boolean isAction() {
        return "execute_task".equals(action) || "dig".equals(action) || "move".equals(action)
                || "attack".equals(action) || "craft".equals(action) || "explore".equals(action);
    }

    public boolean isChat() {
        return "chat".equals(action);
    }

    public boolean isEmpty() {
        return action == null;
    }

    public String getAction() { return action != null ? action : "chat"; }
    public String getSkill() { return skill != null ? skill : "move"; }
    public String getMessage() { return message != null ? message : ""; }
    public String getMemoryNote() { return memorySave != null ? memorySave : ""; }

    public static AIResponse empty() {
        AIResponse r = new AIResponse();
        r.action = "wait";
        r.message = "";
        return r;
    }
}
