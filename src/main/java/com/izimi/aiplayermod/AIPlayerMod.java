package com.izimi.aiplayermod;

import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.adapter.MinecraftActionAdapter;
import com.izimi.aiplayermod.cortex.api.*;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.amygdala.NaiveBayesClassifier;
import com.izimi.aiplayermod.amygdala.SocialObserver;
import com.izimi.aiplayermod.brainstem.bot.BotSpawner;
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
import com.izimi.aiplayermod.amygdala.reflexes.InnateReflexRegistry;
import com.izimi.aiplayermod.amygdala.reflexes.MinecraftReflexEvaluator;
import com.izimi.aiplayermod.util.FileUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

        aiTaskPlanner = new AITaskPlanner(aiClient);
        aiChatHandler = new AIChatHandler(aiClient);
        aiMemoryGenerator = new AIMemoryGenerator(aiClient);
        planManager = new PlanManager(aiTaskPlanner);

        botSpawner = new BotSpawner();
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
        taskExecutor = new TaskExecutor(taskManager, skillManager, stateManager, executionLogger);
        behaviorStats = new BehaviorStats();
        behaviorEventHandler = new BehaviorEventHandler(behaviorStats);
        idleBrain = new IdleBrain(taskManager, skillManager);

        thresholdConfig = ThresholdConfig.load();
        evaluationCycle = new EvaluationCycle(conditionedReflex);
        familiarityTracker = new FamiliarityTracker();
        socialObserver = new SocialObserver(familiarityTracker);
        socialClassifier = new NaiveBayesClassifier(thresholdConfig);
        inhibitor = new InhibitoryControl();

        botController = new BotController(botSpawner, taskManager, taskExecutor, stateManager,
                conditionedReflex, aiTaskPlanner, aiChatHandler, aiClient, idleBrain,
                socialClassifier, reflexRegistry, inhibitor);

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
            if (botController != null) {
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
                if (content != null && !content.startsWith("/")) {
                    if (idleBrain != null) {
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

                    if (evaluationCycle != null) {
                        evaluationCycle.checkMessage(content);
                    }

                    if (aiClient.isConfigured()) {
                        var state = stateManager.loadState();
                        var task = taskManager.getActiveTask();
                        var mems = memoryManager.getRecentMemories();
                        pendingChat = new PendingChat(content,
                                state != null ? state.toString() : "",
                                task != null ? task.getGoal() : "",
                                mems != null ? mems.toString() : "");
                        pendingChatTime = System.currentTimeMillis();
                    }
                }
            }
        });

        LOGGER.info("[AI Player] 初始化完成");
    }

    public static ModConfig getConfig() { return config; }
    public static TaskManager getTaskManager() { return taskManager; }
    public static MemoryManager getMemoryManager() { return memoryManager; }
    public static BotSpawner getBotSpawner() { return botSpawner; }
    public static SkillManager getSkillManager() { return skillManager; }
    public static StateManager getStateManager() { return stateManager; }
    public static ExecutionLogger getExecutionLogger() { return executionLogger; }
    public static BotController getBotController() { return botController; }
    public static ConditionedReflex getConditionedReflex() { return conditionedReflex; }
    public static MemoryQuery getMemoryQuery() { return memoryQuery; }
    public static AIClient getAIClient() { return aiClient; }
    public static AITaskPlanner getAiTaskPlanner() { return aiTaskPlanner; }
    public static AIChatHandler getAiChatHandler() { return aiChatHandler; }
    public static AIMemoryGenerator getAiMemoryGenerator() { return aiMemoryGenerator; }
    public static IdleBrain getIdleBrain() { return idleBrain; }
    public static LearningSystem getLearningSystem() { return learningSystem; }
    public static FamiliarityTracker getFamiliarityTracker() { return familiarityTracker; }
    public static SocialObserver getSocialObserver() { return socialObserver; }
    public static ThresholdConfig getThresholdConfig() { return thresholdConfig; }
    public static EvaluationCycle getEvaluationCycle() { return evaluationCycle; }
    public static InnateReflexRegistry getReflexRegistry() { return reflexRegistry; }
    public static BasicActionAdapter getActionAdapter() { return actionAdapter; }
    public static PlanManager getPlanManager() { return planManager; }
    public static InhibitoryControl getInhibitor() { return inhibitor; }
    public static BehaviorStats getBehaviorStats() { return behaviorStats; }

    public static boolean hasPendingChat(double timeoutSecs) {
        if (pendingChat == null) return false;
        long elapsed = (System.currentTimeMillis() - pendingChatTime) / 1000;
        return elapsed < (timeoutSecs > 0 ? timeoutSecs : 30);
    }

    public static PendingChat consumePendingChat() {
        PendingChat pc = pendingChat;
        pendingChat = null;
        pendingChatTime = 0;
        return pc;
    }

    private static void updateNearbyPlayers(MinecraftServer server) {
        if (socialObserver == null || botSpawner == null || !botSpawner.isSpawned()) return;

        var bot = botSpawner.getBotEntity();
        if (bot == null) return;

        var world = bot.getServerWorld();
        var botPos = bot.getBlockPos();

        List<String> nearby = new ArrayList<>();
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (player == bot) continue;
            if (player.getServerWorld() != world) continue;
            if (player.getBlockPos().getSquaredDistance(botPos) <= 900) { // 30 blocks
                nearby.add(player.getName().getString());
            }
        }

        socialObserver.markNearbyPlayers(nearby);
    }
}
