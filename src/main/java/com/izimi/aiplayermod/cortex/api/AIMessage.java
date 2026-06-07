package com.izimi.aiplayermod.cortex.api;

public class AIMessage {
    public String role;
    public String content;

    public AIMessage() {}

    public AIMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static AIMessage system(String content) {
        return new AIMessage("system", content);
    }

    public static AIMessage user(String content) {
        return new AIMessage("user", content);
    }

    public static AIMessage assistant(String content) {
        return new AIMessage("assistant", content);
    }
}
