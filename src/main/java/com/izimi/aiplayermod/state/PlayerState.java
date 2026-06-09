package com.izimi.aiplayermod.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerState {
    public long timestamp;
    public Player player;
    public World world;

    public record InvSlot(int index, String itemId, int count) {}

    public static class Player {
        public int[] position;
        public float health;
        public int hunger;
        public Map<String, Integer> inventory = new HashMap<>();
        public List<InvSlot> slots = new ArrayList<>();
    }

    public static class World {
        public String biome;
        public long timeOfDay;
        public List<String> nearbyEntities;
    }

    public PlayerState() {}

    public PlayerState(int x, int y, int z) {
        this.timestamp = System.currentTimeMillis();
        this.player = new Player();
        this.player.position = new int[]{x, y, z};
        this.world = new World();
    }
}
