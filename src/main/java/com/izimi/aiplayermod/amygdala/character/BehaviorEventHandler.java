package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.learning.BehaviorEvent;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BehaviorEventHandler {

    private final BehaviorStats stats;
    private final BehaviorAnalyzer analyzer;
    private final List<Consumer<BehaviorEvent>> learningListeners = new ArrayList<>();

    public BehaviorEventHandler(BehaviorStats stats, BehaviorAnalyzer analyzer) {
        this.stats = stats;
        this.analyzer = analyzer;
    }

    public void addLearningListener(Consumer<BehaviorEvent> listener) {
        learningListeners.add(listener);
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity sp) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                stats.recordBlockBreak(blockId);
                analyzer.recordEvent(blockId, 0.05, 0, 0.2);

                String heldItem = getHeldItemName(sp);
                String timeOfDay = getTimeOfDay(world);
                notifyLearning(new BehaviorEvent(sp.getName().getString(), "dig",
                        blockId, System.currentTimeMillis(), heldItem, timeOfDay));
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp && entity instanceof LivingEntity le) {
                String entityType = le.getType().getName().getString();
                stats.recordEntityAttack(entityType);
                analyzer.recordEvent(entityType, -0.03, 0, 0.3);

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
                stats.recordItemUse(itemId);
                analyzer.recordEvent(itemId, 0.03, 0, 0.1);

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
                stats.recordBlockPlace(blockId);
                analyzer.recordEvent(blockId, 0.04, 0, 0.15);

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

        AIPlayerMod.LOGGER.info("[BehaviorEventHandler] 行为观察器已注册 (blockBreak + attack + useItem + placeBlock + chat)");
    }

    private void onChatMessage(String message) {
        if (message == null) return;
        String lower = message.toLowerCase();
        List<String> keywords = extractItemKeywords(lower);
        stats.recordChatKeywords(keywords);

        for (String keyword : keywords) {
            double delta = 0.03;
            if (lower.contains("喜欢") || lower.contains("想要") || lower.contains("优先") || lower.contains("like")
                    || lower.contains("want") || lower.contains("love")) {
                delta = 0.07;
            } else if (lower.contains("讨厌") || lower.contains("烦") || lower.contains("hate")
                    || lower.contains("dislike")) {
                delta = -0.07;
            }
            analyzer.recordEvent(keyword, 0, delta, 0.5);
        }

        if (keywords.isEmpty()) {
            analyzer.recordEvent("chat", 0, 0, 0.5);
        }
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

    private void notifyLearning(BehaviorEvent event) {
        for (Consumer<BehaviorEvent> listener : learningListeners) {
            listener.accept(event);
        }
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
