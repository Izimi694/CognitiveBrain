package com.izimi.aiplayermod.brainstem.bot;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.brainstem.adapter.TemporalScaler;
import com.izimi.aiplayermod.util.FileUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BotManager {
    private final Map<UUID, BotInstance> bots = new LinkedHashMap<>();

    public BotInstance spawn(String name, MinecraftServer server, ServerWorld world, Vec3d position) {
        UUID botId = UUID.randomUUID();
        GameProfile profile = new GameProfile(botId, name);
        BotPlayer botPlayer = BotPlayer.create(server, world, profile);

        ServerPlayerEntity entity = botPlayer.asEntity();
        entity.setPos(position.x, position.y, position.z);
        entity.setPitch(0);
        entity.setYaw(0);

        world.onPlayerConnected(entity);

        PlayerManager playerManager = server.getPlayerManager();
        playerManager.getPlayerList().add(entity);

        entity.setHealth(20.0f);
        entity.getHungerManager().setFoodLevel(20);

        BotInstance instance = new BotInstance(botId, name, botPlayer);

        FileUtil.getBotDir(botId).toFile().mkdirs();

        copyReflexesFromMentor(instance);

        bots.put(botId, instance);
        AIPlayerMod.LOGGER.info("[BotManager] Bot已生成: {} ({})", name, botId);
        return instance;
    }

    public boolean despawn(UUID botId) {
        BotInstance instance = bots.get(botId);
        if (instance == null) return false;

        try {
            MinecraftServer server = instance.getBotPlayer().getServer();
            if (server != null) {
                server.getPlayerManager().remove(instance.asEntity());
            }
            instance.getBotPlayer().setRemoved(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            bots.remove(botId);
            AIPlayerMod.LOGGER.info("[BotManager] Bot已移除: {} ({})", instance.getBotName(), botId);
            return true;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[BotManager] Bot移除失败: {}", botId, e);
            return false;
        }
    }

    public void despawnAll() {
        List<UUID> ids = new ArrayList<>(bots.keySet());
        for (UUID id : ids) {
            despawn(id);
        }
    }

    public BotInstance getById(UUID botId) {
        return bots.get(botId);
    }

    public BotInstance getByName(String name) {
        String lower = name.toLowerCase();
        for (BotInstance instance : bots.values()) {
            if (instance.getBotName().toLowerCase().equals(lower)) {
                return instance;
            }
        }
        // Partial match fallback
        for (BotInstance instance : bots.values()) {
            if (instance.getBotName().toLowerCase().contains(lower)) {
                return instance;
            }
        }
        return null;
    }

    public BotInstance getNearest(ServerPlayerEntity player) {
        if (player == null || bots.isEmpty()) return null;
        ServerWorld world = player.getServerWorld();

        BotInstance nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BotInstance instance : bots.values()) {
            ServerPlayerEntity bot = instance.asEntity();
            if (bot == null || bot.isRemoved()) continue;
            if (bot.getServerWorld() != world) continue;

            double dist = bot.squaredDistanceTo(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = instance;
            }
        }

        return nearest;
    }

    public List<BotInstance> getAll() {
        return new ArrayList<>(bots.values());
    }

    public List<BotInstance> getNearby(ServerPlayerEntity player, double radius) {
        if (player == null) return List.of();
        ServerWorld world = player.getServerWorld();
        double radiusSq = radius * radius;

        return bots.values().stream()
                .filter(i -> {
                    ServerPlayerEntity bot = i.asEntity();
                    return bot != null && !bot.isRemoved()
                            && bot.getServerWorld() == world
                            && bot.squaredDistanceTo(player) <= radiusSq;
                })
                .collect(Collectors.toList());
    }

    public boolean isEmpty() {
        return bots.isEmpty();
    }

    public int getCount() {
        return bots.size();
    }

    public void tickAll(MinecraftServer server) {
        List<BotInstance> active = new ArrayList<>(bots.values());
        for (BotInstance instance : active) {
            if (!instance.isSpawned()) {
                bots.remove(instance.getBotId());
                continue;
            }

            TemporalScaler scaler = instance.getTemporalScaler();
            if (scaler != null && scaler.getSpeed() < 0.01f) {
                continue;
            }

            instance.tick(server);
        }
    }

    private void copyReflexesFromMentor(BotInstance newBot) {
        if (bots.isEmpty()) return;

        BotInstance mentor = bots.values().iterator().next();
        Path mentorDir = FileUtil.getBotConditionedDir(mentor.getBotId());
        Path newBotDir = FileUtil.getBotConditionedDir(newBot.getBotId());

        if (!Files.exists(mentorDir)) return;

        try {
            Files.createDirectories(newBotDir);
            try (var stream = Files.walk(mentorDir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(src -> {
                            try {
                                Path rel = mentorDir.relativize(src);
                                Path dest = newBotDir.resolve(rel);
                                Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                                ConditionedReflex.resetReflexWeights(dest);
                            } catch (IOException e) {
                                AIPlayerMod.LOGGER.warn("[BotManager] 复制反射失败: {}", src, e);
                            }
                        });
            }
            AIPlayerMod.LOGGER.info("[BotManager] 从 {} 复制 {} 个反射到 {}",
                    mentor.getBotName(), Files.list(mentorDir).count(), newBot.getBotName());
        } catch (IOException e) {
            AIPlayerMod.LOGGER.warn("[BotManager] 冷启动反射复制失败", e);
        }
    }

    public void notifyReflexSuccess(ServerPlayerEntity executor, String category) {
        if (executor == null || category == null) return;
        ServerWorld world = executor.getServerWorld();
        for (BotInstance observer : bots.values()) {
            if (!observer.isSpawned()) continue;
            ServerPlayerEntity obsEntity = observer.asEntity();
            if (obsEntity == null || obsEntity == executor) continue;
            if (obsEntity.getServerWorld() != world) continue;
            if (obsEntity.squaredDistanceTo(executor) > 900) continue; // 30 blocks
            observer.getConditionedReflex().observePeerSuccess(category);
        }
    }

    public boolean isSpawned() {
        return !bots.isEmpty();
    }
}
