package com.izimi.aiplayermod.brainstem.adapter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
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

public class MinecraftActionAdapter implements BasicActionAdapter {

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

        Vec3d botPos = bot.getPos();
        Vec3d targetVec = target.toCenterPos();
        double dist = botPos.squaredDistanceTo(targetVec);

        if (dist < 4.0) {
            return ActionResult.success("已到达");
        }

        Vec3d dir = targetVec.subtract(botPos).normalize().multiply(0.15);
        bot.setVelocity(new Vec3d(dir.x, 0.08, dir.z));
        bot.velocityModified = true;

        if (Math.random() < 0.3 && bot.isOnGround()) {
            bot.jump();
        }

        return ActionResult.partial(Math.max(0, 1.0 - dist / 100.0), "移动中");
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

        bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + message));
        return ActionResult.success("发送消息");
    }

    @Override
    public ActionResult jump(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("jump: bot为null");

        if (bot.isOnGround()) {
            bot.jump();
            return ActionResult.success("跳跃");
        }

        return ActionResult.success("在空中，无法跳跃");
    }

    @Override
    public ActionResult flee(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("flee: bot为null");
        HostileEntity nearest = findNearestHostileForFlee(bot, 10);
        if (nearest == null) return ActionResult.unable("flee: 未检测到威胁");
        Vec3d away = bot.getPos().subtract(nearest.getPos()).normalize().multiply(speed);
        bot.setVelocity(new Vec3d(away.x, 0.1, away.z));
        bot.velocityModified = true;
        bot.jump();
        return ActionResult.success("flee: " + nearest.getType().getName().getString());
    }

    @Override
    public ActionResult eat(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("eat: bot为null");
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) {
                inv.selectedSlot = i;
                bot.swingHand(Hand.MAIN_HAND);
                return ActionResult.success("eat: " + stack.getItem().getName().getString());
            }
        }
        return ActionResult.unable("eat: 背包没有食物");
    }

    @Override
    public ActionResult retreat(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("retreat: bot为null");
        HostileEntity nearest = findNearestHostileForFlee(bot, 20);
        if (nearest == null) return ActionResult.unable("retreat: 未检测到威胁");
        Vec3d away = bot.getPos().subtract(nearest.getPos()).normalize().multiply(speed);
        bot.setVelocity(new Vec3d(away.x, 0.05, away.z));
        bot.velocityModified = true;
        return ActionResult.success("retreat: " + nearest.getType().getName().getString());
    }

    @Override
    public ActionResult avoidLava(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("avoidLava: bot为null");
        BlockPos botPos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();
        Vec3d away = null;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (world.getBlockState(botPos.add(dx, dy, dz)).isOf(Blocks.LAVA)) {
                        away = Vec3d.ofCenter(botPos).subtract(Vec3d.ofCenter(botPos.add(dx, dy, dz))).normalize();
                        break;
                    }
                }
                if (away != null) break;
            }
            if (away != null) break;
        }
        if (away == null) return ActionResult.unable("avoidLava: 附近没有熔岩");
        bot.setVelocity(away.multiply(speed));
        bot.velocityModified = true;
        return ActionResult.success("avoid_lava");
    }

    @Override
    public ActionResult seekShelter(ServerPlayerEntity bot, double speed) {
        if (bot == null) return ActionResult.unable("seekShelter: bot为null");
        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos check = botPos.add(dx, 0, dz);
                    BlockPos above = check.up();
                    if (!world.getBlockState(above).isAir() && world.getBlockState(above).isOpaque()
                            && !world.getBlockState(check).isAir()) {
                        Vec3d dir = Vec3d.ofCenter(check).subtract(Vec3d.ofCenter(botPos)).normalize();
                        bot.setVelocity(dir.multiply(speed));
                        bot.velocityModified = true;
                        return ActionResult.success("seek_shelter");
                    }
                }
            }
        }
        return ActionResult.unable("seekShelter: 附近没有庇护所");
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
        if (items.isEmpty()) return ActionResult.unable("collectItem: 附近没有掉落物");
        var nearest = items.get(0);
        Vec3d dir = nearest.getPos().subtract(bot.getPos()).normalize();
        bot.setVelocity(dir.multiply(speed));
        bot.velocityModified = true;
        return ActionResult.success("collect: " + nearest.getStack().getItem().getName().getString());
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

            if (recipe instanceof ShapedRecipe shaped) {
                int rw = shaped.getWidth();
                int rh = shaped.getHeight();
                for (int row = 0; row < rh; row++) {
                    for (int col = 0; col < rw; col++) {
                        int idx = row * rw + col;
                        if (idx >= ingredients.size()) break;
                        Ingredient ing = ingredients.get(idx);
                        if (ing.isEmpty()) continue;
                        int gridSlot = row * gridWidth + col + gridStart;
                        moveIngredientToSlot(bot, ing, gridSlot, invStart, hotbarStart);
                    }
                }
            } else {
                int slot = gridStart;
                for (Ingredient ing : ingredients) {
                    if (ing.isEmpty()) continue;
                    if (slot > (needsTable ? CT_GRID_END : INV_GRID_END)) break;
                    moveIngredientToSlot(bot, ing, slot, invStart, hotbarStart);
                    slot++;
                }
            }

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
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && state.getHardness(world, pos) >= 0
                            && !state.isOf(Blocks.BEDROCK)
                            && !state.isOf(Blocks.WATER)
                            && !state.isOf(Blocks.LAVA)) {
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
            world.getEntitiesByClass(HostileEntity.class,
                    bot.getBoundingBox().expand(SCAN_RANGE),
                    e -> e.isAlive()).forEach(entities::add);
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

    private HostileEntity findNearestHostileForFlee(ServerPlayerEntity bot, int range) {
        ServerWorld world = bot.getServerWorld();
        List<HostileEntity> mobs = world.getEntitiesByClass(
                HostileEntity.class,
                bot.getBoundingBox().expand(range),
                e -> e.isAlive()
        );
        if (mobs.isEmpty()) return null;
        mobs.sort((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)));
        return mobs.get(0);
    }
}
