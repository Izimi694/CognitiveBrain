package com.izimi.aiplayermod.reflexes;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class InnateReflexes {

    public static final float FLEE_HEALTH_THRESHOLD = 10.0f;
    public static final float RETREAT_HEALTH_THRESHOLD = 6.0f;
    public static final int EAT_FOOD_THRESHOLD = 6;
    public static final int HOSTILE_SCAN_RANGE = 10;
    public static final int LAVA_SCAN_RANGE = 3;
    public static final int SHELTER_SCAN_RANGE = 5;

    public enum ReflexType { SAFETY, NON_SAFETY, IDLE }

    public record ReflexResult(boolean handled, String reason) {
        public static ReflexResult no() { return new ReflexResult(false, null); }
        public static ReflexResult yes(String reason) { return new ReflexResult(true, reason); }
    }

    public ReflexResult checkSafety(ServerPlayerEntity bot) {
        ReflexResult r;

        r = checkFlee(bot);
        if (r.handled()) return r;

        r = checkEat(bot);
        if (r.handled()) return r;

        r = checkRetreatLowHealth(bot);
        if (r.handled()) return r;

        return ReflexResult.no();
    }

    public ReflexResult checkNonSafety(ServerPlayerEntity bot) {
        ReflexResult r;

        r = checkAvoidLava(bot);
        if (r.handled()) return r;

        r = checkSeekShelter(bot);
        if (r.handled()) return r;

        r = checkCollectNearbyItems(bot);
        if (r.handled()) return r;

        return ReflexResult.no();
    }

    public void doIdleAnimation(ServerPlayerEntity bot) {
        long tick = bot.age;
        if (tick % 40 < 20) {
            float yaw = bot.getYaw() + (float) (Math.sin(tick * 0.05) * 15);
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);
        }
        if (tick % 100 == 0) {
            Vec3d pos = bot.getPos();
            double angle = Math.random() * Math.PI * 2;
            double dist = 2.0 + Math.random() * 3.0;
            Vec3d target = pos.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                bot.setVelocity(new Vec3d(dx / len * 0.15, 0.08, dz / len * 0.15));
                bot.velocityModified = true;
            }
        }
    }

    private ReflexResult checkFlee(ServerPlayerEntity bot) {
        if (bot.getHealth() > FLEE_HEALTH_THRESHOLD) return ReflexResult.no();

        HostileEntity nearest = findNearestHostile(bot, HOSTILE_SCAN_RANGE);
        if (nearest == null) return ReflexResult.no();

        Vec3d botPos = bot.getPos();
        Vec3d monsterPos = nearest.getPos();
        Vec3d away = botPos.subtract(monsterPos).normalize().multiply(0.3);
        bot.setVelocity(new Vec3d(away.x, 0.1, away.z));
        bot.velocityModified = true;
        bot.jump();

        return ReflexResult.yes("flee:" + nearest.getType().getName().getString());
    }

    private ReflexResult checkEat(ServerPlayerEntity bot) {
        if (bot.getHungerManager().getFoodLevel() > EAT_FOOD_THRESHOLD) return ReflexResult.no();

        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) {
                int prevSlot = inv.selectedSlot;
                inv.selectedSlot = i;
                bot.swingHand(Hand.MAIN_HAND);
                return ReflexResult.yes("eat:" + stack.getItem().getName().getString());
            }
        }
        return ReflexResult.no();
    }

    private ReflexResult checkRetreatLowHealth(ServerPlayerEntity bot) {
        if (bot.getHealth() > RETREAT_HEALTH_THRESHOLD) return ReflexResult.no();

        HostileEntity nearest = findNearestHostile(bot, HOSTILE_SCAN_RANGE * 2);
        if (nearest == null) return ReflexResult.no();

        Vec3d botPos = bot.getPos();
        Vec3d monsterPos = nearest.getPos();
        Vec3d away = botPos.subtract(monsterPos).normalize().multiply(0.25);
        bot.setVelocity(new Vec3d(away.x, 0.05, away.z));
        bot.velocityModified = true;

        return ReflexResult.yes("retreat:" + nearest.getType().getName().getString());
    }

    private ReflexResult checkAvoidLava(ServerPlayerEntity bot) {
        BlockPos botPos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();

        for (int dx = -LAVA_SCAN_RANGE; dx <= LAVA_SCAN_RANGE; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -LAVA_SCAN_RANGE; dz <= LAVA_SCAN_RANGE; dz++) {
                    BlockPos check = botPos.add(dx, dy, dz);
                    if (world.getBlockState(check).isOf(Blocks.LAVA)) {
                        Vec3d away = Vec3d.ofCenter(botPos).subtract(Vec3d.ofCenter(check)).normalize();
                        if (away.lengthSquared() > 0) {
                            bot.setVelocity(away.multiply(0.2));
                            bot.velocityModified = true;
                            return ReflexResult.yes("avoid_lava");
                        }
                    }
                }
            }
        }
        return ReflexResult.no();
    }

    private ReflexResult checkSeekShelter(ServerPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        long time = world.getTimeOfDay() % 24000;
        if (time < 13000 || time > 23000) return ReflexResult.no();

        BlockPos botPos = bot.getBlockPos();
        if (hasSolidRoof(world, botPos)) return ReflexResult.no();

        for (int r = 1; r <= SHELTER_SCAN_RANGE; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos check = botPos.add(dx, 0, dz);
                    if (hasSolidRoof(world, check) && !world.getBlockState(check).isAir()) {
                        Vec3d dir = Vec3d.ofCenter(check).subtract(Vec3d.ofCenter(botPos)).normalize();
                        bot.setVelocity(dir.multiply(0.1));
                        bot.velocityModified = true;
                        return ReflexResult.yes("seek_shelter");
                    }
                }
            }
        }
        return ReflexResult.no();
    }

    private ReflexResult checkCollectNearbyItems(ServerPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        var items = world.getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                bot.getBoundingBox().expand(5),
                e -> !e.cannotPickup()
        );

        if (items.isEmpty()) return ReflexResult.no();

        var nearest = items.get(0);
        Vec3d dir = nearest.getPos().subtract(bot.getPos()).normalize();
        bot.setVelocity(dir.multiply(0.15));
        bot.velocityModified = true;

        return ReflexResult.yes("collect:" + nearest.getStack().getItem().getName().getString());
    }

    private HostileEntity findNearestHostile(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        List<HostileEntity> mobs = world.getEntitiesByClass(
                HostileEntity.class,
                bot.getBoundingBox().expand(range),
                e -> e.isAlive()
        );
        if (mobs.isEmpty()) return null;

        mobs.sort((a, b) -> Double.compare(
                a.squaredDistanceTo(bot),
                b.squaredDistanceTo(bot)
        ));
        return mobs.get(0);
    }

    private boolean hasSolidRoof(ServerWorld world, BlockPos pos) {
        BlockPos above = pos.up();
        return !world.getBlockState(above).isAir()
                && world.getBlockState(above).isOpaque();
    }
}
