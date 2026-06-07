package com.izimi.aiplayermod.amygdala.reflexes;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class MinecraftReflexEvaluator {

    public boolean matchesAll(List<ReflexTrigger> triggers, ServerPlayerEntity bot) {
        if (bot == null) return false;
        for (ReflexTrigger t : triggers) {
            if (!matches(t, bot)) return false;
        }
        return true;
    }

    public boolean matches(ReflexTrigger trigger, ServerPlayerEntity bot) {
        if (bot == null) return false;
        return switch (trigger.type()) {
            case HEALTH_BELOW -> checkHealthBelow(bot, trigger.value());
            case HUNGER_BELOW -> checkHungerBelow(bot, trigger.value());
            case MONSTER_NEARBY -> checkMonsterNearby(bot, trigger.range());
            case LAVA_NEARBY -> checkLavaNearby(bot, trigger.range());
            case TIME_OF_DAY -> checkTimeOfDay(bot, trigger.range());
            case ITEM_NEARBY -> checkItemNearby(bot, trigger.range());
            case CHAT_PRESENCE -> checkChatPending(bot, trigger.value());
        };
    }

    private boolean checkHealthBelow(ServerPlayerEntity bot, double threshold) {
        return bot.getHealth() <= threshold;
    }

    private boolean checkHungerBelow(ServerPlayerEntity bot, double threshold) {
        return bot.getHungerManager().getFoodLevel() <= (int) threshold;
    }

    private boolean checkMonsterNearby(ServerPlayerEntity bot, int range) {
        return findNearestHostile(bot, range) != null;
    }

    private boolean checkLavaNearby(ServerPlayerEntity bot, int range) {
        BlockPos botPos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (world.getBlockState(botPos.add(dx, dy, dz)).isOf(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkTimeOfDay(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        long time = world.getTimeOfDay() % 24000;
        if (time < 13000 || time > 23000) return false;
        BlockPos botPos = bot.getBlockPos();
        return !hasSolidRoof(world, botPos);
    }

    private boolean checkItemNearby(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        var items = world.getEntitiesByClass(
                ItemEntity.class,
                bot.getBoundingBox().expand(range),
                e -> !e.cannotPickup()
        );
        return !items.isEmpty();
    }

    public HostileEntity findNearestHostile(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        List<HostileEntity> mobs = world.getEntitiesByClass(
                HostileEntity.class,
                bot.getBoundingBox().expand(range),
                e -> e.isAlive()
        );
        if (mobs.isEmpty()) return null;
        mobs.sort((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)));
        return mobs.get(0);
    }

    public boolean hasSolidRoof(ServerWorld world, BlockPos pos) {
        BlockPos above = pos.up();
        return !world.getBlockState(above).isAir() && world.getBlockState(above).isOpaque();
    }

    private boolean checkChatPending(ServerPlayerEntity bot, double timeoutSecs) {
        return com.izimi.aiplayermod.AIPlayerMod.hasPendingChat(timeoutSecs);
    }

    public boolean hasFoodInHotbar(ServerPlayerEntity bot) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) return true;
        }
        return false;
    }

    public BlockPos findNearestLava(ServerPlayerEntity bot, int range) {
        BlockPos botPos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();
        for (int r = 1; r <= range; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = botPos.add(dx, dy, dz);
                        if (world.getBlockState(pos).isOf(Blocks.LAVA)) return pos;
                    }
                }
            }
        }
        return null;
    }

    public ItemEntity findNearestItem(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        var items = world.getEntitiesByClass(
                ItemEntity.class,
                bot.getBoundingBox().expand(range),
                e -> !e.cannotPickup()
        );
        if (items.isEmpty()) return null;
        items.sort((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)));
        return items.get(0);
    }
}
