package com.izimi.aiplayermod.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.learning.BehaviorEvent;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.*;
import java.util.function.Consumer;

public class BehaviorObserver {
    private final CharacterManager characterManager;
    private final ModConfig config;
    private final PersonalityStress personalityStress;

    private final Map<String, Integer> blockBreakCounts = new HashMap<>();
    private final Map<String, Integer> entityAttackCounts = new HashMap<>();
    private final List<String> chatKeywords = new ArrayList<>();
    private final Map<String, Double> pendingBehaviorUpdates = new HashMap<>();
    private final Map<String, Double> pendingChatUpdates = new HashMap<>();
    private int observationCount = 0;
    private static final int EVOLVE_THRESHOLD = 10;

    private final List<Consumer<BehaviorEvent>> learningListeners = new ArrayList<>();

    public BehaviorObserver(CharacterManager characterManager, ModConfig config, PersonalityStress personalityStress) {
        this.characterManager = characterManager;
        this.config = config;
        this.personalityStress = personalityStress;
    }

    public void addLearningListener(Consumer<BehaviorEvent> listener) {
        learningListeners.add(listener);
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity sp) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                onBlockBreak(blockId, state);

                String heldItem = getHeldItemName(sp);
                String timeOfDay = getTimeOfDay(world);
                notifyLearning(new BehaviorEvent(sp.getName().getString(), "dig",
                        blockId, System.currentTimeMillis(), heldItem, timeOfDay));
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp && entity instanceof LivingEntity le) {
                String entityType = le.getType().getName().getString();
                onEntityAttack(entityType);

                String heldItem = getHeldItemName(sp);
                String timeOfDay = getTimeOfDay(world);
                notifyLearning(new BehaviorEvent(sp.getName().getString(), "attack",
                        entityType, System.currentTimeMillis(), heldItem, timeOfDay));
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity sp) {
                ItemStack stack = sp.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                onItemUse(itemId);

                String timeOfDay = getTimeOfDay(world);
                notifyLearning(new BehaviorEvent(sp.getName().getString(), "use_item",
                        itemId, System.currentTimeMillis(), itemId, timeOfDay));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp) {
                var pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                onBlockPlace(blockId);

                ItemStack stack = sp.getStackInHand(hand);
                String heldItem = Registries.ITEM.getId(stack.getItem()).toString();
                String timeOfDay = getTimeOfDay(world);
                notifyLearning(new BehaviorEvent(sp.getName().getString(), "place_block",
                        blockId, System.currentTimeMillis(), heldItem, timeOfDay));
            }
            return ActionResult.PASS;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            onChatMessage(message.getSignedContent());
        });

        AIPlayerMod.LOGGER.info("[BehaviorObserver] 行为观察器已注册 (blockBreak + attack + useItem + placeBlock + chat)");
    }

    private void notifyLearning(BehaviorEvent event) {
        for (Consumer<BehaviorEvent> listener : learningListeners) {
            listener.accept(event);
        }
    }

    private void onBlockBreak(String blockId, BlockState state) {
        blockBreakCounts.merge(blockId, 1, Integer::sum);
        pendingBehaviorUpdates.merge(blockId, 0.05, Double::sum);
        observationCount++;
        if (personalityStress != null) personalityStress.onPlayerInteraction(0.2);
        checkEvolution();
    }

    private void onEntityAttack(String entityType) {
        entityAttackCounts.merge(entityType, 1, Integer::sum);
        pendingBehaviorUpdates.merge(entityType, -0.03, Double::sum);
        observationCount++;
        if (personalityStress != null) personalityStress.onPlayerInteraction(0.3);
        checkEvolution();
    }

    private void onItemUse(String itemId) {
        pendingBehaviorUpdates.merge(itemId, 0.03, Double::sum);
        observationCount++;
        if (personalityStress != null) personalityStress.onPlayerInteraction(0.1);
        checkEvolution();
    }

    private void onBlockPlace(String blockId) {
        pendingBehaviorUpdates.merge(blockId, 0.04, Double::sum);
        observationCount++;
        if (personalityStress != null) personalityStress.onPlayerInteraction(0.15);
        checkEvolution();
    }

    private void onChatMessage(String message) {
        if (message == null) return;
        String lower = message.toLowerCase();
        List<String> keywords = extractItemKeywords(lower);
        chatKeywords.addAll(keywords);

        for (String keyword : keywords) {
            double delta = 0.03;
            if (lower.contains("喜欢") || lower.contains("想要") || lower.contains("优先") || lower.contains("like")
                    || lower.contains("want") || lower.contains("love")) {
                delta = 0.07;
            } else if (lower.contains("讨厌") || lower.contains("烦") || lower.contains("hate")
                    || lower.contains("dislike")) {
                delta = -0.07;
            }
            pendingChatUpdates.merge(keyword, delta, Double::sum);
        }
        observationCount++;
        if (personalityStress != null) personalityStress.onPlayerInteraction(0.5);
        checkEvolution();
    }

    private List<String> extractItemKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        Set<String> knownItems = Set.of(
                "钻石", "铁矿", "金矿", "煤矿", "红石", "青金石", "绿宝石",
                "diamond", "iron", "gold", "coal", "redstone", "lapis", "emerald",
                "橡木", "桦木", "石头", "沙", "玻璃", "雪", "冰",
                "oak", "birch", "stone", "sand", "glass", "snow", "ice",
                "小麦", "胡萝卜", "土豆", "苹果", "肉", "鱼",
                "wheat", "carrot", "potato", "apple", "meat", "fish",
                "剑", "斧", "镐", "铲", "锄",
                "sword", "axe", "pickaxe", "shovel", "hoe",
                "僵尸", "骷髅", "蜘蛛", "苦力怕", "末影人",
                "zombie", "skeleton", "spider", "creeper", "enderman"
        );

        for (String item : knownItems) {
            if (text.contains(item)) {
                keywords.add(item);
            }
        }
        return keywords;
    }

    private void checkEvolution() {
        if (observationCount >= EVOLVE_THRESHOLD) {
            triggerEvolution();
            observationCount = 0;
        }
    }

    public void triggerEvolution() {
        if (pendingBehaviorUpdates.isEmpty() && pendingChatUpdates.isEmpty()) return;

        Map<String, Double> commandUpdates = new HashMap<>();
        Map<String, Double> behaviorUpdates = new HashMap<>(pendingBehaviorUpdates);
        Map<String, Double> chatUpdates = new HashMap<>(pendingChatUpdates);

        characterManager.evolvePreferences(behaviorUpdates, commandUpdates, chatUpdates);

        pendingBehaviorUpdates.clear();
        pendingChatUpdates.clear();

        if (personalityStress != null && personalityStress.checkAndTrigger()) {
            characterManager.triggerStressEvolution();
            AIPlayerMod.LOGGER.info("[BehaviorObserver] 压力触发性格修改!");
        }

        AIPlayerMod.LOGGER.info("[BehaviorObserver] 性格演化已触发");
    }

    public String getBehaviorSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("已观察 ").append(observationCount).append(" 次行为 (阈值用完后触发演化)\n");
        if (!blockBreakCounts.isEmpty()) {
            sb.append("挖掘统计:\n");
            blockBreakCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        return sb.toString();
    }

    private String getHeldItemName(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) return "empty";
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private String getTimeOfDay(World world) {
        long time = world.getTimeOfDay() % 24000;
        if (time < 6000) return "morning";
        if (time < 12000) return "noon";
        if (time < 18000) return "evening";
        return "night";
    }
}
