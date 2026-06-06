package com.izimi.aiplayermod.skill;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.learning.CategoryMapper;
import com.izimi.aiplayermod.learning.ObservedSequence;
import com.izimi.aiplayermod.task.Task;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.*;

public class ConditionedReflex {
    private final SkillManager skillManager;
    private final ModConfig config;

    private final Map<String, List<Double>> actionHistory = new HashMap<>();
    private int actionCount = 0;

    public ConditionedReflex(SkillManager skillManager, ModConfig config) {
        this.skillManager = skillManager;
        this.config = config;
    }

    public Skill match(Task task) {
        if (task == null) return null;
        String goal = task.getGoal().toLowerCase();

        for (Map.Entry<String, Skill> entry : skillManager.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if ("conditioned".equals(skill.getType())) {
                String skillId = skill.getSkillId().toLowerCase();
                String displayName = skill.getName().toLowerCase();

                if (goal.contains(displayName)) return skill;

                String shortId = skillId.replace("reflex_", "").replace("_", " ");
                if (goal.contains(shortId)) return skill;
            }
        }
        return null;
    }

    private List<String> extractTargets(ObservedSequence sequence) {
        List<String> targets = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            String t = step.target();
            if (!targets.contains(t)) {
                targets.add(t);
            }
        }
        return targets;
    }

    public void recordAction(String skillId, double effectiveness) {
        actionHistory.computeIfAbsent(skillId, k -> new ArrayList<>()).add(effectiveness);
        actionCount++;

        if (actionCount >= 20) {
            analyzeAndGenerate();
            actionCount = 0;
        }
    }

    public void executeReflex(Skill reflex, ServerPlayerEntity bot) {
        if (reflex instanceof ConditionedSkill conditioned) {
            Map<String, Object> context = new HashMap<>();
            Skill.SkillResult result = conditioned.execute(bot.getServerWorld(), bot, context);
            AIPlayerMod.LOGGER.info("[ConditionedReflex] 条件反射执行: {} -> {}", reflex.getSkillId(), result.success());
        }
    }

    public void solidifySequence(ObservedSequence sequence) {
        String category = CategoryMapper.getCategory(
                sequence.steps().get(0).action(), sequence.target());
        solidifySequence(sequence, category);
    }

    public void solidifySequence(ObservedSequence sequence, String category) {
        String skillId = "reflex_" + category;
        String name = CategoryMapper.getCategoryDisplayName(category);

        if (skillManager.getSkill(skillId) != null) {
            incrementProficiency(skillId);
            AIPlayerMod.LOGGER.info("[ConditionedReflex] 分类已有反射，强化熟练度: {} -> {}", category, skillId);
            return;
        }

        ConditionedSkill skill = new ConditionedSkill(skillId, name);
        skillManager.registerConditionedSkill(skill);

        Map<String, Object> reflexData = new LinkedHashMap<>();
        reflexData.put("skillId", skillId);
        reflexData.put("type", "conditioned");
        reflexData.put("displayName", name);
        reflexData.put("category", category);
        reflexData.put("source", sequence.source());
        reflexData.put("occurrences", sequence.occurrences());
        reflexData.put("proficiency", sequence.proficiency());
        reflexData.put("executionCount", 0);
        reflexData.put("successRate", 0.0);
        reflexData.put("target", sequence.target());
        reflexData.put("contributedTargets", extractTargets(sequence));
        reflexData.put("solidifiedAt", System.currentTimeMillis());

        List<Map<String, String>> steps = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            steps.add(Map.of("action", step.action(), "target", step.target()));
        }
        reflexData.put("steps", steps);
        reflexData.put("trigger", Map.of(
                "type", "subtask",
                "target", category
        ));

        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        JsonUtil.writeToFileSafe(path, reflexData);

        AIPlayerMod.LOGGER.info("[ConditionedReflex] 条件反射已固化: {} (category={}, 观察{}次, proficiency={})",
                skillId, category, sequence.occurrences(), String.format("%.2f", sequence.proficiency()));
    }

    public void incrementProficiency(String skillId) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return;

        double proficiency = ((Number) data.getOrDefault("proficiency", 0.3)).doubleValue();
        proficiency = Math.min(1.0, proficiency + 0.05);
        data.put("proficiency", proficiency);

        int observed = ((Number) data.getOrDefault("occurrences", 0)).intValue();
        data.put("occurrences", observed + 1);

        JsonUtil.writeToFileSafe(path, data);

        AIPlayerMod.LOGGER.info("[ConditionedReflex] 熟练度提升: {} -> proficiency={} (观察{}次)",
                skillId, String.format("%.2f", proficiency), observed + 1);

        if (proficiency >= 0.8) {
            var bot = AIPlayerMod.getBotSpawner() != null ? AIPlayerMod.getBotSpawner().getBotEntity() : null;
            if (bot != null) {
                String name = (String) data.getOrDefault("displayName", skillId);
                bot.sendMessage(net.minecraft.text.Text.literal("§b[AI_Assistant] §f我现在" + name +
                        "已经很熟练了，需要我承包吗？"));
            }
        }
    }

    public String buildSkillId(ObservedSequence sequence) {
        String action = sequence.steps().get(0).action();
        String target = sequence.target();
        String category = CategoryMapper.getCategory(action, target);
        return "reflex_" + category;
    }

    private void analyzeAndGenerate() {
        for (Map.Entry<String, List<Double>> entry : actionHistory.entrySet()) {
            String skillId = entry.getKey();
            List<Double> scores = entry.getValue();

            if (scores.size() < config.reflexMinSuccesses) continue;

            long successes = scores.stream().filter(s -> s >= config.reflexThreshold).count();
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            if (successes >= config.reflexMinSuccesses && avg >= config.reflexThreshold) {
                ConditionedSkill newSkill = new ConditionedSkill("reflex_" + skillId, "条件反射_" + skillId);
                skillManager.registerConditionedSkill(newSkill);
                AIPlayerMod.LOGGER.info("[ConditionedReflex] 生成条件反射: {}", skillId);

                var stress = AIPlayerMod.getPersonalityStress();
                if (stress != null) {
                    stress.onReflexChange();
                }
            }
        }
        actionHistory.clear();
    }

    public static class ConditionedSkill extends Skill {
        public ConditionedSkill(String skillId, String name) {
            super(skillId, name, "conditioned");
        }

        @Override
        public boolean canExecute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return bot != null && world != null;
        }

        @Override
        public SkillResult execute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return SkillResult.success("条件反射已触发: " + getName());
        }
    }
}
