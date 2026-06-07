package com.izimi.aiplayermod.brainstem.navigation;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class NavigationController {
    private List<BlockPos> currentPath = null;
    private int currentPathIndex = 0;
    private BlockPos lastTarget = null;
    private int stuckTicks = 0;
    private static final int STUCK_THRESHOLD = 60;
    private static final double ARRIVAL_THRESHOLD = 1.5;

    public boolean navigateTo(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) return false;

        double distance = bot.getPos().distanceTo(Vec3d.ofCenter(target));
        if (distance < ARRIVAL_THRESHOLD) {
            currentPath = null;
            currentPathIndex = 0;
            return true;
        }

        if (currentPath == null || lastTarget == null || !lastTarget.equals(target) || stuckTicks > STUCK_THRESHOLD) {
            AStarPathfinder pathfinder = new AStarPathfinder(bot.getWorld());
            currentPath = pathfinder.findPath(bot.getBlockPos(), target);
            currentPathIndex = 0;
            lastTarget = target;
            stuckTicks = 0;
        }

        if (currentPath == null || currentPath.isEmpty()) return false;

        if (currentPathIndex >= currentPath.size()) {
            currentPathIndex = currentPath.size() - 1;
        }

        BlockPos waypoint = currentPath.get(currentPathIndex);
        double waypointDist = bot.getPos().distanceTo(Vec3d.ofCenter(waypoint));

        if (waypointDist < ARRIVAL_THRESHOLD) {
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size()) {
                return true;
            }
            waypoint = currentPath.get(currentPathIndex);
        }

        Vec3d direction = Vec3d.ofCenter(waypoint).subtract(bot.getPos());
        double horiDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        if (horiDist > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);

            double speed = 0.3;
            if (bot.isOnGround() && waypoint.getY() > bot.getBlockY()) {
                bot.jump();
            }

            Vec3d velocity = new Vec3d(
                    direction.x / horiDist * speed,
                    direction.y * 0.2,
                    direction.z / horiDist * speed
            );
            bot.setVelocity(velocity);
            bot.velocityModified = true;

            BlockPos currentPos = bot.getBlockPos();
            if (direction.y > 0.5 && bot.isOnGround() && !bot.getWorld().getBlockState(currentPos.up()).isAir()) {
                bot.jump();
            }
        }

        BlockPos currentBlock = bot.getBlockPos();
        if (currentBlock.getSquaredDistance(waypoint) < 0.1) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        return false;
    }

    public void stopNavigation() {
        currentPath = null;
        currentPathIndex = 0;
        lastTarget = null;
        stuckTicks = 0;
    }

    public boolean isNavigating() {
        return currentPath != null && !currentPath.isEmpty();
    }
}
