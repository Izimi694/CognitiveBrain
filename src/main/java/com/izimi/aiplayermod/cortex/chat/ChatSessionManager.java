package com.izimi.aiplayermod.cortex.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.izimi.aiplayermod.bayesian.BayesianModule;

public class ChatSessionManager {

    public record Message(String role, String content, long timestamp) {
        public Message(String role, String content) {
            this(role, content, System.currentTimeMillis());
        }
    }

    private static final int MAX_WINDOW_SIZE = 6;
    private final Deque<Message> window = new ArrayDeque<>(MAX_WINDOW_SIZE);
    private final BayesianModule bayesianModule;
    @SuppressWarnings("unused")
    private final UUID botId;
    private String currentGoal = "";

    public ChatSessionManager(BayesianModule bayesianModule, UUID botId) {
        this.bayesianModule = bayesianModule;
        this.botId = botId;
    }

    public void addMessage(Message msg) {
        window.addLast(msg);
        if (window.size() > MAX_WINDOW_SIZE) {
            window.removeFirst();
        }
    }

    public void setCurrentGoal(String goal) {
        this.currentGoal = goal != null ? goal : "";
    }

    public String buildPrompt() {
        String direction = bayesianModule != null
                ? bayesianModule.getCurrentDirection()
                : "暂无经验数据";

        StringBuilder sb = new StringBuilder();
        sb.append("[方向] ").append(direction).append("\n");
        sb.append("[当前目标] ").append(currentGoal).append("\n");

        if (!window.isEmpty()) {
            sb.append("[最近对话]\n");
            for (Message msg : window) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }

        sb.append("请基于以上信息填空：");
        return sb.toString();
    }

    public void refresh() {
        window.clear();
    }

    public int getWindowSize() {
        return window.size();
    }

    public Deque<Message> getWindow() {
        return new ArrayDeque<>(window);
    }
}
