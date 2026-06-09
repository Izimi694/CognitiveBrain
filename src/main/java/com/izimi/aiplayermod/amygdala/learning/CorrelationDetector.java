package com.izimi.aiplayermod.amygdala.learning;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.scheduler.MetaContext;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class CorrelationDetector {

    private static final int TRIAL_COOLDOWN_TICKS = 40;
    private static final int PATTERN_WINDOW = 12;
    private static final int SOLIDIFY_THRESHOLD = 3;
    private static final int SCAN_RANGE = 6;

    private final SkillManager skillManager;
    private final BasicActionAdapter adapter;
    private final Deque<TrialRecord> recentTrials = new ArrayDeque<>();
    private int cooldown = 0;

    record TrialRecord(String action, String target, String contextFingerprint, boolean success) {}

    public CorrelationDetector(SkillManager skillManager, BasicActionAdapter adapter) {
        this.skillManager = skillManager;
        this.adapter = adapter;
    }

    public boolean tryExplore(ServerPlayerEntity bot, MetaContext ctx) {
        if (cooldown > 0) { cooldown--; return false; }
        if (adapter == null || bot == null) return false;

        String action = pickRandomAction(bot);
        if (action == null) return false;

        String target = pickTargetFor(action, bot);
        String contextFp = fingerprint(bot, ctx);

        boolean success = executeTrial(action, target, bot);
        if (!success && action.equals("dig")) {
            action = "jump";
            success = executeTrial(action, "", bot);
        }

        recentTrials.addLast(new TrialRecord(action, target != null ? target : "", contextFp, success));
        if (recentTrials.size() > PATTERN_WINDOW) {
            recentTrials.pollFirst();
        }

        checkForPatterns(bot);
        cooldown = TRIAL_COOLDOWN_TICKS;
        return true;
    }

    private String pickRandomAction(ServerPlayerEntity bot) {
        List<String> pool = new ArrayList<>();
        if (hasDiggableBlockNearby(bot)) pool.add("dig");
        if (hasAttackableEntityNearby(bot)) pool.add("attack");
        pool.add("jump");
        pool.add("moveTo");
        return pool.get(new Random().nextInt(pool.size()));
    }

    private String pickTargetFor(String action, ServerPlayerEntity bot) {
        return switch (action) {
            case "dig" -> findNearestDiggableBlock(bot);
            case "attack" -> findNearestHostileName(bot);
            case "moveTo" -> findRandomNearbyPos(bot);
            default -> null;
        };
    }

    private boolean executeTrial(String action, String target, ServerPlayerEntity bot) {
        return switch (action) {
            case "dig" -> {
                if (target == null) yield false;
                BlockPos pos = parseBlockPos(target);
                if (pos == null) yield false;
                yield adapter.dig(bot, pos).success();
            }
            case "attack" -> adapter.attack(bot, target != null ? target : "hostile").success();
            case "jump" -> adapter.jump(bot).success();
            case "moveTo" -> {
                if (target == null) yield false;
                BlockPos pos = parseBlockPos(target);
                if (pos == null) yield false;
                yield adapter.moveTo(bot, pos).success();
            }
            default -> false;
        };
    }

    private String fingerprint(ServerPlayerEntity bot, MetaContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("hp:").append((int) bot.getHealth()).append(",");
        sb.append("food:").append(bot.getHungerManager().getFoodLevel()).append(",");
        sb.append("day:").append(bot.getServerWorld().isDay() ? "day" : "night").append(",");

        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        Set<String> nearbyBlocks = new LinkedHashSet<>();
        for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx += 3) {
            for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz += 3) {
                BlockPos pos = botPos.add(dx, 0, dz);
                if (world.isInBuildLimit(pos)) {
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        nearbyBlocks.add(Registries.BLOCK.getId(state.getBlock()).getPath());
                    }
                }
            }
        }
        List<String> sorted = new ArrayList<>(nearbyBlocks);
        Collections.sort(sorted);
        sb.append("blocks:").append(String.join("_", sorted));
        return sb.toString();
    }

    private void checkForPatterns(ServerPlayerEntity bot) {
        if (recentTrials.size() < SOLIDIFY_THRESHOLD) return;

        Map<String, List<TrialRecord>> byAction = new HashMap<>();
        for (TrialRecord t : recentTrials) {
            byAction.computeIfAbsent(t.action(), k -> new ArrayList<>()).add(t);
        }

        for (var entry : byAction.entrySet()) {
            String action = entry.getKey();
            List<TrialRecord> trials = entry.getValue();
            long successCount = trials.stream().filter(t -> t.success).count();

            if (trials.size() >= SOLIDIFY_THRESHOLD && successCount >= SOLIDIFY_THRESHOLD) {
                String skillId = "reflex_selforg_" + action;

                if (skillManager.getSkill(skillId) == null) {
                    String canonicalTarget = trials.stream()
                            .filter(t -> t.success)
                            .map(t -> t.target())
                            .filter(t -> !t.isEmpty())
                            .findFirst().orElse("");

                    AIPlayerMod.LOGGER.info("[CorrelationDetector] 自组织固化: {} ({}次试验, {}次成功)",
                            skillId, trials.size(), successCount);
                    solidateReflex(skillId, action, canonicalTarget, bot);
            }
        }
    }
    }

    private void solidateReflex(String skillId, String action, String target, ServerPlayerEntity bot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skillId", skillId);
        data.put("type", "conditioned");
        data.put("status", "active");
        data.put("displayName", action + "_" + (target.isEmpty() ? "auto" : target));
        data.put("category", "selforg_" + action);
        data.put("target", target);
        data.put("source", "SELF_ORGANIZED");
        data.put("shortTermWeight", 0.5);
        data.put("longTermBaseline", 0.5);
        data.put("executionCount", 0);
        data.put("successRate", 0.0);
        data.put("proficiency", 0.3);
        data.put("occurrences", 1);

        List<Map<String, Object>> atoms = new ArrayList<>();
        Map<String, Object> atom = new LinkedHashMap<>();
        atom.put("action", action);
        atom.put("target", target);
        atom.put("proficiency", 0.3);
        atom.put("status", "healthy");
        atoms.add(atom);
        data.put("atoms", atoms);
        data.put("trigger", Map.of("type", "selforg", "action", action));

        JsonUtil.writeToFileSafeAtomic(FileUtil.getConditionedDir().resolve(skillId + ".json"), data);
        skillManager.registerConditionedSkill(new ConditionedReflex.ConditionedSkill(skillId, action + "_" + (target.isEmpty() ? "auto" : target)));

        if (bot != null) {
            bot.sendMessage(net.minecraft.text.Text.literal("§b[AI_Assistant] §7我好像学会了" + action));
        }
        AIPlayerMod.LOGGER.info("[CorrelationDetector] 自组织固化: {} (action={}, target={})", skillId, action, target);
    }

    private boolean hasDiggableBlockNearby(ServerPlayerEntity bot) {
        return findNearestDiggableBlock(bot) != null;
    }

    private String findNearestDiggableBlock(ServerPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && state.getHardness(world, pos) >= 0 && state.getHardness(world, pos) < 50) {
                        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
                    }
                }
            }
        }
        return null;
    }

    private boolean hasAttackableEntityNearby(ServerPlayerEntity bot) {
        return findNearestHostileName(bot) != null;
    }

    private String findNearestHostileName(ServerPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        HostileEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var e : world.getEntitiesByClass(HostileEntity.class, bot.getBoundingBox().expand(SCAN_RANGE), e -> e.isAlive())) {
            double d = bot.squaredDistanceTo(e);
            if (d < nearestDist) { nearestDist = d; nearest = e; }
        }
        if (nearest != null) {
            return Registries.ENTITY_TYPE.getId(nearest.getType()).getPath();
        }
        return null;
    }

    private String findRandomNearbyPos(ServerPlayerEntity bot) {
        BlockPos bp = bot.getBlockPos();
        Random rng = new Random();
        int x = bp.getX() + rng.nextInt(SCAN_RANGE * 2) - SCAN_RANGE;
        int z = bp.getZ() + rng.nextInt(SCAN_RANGE * 2) - SCAN_RANGE;
        return x + "," + bp.getY() + "," + z;
    }

    private BlockPos parseBlockPos(String s) {
        try {
            String[] parts = s.split(",");
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }

    public int getRecentTrialCount() { return recentTrials.size(); }

    public void resetCooldown() { cooldown = 0; }
}
