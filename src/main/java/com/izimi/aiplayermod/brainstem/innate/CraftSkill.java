package com.izimi.aiplayermod.brainstem.innate;

import com.izimi.aiplayermod.brainstem.skill.Skill;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

public class CraftSkill extends Skill {

    public CraftSkill() {
        super("craft", "合成", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        return bot != null && world != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        var recipes = world.getRecipeManager().listAllOfType(RecipeType.CRAFTING);
        if (recipes.isEmpty()) {
            return SkillResult.partial(0.1, "暂无可用配方");
        }

        var inventory = bot.getInventory();
        for (RecipeEntry<?> entry : recipes) {
            Recipe<?> recipe = entry.value();
            if (recipe.getType() == RecipeType.CRAFTING) {
                try {
                    if (recipe instanceof net.minecraft.recipe.CraftingRecipe craftingRecipe) {
                        boolean canCraft = canCraftRecipe(craftingRecipe, inventory);
                        if (canCraft) {
                            return SkillResult.partial(0.4, "找到可合成配方");
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return SkillResult.partial(0.2, "暂无可合成物品");
    }

    private boolean canCraftRecipe(net.minecraft.recipe.CraftingRecipe recipe,
                                   net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < inventory.main.size(); i++) {
            if (!inventory.main.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
