package com.izimi.aiplayermod.brainstem.navigation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class BlockPosUtil {

    public static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.getSquaredDistance(b));
    }

    public static double distance2D(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static Vec3d toVec3d(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public static Vec3d toVec3dCenter(BlockPos pos) {
        return Vec3d.ofCenter(pos);
    }

    public static BlockPos fromVec3d(Vec3d vec) {
        return new BlockPos((int) Math.floor(vec.x), (int) Math.floor(vec.y), (int) Math.floor(vec.z));
    }

    public static BlockPos floor(Vec3d vec) {
        return BlockPos.ofFloored(vec);
    }

    public static Vec3i direction(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        return new Vec3i(dx, dy, dz);
    }

    public static boolean isWithinRadius(BlockPos center, BlockPos pos, double radius) {
        return center.getSquaredDistance(pos) <= radius * radius;
    }

    public static BlockPos getMidpoint(BlockPos a, BlockPos b) {
        return new BlockPos(
                (a.getX() + b.getX()) / 2,
                (a.getY() + b.getY()) / 2,
                (a.getZ() + b.getZ()) / 2
        );
    }
}
