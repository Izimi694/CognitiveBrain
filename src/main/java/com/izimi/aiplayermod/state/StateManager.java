package com.izimi.aiplayermod.state;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraft.registry.Registries;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class StateManager {

    private final UUID botId;

    public StateManager() {
        this(null);
    }

    public StateManager(UUID botId) {
        this.botId = botId;
    }

    private Path statePath() {
        return botId != null
                ? FileUtil.getBotStateDir(botId).resolve("current.json")
                : FileUtil.getStatePath();
    }

    public PlayerState collectState(ServerPlayerEntity bot) {
        if (bot == null) return null;

        PlayerState state = new PlayerState();
        state.timestamp = System.currentTimeMillis();

        BlockPos pos = bot.getBlockPos();
        state.player = new PlayerState.Player();
        state.player.position = new int[]{pos.getX(), pos.getY(), pos.getZ()};
        state.player.health = bot.getHealth();
        state.player.hunger = bot.getHungerManager().getFoodLevel();

        state.player.inventory = new HashMap<>();
        state.player.slots = new ArrayList<>();
        var mainInv = bot.getInventory().main;
        for (int i = 0; i < mainInv.size(); i++) {
            var stack = mainInv.get(i);
            if (stack.isEmpty()) continue;
            String name = Registries.ITEM.getId(stack.getItem()).toString();
            int count = stack.getCount();
            state.player.inventory.merge(name, count, Integer::sum);
            state.player.slots.add(new PlayerState.InvSlot(i, name, count));
        }

        World world = bot.getWorld();
        state.world = new PlayerState.World();
        if (world != null) {
            state.world.timeOfDay = world.getTimeOfDay();
            state.world.biome = world.getBiome(pos).getIdAsString();
            state.world.nearbyEntities = new ArrayList<>();
        }

        return state;
    }

    public void saveState(ServerPlayerEntity bot) {
        PlayerState state = collectState(bot);
        if (state != null) {
            Path path = statePath();
            path.getParent().toFile().mkdirs();
            JsonUtil.writeToFileSafeAtomic(path, state);
        }
    }

    public PlayerState loadState() {
        return JsonUtil.readFromFileSafe(statePath(), PlayerState.class);
    }
}
