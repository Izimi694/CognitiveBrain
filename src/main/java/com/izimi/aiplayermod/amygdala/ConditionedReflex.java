package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.brainstem.adapter.ActionResult;
import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.skill.Skill;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.brainstem.skill.Skill.SkillResult;
import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.amygdala.learning.CategoryMapper;
import com.izimi.aiplayermod.amygdala.learning.ObservedSequence;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.*;

public class ConditionedReflex {
    private static final double SELF_REINFORCE_SUCCESS = 0.05;
    private static final double SELF_REINFORCE_FAIL = -0.03;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double WEIGHT_MIN = -1.0;
    private static final double WEIGHT_MAX = 1.0;
    private static final double EW_STW_RATIO = 0.7;
    private static final double EW_LTB_RATIO = 0.3;
    private static final double SPILL_RATIO = 0.3;

    private final SkillManager skillManager;
    private final ModConfig config;
    private final BasicActionAdapter actionAdapter;
    private final BotParams botParams;

    private final Map<String, List<Double>> actionHistory = new HashMap<>();
    private int actionCount = 0;
    private String lastExecutedReflexId;

    public ConditionedReflex(SkillManager skillManager, ModConfig config, BasicActionAdapter actionAdapter) {
        this.skillManager = skillManager;
        this.config = config;
        this.actionAdapter = actionAdapter;
        this.botParams = BotParams.load();
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

    public Skill scanAndTrigger(ServerPlayerEntity bot) {
        if (bot == null) return null;

        record Candidate(Skill skill, double score, int atomIndex) {}
        List<Candidate> candidates = new ArrayList<>();

        for (Map.Entry<String, Skill> entry : skillManager.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if (!"conditioned".equals(skill.getType())) continue;

            Path reflexPath = FileUtil.getConditionedDir().resolve(skill.getSkillId() + ".json");
            Map<String, Object> data = JsonUtil.readFromFileSafe(reflexPath, Map.class);
            if (data == null) continue;

            String status = (String) data.getOrDefault("status", "healthy");
            if ("deprecated".equals(status) || "dormant".equals(status)) continue;

            double reflexWeight = effectiveWeight(data);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get("atoms");

            if (atoms != null && !atoms.isEmpty()) {
                for (int i = 0; i < atoms.size(); i++) {
                    Map<String, Object> atom = atoms.get(i);
                    String atomStatus = (String) atom.getOrDefault("status", "healthy");
                    if ("deprecated".equals(atomStatus) || "impossible".equals(atomStatus)) continue;

                    String atomTarget = (String) atom.get("atomTarget");
                    if (atomTarget == null) continue;

                    double atomProficiency = ((Number) atom.getOrDefault("proficiency", 0.0)).doubleValue();
                    if (isAtomTargetNearby(bot, (String) atom.get("action"), atomTarget)) {
                        double score = reflexWeight * atomProficiency;
                        candidates.add(new Candidate(skill, score, i));
                    }
                }
            } else {
                String category = (String) data.get("category");
                if (category != null && isTargetNearby(bot, category, data)) {
                    double compoundProficiency = ((Number) data.getOrDefault("proficiency", 0.0)).doubleValue();
                    candidates.add(new Candidate(skill, reflexWeight * compoundProficiency, -1));
                }
            }
        }

        if (candidates.isEmpty()) return null;

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        Candidate best = candidates.get(0);

        if (best.atomIndex() >= 0) {
            Path reflexPath = FileUtil.getConditionedDir().resolve(best.skill().getSkillId() + ".json");
            Map<String, Object> data = JsonUtil.readFromFileSafe(reflexPath, Map.class);
            if (data != null) {
                data.put("currentAtomIndex", best.atomIndex());
                JsonUtil.writeToFileSafeAtomic(reflexPath, data);
            }
        }

        return best.skill();
    }

    @SuppressWarnings("unchecked")
    private boolean isTargetNearby(ServerPlayerEntity bot, String category, Map<String, Object> reflexData) {
        var world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        int scanRange = 8;

        if (category.startsWith("dig_")) {
            List<String> contributedTargets = (List<String>) reflexData.get("contributedTargets");
            List<String> searchTargets = contributedTargets != null ? contributedTargets : List.of();

            if (searchTargets.isEmpty()) {
                String target = (String) reflexData.get("target");
                if (target != null) searchTargets = List.of(target);
            }

            for (int dx = -scanRange; dx <= scanRange; dx++) {
                for (int dy = -scanRange; dy <= scanRange; dy++) {
                    for (int dz = -scanRange; dz <= scanRange; dz++) {
                        BlockPos pos = botPos.add(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;

                        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                        for (String target : searchTargets) {
                            if (blockId.toLowerCase().contains(target.toLowerCase())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        if (category.startsWith("attack_")) {
            var entities = world.getEntitiesByClass(LivingEntity.class,
                    bot.getBoundingBox().expand(scanRange),
                    e -> e.isAlive() && e != bot);
            for (var entity : entities) {
                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                String target = (String) reflexData.get("target");
                if (target != null && entityId.toLowerCase().contains(target.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    private boolean isAtomTargetNearby(ServerPlayerEntity bot, String action, String atomTarget) {
        var world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        int scanRange = 8;

        if ("dig".equals(action) || atomTarget.startsWith("dig_")) {
            String search = atomTarget;
            if (search.contains("_")) {
                search = search.substring(search.indexOf('_') + 1);
            }

            for (int dx = -scanRange; dx <= scanRange; dx++) {
                for (int dy = -scanRange; dy <= scanRange; dy++) {
                    for (int dz = -scanRange; dz <= scanRange; dz++) {
                        BlockPos pos = botPos.add(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;

                        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                        for (var rule : CategoryMapper.getCategoryRules().entrySet()) {
                            if (rule.getKey().equals(search) || rule.getKey().equals(atomTarget)) {
                                for (String pattern : rule.getValue()) {
                                    if (blockId.toLowerCase().contains(pattern.toLowerCase())) {
                                        return true;
                                    }
                                }
                            }
                        }
                        if (blockId.toLowerCase().contains(search.toLowerCase())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        if ("attack".equals(action) || atomTarget.startsWith("attack_")) {
            String search = atomTarget;
            if (search.contains("_")) {
                search = search.substring(search.indexOf('_') + 1);
            }
            var entities = world.getEntitiesByClass(LivingEntity.class,
                    bot.getBoundingBox().expand(scanRange),
                    e -> e.isAlive() && e != bot);
            for (var entity : entities) {
                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (entityId.toLowerCase().contains(search.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        if ("moveTo".equals(action) || atomTarget.startsWith("moveTo_")) {
            String search = atomTarget;
            if (search.contains("_")) {
                search = search.substring(search.indexOf('_') + 1);
            }
            for (int dx = -scanRange; dx <= scanRange; dx++) {
                for (int dy = -scanRange; dy <= scanRange; dy++) {
                    for (int dz = -scanRange; dz <= scanRange; dz++) {
                        BlockPos pos = botPos.add(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;
                        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                        for (var rule : CategoryMapper.getCategoryRules().entrySet()) {
                            if (rule.getKey().equals(search)) {
                                for (String pattern : rule.getValue()) {
                                    if (blockId.toLowerCase().contains(pattern.toLowerCase())) {
                                        return true;
                                    }
                                }
                            }
                        }
                        if (blockId.toLowerCase().contains(search.toLowerCase())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        if ("equipItem".equals(action) || atomTarget.startsWith("equip_")) {
            return true;
        }

        return false;
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

    private List<Map<String, Object>> buildAtomEntries(List<Map<String, String>> steps) {
        List<Map<String, Object>> atoms = new ArrayList<>();
        for (Map<String, String> step : steps) {
            Map<String, Object> atom = new LinkedHashMap<>();
            atom.put("action", step.get("action"));
            atom.put("target", step.get("target"));
            atom.put("atomTarget", buildAtomTarget(step.get("action"), step.get("target")));
            atom.put("executionCount", 0);
            atom.put("successRate", 0.0);
            atom.put("proficiency", 0.1);
            atom.put("status", "healthy");
            atoms.add(atom);
        }
        return atoms;
    }

    private String buildAtomTarget(String action, String target) {
        if ("equipItem".equals(action)) {
            return "equip_" + target.toLowerCase().replace(" ", "_");
        }
        if ("moveTo".equals(action)) {
            String cat = CategoryMapper.getCategory("dig", target);
            return "moveTo_" + (cat.contains("_") ? cat.substring(cat.indexOf('_') + 1) : target.toLowerCase());
        }
        return CategoryMapper.getCategory(action, target);
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
        if (!(reflex instanceof ConditionedSkill)) return;

        String skillId = reflex.getSkillId();
        Path reflexPath = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(reflexPath, Map.class);
        if (data == null) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get("atoms");
        int atomIdx = ((Number) data.getOrDefault("currentAtomIndex", 0)).intValue();

        Skill.SkillResult result;

        if (atoms != null && atomIdx >= 0 && atomIdx < atoms.size()) {
            Map<String, Object> atom = atoms.get(atomIdx);
            String atomStatus = (String) atom.getOrDefault("status", "healthy");

            if ("impossible".equals(atomStatus)) {
                AIPlayerMod.LOGGER.debug("[ConditionedReflex] 原子已标记不可能，跳过: {} [{}]",
                        skillId, atom.get("action"));
                return;
            }

            String action = (String) atom.get("action");
            String target = (String) atom.get("target");
            ActionResult adapterResult = dispatchAction(action, target, bot);

            if (adapterResult != null) {
                result = new Skill.SkillResult(
                        adapterResult.executed(), adapterResult.success(),
                        adapterResult.effectiveness(), adapterResult.message());
            } else {
                Skill innate = skillManager.getSkill(action);
                if (innate != null) {
                    Map<String, Object> context = new HashMap<>();
                    if (innate.canExecute(bot.getServerWorld(), bot, context)) {
                        result = innate.execute(bot.getServerWorld(), bot, context);
                    } else {
                        result = Skill.SkillResult.unable("先天技能" + action + "无法执行");
                    }
                } else {
                    result = ((ConditionedSkill) reflex).execute(bot.getServerWorld(), bot, new HashMap<>());
                }
            }

            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 原子{}/{}: {} {} → executed={} success={}",
                    atomIdx + 1, atoms.size(), atom.get("action"), atom.get("target"),
                    result.executed(), result.success());

        } else {
            result = ((ConditionedSkill) reflex).execute(bot.getServerWorld(), bot, new HashMap<>());
        }

        if (result != null && result.executed()) {
            lastExecutedReflexId = skillId;
            double delta = result.success() ? SELF_REINFORCE_SUCCESS : SELF_REINFORCE_FAIL;
            reinforce(data, delta);
        }

        updateReflexStats(skillId, result.success(), result.effectiveness());

        if (result.success()) {
            AIPlayerMod.LOGGER.info("[ConditionedReflex] 成功: {}", skillId);
        } else {
            handleReflexFailure(skillId, bot, atoms, atomIdx);
        }
    }

    public enum ReflexHealth { HEALTHY, WATCHING, DORMANT }

    public record ReflexState(int executionCount, double successRate, ReflexHealth health) {}

    private void updateReflexStats(String skillId, boolean success, double effectiveness) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return;

        int count = ((Number) data.getOrDefault("executionCount", 0)).intValue() + 1;
        double oldRate = ((Number) data.getOrDefault("successRate", 0.0)).doubleValue();
        int oldSuccesses = (int) Math.round(oldRate * (count - 1));
        int newSuccesses = oldSuccesses + (success ? 1 : 0);
        double newRate = count > 0 ? newSuccesses / (double) count : 0.0;

        data.put("executionCount", count);
        data.put("successRate", newRate);
        data.put("lastEffectiveness", effectiveness);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get("atoms");
        if (atoms != null && !atoms.isEmpty()) {
            int atomIdx = ((Number) data.getOrDefault("currentAtomIndex", 0)).intValue();
            if (atomIdx >= 0 && atomIdx < atoms.size()) {
                Map<String, Object> atom = atoms.get(atomIdx);
                int aCount = ((Number) atom.getOrDefault("executionCount", 0)).intValue() + 1;
                double aOldRate = ((Number) atom.getOrDefault("successRate", 0.0)).doubleValue();
                int aOldSuccesses = (int) Math.round(aOldRate * (aCount - 1));
                int aNewSuccesses = aOldSuccesses + (success ? 1 : 0);
                double aNewRate = aCount > 0 ? aNewSuccesses / (double) aCount : 0.0;

                atom.put("executionCount", aCount);
                atom.put("successRate", aNewRate);
                atom.put("proficiency", Math.min(1.0,
                        ((Number) atom.getOrDefault("proficiency", 0.1)).doubleValue() + (success ? 0.05 : 0.0)));

                if (success && atomIdx + 1 < atoms.size()) {
                    data.put("currentAtomIndex", atomIdx + 1);
                }
            }
        }

        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    @SuppressWarnings("unchecked")
    private void handleReflexFailure(String skillId, ServerPlayerEntity bot,
                                     List<Map<String, Object>> atoms, int atomIdx) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return;

        int count = ((Number) data.getOrDefault("executionCount", 0)).intValue();
        double rate = ((Number) data.getOrDefault("successRate", 0.0)).doubleValue();
        String currentStatus = (String) data.getOrDefault("status", "healthy");

        if (count < 5) {
            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 样本不足，重试: {} (count={})", skillId, count);
            return;
        }

        if (rate > 0.4) {
            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 概率性失败: {} (rate={:.2f})", skillId, rate);
            return;
        }

        if (rate <= 0.4 && rate > 0.2 && !"watching".equals(currentStatus)) {
            data.put("status", "watching");
            JsonUtil.writeToFileSafeAtomic(path, data);
            AIPlayerMod.LOGGER.warn("[ConditionedReflex] 标记观察: {} (rate={:.2f}, count={})", skillId, rate, count);
            return;
        }

        if (rate <= 0.2 && count > 20) {
            if (atoms != null && atomIdx >= 0 && atomIdx < atoms.size()) {
                Map<String, Object> atom = atoms.get(atomIdx);
                String aTarget = (String) atom.getOrDefault("target", atom.get("action"));
                atom.put("status", "impossible");
                data.put("atoms", atoms);
                JsonUtil.writeToFileSafeAtomic(path, data);
                AIPlayerMod.LOGGER.warn("[ConditionedReflex] 原子永久跳过: {} [{}] (rate={:.2f}, count={})",
                        skillId, aTarget, rate, count);
                if (bot != null) {
                    bot.sendMessage(Text.literal("§c[AI_Assistant] §7" + aTarget +
                            " 这个我做不了，跳过"));
                }
                return;
            }

            if (!"dormant".equals(currentStatus)) {
                data.put("status", "dormant");
                JsonUtil.writeToFileSafeAtomic(path, data);
                moveToArchived(skillId, data);
                AIPlayerMod.LOGGER.warn("[ConditionedReflex] 标记休眠并归档: {} (rate={:.2f}, count={})",
                        skillId, rate, count);
                if (bot != null) {
                    bot.sendMessage(Text.literal("§c[AI_Assistant] §7这个" +
                            data.getOrDefault("displayName", skillId) + "好像不太对，我先放一放..."));
                }
            }
        }
    }

    public ReflexState getReflexState(String skillId) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return new ReflexState(0, 0.0, ReflexHealth.HEALTHY);

        int count = ((Number) data.getOrDefault("executionCount", 0)).intValue();
        double rate = ((Number) data.getOrDefault("successRate", 0.0)).doubleValue();
        String status = (String) data.getOrDefault("status", "healthy");

        ReflexHealth health = switch (status) {
            case "dormant" -> ReflexHealth.DORMANT;
            case "watching" -> ReflexHealth.WATCHING;
            default -> ReflexHealth.HEALTHY;
        };

        return new ReflexState(count, rate, health);
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
        reflexData.put("shortTermWeight", DEFAULT_WEIGHT);
        reflexData.put("longTermBaseline", DEFAULT_WEIGHT);
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

        List<Map<String, String>> atomSteps = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            atomSteps.add(Map.of("action", step.action(), "target", step.target()));
        }
        reflexData.put("atoms", buildAtomEntries(atomSteps));
        reflexData.put("currentAtomIndex", 0);

        reflexData.put("trigger", Map.of(
                "type", "subtask",
                "target", category
        ));

        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        JsonUtil.writeToFileSafeAtomic(path, reflexData);

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

        JsonUtil.writeToFileSafeAtomic(path, data);

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

    public void reinforce(String skillId, double delta) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return;
        reinforce(data, delta);
        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    private void reinforce(Map<String, Object> data, double delta) {
        double stw = ((Number) data.getOrDefault("shortTermWeight", DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault("longTermBaseline", DEFAULT_WEIGHT)).doubleValue();

        stw = stw * (1 - botParams.getAlpha()) + botParams.getAlpha() * delta;
        ltb = ltb * (1 - botParams.getBeta()) + botParams.getBeta() * stw;

        data.put("shortTermWeight", Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, stw)));
        data.put("longTermBaseline", Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, ltb)));
    }

    public double effectiveWeight(Map<String, Object> data) {
        double stw = ((Number) data.getOrDefault("shortTermWeight", DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault("longTermBaseline", DEFAULT_WEIGHT)).doubleValue();
        return Math.max(0, stw * EW_STW_RATIO + ltb * EW_LTB_RATIO);
    }

    public String getLastExecutedReflexId() {
        return lastExecutedReflexId;
    }

    public String getReflexContext(String skillId) {
        if (skillId == null) return null;
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return null;
        String category = (String) data.getOrDefault("category", "");
        String target = (String) data.getOrDefault("target", "");
        String displayName = (String) data.getOrDefault("displayName", skillId);
        double stw = ((Number) data.getOrDefault("shortTermWeight", DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault("longTermBaseline", DEFAULT_WEIGHT)).doubleValue();
        return String.format("reflexId=%s, category=%s, target=%s, displayName=%s, stw=%.2f, ltb=%.2f",
                skillId, category, target, displayName, stw, ltb);
    }

    public String getLastReflexSummary() {
        if (lastExecutedReflexId == null) return "最近没有执行特定反射";
        String ctx = getReflexContext(lastExecutedReflexId);
        return "Bot最近执行的反射: " + (ctx != null ? ctx : lastExecutedReflexId);
    }

    public void moveToArchived(String skillId, Map<String, Object> data) {
        Path src = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Path dst = FileUtil.getArchivedDir().resolve(skillId + ".json");
        JsonUtil.writeToFileSafeAtomic(dst, data);
        try {
            java.nio.file.Files.deleteIfExists(src);
        } catch (java.io.IOException e) {
            AIPlayerMod.LOGGER.warn("[ConditionedReflex] 删除原反射文件失败: {}", skillId);
        }
    }

    public boolean tryReactivate(String skillId) {
        Path archivedPath = FileUtil.getArchivedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(archivedPath, Map.class);
        if (data == null) return false;
        data.put("status", "healthy");
        data.put("executionCount", 0);
        data.put("successRate", 0.0);
        data.put("shortTermWeight", DEFAULT_WEIGHT);
        data.put("longTermBaseline", DEFAULT_WEIGHT);
        Path activePath = FileUtil.getConditionedDir().resolve(skillId + ".json");
        JsonUtil.writeToFileSafeAtomic(activePath, data);
        try {
            java.nio.file.Files.deleteIfExists(archivedPath);
        } catch (java.io.IOException e) {
            AIPlayerMod.LOGGER.warn("[ConditionedReflex] 删除归档文件失败: {}", skillId);
        }
        AIPlayerMod.LOGGER.info("[ConditionedReflex] 反射复活: {}", skillId);
        return true;
    }

    public String matchReflexIdByHint(String hint) {
        if (hint == null || hint.isEmpty()) return null;

        if (skillManager.getSkill(hint) != null) {
            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 精确匹配: {}", hint);
            return hint;
        }

        String lower = hint.toLowerCase();

        if (lastExecutedReflexId != null && lastExecutedReflexId.toLowerCase().contains(lower)) {
            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 最近执行匹配: {} <- {}", lastExecutedReflexId, hint);
            return lastExecutedReflexId;
        }

        if (lastExecutedReflexId != null) {
            String lastCategory = getReflexCategory(lastExecutedReflexId);
            if (lastCategory != null) {
                for (String id : skillManager.getSkills().keySet()) {
                    if (id.equals(lastExecutedReflexId)) continue;
                    String cat = getReflexCategory(id);
                    if (lastCategory.equals(cat) && id.toLowerCase().contains(lower)) {
                        AIPlayerMod.LOGGER.debug("[ConditionedReflex] 同category匹配: {} <- {} (cat={})", id, hint, lastCategory);
                        return id;
                    }
                }
            }
        }

        for (String id : skillManager.getSkills().keySet()) {
            if (id.toLowerCase().contains(lower)) {
                AIPlayerMod.LOGGER.warn("[ConditionedReflex] 兜底子串匹配(LLM输出可能不够精确): {} <- {}", id, hint);
                return id;
            }
        }

        AIPlayerMod.LOGGER.warn("[ConditionedReflex] 无匹配反射: hint={}", hint);
        return null;
    }

    private String getReflexCategory(String skillId) {
        Path path = FileUtil.getConditionedDir().resolve(skillId + ".json");
        Map<String, Object> data = JsonUtil.readFromFileSafe(path, Map.class);
        if (data == null) return null;
        return (String) data.getOrDefault("category", null);
    }

    public void reinforceWithSpill(String skillId, double delta) {
        reinforce(skillId, delta);
        AIPlayerMod.LOGGER.info("[ConditionedReflex] 反射强化: {} delta={}", skillId, delta);

        String category = getReflexCategory(skillId);
        if (category == null) return;

        double spill = delta * SPILL_RATIO;
        int count = 0;
        for (String id : skillManager.getSkills().keySet()) {
            if (id.equals(skillId)) continue;
            String cat = getReflexCategory(id);
            if (category.equals(cat)) {
                reinforce(id, spill);
                count++;
            }
        }
        if (count > 0) {
            AIPlayerMod.LOGGER.debug("[ConditionedReflex] 溢出强化: {}个同category反射 x{:.3f} (cat={})", count, spill, category);
        }
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
            }
        }
        actionHistory.clear();
    }

    private ActionResult dispatchAction(String action, String target, ServerPlayerEntity bot) {
        if (actionAdapter == null || bot == null) return null;

        var world = bot.getServerWorld();

        return switch (action) {
            case "moveTo" -> {
                BlockPos targetPos = findBlockPosByName(world, bot, target);
                yield actionAdapter.moveTo(bot, targetPos != null ? targetPos : bot.getBlockPos());
            }
            case "lookAt" -> actionAdapter.lookAt(bot, bot.getX(), bot.getY() + 1.6, bot.getZ());
            case "dig" -> {
                BlockPos digTarget = findBlockPosByName(world, bot, target);
                yield actionAdapter.dig(bot, digTarget);
            }
            case "attack" -> actionAdapter.attack(bot, target);
            case "placeBlock" -> {
                BlockPos placePos = bot.getBlockPos().offset(net.minecraft.util.math.Direction.UP);
                yield actionAdapter.placeBlock(bot, placePos, "up");
            }
            case "useItem" -> actionAdapter.useItem(bot);
            case "equipItem" -> actionAdapter.equipItem(bot, target);
            case "openBlock" -> {
                BlockPos openPos = findBlockPosByName(world, bot, target);
                yield actionAdapter.openBlock(bot, openPos != null ? openPos : bot.getBlockPos());
            }
            case "closeWindow" -> actionAdapter.closeWindow(bot);
            case "clickSlot" -> actionAdapter.clickSlot(bot, 0, 0);
            case "chat" -> actionAdapter.chat(bot, target != null ? target : "");
            case "jump" -> actionAdapter.jump(bot);
            default -> null;
        };
    }

    private BlockPos findBlockPosByName(net.minecraft.server.world.ServerWorld world,
                                         ServerPlayerEntity bot, String name) {
        if (world == null || bot == null) return null;
        BlockPos botPos = bot.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    var state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
                    if (name != null && blockId.toLowerCase().contains(name.toLowerCase())) {
                        return pos;
                    }
                }
            }
        }
        return null;
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
