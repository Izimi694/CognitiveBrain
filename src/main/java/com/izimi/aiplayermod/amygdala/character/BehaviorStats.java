package com.izimi.aiplayermod.amygdala.character;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BehaviorStats {

    private final Map<String, Integer> blockBreakCounts = new HashMap<>();
    private final Map<String, Integer> entityAttackCounts = new HashMap<>();
    private final Map<String, Integer> itemUseCounts = new HashMap<>();
    private final Map<String, Integer> blockPlaceCounts = new HashMap<>();
    private final List<String> chatKeywords = new ArrayList<>();

    public void recordBlockBreak(String blockId) {
        blockBreakCounts.merge(blockId, 1, Integer::sum);
    }

    public void recordEntityAttack(String entityType) {
        entityAttackCounts.merge(entityType, 1, Integer::sum);
    }

    public void recordItemUse(String itemId) {
        itemUseCounts.merge(itemId, 1, Integer::sum);
    }

    public void recordBlockPlace(String blockId) {
        blockPlaceCounts.merge(blockId, 1, Integer::sum);
    }

    public void recordChatKeywords(List<String> keywords) {
        chatKeywords.addAll(keywords);
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        if (!blockBreakCounts.isEmpty()) {
            sb.append("挖掘统计:\n");
            blockBreakCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        if (!entityAttackCounts.isEmpty()) {
            sb.append("攻击统计:\n");
            entityAttackCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        if (!itemUseCounts.isEmpty()) {
            sb.append("物品使用:\n");
            itemUseCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        if (!blockPlaceCounts.isEmpty()) {
            sb.append("方块放置:\n");
            blockPlaceCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        return sb.toString();
    }

    public Map<String, Integer> getBlockBreakCounts() { return blockBreakCounts; }
    public Map<String, Integer> getEntityAttackCounts() { return entityAttackCounts; }
    public List<String> getChatKeywords() { return chatKeywords; }
}
