package com.izimi.aiplayermod.command;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.brainstem.bot.BotInstance;
import com.izimi.aiplayermod.brainstem.bot.BotManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AICommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("ai")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§6[AI Player] §7使用 /ai <目标> 创建任务，或 /ai help 查看帮助"), false);
                    return 1;
                })
                .then(literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(literal("status").executes(ctx -> showStatus(ctx.getSource(), null)))
                .then(literal("cancel").executes(ctx -> cancelTask(ctx.getSource(), null)))
                .then(literal("resume").executes(ctx -> resumeTask(ctx.getSource(), null)))
                .then(literal("explore").executes(ctx -> startExplore(ctx.getSource(), null)))
                .then(literal("spawn")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return spawnBot(ctx.getSource(), name);
                        }))
                        .executes(ctx -> spawnBot(ctx.getSource(), "AI_Assistant"))
                )
                .then(literal("despawn")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return despawnBot(ctx.getSource(), name);
                        }))
                        .executes(ctx -> despawnBot(ctx.getSource(), null))
                )
                .then(literal("list").executes(ctx -> listBots(ctx.getSource())))
                .then(literal("personality")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return showPersonality(ctx.getSource(), name);
                        }))
                        .executes(ctx -> showPersonality(ctx.getSource(), null))
                )
                .then(literal("suggestions")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return triggerSuggestions(ctx.getSource(), name);
                        }))
                        .executes(ctx -> triggerSuggestions(ctx.getSource(), null))
                )
                .then(literal("forget")
                        .then(argument("id", StringArgumentType.string()).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            return forgetMemory(ctx.getSource(), id, null);
                        }))
                )
                .then(literal("setkey")
                        .then(argument("key", StringArgumentType.greedyString()).executes(ctx -> {
                            String key = StringArgumentType.getString(ctx, "key");
                            return setApiKey(ctx.getSource(), key);
                        }))
                )
                .then(literal("apikey").executes(ctx -> showApiKeyStatus(ctx.getSource())))
                .then(literal("model")
                        .executes(ctx -> showCurrentModel(ctx.getSource()))
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String modelName = StringArgumentType.getString(ctx, "name");
                            return setApiModel(ctx.getSource(), modelName);
                        }))
                )
                .then(literal("bot")
                        .then(argument("botName", StringArgumentType.word())
                                .then(literal("status").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return showStatus(ctx.getSource(), botName);
                                }))
                                .then(literal("cancel").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return cancelTask(ctx.getSource(), botName);
                                }))
                                .then(literal("resume").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return resumeTask(ctx.getSource(), botName);
                                }))
                                .then(literal("explore").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return startExplore(ctx.getSource(), botName);
                                }))
                                .then(literal("personality").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return showPersonality(ctx.getSource(), botName);
                                }))
                                .then(literal("suggestions").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return triggerSuggestions(ctx.getSource(), botName);
                                }))
                                .then(literal("despawn").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return despawnBot(ctx.getSource(), botName);
                                }))
                                .then(argument("goal", StringArgumentType.greedyString()).executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    String goal = StringArgumentType.getString(ctx, "goal");
                                    return createTaskForBot(ctx.getSource(), botName, goal);
                                }))
                        )
                )
                .then(argument("goal", StringArgumentType.greedyString()).executes(ctx -> {
                    String goal = StringArgumentType.getString(ctx, "goal");
                    return createTask(ctx.getSource(), goal);
                }));

        dispatcher.register(root);
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== AI Player 指令帮助 ====="), false);
        source.sendFeedback(() -> Text.literal("§e/ai <目标> §7- 创建新任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai spawn <名字> §7- 生成指定名字的AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai despawn [名字] §7- 移除AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai list §7- 列出所有AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai status §7- 查看当前任务状态"), false);
        source.sendFeedback(() -> Text.literal("§e/ai cancel §7- 中断当前任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai resume §7- 恢复上一个任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai explore §7- 开始自由探索"), false);
        source.sendFeedback(() -> Text.literal("§e/ai personality [名字] §7- 查看AI个性偏好"), false);
        source.sendFeedback(() -> Text.literal("§e/ai suggestions [名字] §7- 触发AI主动建议"), false);
        source.sendFeedback(() -> Text.literal("§e/ai forget <id> §7- 删除指定记忆"), false);
        source.sendFeedback(() -> Text.literal("§e/ai setkey <key> §7- 设置AI API密钥"), false);
        source.sendFeedback(() -> Text.literal("§e/ai apikey §7- 查看API密钥状态"), false);
        source.sendFeedback(() -> Text.literal("§e/ai model [name] §7- 查看/设置AI模型 (如 deepseek-chat)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai bot <名字> <指令> §7- 指定AI执行指令"), false);
        return 1;
    }

    private static BotManager getManager() {
        return AIPlayerMod.getBotManager();
    }

    private static BotInstance resolveBot(ServerCommandSource source, String name) {
        BotManager mgr = getManager();
        if (mgr == null || mgr.isEmpty()) return null;
        if (name != null) return mgr.getByName(name);
        // Nearest to command sender
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) return mgr.getNearest(player);
        // Fallback: first bot
        return mgr.getAll().get(0);
    }

    private static int showStatus(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }
            var taskManager = bot.getTaskManager();
            var activeTask = taskManager.getActiveTask();
            if (activeTask != null) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 当前任务: " + activeTask.getGoal()), false);
                source.sendFeedback(() -> Text.literal("§7  状态: " + activeTask.getStatus() + " | 进度: " + activeTask.getProgressSummary()), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 当前没有活动任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 获取状态失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int cancelTask(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }
            bot.getTaskManager().cancelActiveTask();
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 任务已中断"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 中断失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int resumeTask(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }
            boolean resumed = bot.getTaskManager().resumeLastTask();
            if (resumed) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 已恢复上一个任务"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 没有可恢复的任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 恢复失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int startExplore(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }
            String taskId = bot.getTaskManager().createExploreTask();
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 探索任务已创建: " + taskId), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 探索启动失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int createTask(ServerCommandSource source, String goal) {
        BotInstance bot = resolveBot(source, null);
        if (bot == null) {
            source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
            return 0;
        }
        return createTaskForBot(source, bot.getBotName(), goal);
    }

    private static int createTaskForBot(ServerCommandSource source, String botName, String goal) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 未找到AI: " + botName), false);
                return 0;
            }
            String taskId = bot.getTaskManager().createTask(goal);
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 任务已创建: " + taskId), false);
            source.sendFeedback(() -> Text.literal("§7  目标: " + goal), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 任务创建失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int spawnBot(ServerCommandSource source, String name) {
        try {
            BotManager mgr = getManager();
            if (mgr == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] Bot系统未初始化"), false);
                return 0;
            }

            Vec3d spawnPos = source.getPosition();
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                // Player's view direction + 3 blocks forward
                Vec3d look = player.getRotationVector();
                spawnPos = player.getPos().add(look.x * 3, 0, look.z * 3);
                // Find ground
                BlockPos ground = findGroundBelow(player.getServerWorld(), spawnPos);
                if (ground != null) {
                    spawnPos = new Vec3d(spawnPos.x, ground.getY() + 1, spawnPos.z);
                }
            }

            var server = source.getServer();
            var world = source.getWorld();
            mgr.spawn(name, server, world, spawnPos);
            source.sendFeedback(() -> Text.literal("§a[AI Player] AI已生成: " + name), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 生成失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int despawnBot(ServerCommandSource source, String name) {
        try {
            BotManager mgr = getManager();
            if (mgr == null || mgr.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }

            if (name != null) {
                BotInstance bot = mgr.getByName(name);
                if (bot == null) {
                    source.sendFeedback(() -> Text.literal("§7[AI Player] 未找到AI: " + name), false);
                    return 0;
                }
                mgr.despawn(bot.getBotId());
                source.sendFeedback(() -> Text.literal("§a[AI Player] AI已移除: " + name), false);
            } else {
                // Despawn nearest
                BotInstance nearest = resolveBot(source, null);
                if (nearest != null) {
                    mgr.despawn(nearest.getBotId());
                    source.sendFeedback(() -> Text.literal("§a[AI Player] AI已移除: " + nearest.getBotName()), false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 移除失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int listBots(ServerCommandSource source) {
        try {
            BotManager mgr = getManager();
            if (mgr == null || mgr.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 当前没有活动的AI"), false);
                return 0;
            }
            source.sendFeedback(() -> Text.literal("§6===== AI列表 (" + mgr.getCount() + "个) ====="), false);
            for (BotInstance bot : mgr.getAll()) {
                boolean hasTask = bot.getTaskManager().getActiveTask() != null;
                String status = hasTask ? "§a[工作中]" : "§7[空闲]";
                source.sendFeedback(() -> Text.literal("§e" + bot.getBotName() + " §f" + status), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showPersonality(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }

            var botParams = bot.getBotParams();
            source.sendFeedback(() -> Text.literal("§6===== " + bot.getBotName() + " 参数 ====="), false);
            source.sendFeedback(() -> Text.literal("§e学习速率(α): §f" + String.format("%.3f", botParams.getAlpha()) + " §7(0.1~0.6)"), false);
            source.sendFeedback(() -> Text.literal("§e固执程度(β): §f" + String.format("%.4f", botParams.getBeta()) + " §7(0.002~0.03)"), false);

            var skillManager = AIPlayerMod.getSkillManager();
            if (skillManager != null) {
                var skills = skillManager.getSkills();
                long conditionedCount = skills.values().stream()
                        .filter(s -> "conditioned".equals(s.getType())).count();
                source.sendFeedback(() -> Text.literal("§e已学习反射: §f" + conditionedCount + "个"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 获取参数失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int forgetMemory(ServerCommandSource source, String id, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }
            boolean deleted = bot.getMemoryManager().deleteMemory(id);
            if (deleted) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 记忆已删除: " + id), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 未找到记忆: " + id), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 删除失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int triggerSuggestions(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null || !bot.isSpawned()) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有活动的AI"), false);
                return 0;
            }

            var suggestion = bot.getIdleBrain().forceSuggest();
            if (suggestion != null) {
                bot.sendMessage(suggestion.text());
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 建议已发送: " + suggestion.text()), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 当前没有可用的建议模板"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 建议触发失败: " + e.getMessage()), false);
        }
        return 1;
    }

    // Shared utility methods
    private static int setApiKey(ServerCommandSource source, String key) {
        try {
            var aiClient = AIPlayerMod.getAIClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] AI客户端未初始化"), false);
                return 0;
            }
            if ("clear".equalsIgnoreCase(key.trim())) {
                aiClient.setApiKey("");
                source.sendFeedback(() -> Text.literal("§a[AI Player] API密钥已清除，退回规则引擎模式"), false);
            } else {
                aiClient.setApiKey(key.trim());
                source.sendFeedback(() -> Text.literal("§a[AI Player] API密钥已设置，正在测试连接..."), false);
                boolean ok = aiClient.testConnection();
                if (ok) {
                    source.sendFeedback(() -> Text.literal("§a[AI Player] 连接成功! AI模式已激活"), false);
                } else {
                    source.sendFeedback(() -> Text.literal("§c[AI Player] 连接失败，请检查密钥是否正确"), false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 设置失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showApiKeyStatus(ServerCommandSource source) {
        try {
            var aiClient = AIPlayerMod.getAIClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] AI客户端未初始化"), false);
                return 0;
            }
            if (aiClient.isConfigured()) {
                source.sendFeedback(() -> Text.literal("§a[AI Player] AI模式已激活 (DeepSeek)"), false);
                source.sendFeedback(() -> Text.literal("§7  使用 /ai setkey clear 可清除密钥，退回规则引擎"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 规则引擎模式"), false);
                source.sendFeedback(() -> Text.literal("§7  使用 /ai setkey <key> 设置API密钥启用AI"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showCurrentModel(ServerCommandSource source) {
        try {
            var config = com.izimi.aiplayermod.cortex.api.AIConfig.load();
            source.sendFeedback(() -> Text.literal("§e[AI Player] 当前模型: §f" + config.apiModel), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int setApiModel(ServerCommandSource source, String modelName) {
        try {
            var aiClient = AIPlayerMod.getAIClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] AI客户端未初始化"), false);
                return 0;
            }
            aiClient.setApiModel(modelName);
            source.sendFeedback(() -> Text.literal("§a[AI Player] AI模型已设置为: §f" + modelName), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 设置失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static BlockPos findGroundBelow(net.minecraft.server.world.ServerWorld world, Vec3d pos) {
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int y = (int) Math.floor(pos.y);
        for (int dy = 0; dy > -20; dy--) {
            BlockPos check = new BlockPos(x, y + dy, z);
            var state = world.getBlockState(check);
            if (state.isSolidBlock(world, check)) {
                return check.up();
            }
        }
        return new BlockPos(x, y, z);
    }
}
