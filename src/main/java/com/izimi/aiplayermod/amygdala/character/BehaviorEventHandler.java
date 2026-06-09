package com.izimi.aiplayermod.amygdala.character;

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
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import com.izimi.aiplayermod.AIPlayerMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BehaviorEventHandler {

    private final BehaviorStats stats;
    private final List<Consumer<BehaviorEvent>> learningListeners = new ArrayList<>();

    public BehaviorEventHandler(BehaviorStats stats) {
        this.stats = stats;
    }

    public void addLearningListener(Consumer<BehaviorEvent> listener) {
        learningListeners.add(listener);
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity sp) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                stats.recordBlockBreak(blockId);

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

        if (keywords.isEmpty()) {
            notifyLearning(new BehaviorEvent("chat", "chat",
                    "generic", System.currentTimeMillis(), "chat", "any"));
        } else {
            for (String keyword : keywords) {
                notifyLearning(new BehaviorEvent("chat", "chat",
                        keyword, System.currentTimeMillis(), "chat", "any"));
            }
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
