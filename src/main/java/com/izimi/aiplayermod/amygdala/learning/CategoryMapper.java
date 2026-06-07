package com.izimi.aiplayermod.amygdala.learning;

import java.util.*;

public class CategoryMapper {

    private static final Map<String, List<String>> CATEGORY_RULES = new LinkedHashMap<>();

    static {
        CATEGORY_RULES.put("tree_log",
                List.of("_log", "_wood", "stem", "hyphae", "bamboo_block"));
        CATEGORY_RULES.put("ore",
                List.of("_ore", "ancient_debris", "nether_gold"));
        CATEGORY_RULES.put("crop",
                List.of("wheat", "carrot", "potato", "beetroot", "melon", "pumpkin",
                        "sugar_cane", "cocoa", "nether_wart", "kelp"));
        CATEGORY_RULES.put("common_block",
                List.of("_planks", "stone", "cobblestone", "dirt", "sand", "gravel",
                        "netherrack", "end_stone", "granite", "diorite", "andesite",
                        "deepslate", "tuff", "calcite"));
        CATEGORY_RULES.put("hostile",
                List.of("zombie", "skeleton", "creeper", "spider", "enderman",
                        "witch", "blaze", "ghast", "wither", "slime", "magma_cube",
                        "piglin", "hoglin", "pillager", "vindicator", "drowned"));
        CATEGORY_RULES.put("passive",
                List.of("villager", "sheep", "cow", "pig", "chicken", "horse",
                        "donkey", "llama", "rabbit", "fox", "wolf", "cat",
                        "panda", "turtle", "dolphin"));
    }

    public static String getCategory(String action, String target) {
        if (target == null) return action;
        String lower = target.toLowerCase();

        for (var entry : CATEGORY_RULES.entrySet()) {
            String category = entry.getKey();
            for (String pattern : entry.getValue()) {
                if (lower.contains(pattern.toLowerCase())) {
                    return action + "_" + category;
                }
            }
        }

        return action;
    }

    public static String getCategoryDisplayName(String categoryId) {
        return switch (categoryId) {
            case "dig_tree_log" -> "砍树";
            case "dig_ore" -> "挖矿";
            case "dig_crop" -> "收割作物";
            case "dig_common_block" -> "挖掘方块";
            case "attack_hostile" -> "打怪";
            case "attack_passive" -> "攻击动物";
            case "use_item" -> "使用物品";
            case "place_block" -> "放置方块";
            case "dig" -> "挖掘";
            case "attack" -> "攻击";
            default -> categoryId;
        };
    }

    public static String getCategoryName(String categoryId) {
        String display = getCategoryDisplayName(categoryId);
        return display.equals(categoryId) ? "其他行为" : display;
    }

    public static Map<String, List<String>> getCategoryRules() {
        return Collections.unmodifiableMap(CATEGORY_RULES);
    }
}
