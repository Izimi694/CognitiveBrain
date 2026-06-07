package com.izimi.aiplayermod.hippocampus;

import java.util.*;

public class MemoryQuery {
    private final MemoryManager memoryManager;

    private static final List<String> TRIGGER_PATTERNS = List.of(
            "还记得", "你之前", "你几天前", "你刚才", "你昨天",
            "记得", "之前做了", "上次", "你做过", "你的记忆",
            "remember", "before", "last time", "you did", "your memory"
    );

    private static final Set<String> TIME_KEYWORDS = Set.of(
            "昨天", "今天", "前天", "之前", "几天前", "上周",
            "yesterday", "today", "ago", "before", "last week"
    );

    public MemoryQuery(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public boolean isMemoryQuery(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String pattern : TRIGGER_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    public List<String> extractKeywords(String message) {
        if (message == null) return Collections.emptyList();
        List<String> keywords = new ArrayList<>();

        String lower = message.toLowerCase();
        for (String trigger : TRIGGER_PATTERNS) {
            lower = lower.replace(trigger, " ");
        }
        for (String time : TIME_KEYWORDS) {
            lower = lower.replace(time, " ");
        }

        String[] words = lower.split("[\\s，。！？,.!?]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    public List<MemoryEntry> query(String message) {
        if (!isMemoryQuery(message)) {
            return memoryManager.getRecentMemories();
        }

        List<String> keywords = extractKeywords(message);
        if (keywords.isEmpty()) {
            return memoryManager.getRecentMemories();
        }

        return memoryManager.searchByKeywords(keywords);
    }

    public String formatMemories(List<MemoryEntry> memories) {
        if (memories.isEmpty()) return "没有找到相关记忆";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(memories.size()).append(" 条相关记忆:\n");
        for (int i = 0; i < Math.min(memories.size(), 5); i++) {
            MemoryEntry m = memories.get(i);
            sb.append("● [").append(m.getId()).append("] ").append(m.getSummary());
            if (m.gameDay > 0) {
                sb.append(" (第").append(m.gameDay).append("天)");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("的", "了", "吗", "呢", "吧", "啊", "在", "是",
                "the", "a", "an", "is", "are", "was", "were", "did", "do", "does");
        return stopWords.contains(word) || word.length() < 2;
    }
}
