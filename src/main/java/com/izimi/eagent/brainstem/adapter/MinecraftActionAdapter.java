package com.izimi.eagent.brainstem.adapter;

import com.izimi.eagent.brainstem.navigation.NavigationController;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinecraftActionAdapter implements BasicActionAdapter {

    private final Map<UUID, NavigationController> navigationControllers = new ConcurrentHashMap<>();

    // Container slot layout constants (handler slot indices)
    // CraftingScreenHandler: 46 slots total
    private static final int CT_RESULT = 0;
    private static final int CT_GRID_START = 1;
    private static final int CT_GRID_END = 9;        // 3x3 = 9 slots
    private static final int CT_GRID_WIDTH = 3;
    private static final int CT_INV_START = 10;       // player main 27 slots
    private static final int CT_HOTBAR_START = 37;    // hotbar 9 slots

    // PlayerScreenHandler (2x2 inventory crafting): 41 slots total
    private static final int INV_RESULT = 0;
    private static final int INV_GRID_START = 1;
    private static final int INV_GRID_END = 4;        // 2x2 = 4 slots
    private static final int INV_GRID_WIDTH = 2;
    private static final int INV_INV_START = 5;       // player main 27 slots
    private static final int INV_HOTBAR_START = 32;   // hotbar 9 slots

    private BlockPos currentDigTarget = null;
    private int digBreakingTicks = 0;
    private static final int BREAK_TIME_TICKS = 40;
    private static final int SCAN_RANGE = 8;

    @Override
    public ActionResult moveTo(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) return ActionResult.unable("moveTo: bot或target为null");

        NavigationController nav = navigationControllers.computeIfAbsent(
                bot.getUuid(), k -> new NavigationController());

        Vec3d botPos = bot.getPos();
        double dist = botPos.squaredDistanceTo(target.toCenterPos());

        if (dist < 4.0) {
            nav.stopNavigation();
            return ActionResult.success("已到达");
        }

        boolean navigating = nav.navigateTo(bot, target);
        return navigating
                ? ActionResult.success("已到达")
                : ActionResult.partial(Math.max(0, 1.0 - dist / 100.0), "移动中");
    }

    public void stopNavigation(UUID botId) {
        NavigationController nav = navigationControllers.remove(botId);
        if (nav != null) nav.stopNavigation();
    }

    @Override
    public ActionResult lookAt(ServerPlayerEntity bot, double x, double y, double z) {
        if (bot == null) return ActionResult.unable("lookAt: bot为null");

        Vec3d botPos = bot.getPos();
        double dx = x - botPos.x;
        double dy = y - (botPos.y + bot.getStandingEyeHeight());
        double dz = z - botPos.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));

        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);

        return ActionResult.success("lookAt: (" + x + "," + y + "," + z + ")");
    }

    @Override
    public ActionResult dig(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null) return ActionResult.unable("dig: bot为null");

        ServerWorld world = bot.getServerWorld();

        if (target != null) {
            currentDigTarget = target;
        }

        if (currentDigTarget == null) {
            currentDigTarget = findNearbyBlock(world, bot);
            if (currentDigTarget == null) {
                return ActionResult.unable("附近没有可挖掘的方块");
            }
        } else {
            BlockState state = world.getBlockState(currentDigTarget);
            if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
                currentDigTarget = findNearbyBlock(world, bot);
                if (currentDigTarget == null) {
                    return ActionResult.unable("附近没有可挖掘的方块");
                }
            }
        }

        double distance = bot.getPos().squaredDistanceTo(
                currentDigTarget.getX() + 0.5, currentDigTarget.getY(), currentDigTarget.getZ() + 0.5);
        if (distance > 25.0) {
            return ActionResult.partial(0.3, "距离太远");
        }

        equipBestTool(bot, world.getBlockState(currentDigTarget));

        digBreakingTicks++;
        if (digBreakingTicks >= BREAK_TIME_TICKS) {
            digBreakingTicks = 0;
            BlockPos completed = currentDigTarget;
            currentDigTarget = null;
            world.breakBlock(completed, true, bot);
            return ActionResult.success("挖掘完成");
        }

        if (Math.random() < 0.1) {
            world.setBlockBreakingInfo(bot.getId(), currentDigTarget, (int) (digBreakingTicks * 10.0 / BREAK_TIME_TICKS));
        }

        return ActionResult.partial(0.6, "挖掘中");
    }

    @Override
    public ActionResult attack(ServerPlayerEntity bot, String entityName) {
        if (bot == null) return ActionResult.unable("attack: bot为null");

        ServerWorld world = bot.getServerWorld();
        LivingEntity target = findNearbyEntity(world, bot, entityName);

        if (target == null) {
            return ActionResult.unable("附近没有" + (entityName != null ? entityName : "攻击目标"));
        }

        lookAtEntity(bot, target);

        double dist = bot.squaredDistanceTo(target);
        if (dist > 25.0) {
            Vec3d dir = target.getPos().subtract(bot.getPos()).normalize().multiply(0.15);
            bot.setVelocity(new Vec3d(dir.x, 0.08, dir.z));
            bot.velocityModified = true;
            return ActionResult.partial(0.4, "追击中");
        }

        bot.swingHand(Hand.MAIN_HAND);
        bot.attack(target);
        return ActionResult.partial(0.7, "攻击");
    }

    @Override
    public ActionResult placeBlock(ServerPlayerEntity bot, BlockPos pos, String faceStr) {
        if (bot == null || pos == null) return ActionResult.unable("placeBlock: 参数无效");

        ServerWorld world = bot.getServerWorld();
        BlockPos placePos = pos.offset(parseFace(faceStr));

        if (placePos.getSquaredDistance(bot.getBlockPos()) > 25.0) {
            return ActionResult.partial(0.3, "距离太远");
        }

        ItemStack mainHand = bot.getMainHandStack();
        if (mainHand.isEmpty()) {
            return ActionResult.unable("主手没有物品");
        }

        world.setBlockState(placePos, Blocks.STONE.getDefaultState());
        bot.swingHand(Hand.MAIN_HAND);

        return ActionResult.success("放置完成");
    }

    @Override
    public ActionResult useItem(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("useItem: bot为null");

        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) return ActionResult.unable("主手没有物品");

        bot.swingHand(Hand.MAIN_HAND);
        bot.getInventory().markDirty();

        return ActionResult.success("使用物品");
    }

    @Override
    public ActionResult equipItem(ServerPlayerEntity bot, String itemName) {
        if (bot == null || itemName == null) return ActionResult.unable("equipItem: 参数无效");

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                if (id.toLowerCase().contains(itemName.toLowerCase())) {
                    if (i < 9) {
                        bot.getInventory().selectedSlot = i;
                    } else {
                        bot.getInventory().selectedSlot = 0;
                    }
                    return ActionResult.success("装备: " + id);
                }
            }
        }

        return ActionResult.unable("背包中没有: " + itemName);
    }

    @Override
    public ActionResult openBlock(ServerPlayerEntity bot, BlockPos pos) {
        if (bot == null || pos == null) return ActionResult.unable("openBlock: 参数无效");

        if (pos.getSquaredDistance(bot.getBlockPos()) > 25.0) {
            return ActionResult.partial(0.3, "距离太远");
        }

        bot.openHandledScreen(bot.getServerWorld().getBlockState(pos).createScreenHandlerFactory(bot.getServerWorld(), pos));
        return ActionResult.success("打开: " + pos.toShortString());
    }

    @Override
    public ActionResult closeWindow(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("closeWindow: bot为null");

        if (bot.currentScreenHandler != null && bot.currentScreenHandler != bot.playerScreenHandler) {
            bot.closeHandledScreen();
            return ActionResult.success("关闭窗口");
        }

        return ActionResult.success("无窗口可关闭");
    }

    @Override
    public ActionResult clickSlot(ServerPlayerEntity bot, int slot, int button) {
        if (bot == null) return ActionResult.unable("clickSlot: bot为null");

        ScreenHandler handler = bot.currentScreenHandler;
        if (handler == null || handler == bot.playerScreenHandler) {
            return ActionResult.unable("没有打开的容器");
        }

        try {
            handler.onSlotClick(slot, button, net.minecraft.screen.slot.SlotActionType.PICKUP, bot);
            return ActionResult.success("点击槽位: " + slot);
        } catch (Exception e) {
            return ActionResult.fail("点击失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult chat(ServerPlayerEntity bot, String message) {
        if (bot == null || message == null) return ActionResult.unable("chat: 参数无效");

        bot.sendMessage(Text.literal("§b[E-Agent] §f" + message));
        return ActionResult.success("发送消息");
    }

    @Override
    public ActionResult jump(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("jump: bot为null");
        bot.jump();
        return ActionResult.success("跳跃");
    }

    @Override
    public ActionResult sneak(ServerPlayerEntity bot, boolean sneaking) {
        if (bot == null) return ActionResult.unable("sneak: bot为null");
        bot.setSneaking(sneaking);
        return ActionResult.success("sneak: " + sneaking);
    }

    @Override
    public ActionResult flee(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("flee: bot为null");
        Vec3d away = fleeDirection(bot);
        bot.setVelocity(away.multiply(speed));
        bot.velocityModified = true;
        bot.jump();
        return ActionResult.success("flee");
    }

    @Override
    public ActionResult eat(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("eat: bot为null");
        PlayerInventory inv = bot.getInventory();
        ItemStack held = inv.getMainHandStack();
        if (!held.isEmpty() && held.contains(DataComponentTypes.FOOD)) {
            bot.swingHand(Hand.MAIN_HAND);
            return ActionResult.success("eat: " + held.getItem().getName().getString());
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) {
                inv.selectedSlot = i;
                bot.swingHand(Hand.MAIN_HAND);
                return ActionResult.success("eat: " + stack.getItem().getName().getString());
            }
        }
        return ActionResult.partial(0.1, "eat: 没有食物");
    }

    @Override
    public ActionResult retreat(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("retreat: bot为null");
        Vec3d away = fleeDirection(bot);
        bot.setVelocity(away.multiply(speed));
        bot.velocityModified = true;
        return ActionResult.success("retreat");
    }

    @Override
    public ActionResult avoidLava(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("avoidLava: bot为null");
        ServerWorld world = bot.getServerWorld();
        Vec3d away = findLavaAwayVector(bot, world);
        if (away == null) return ActionResult.unable("avoidLava: 附近没有熔岩");
        bot.setVelocity(away.multiply(speed));
        bot.velocityModified = true;
        return ActionResult.success("avoid_lava");
    }

    private static Vec3d findLavaAwayVector(ServerPlayerEntity bot, ServerWorld world) {
        BlockPos botPos = bot.getBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (world.getBlockState(botPos.add(dx, dy, dz)).isOf(Blocks.LAVA)) {
                        return Vec3d.ofCenter(botPos).subtract(Vec3d.ofCenter(botPos.add(dx, dy, dz))).normalize();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public ActionResult seekShelter(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("seekShelter: bot为null");
        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        if (trySeekShelterAt(bot, world, botPos, speed)) return ActionResult.success("seek_shelter");
        Vec3d forward = bot.getRotationVector().multiply(speed);
        bot.setVelocity(new Vec3d(forward.x, 0.05, forward.z));
        bot.velocityModified = true;
        return ActionResult.partial(0.1, "seekShelter: 寻找中");
    }

    private static boolean trySeekShelterAt(ServerPlayerEntity bot, ServerWorld world, BlockPos botPos, double speed) {
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos check = botPos.add(dx, 0, dz);
                    BlockPos above = check.up();
                    if (world.getBlockState(above).isAir() || !world.getBlockState(above).isOpaque()) continue;
                    if (world.getBlockState(check).isAir()) continue;
                    Vec3d dir = Vec3d.ofCenter(check).subtract(Vec3d.ofCenter(botPos)).normalize();
                    bot.setVelocity(dir.multiply(speed));
                    bot.velocityModified = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ActionResult collectItem(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("collectItem: bot为null");
        ServerWorld world = bot.getServerWorld();
        var items = world.getEntitiesByClass(
                ItemEntity.class,
                bot.getBoundingBox().expand(5),
                e -> !e.cannotPickup()
        );
        if (!items.isEmpty()) {
            var nearest = items.get(0);
            Vec3d dir = nearest.getPos().subtract(bot.getPos()).normalize();
            bot.setVelocity(dir.multiply(speed));
            bot.velocityModified = true;
            return ActionResult.success("collect: " + nearest.getStack().getItem().getName().getString());
        }
        Vec3d forward = bot.getRotationVector().multiply(speed);
        bot.setVelocity(new Vec3d(forward.x, 0.05, forward.z));
        bot.velocityModified = true;
        return ActionResult.partial(0.1, "collectItem: 搜索中");
    }

    @Override
    public ActionResult craft(ServerPlayerEntity bot, String itemId) {
        if (bot == null || itemId == null || itemId.isEmpty())
            return ActionResult.unable("craft: 参数无效");

        ServerWorld world = bot.getServerWorld();
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return ActionResult.fail("无效物品ID: " + itemId);

        var recipeManager = world.getRecipeManager();
        List<RecipeEntry<CraftingRecipe>> allRecipes = recipeManager.listAllOfType(RecipeType.CRAFTING);

        // Step 1: Find recipe that produces target item
        CraftingRecipe recipe = null;
        for (RecipeEntry<CraftingRecipe> entry : allRecipes) {
            ItemStack result = entry.value().getResult(world.getRegistryManager());
            if (Registries.ITEM.getId(result.getItem()).equals(id)) {
                recipe = entry.value();
                break;
            }
        }
        if (recipe == null) return ActionResult.fail("无此配方: " + itemId);

        // Step 2: Determine grid size
        boolean needsTable = !recipe.fits(2, 2);
        List<Ingredient> ingredients = recipe.getIngredients();
        int craftableCount = countCraftable(recipe, bot);
        if (craftableCount <= 0) return ActionResult.fail("材料不足: " + itemId);

        // Step 3: Open crafting interface
        if (needsTable) {
            BlockPos tablePos = findBlockPosByName(world, bot, "crafting_table");
            if (tablePos == null) return ActionResult.fail("需要工作台");
            openBlock(bot, tablePos);
        } else {
            openInventory(bot);
        }

        // Wait one tick for screen to open
        ScreenHandler handler = bot.currentScreenHandler;
        if (handler == null) return ActionResult.fail("无法打开合成界面");

        try {
            // Step 4: Place ingredients in grid
            int gridWidth = needsTable ? CT_GRID_WIDTH : INV_GRID_WIDTH;
            int gridStart = needsTable ? CT_GRID_START : INV_GRID_START;
            int invStart = needsTable ? CT_INV_START : INV_INV_START;
            int hotbarStart = needsTable ? CT_HOTBAR_START : INV_HOTBAR_START;

            placeIngredients(recipe, ingredients, bot, gridWidth, gridStart, invStart, hotbarStart, needsTable);

            // Step 5: Check result slot and take output
            ItemStack resultStack = handler.getSlot(needsTable ? CT_RESULT : INV_RESULT).getStack();
            if (resultStack.isEmpty()) {
                closeWindow(bot);
                return ActionResult.fail("合成失败: 材料摆放有误");
            }

            handler.onSlotClick(needsTable ? CT_RESULT : INV_RESULT, 0, SlotActionType.PICKUP, bot);
            int resultCount = resultStack.getCount();
            closeWindow(bot);

            return ActionResult.success("合成 " + itemId + " x" + resultCount + " 完成");
        } catch (Exception e) {
            try { closeWindow(bot); } catch (Exception ignored) {}
            return ActionResult.fail("合成失败: " + e.getMessage());
        }
    }

    private void openInventory(ServerPlayerEntity bot) {
        // The 2x2 crafting grid is part of PlayerScreenHandler
        // which is the default when no container screen is open
        if (bot.currentScreenHandler == null) {
            bot.currentScreenHandler = bot.playerScreenHandler;
        }
    }

    private int countCraftable(CraftingRecipe recipe, ServerPlayerEntity bot) {
        PlayerInventory inv = bot.getInventory();
        int maxSets = Integer.MAX_VALUE;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            int matchingSlots = 0;
            for (int i = 0; i < 36; i++) {
                if (!inv.main.get(i).isEmpty() && ing.test(inv.main.get(i))) {
                    matchingSlots += inv.main.get(i).getCount();
                }
            }
            if (matchingSlots == 0) return 0;
            maxSets = Math.min(maxSets, matchingSlots);
        }
        return maxSets;
    }

    private void moveIngredientToSlot(ServerPlayerEntity bot, Ingredient ing, int targetSlot,
                                      int invStart, int hotbarStart) {
        ScreenHandler handler = bot.currentScreenHandler;
        if (handler == null) return;

        // Find matching item in inventory (search main first, then hotbar)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty() || !ing.test(stack)) continue;

            int sourceSlot = i < 27 ? (i + invStart) : (i - 27 + hotbarStart);
            handler.onSlotClick(sourceSlot, 0, SlotActionType.PICKUP, bot);
            handler.onSlotClick(targetSlot, 0, SlotActionType.PICKUP, bot);
            return;
        }
    }

    private void lookAtEntity(ServerPlayerEntity bot, LivingEntity target) {
        double px = target.getX();
        double py = target.getEyeY();
        double pz = target.getZ();
        double dx = px - bot.getX();
        double dy = py - (bot.getY() + bot.getStandingEyeHeight());
        double dz = pz - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);
    }

    private BlockPos findBlockPosByName(net.minecraft.server.world.ServerWorld world,
                                         ServerPlayerEntity bot, String name) {
        if (world == null || bot == null || name == null) return null;
        BlockPos botPos = bot.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    var state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (blockId.toLowerCase().contains(name.toLowerCase())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNearbyBlock(ServerWorld world, ServerPlayerEntity bot) {
        BlockPos botPos = bot.getBlockPos();
        for (int dy = 4; dy >= -1; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private LivingEntity findNearbyEntity(ServerWorld world, ServerPlayerEntity bot, String entityName) {
        List<? extends LivingEntity> allEntities = world.getEntitiesByClass(
                LivingEntity.class,
                bot.getBoundingBox().expand(SCAN_RANGE),
                e -> e.isAlive() && e != bot);

        List<LivingEntity> entities = new ArrayList<>();
        if (entityName != null && !entityName.isEmpty()) {
            for (var e : allEntities) {
                String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                if (id.toLowerCase().contains(entityName.toLowerCase())) {
                    entities.add(e);
                }
            }
        } else {
            entities.addAll(allEntities);
        }

        if (entities.isEmpty()) return null;

        entities.sort((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)));
        return entities.get(0);
    }

    private void equipBestTool(ServerPlayerEntity bot, BlockState state) {
        ItemStack bestTool = ItemStack.EMPTY;
        float bestSpeed = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestTool = stack;
            }
        }

        if (!bestTool.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (bot.getInventory().getStack(i) == bestTool) {
                    bot.getInventory().selectedSlot = i;
                    break;
                }
            }
        }
    }

    private static Direction parseFace(String face) {
        if (face == null) return Direction.UP;
        return switch (face.toLowerCase()) {
            case "up", "top" -> Direction.UP;
            case "down", "bottom" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.UP;
        };
    }

    private Vec3d fleeDirection(ServerPlayerEntity bot) {
        ServerWorld world = bot.getServerWorld();
        var entities = world.getEntitiesByClass(
                LivingEntity.class, bot.getBoundingBox().expand(10), e -> e.isAlive() && e != bot);
        if (!entities.isEmpty()) {
            var nearest = entities.get(0);
            Vec3d away = bot.getPos().subtract(nearest.getPos());
            double len = away.length();
            if (len > 0.01) return away.normalize();
        }
        Vec3d look = bot.getRotationVector();
        return new Vec3d(-look.x, 0, -look.z).normalize();
    }

    private void placeIngredients(CraftingRecipe recipe, List<Ingredient> ingredients, ServerPlayerEntity bot,
                                   int gridWidth, int gridStart, int invStart, int hotbarStart, boolean needsTable) {
        if (recipe instanceof ShapedRecipe shaped) {
            placeShapedIngredients(shaped, ingredients, bot, gridWidth, gridStart, invStart, hotbarStart);
        } else {
            placeShapelessIngredients(ingredients, bot, gridStart, invStart, hotbarStart, needsTable);
        }
    }

    private void placeShapedIngredients(ShapedRecipe shaped, List<Ingredient> ingredients, ServerPlayerEntity bot,
                                         int gridWidth, int gridStart, int invStart, int hotbarStart) {
        int rw = shaped.getWidth();
        int rh = shaped.getHeight();
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int idx = row * rw + col;
                if (idx >= ingredients.size()) return;
                Ingredient ing = ingredients.get(idx);
                if (ing.isEmpty()) continue;
                int gridSlot = row * gridWidth + col + gridStart;
                moveIngredientToSlot(bot, ing, gridSlot, invStart, hotbarStart);
            }
        }
    }

    private void placeShapelessIngredients(List<Ingredient> ingredients, ServerPlayerEntity bot,
                                            int gridStart, int invStart, int hotbarStart, boolean needsTable) {
        int slot = gridStart;
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            if (slot > (needsTable ? CT_GRID_END : INV_GRID_END)) return;
            moveIngredientToSlot(bot, ing, slot, invStart, hotbarStart);
            slot++;
        }
    }
}
