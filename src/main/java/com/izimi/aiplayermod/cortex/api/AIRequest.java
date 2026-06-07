package com.izimi.aiplayermod.cortex.api;

import com.izimi.aiplayermod.amygdala.character.PersonalityStress;
import com.izimi.aiplayermod.hippocampus.MemoryEntry;
import com.izimi.aiplayermod.state.PlayerState;
import com.izimi.aiplayermod.cortex.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AIRequest {

    public static List<AIMessage> buildPlanningRequest(String playerMessage, PlayerState state,
                                                       Task activeTask, List<MemoryEntry> recentMemories,
                                                       Map<String, Double> preferences, PersonalityStress stress) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(AIMessage.system(buildSystemPrompt(state, activeTask, recentMemories, preferences, stress)));
        messages.add(AIMessage.user(playerMessage));
        return messages;
    }

    public static List<AIMessage> buildMemoryRequest(Task completedTask, PlayerState state) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(AIMessage.system(buildMemorySystemPrompt(completedTask, state)));
        messages.add(AIMessage.user("请为上述任务生成一条记忆摘要"));
        return messages;
    }

    private static String buildSystemPrompt(PlayerState state, Task activeTask,
                                            List<MemoryEntry> recentMemories,
                                            Map<String, Double> preferences,
                                            PersonalityStress stress) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是Minecraft服务器中的一个AI玩家助手。\n");
        sb.append("你的名字: AI_Assistant\n\n");

        sb.append("【可用技能】\n");
        sb.append("- move: 移动到指定坐标\n");
        sb.append("- dig: 挖掘方块（需要目标类型）\n");
        sb.append("- attack: 攻击实体（需要目标类型）\n");
        sb.append("- craft: 合成物品（需要目标物品名）\n");
        sb.append("- explore: 探索周围区域\n");
        sb.append("- chat: 回复玩家消息\n\n");

        sb.append("【当前状态】\n");
        if (state != null && state.player != null) {
            sb.append("- 位置: (").append(state.player.position[0])
                    .append(", ").append(state.player.position[1])
                    .append(", ").append(state.player.position[2]).append(")\n");
            sb.append("- 血量: ").append(state.player.health).append("\n");
            sb.append("- 饥饿: ").append(state.player.hunger).append("\n");
            sb.append("- 背包: ").append(state.player.inventory != null
                    ? state.player.inventory.toString() : "空").append("\n");
        }
        if (state != null && state.world != null) {
            sb.append("- 生物群系: ").append(state.world.biome).append("\n");
            sb.append("- 时间: ").append(state.world.timeOfDay).append("\n");
        }

        sb.append("\n【当前任务】\n");
        if (activeTask != null) {
            sb.append("- 目标: ").append(activeTask.getGoal()).append("\n");
            sb.append("- 状态: ").append(activeTask.getStatus()).append("\n");
            sb.append("- 进度: ").append(activeTask.getProgressSummary()).append("\n");
        } else {
            sb.append("- 无\n");
        }

        if (preferences != null && !preferences.isEmpty()) {
            sb.append("\n【性格偏好（valence越接近1越喜欢，-1越讨厌）】\n");
            preferences.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(String.format("%.2f", v)).append("\n"));
        }

        if (stress != null) {
            sb.append("\n【性格压力值】\n");
            sb.append("- 当前: ").append(String.format("%.2f", stress.getCurrentStress())).append("/").append(String.format("%.2f", stress.getStressThreshold())).append("\n");
            sb.append("- 已触发次数: ").append(stress.getTotalStressEvents()).append("\n");
        }

        if (recentMemories != null && !recentMemories.isEmpty()) {
            sb.append("\n【最近记忆】\n");
            for (int i = 0; i < Math.min(5, recentMemories.size()); i++) {
                MemoryEntry m = recentMemories.get(i);
                sb.append("- [").append(m.getId()).append("] ").append(m.getSummary()).append("\n");
            }
        }

        sb.append("\n【回复格式】请严格按照以下JSON格式回复，不要加额外文字：\n");
        sb.append("{\n");
        sb.append("  \"action\": \"execute_task|chat|explore|wait\",\n");
        sb.append("  \"skill\": \"dig|move|attack|craft\",\n");
        sb.append("  \"params\": {\"target\": \"目标\", \"amount\": 数量, \"position\": [x,y,z]},\n");
        sb.append("  \"message\": \"你说的话\",\n");
        sb.append("  \"memory_save\": \"记忆摘要\",\n");
        sb.append("  \"personality_delta\": {\"target\": 0.05}\n");
        sb.append("}\n");
        sb.append("\n如果玩家在聊天，action用chat；如果是执行指令，action用execute_task；想自由探索用explore。");

        return sb.toString();
    }

    private static String buildMemorySystemPrompt(Task completedTask, PlayerState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是Minecraft AI玩家的记忆记录系统。请为已完成的任务生成简洁的记忆摘要。\n\n");
        sb.append("任务: ").append(completedTask.getGoal()).append("\n");
        sb.append("结果: 完成\n");
        sb.append("类型: ").append(completedTask.type).append("\n");
        if (state != null && state.player != null) {
            sb.append("位置: (").append(state.player.position[0]).append(", ")
                    .append(state.player.position[1]).append(", ")
                    .append(state.player.position[2]).append(")\n");
        }
        sb.append("\n请用JSON格式回复：\n");
        sb.append("{\"summary\": \"一句话总结\", \"key_learnings\": [\"学到的东西\"], \"related_skills\": [\"技能1\"]}\n");
        return sb.toString();
    }
}
