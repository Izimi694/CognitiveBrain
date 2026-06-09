package com.izimi.aiplayermod.brainstem.adapter;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public interface BasicActionAdapter {

    ActionResult moveTo(ServerPlayerEntity bot, BlockPos target);

    ActionResult lookAt(ServerPlayerEntity bot, double x, double y, double z);

    ActionResult dig(ServerPlayerEntity bot, BlockPos target);

    ActionResult attack(ServerPlayerEntity bot, String entityName);

    ActionResult placeBlock(ServerPlayerEntity bot, BlockPos pos, String face);

    ActionResult useItem(ServerPlayerEntity bot);

    ActionResult equipItem(ServerPlayerEntity bot, String itemName);

    ActionResult openBlock(ServerPlayerEntity bot, BlockPos pos);

    ActionResult closeWindow(ServerPlayerEntity bot);

    ActionResult clickSlot(ServerPlayerEntity bot, int slot, int button);

    ActionResult craft(ServerPlayerEntity bot, String itemId);

    ActionResult chat(ServerPlayerEntity bot, String message);

    ActionResult jump(ServerPlayerEntity bot);

    ActionResult flee(ServerPlayerEntity bot, double speed);

    ActionResult eat(ServerPlayerEntity bot);

    ActionResult retreat(ServerPlayerEntity bot, double speed);

    ActionResult avoidLava(ServerPlayerEntity bot, double speed);

    ActionResult seekShelter(ServerPlayerEntity bot, double speed);

    ActionResult collectItem(ServerPlayerEntity bot, double speed);
}
