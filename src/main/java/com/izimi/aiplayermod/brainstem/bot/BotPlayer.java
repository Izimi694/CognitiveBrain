package com.izimi.aiplayermod.brainstem.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class BotPlayer {
    private final ServerPlayerEntity playerEntity;

    private BotPlayer(ServerPlayerEntity playerEntity) {
        this.playerEntity = playerEntity;
    }

    public static BotPlayer create(MinecraftServer server, ServerWorld world, GameProfile profile) {
        SyncedClientOptions options = SyncedClientOptions.createDefault();
        ServerPlayerEntity entity = server.getPlayerManager().createPlayer(profile, options);

        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
        ServerPlayNetworkHandler handler = new ServerPlayNetworkHandler(server, connection, entity, clientData);
        entity.networkHandler = handler;

        return new BotPlayer(entity);
    }

    public ServerPlayerEntity asEntity() {
        return playerEntity;
    }

    public boolean isRemoved() {
        return playerEntity.isRemoved();
    }

    public void setRemoved(net.minecraft.entity.Entity.RemovalReason reason) {
        playerEntity.setRemoved(reason);
    }

    public void setPos(double x, double y, double z) {
        playerEntity.setPos(x, y, z);
    }

    public void setPitch(float pitch) {
        playerEntity.setPitch(pitch);
    }

    public void setYaw(float yaw) {
        playerEntity.setYaw(yaw);
    }

    public void setHeadYaw(float headYaw) {
        playerEntity.setHeadYaw(headYaw);
    }

    public void setHealth(float health) {
        playerEntity.setHealth(health);
    }

    public void tick() {
        playerEntity.tick();
    }

    public MinecraftServer getServer() {
        return playerEntity.getServer();
    }

    public BlockPos getBlockPos() {
        return playerEntity.getBlockPos();
    }

    public Vec3d getPos() {
        return playerEntity.getPos();
    }

    public ServerWorld getServerWorld() {
        return playerEntity.getServerWorld();
    }

    public boolean isOnGround() {
        return playerEntity.isOnGround();
    }

    public void jump() {
        playerEntity.jump();
    }

    public net.minecraft.entity.player.PlayerInventory getInventory() {
        return playerEntity.getInventory();
    }

    public net.minecraft.entity.player.HungerManager getHungerManager() {
        return playerEntity.getHungerManager();
    }

    public float getHealth() {
        return playerEntity.getHealth();
    }

    public void setVelocity(Vec3d velocity) {
        playerEntity.setVelocity(velocity);
        playerEntity.velocityModified = true;
    }

    public void attack(net.minecraft.entity.Entity target) {
        playerEntity.attack(target);
    }

    public void swingHand(net.minecraft.util.Hand hand) {
        playerEntity.swingHand(hand);
    }

    public void lookAtEntity(net.minecraft.entity.Entity target) {
        double dx = target.getX() - playerEntity.getX();
        double dy = target.getEyeY() - playerEntity.getEyeY();
        double dz = target.getZ() - playerEntity.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));
        playerEntity.setYaw(yaw);
        playerEntity.setHeadYaw(yaw);
        playerEntity.setPitch(pitch);
    }

    public ServerPlayNetworkHandler getNetworkHandler() {
        return playerEntity.networkHandler;
    }
}
