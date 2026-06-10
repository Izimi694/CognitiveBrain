package com.izimi.aiplayermod;

import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.api.impl.WorldContextImpl;
import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.cortex.planner.KnowledgeBase;
import com.izimi.aiplayermod.cortex.planner.LocalTaskDecomposer;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.adapter.MinecraftActionAdapter;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import com.izimi.aiplayermod.brainstem.scheduler.*;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.cortex.api.*;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.amygdala.NaiveBayesClassifier;
import com.izimi.aiplayermod.amygdala.SocialObserver;
import com.izimi.aiplayermod.brainstem.bot.BotSpawner;
import com.izimi.aiplayermod.brainstem.bot.BotManager;
import com.izimi.aiplayermod.brainstem.bot.BotInstance;
import com.izimi.aiplayermod.brainstem.bot.BotController;
import com.izimi.aiplayermod.amygdala.character.BehaviorEventHandler;
import com.izimi.aiplayermod.amygdala.character.BehaviorStats;
import com.izimi.aiplayermod.amygdala.character.EvaluationCycle;
import com.izimi.aiplayermod.amygdala.ThresholdConfig;
import com.izimi.aiplayermod.command.AICommand;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.hippocampus.MemoryManager;
import com.izimi.aiplayermod.hippocampus.MemoryQuery;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.cortex.task.TaskExecutor;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.log.ExecutionLogger;
import com.izimi.aiplayermod.amygdala.learning.LearningSystem;
import com.izimi.aiplayermod.bayesian.BayesianModule;
import com.izimi.aiplayermod.cortex.api.TemplateManager;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.brainstem.innate.MinecraftReflexEvaluator;
import com.izimi.aiplayermod.util.FileUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AIPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai-player-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;
    private static TaskManager taskManager;
    private static TaskExecutor taskExecutor;
    private static MemoryManager memoryManager;
    private static MemoryQuery memoryQuery;
    private static SkillManager skillManager;
    private static ConditionedReflex conditionedReflex;
    private static StateManager stateManager;
    private static BotSpawner botSpawner;
    private static BotManager botManager;
    private static BotController botController;
    private static BehaviorEventHandler behaviorEventHandler;
    private static BehaviorStats behaviorStats;
    private static ExecutionLogger executionLogger;
    private static IdleBrain idleBrain;
    private static LearningSystem learningSystem;
    private static FamiliarityTracker familiarityTracker;
    private static SocialObserver socialObserver;
    private static ThresholdConfig thresholdConfig;
    private static EvaluationCycle evaluationCycle;
    private static NaiveBayesClassifier socialClassifier;
    private static InnateReflexRegistry reflexRegistry;
    private static AIClient aiClient;
    private static AITaskPlanner aiTaskPlanner;
    private static AIChatHandler aiChatHandler;
    private static AIMemoryGenerator aiMemoryGenerator;
    private static BasicActionAdapter actionAdapter;
    private static PlanManager planManager;
    private static InhibitoryControl inhibitor;
    private static KnowledgeBase knowledgeBase;
    private static LocalTaskDecomposer localTaskDecomposer;
    private static LocalChatHandler localChatHandler;
    private static BayesianModule bayesianModule;
    private static TemplateManager templateManager;
    private static WorldContext worldContext;

    public record PendingChat(String message, String stateStr, String taskStr, String memsStr) {}
    private static PendingChat pendingChat;
    private static long pendingChatTime;

    @Override
    public void onInitialize() {
        LOGGER.info("[AI Player] 初始化开始...");

        try {
            FileUtil.ensureDirectories();
            FileUtil.cleanupTempFiles();
            LOGGER.info("[AI Player] 目录结构已创建, 已清理残留tmp文件");
        } catch (Exception e) {
            LOGGER.error("[AI Player] 目录创建/清理失败", e);
        }

        config = ModConfig.load();
        LOGGER.info("[AI Player] 配置已加载");

        aiClient = new DeepSeekClient();
        AIConfig.load();
        LOGGER.info("[AI Player] AI客户端已初始化 (模式: {})",
                aiClient.isConfigured() ? "AI" : "规则引擎");

        templateManager = new TemplateManager(aiClient);
        aiTaskPlanner = new AITaskPlanner(aiClient);
        aiChatHandler = new AIChatHandler(aiClient);
        aiMemoryGenerator = new AIMemoryGenerator(aiClient);
        planManager = new PlanManager(aiTaskPlanner);
        knowledgeBase = KnowledgeBase.load();
        localTaskDecomposer = new LocalTaskDecomposer(knowledgeBase, planManager);
        localChatHandler = new LocalChatHandler();
        LOGGER.info("[AI Player] KnowledgeBase + LocalTaskDecomposer + LocalChatHandler 已初始化 ({} 条知识)", knowledgeBase.allKeys().size());

        botSpawner = new BotSpawner();
        botManager = new BotManager();
        stateManager = new StateManager();
        memoryManager = new MemoryManager(config);
        memoryQuery = new MemoryQuery(memoryManager);
        taskManager = new TaskManager();
        skillManager = new SkillManager();
        executionLogger = new ExecutionLogger();
        reflexRegistry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        var reflexesPath = FileUtil.getInnateReflexesPath();
        reflexRegistry.loadFromJson(reflexesPath);
        if (reflexRegistry.size() == 0) {
            reflexRegistry.loadDefaults();
            reflexRegistry.saveToJson(reflexesPath);
            LOGGER.info("[AI Player] 已创建默认先天反射配置: {}", reflexesPath);
        }
        LOGGER.info("[AI Player] 先天反射注册表已初始化, {} 个反射", reflexRegistry.size());
        actionAdapter = new MinecraftActionAdapter();
        conditionedReflex = new ConditionedReflex(skillManager, config, actionAdapter);
        taskExecutor = new TaskExecutor(taskManager, skillManager, executionLogger);
        behaviorStats = new BehaviorStats();
        behaviorEventHandler = new BehaviorEventHandler(behaviorStats);
        idleBrain = new IdleBrain(taskManager, skillManager);

        thresholdConfig = ThresholdConfig.load();
        evaluationCycle = new EvaluationCycle(conditionedReflex);
        familiarityTracker = new FamiliarityTracker();
        socialObserver = new SocialObserver(familiarityTracker);
        bayesianModule = new BayesianModule(null);
        conditionedReflex.setBayesianModule(bayesianModule);
        socialClassifier = new NaiveBayesClassifier(thresholdConfig, bayesianModule);
        inhibitor = new InhibitoryControl();

        worldContext = new WorldContextImpl(
                reflexRegistry, actionAdapter, inhibitor,
                socialObserver, familiarityTracker,
                localTaskDecomposer, localChatHandler, templateManager, knowledgeBase, aiClient,
                skillManager, behaviorStats, config
        );
        botManager.setWorldContext(worldContext);

        botController = new BotController(botSpawner, taskManager, taskExecutor, stateManager,
                conditionedReflex, aiChatHandler, aiClient, idleBrain,
                socialClassifier, reflexRegistry, inhibitor);

        MotivationEngine motivationEngine = new MotivationEngine();
        MetaScheduler metaScheduler = new MetaScheduler(motivationEngine);
        var defaultParams = com.izimi.aiplayermod.amygdala.BotParams.load();
        HormonalSystem hormonalSystem = new HormonalSystem();
        MetaContext metaContext = new MetaContext(
                UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                "AI_Assistant",
                defaultParams,
                hormonalSystem,
                new OneShotAlarmSystem(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")),
                conditionedReflex,
                new DispatchReflex(defaultParams, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")),
                reflexRegistry,
                inhibitor,
                taskManager,
                memoryManager,
                stateManager,
                idleBrain,
                localTaskDecomposer,
                localChatHandler,
                planManager,
                socialObserver,
                familiarityTracker,
                bayesianModule,
                null
        );
        botController.setMetaScheduler(metaScheduler, metaContext);

        var correlationDetector = new com.izimi.aiplayermod.amygdala.learning.CorrelationDetector(
                skillManager, actionAdapter);
        metaScheduler.setCorrelationDetector(correlationDetector);

        LOGGER.info("[AI Player] MetaScheduler 已初始化 (MotivationEngine + LLM Gate)");

        learningSystem = new LearningSystem(conditionedReflex, skillManager);
        behaviorEventHandler.addLearningListener(learningSystem::onEvent);

        behaviorEventHandler.addLearningListener(socialObserver::onEvent);

        LOGGER.info("[AI Player] P1.5 社交镜像系统已初始化");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AICommand.register(dispatcher);
            LOGGER.info("[AI Player] 指令已注册");
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            LOGGER.info("[AI Player] 世界已加载");
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (botManager != null) {
                botManager.tickAll(server);
            }
            if (botController != null && (botManager == null || botManager.isEmpty())) {
                botController.onTick(server);
            }
            if (evaluationCycle != null) {
                evaluationCycle.onTick();
            }
            updateNearbyPlayers(server);
        });

        behaviorEventHandler.register();

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender != null) {
                String content = message.getSignedContent();
                if (content == null || content.startsWith("/")) return;

                // Step 1: Check @bot_name routing
                if (content.startsWith("@")) {
                    int spaceIdx = content.indexOf(' ');
                    String namePart = spaceIdx > 0 ? content.substring(1, spaceIdx) : content.substring(1);
                    String msgBody = spaceIdx > 0 ? content.substring(spaceIdx + 1) : "";
                    BotInstance target = botManager != null ? botManager.getByName(namePart) : null;
                    if (target != null && target.isSpawned()) {
                        target.sendMessage("§7收到来自 " + sender.getName().getString() + " 的指令");
                        routeChatToBot(target, sender, msgBody);
                        return;
                    }
                }

                // Step 2: Try IdleBrain on nearest bot
                if (botManager != null && !botManager.isEmpty()) {
                    BotInstance nearest = null;
                    String msgBody = content;
                    // Strip leading @ if present (but we already checked above)
                    if (sender != null && botManager != null) {
                        nearest = botManager.getNearest(sender);
                    }

                    if (nearest != null) {
                        var idleResponse = nearest.getIdleBrain().handlePlayerChat(msgBody);
                        switch (idleResponse.type()) {
                            case AFFIRMATIVE:
                                nearest.getTaskManager().createTask(idleResponse.taskGoal());
                                nearest.sendMessage(idleResponse.message());
                                return;
                            case NEGATIVE:
                                nearest.sendMessage(idleResponse.message());
                                return;
                            case IRRELEVANT:
                                break;
                        }
                    }
                } else if (idleBrain != null) {
                    // Legacy single-bot path
                    IdleBrain.IdleResponse response = idleBrain.handlePlayerChat(content);
                    switch (response.type()) {
                        case AFFIRMATIVE:
                            taskManager.createTask(response.taskGoal());
                            if (botController != null) {
                                var bot = botSpawner.getBotEntity();
                                if (bot != null) {
                                    bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.message()));
                                }
                            }
                            return;
                        case NEGATIVE:
                            if (botController != null) {
                                var bot = botSpawner.getBotEntity();
                                if (bot != null) {
                                    bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.message()));
                                }
                            }
                            return;
                        case IRRELEVANT:
                            break;
                    }
                }

                // Step 3: Evaluation check
                if (evaluationCycle != null) {
                    evaluationCycle.checkMessage(content);
                }

                // Step 4: Route to nearest bot for LLM processing
                BotInstance chatTarget = null;
                if (botManager != null && sender != null) {
                    chatTarget = botManager.getNearest(sender);
                }

                if (chatTarget != null) {
                    chatTarget.getMetaContext().setPendingChat(content);
                } else {
                    pendingChat = new PendingChat(content, "", "", "");
                    pendingChatTime = System.currentTimeMillis();
                }
            }
        });

        LOGGER.info("[AI Player] 初始化完成");
    }

    private static void routeChatToBot(BotInstance bot, ServerPlayerEntity sender, String message) {
        if (message == null || message.isEmpty()) return;
        bot.getTaskManager().createTask(message);
        bot.sendMessage("§7收到: " + message);
    }

    public static void onReflexSuccess(ServerPlayerEntity bot, String skillId) {
        if (bot == null || botManager == null) return;
        String category;
        if (botManager != null) {
            BotInstance inst = botManager.getById(bot.getUuid());
            category = inst != null ? inst.getConditionedReflex().getReflexCategoryPublic(skillId) : null;
        } else {
            category = conditionedReflex != null ? conditionedReflex.getReflexCategoryPublic(skillId) : null;
        }
        if (category == null) return;
        botManager.notifyReflexSuccess(bot, category);
    }

    public static ModConfig getConfig() { return config; }
    @Deprecated public static TaskManager getTaskManager() { return taskManager; }
    @Deprecated public static MemoryManager getMemoryManager() { return memoryManager; }
    @Deprecated public static BotSpawner getBotSpawner() { return botSpawner; }
    public static BotManager getBotManager() { return botManager; }
    public static SkillManager getSkillManager() { return skillManager; }
    @Deprecated public static StateManager getStateManager() { return stateManager; }
    public static ExecutionLogger getExecutionLogger() { return executionLogger; }
    @Deprecated public static BotController getBotController() { return botController; }
    @Deprecated public static ConditionedReflex getConditionedReflex() { return conditionedReflex; }
    public static MemoryQuery getMemoryQuery() { return memoryQuery; }
    public static AIClient getAIClient() { return aiClient; }
    public static AITaskPlanner getAiTaskPlanner() { return aiTaskPlanner; }
    public static AIChatHandler getAiChatHandler() { return aiChatHandler; }
    public static AIMemoryGenerator getAiMemoryGenerator() { return aiMemoryGenerator; }
    @Deprecated public static IdleBrain getIdleBrain() { return idleBrain; }
    @Deprecated public static LearningSystem getLearningSystem() { return learningSystem; }
    public static FamiliarityTracker getFamiliarityTracker() { return familiarityTracker; }
    public static SocialObserver getSocialObserver() { return socialObserver; }
    public static ThresholdConfig getThresholdConfig() { return thresholdConfig; }
    public static EvaluationCycle getEvaluationCycle() { return evaluationCycle; }
    public static InnateReflexRegistry getReflexRegistry() { return reflexRegistry; }
    public static BasicActionAdapter getActionAdapter() { return actionAdapter; }
    public static PlanManager getPlanManager() { return planManager; }
    public static InhibitoryControl getInhibitor() { return inhibitor; }
    public static BehaviorStats getBehaviorStats() { return behaviorStats; }
    public static KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public static LocalTaskDecomposer getLocalTaskDecomposer() { return localTaskDecomposer; }
    public static LocalChatHandler getLocalChatHandler() { return localChatHandler; }
    public static BayesianModule getBayesianModule() { return bayesianModule; }
    public static TemplateManager getTemplateManager() { return templateManager; }

    // UUID-based overloads — route through BotManager
    public static ConditionedReflex getConditionedReflex(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getConditionedReflex();
        }
        return conditionedReflex;
    }

    public static TaskManager getTaskManager(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getTaskManager();
        }
        return taskManager;
    }

    public static StateManager getStateManager(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getStateManager();
        }
        return stateManager;
    }

    public static MemoryManager getMemoryManager(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getMemoryManager();
        }
        return memoryManager;
    }

    public static HormonalSystem getHormonalSystem(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getHormonalSystem();
        }
        return null;
    }

    public static BayesianModule getBayesianModule(UUID botId) {
        if (botManager != null) {
            BotInstance inst = botManager.getById(botId);
            if (inst != null) return inst.getBayesianModule();
        }
        return bayesianModule;
    }

    public static boolean hasPendingChat(double timeoutSecs) {
        if (pendingChat == null) return false;
        long elapsed = (System.currentTimeMillis() - pendingChatTime) / 1000;
        return elapsed < (timeoutSecs > 0 ? timeoutSecs : 30);
    }

    public static boolean hasPendingChat() {
        return pendingChat != null;
    }

    public static String peekPendingChatMessage() {
        return pendingChat != null ? pendingChat.message() : null;
    }

    public static PendingChat consumePendingChat() {
        PendingChat pc = pendingChat;
        pendingChat = null;
        pendingChatTime = 0;
        return pc;
    }

    private static void updateNearbyPlayers(MinecraftServer server) {
        if (socialObserver == null) return;

        // Update for each BotManager bot
        if (botManager != null) {
            for (BotInstance inst : botManager.getAll()) {
                if (!inst.isSpawned()) continue;
                var bot = inst.asEntity();
                if (bot == null) continue;
                var world = bot.getServerWorld();
                var botPos = bot.getBlockPos();
                List<String> nearby = new ArrayList<>();
                for (var player : server.getPlayerManager().getPlayerList()) {
                    if (player == bot) continue;
                    if (player.getServerWorld() != world) continue;
                    if (player.getBlockPos().getSquaredDistance(botPos) <= 900) {
                        nearby.add(player.getName().getString());
                    }
                }
                socialObserver.markNearbyPlayers(nearby);
            }
        }

        // Also update for legacy bot
        if (botSpawner != null && botSpawner.isSpawned()) {
            var bot = botSpawner.getBotEntity();
            if (bot == null) return;
            var world = bot.getServerWorld();
            var botPos = bot.getBlockPos();
            List<String> nearby = new ArrayList<>();
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (player == bot) continue;
                if (player.getServerWorld() != world) continue;
                if (player.getBlockPos().getSquaredDistance(botPos) <= 900) {
                    nearby.add(player.getName().getString());
                }
            }
            socialObserver.markNearbyPlayers(nearby);
        }
    }
}
