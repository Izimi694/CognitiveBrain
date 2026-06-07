package com.izimi.aiplayermod.state;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StateManager {

    public StateManager() {
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
        var mainInv = bot.getInventory().main;
        for (var stack : mainInv) {
            if (stack.isEmpty()) continue;
            String name = stack.getItem().getName().getString();
            int count = stack.getCount();
            state.player.inventory.merge(name, count, Integer::sum);
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
            JsonUtil.writeToFileSafeAtomic(FileUtil.getStatePath(), state);
        }
    }

    public PlayerState loadState() {
        return JsonUtil.readFromFileSafe(FileUtil.getStatePath(), PlayerState.class);
    }
}
