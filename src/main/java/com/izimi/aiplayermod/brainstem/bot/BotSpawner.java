package com.izimi.aiplayermod.brainstem.bot;

import com.izimi.aiplayermod.AIPlayerMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class BotSpawner {
    private static final UUID BOT_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String BOT_NAME = "AI_Assistant";

    private BotPlayer bot;
    private boolean isSpawned = false;

    public BotPlayer getBot() {
        return bot;
    }

    public ServerPlayerEntity getBotEntity() {
        return bot != null ? bot.asEntity() : null;
    }

    public boolean isSpawned() {
        return isSpawned && bot != null && !bot.isRemoved();
    }

    public boolean spawn(MinecraftServer server, ServerWorld world, Vec3d position) {
        if (isSpawned) return false;

        try {
            GameProfile profile = new GameProfile(BOT_UUID, BOT_NAME);
            bot = BotPlayer.create(server, world, profile);

            ServerPlayerEntity entity = bot.asEntity();
            entity.setPos(position.x, position.y, position.z);
            entity.setPitch(0);
            entity.setYaw(0);

            world.onPlayerConnected(entity);

            PlayerManager playerManager = server.getPlayerManager();
            playerManager.getPlayerList().add(entity);

            entity.setHealth(20.0f);
            entity.getHungerManager().setFoodLevel(20);

            isSpawned = true;
            AIPlayerMod.LOGGER.info("[BotSpawner] Bot已生成在 ({}, {}, {})", position.x, position.y, position.z);
            return true;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[BotSpawner] Bot生成失败", e);
            isSpawned = false;
            bot = null;
            return false;
        }
    }

    public boolean despawn() {
        if (!isSpawned || bot == null) return false;

        try {
            MinecraftServer server = bot.getServer();
            if (server != null) {
                server.getPlayerManager().remove(bot.asEntity());
            }
            bot.setRemoved(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            bot = null;
            isSpawned = false;
            AIPlayerMod.LOGGER.info("[BotSpawner] Bot已移除");
            return true;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[BotSpawner] Bot移除失败", e);
            return false;
        }
    }

    public BlockPos getBotPosition() {
        if (bot == null) return BlockPos.ORIGIN;
        return bot.getBlockPos();
    }

    public ServerWorld getBotWorld() {
        if (bot == null) return null;
        return bot.getServerWorld();
    }
}
