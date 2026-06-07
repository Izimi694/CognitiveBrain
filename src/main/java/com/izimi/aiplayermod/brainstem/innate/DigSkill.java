package com.izimi.aiplayermod.brainstem.innate;

import com.izimi.aiplayermod.brainstem.skill.Skill;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Map;

public class DigSkill extends Skill {
    private BlockPos currentTarget = null;
    private int breakingTicks = 0;
    private static final int BREAK_TIME_TICKS = 40;

    public DigSkill() {
        super("dig", "挖掘", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        if (bot == null || world == null) return false;
        return findDigTarget(world, bot) != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        BlockPos target = findDigTarget(world, bot);
        if (target == null) {
            return SkillResult.success("附近没有可挖掘的方块");
        }

        double distance = bot.getPos().squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distance > 25.0) {
            return SkillResult.partial(0.3, "距离目标太远，需要移动");
        }

        equipBestTool(world, bot, target);

        breakingTicks++;

        if (breakingTicks >= BREAK_TIME_TICKS) {
            breakingTicks = 0;
            world.breakBlock(target, true, bot);
            currentTarget = null;
            return SkillResult.success("成功挖掘方块");
        }

        if (Math.random() < 0.1) {
            world.setBlockBreakingInfo(bot.getId(), target, (int)(breakingTicks * 10.0 / BREAK_TIME_TICKS));
        }

        return SkillResult.partial(0.6, "正在挖掘");
    }

    private BlockPos findDigTarget(ServerWorld world, ServerPlayerEntity bot) {
        if (currentTarget != null) {
            BlockState state = world.getBlockState(currentTarget);
            if (!state.isAir() && !state.isOf(net.minecraft.block.Blocks.BEDROCK)) {
                return currentTarget;
            }
        }

        BlockPos botPos = bot.getBlockPos();
        for (int dy = 4; dy >= -1; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && state.getHardness(world, pos) >= 0
                            && !state.isOf(net.minecraft.block.Blocks.BEDROCK)
                            && !state.isOf(net.minecraft.block.Blocks.WATER)
                            && !state.isOf(net.minecraft.block.Blocks.LAVA)) {
                        currentTarget = pos;
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void equipBestTool(ServerWorld world, ServerPlayerEntity bot, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        boolean isWood = state.isIn(net.minecraft.registry.tag.BlockTags.AXE_MINEABLE);
        boolean isStone = state.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE);
        boolean isDirt = state.isIn(net.minecraft.registry.tag.BlockTags.SHOVEL_MINEABLE);
        boolean isLeaves = state.isIn(net.minecraft.registry.tag.BlockTags.HOE_MINEABLE);

        ItemStack bestTool = ItemStack.EMPTY;
        float bestSpeed = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestTool = stack;
            }
        }

        if (!bestTool.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (bot.getInventory().getStack(i) == bestTool) {
                    bot.getInventory().selectedSlot = i;
                    break;
                }
            }
        }
    }
}
