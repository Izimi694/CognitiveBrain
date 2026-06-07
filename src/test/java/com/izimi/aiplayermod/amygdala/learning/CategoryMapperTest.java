package com.izimi.aiplayermod.amygdala.learning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CategoryMapperTest {

    @Test
    @DisplayName("dig tree_log: oak_log → dig_tree_log")
    void digTreeLogOakLog() {
        assertEquals("dig_tree_log", CategoryMapper.getCategory("dig", "oak_log"));
    }

    @Test
    @DisplayName("dig tree_log: birch_wood → dig_tree_log")
    void digTreeLogBirchWood() {
        assertEquals("dig_tree_log", CategoryMapper.getCategory("dig", "birch_wood"));
    }

    @Test
    @DisplayName("dig tree_log: crimson_stem → dig_tree_log")
    void digTreeLogCrimsonStem() {
        assertEquals("dig_tree_log", CategoryMapper.getCategory("dig", "crimson_stem"));
    }

    @Test
    @DisplayName("dig tree_log: bamboo_block → dig_tree_log")
    void digTreeLogBambooBlock() {
        assertEquals("dig_tree_log", CategoryMapper.getCategory("dig", "bamboo_block"));
    }

    @Test
    @DisplayName("dig ore: iron_ore → dig_ore")
    void digOreIronOre() {
        assertEquals("dig_ore", CategoryMapper.getCategory("dig", "iron_ore"));
    }

    @Test
    @DisplayName("dig ore: ancient_debris → dig_ore")
    void digOreAncientDebris() {
        assertEquals("dig_ore", CategoryMapper.getCategory("dig", "ancient_debris"));
    }

    @Test
    @DisplayName("dig ore: diamond_ore → dig_ore")
    void digOreDiamondOre() {
        assertEquals("dig_ore", CategoryMapper.getCategory("dig", "diamond_ore"));
    }

    @Test
    @DisplayName("attack hostile: zombie → attack_hostile")
    void attackHostileZombie() {
        assertEquals("attack_hostile", CategoryMapper.getCategory("attack", "zombie"));
    }

    @Test
    @DisplayName("attack hostile: creeper → attack_hostile")
    void attackHostileCreeper() {
        assertEquals("attack_hostile", CategoryMapper.getCategory("attack", "creeper"));
    }

    @Test
    @DisplayName("attack hostile: skeleton → attack_hostile")
    void attackHostileSkeleton() {
        assertEquals("attack_hostile", CategoryMapper.getCategory("attack", "skeleton"));
    }

    @Test
    @DisplayName("attack passive: cow → attack_passive")
    void attackPassiveCow() {
        assertEquals("attack_passive", CategoryMapper.getCategory("attack", "cow"));
    }

    @Test
    @DisplayName("unknown target returns raw action")
    void unknownTargetReturnsRawAction() {
        assertEquals("dig", CategoryMapper.getCategory("dig", "nonexistent_block_xyz"));
    }

    @Test
    @DisplayName("null target returns raw action")
    void nullTargetReturnsRawAction() {
        assertEquals("attack", CategoryMapper.getCategory("attack", null));
    }

    @Test
    @DisplayName("dig common_block: stone → dig_common_block")
    void digCommonBlockStone() {
        assertEquals("dig_common_block", CategoryMapper.getCategory("dig", "stone"));
    }

    @Test
    @DisplayName("dig crop: wheat → dig_crop")
    void digCropWheat() {
        assertEquals("dig_crop", CategoryMapper.getCategory("dig", "wheat"));
    }

    @Test
    @DisplayName("first matching category wins (log before ore for items with _log with _ore)")
    void firstMatchWins() {
        assertEquals("dig_tree_log", CategoryMapper.getCategory("dig", "birch_log"));
    }

    @Test
    @DisplayName("getCategoryDisplayName returns Chinese names for known categories")
    void displayNames() {
        assertEquals("砍树", CategoryMapper.getCategoryDisplayName("dig_tree_log"));
        assertEquals("挖矿", CategoryMapper.getCategoryDisplayName("dig_ore"));
        assertEquals("打怪", CategoryMapper.getCategoryDisplayName("attack_hostile"));
        assertEquals("挖掘", CategoryMapper.getCategoryDisplayName("dig"));
        assertEquals("unknown_category", CategoryMapper.getCategoryDisplayName("unknown_category"));
    }

    @Test
    @DisplayName("getCategoryRules returns unmodifiable map with all 6 categories")
    void getCategoryRules() {
        Map<String, List<String>> rules = CategoryMapper.getCategoryRules();
        assertNotNull(rules);
        assertEquals(6, rules.size());
        assertTrue(rules.containsKey("tree_log"));
        assertTrue(rules.containsKey("ore"));
        assertTrue(rules.containsKey("crop"));
        assertTrue(rules.containsKey("common_block"));
        assertTrue(rules.containsKey("hostile"));
        assertTrue(rules.containsKey("passive"));

        assertThrows(UnsupportedOperationException.class, () -> rules.put("new_cat", List.of()));
    }
}
