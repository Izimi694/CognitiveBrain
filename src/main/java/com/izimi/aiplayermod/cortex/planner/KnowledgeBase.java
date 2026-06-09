package com.izimi.aiplayermod.cortex.planner;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class KnowledgeBase {

    private final Map<String, Map<String, Object>> recipes;
    private final Map<String, Map<String, Object>> entities;
    private final Map<String, List<String>> itemUses;
    private final Map<String, Template> templates;
    private final Map<String, String> toolMap;

    public record Template(String name, Pattern pattern, List<TemplateStep> steps) {}

    public record TemplateStep(String skillId, String action, String target, int amount) {}

    private KnowledgeBase(Map<String, Map<String, Object>> recipes,
                          Map<String, Map<String, Object>> entities,
                          Map<String, List<String>> itemUses,
                          Map<String, Template> templates,
                          Map<String, String> toolMap) {
        this.recipes = recipes;
        this.entities = entities;
        this.itemUses = itemUses;
        this.templates = templates;
        this.toolMap = toolMap;
    }

    public Optional<Map<String, Object>> getRecipe(String itemId) {
        return Optional.ofNullable(recipes.get(itemId));
    }

    public Optional<Map<String, Object>> getEntity(String name) {
        return Optional.ofNullable(entities.get(name));
    }

    public Optional<List<String>> getItemUses(String itemId) {
        return Optional.ofNullable(itemUses.get(itemId));
    }

    public Optional<String> getTool(String blockId) {
        return Optional.ofNullable(toolMap.get(blockId));
    }

    public Optional<Template> matchTemplate(String input) {
        for (Template t : templates.values()) {
            if (t.pattern().matcher(input).find()) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public Collection<Template> allTemplates() {
        return templates.values();
    }

    @SuppressWarnings("unchecked")
    public static KnowledgeBase load() {
        Path path = FileUtil.getConfigDir().resolve("knowledge_base.json");
        Map<String, Object> data = null;
        if (Files.exists(path)) {
            data = JsonUtil.readMapFromFileSafe(path);
        }
        if (data == null) {
            data = JsonUtil.fromJson(DEFAULT_JSON, Map.class);
            JsonUtil.writeToFileSafeAtomic(path, data);
            AIPlayerMod.LOGGER.info("[KnowledgeBase] 已创建默认知识库: {}", path);
        }
        return fromMap(data);
    }

    @SuppressWarnings("unchecked")
    private static KnowledgeBase fromMap(Map<String, Object> data) {
        Map<String, Map<String, Object>> recipes = new LinkedHashMap<>();
        Map<String, List<String>> itemUses = new LinkedHashMap<>();
        Map<String, Map<String, Object>> entities = new LinkedHashMap<>();
        Map<String, Template> templates = new LinkedHashMap<>();
        Map<String, String> toolMap = new LinkedHashMap<>();

        if (data.containsKey("recipes")) {
            recipes.putAll((Map<String, Map<String, Object>>) data.get("recipes"));
        }
        if (data.containsKey("entities")) {
            entities.putAll((Map<String, Map<String, Object>>) data.get("entities"));
        }
        if (data.containsKey("item_uses")) {
            Map<String, List<String>> raw = (Map<String, List<String>>) data.get("item_uses");
            itemUses.putAll(raw);
        }
        if (data.containsKey("tool_map")) {
            Map<String, String> raw = (Map<String, String>) data.get("tool_map");
            toolMap.putAll(raw);
        }
        if (data.containsKey("templates")) {
            Map<String, Map<String, Object>> raw = (Map<String, Map<String, Object>>) data.get("templates");
            for (var e : raw.entrySet()) {
                String name = e.getKey();
                Map<String, Object> tm = e.getValue();
                String pattern = (String) tm.get("pattern");
                List<Map<String, Object>> stepsRaw = (List<Map<String, Object>>) tm.get("steps");
                List<TemplateStep> steps = new ArrayList<>();
                if (stepsRaw != null) {
                    for (Map<String, Object> sm : stepsRaw) {
                        steps.add(new TemplateStep(
                                (String) sm.get("skillId"),
                                (String) sm.get("action"),
                                (String) sm.getOrDefault("target", ""),
                                sm.containsKey("amount") ? ((Number) sm.get("amount")).intValue() : 1
                        ));
                    }
                }
                templates.put(name, new Template(name, Pattern.compile(pattern), steps));
            }
        }

        return new KnowledgeBase(recipes, entities, itemUses, templates, toolMap);
    }

    public List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        keys.addAll(recipes.keySet());
        keys.addAll(entities.keySet());
        keys.addAll(itemUses.keySet());
        keys.addAll(templates.keySet());
        return keys;
    }

    private static final String DEFAULT_JSON = """
{
  "templates": {
    "mine": {
      "pattern": "挖(\\\\d+)个(.+)",
      "steps": [
        {"skillId": "equip", "action": "equipItem", "target": "{tool}"},
        {"skillId": "move", "action": "moveTo", "target": "{target}"},
        {"skillId": "dig", "action": "dig", "target": "{target}"},
        {"skillId": "collect", "action": "collectItem", "target": "{target}"}
      ]
    },
    "craft": {
      "pattern": "合成(.+)",
      "steps": [
        {"skillId": "open_craft", "action": "openBlock", "target": "crafting_table"},
        {"skillId": "craft", "action": "craft", "target": "{target}"}
      ]
    },
    "smelt": {
      "pattern": "(?:烧|冶炼)(.+)",
      "steps": [
        {"skillId": "open_furnace", "action": "openBlock", "target": "furnace"},
        {"skillId": "smelt", "action": "clickSlot", "target": "{target}"}
      ]
    },
    "attack": {
      "pattern": "(?:打|杀|攻击)(.+)",
      "steps": [
        {"skillId": "equip", "action": "equipItem", "target": "{weapon}"},
        {"skillId": "move", "action": "moveTo", "target": "{target}"},
        {"skillId": "attack", "action": "attack", "target": "{target}"}
      ]
    },
    "get": {
      "pattern": "(?:拿|取|弄|搞)(\\\\d+)?个?(.+)",
      "steps": [
        {"skillId": "move", "action": "moveTo", "target": "{target}"},
        {"skillId": "collect", "action": "collectItem", "target": "{target}"}
      ]
    },
    "build": {
      "pattern": "(?:建|盖|搭|造)(.+)",
      "steps": [
        {"skillId": "get_blocks", "action": "collectItem", "target": "{material}"},
        {"skillId": "place", "action": "placeBlock", "target": "{target}"}
      ]
    }
  },
  "recipes": {
    "crafting_table": {"ingredients": "4 planks", "tool": "none"},
    "stick": {"ingredients": "2 planks", "tool": "none"},
    "planks": {"ingredients": "1 log", "tool": "none"},
    "wooden_pickaxe": {"ingredients": "3 planks + 2 sticks", "tool": "crafting_table"},
    "stone_pickaxe": {"ingredients": "3 cobblestone + 2 sticks", "tool": "crafting_table"},
    "iron_pickaxe": {"ingredients": "3 iron_ingot + 2 sticks", "tool": "crafting_table"},
    "diamond_pickaxe": {"ingredients": "3 diamond + 2 sticks", "tool": "crafting_table"},
    "wooden_sword": {"ingredients": "2 planks + 1 stick", "tool": "crafting_table"},
    "stone_sword": {"ingredients": "2 cobblestone + 1 stick", "tool": "crafting_table"},
    "iron_sword": {"ingredients": "2 iron_ingot + 1 stick", "tool": "crafting_table"},
    "wooden_axe": {"ingredients": "3 planks + 2 sticks", "tool": "crafting_table"},
    "stone_axe": {"ingredients": "3 cobblestone + 2 sticks", "tool": "crafting_table"},
    "wooden_shovel": {"ingredients": "1 planks + 2 sticks", "tool": "crafting_table"},
    "stone_shovel": {"ingredients": "1 cobblestone + 2 sticks", "tool": "crafting_table"},
    "furnace": {"ingredients": "8 cobblestone", "tool": "crafting_table"},
    "chest": {"ingredients": "8 planks", "tool": "crafting_table"},
    "bed": {"ingredients": "3 wool + 3 planks", "tool": "crafting_table"},
    "torch": {"ingredients": "1 coal + 1 stick", "tool": "none"}
  },
  "entities": {
    "zombie": {"hostile": true, "attackable": true},
    "skeleton": {"hostile": true, "attackable": true},
    "spider": {"hostile": true, "attackable": true},
    "creeper": {"hostile": true, "attackable": false, "warning": "会爆炸，保持距离"},
    "enderman": {"hostile": true, "attackable": true, "warning": "不要看它的眼睛"},
    "witch": {"hostile": true, "attackable": true},
    "slime": {"hostile": true, "attackable": true}
  },
  "item_uses": {
    "diamond": ["diamond_pickaxe", "diamond_sword", "diamond_axe", "diamond_shovel", "enchantment_table"],
    "iron_ingot": ["iron_pickaxe", "iron_sword", "iron_axe", "iron_shovel", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "flint_and_steel", "bucket", "shears"],
    "gold_ingot": ["golden_pickaxe", "golden_sword", "golden_apple", "powered_rail"],
    "coal": ["torch", "campfire"],
    "redstone": ["redstone_dust", "piston", "observer", "repeater", "comparator"],
    "stick": ["torch", "tool_handle", "fence", "ladder", "sign"],
    "planks": ["crafting_table", "stick", "chest", "door", "trapdoor", "boat"],
    "cobblestone": ["stone_pickaxe", "stone_sword", "furnace", "stone_bricks"],
    "log": ["planks", "charcoal"]
  },
  "tool_map": {
    "iron_ore": "stone_pickaxe",
    "diamond_ore": "iron_pickaxe",
    "gold_ore": "iron_pickaxe",
    "stone": "wooden_pickaxe",
    "log": "wooden_axe",
    "coal_ore": "wooden_pickaxe"
  }
}""";
}
