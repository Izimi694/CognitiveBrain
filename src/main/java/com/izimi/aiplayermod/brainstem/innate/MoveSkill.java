package com.izimi.aiplayermod.brainstem.innate;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.brainstem.navigation.NavigationController;
import com.izimi.aiplayermod.brainstem.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class MoveSkill extends Skill {
    private final NavigationController navigationController;

    public MoveSkill() {
        super("move", "移动", "innate");
        this.navigationController = new NavigationController();
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        return bot != null && world != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        BlockPos target = determineTarget(world, bot, context);
        if (target == null) {
            return SkillResult.fail("无法确定目标位置");
        }

        boolean arrived = navigationController.navigateTo(bot, target);
        if (arrived) {
            return SkillResult.success("已到达目标位置");
        } else {
            return SkillResult.partial(0.5, "正在移动中");
        }
    }

    private BlockPos determineTarget(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        if (context != null && context.containsKey("targetPos")) {
            Object pos = context.get("targetPos");
            if (pos instanceof BlockPos) return (BlockPos) pos;
            if (pos instanceof int[] arr && arr.length == 3) return new BlockPos(arr[0], arr[1], arr[2]);
        }

        BlockPos botPos = bot.getBlockPos();
        int dx = (Math.random() > 0.5 ? 1 : -1) * (5 + (int)(Math.random() * 20));
        int dz = (Math.random() > 0.5 ? 1 : -1) * (5 + (int)(Math.random() * 20));
        BlockPos candidate = botPos.add(dx, 0, dz);

        int surfaceY = findSurfaceY(world, candidate);
        if (surfaceY >= world.getBottomY()) {
            return new BlockPos(candidate.getX(), surfaceY, candidate.getZ());
        }

        return botPos.add(dx > 0 ? dx : -dx, 0, dz > 0 ? dz : -dz);
    }

    private int findSurfaceY(ServerWorld world, BlockPos pos) {
        int maxY = world.getTopY();
        for (int y = maxY - 1; y >= world.getBottomY(); y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(check).isAir()
                    && world.getBlockState(check.up()).isAir()
                    && world.getBlockState(check.up(2)).isAir()) {
                return y + 1;
            }
        }
        return world.getBottomY();
    }
}
