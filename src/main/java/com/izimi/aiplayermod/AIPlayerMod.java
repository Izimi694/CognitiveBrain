package com.izimi.aiplayermod;

import com.izimi.aiplayermod.api.*;
import com.izimi.aiplayermod.autonomy.FamiliarityTracker;
import com.izimi.aiplayermod.autonomy.IdleBrain;
import com.izimi.aiplayermod.autonomy.NaiveBayesClassifier;
import com.izimi.aiplayermod.autonomy.SocialObserver;
import com.izimi.aiplayermod.bot.BotSpawner;
import com.izimi.aiplayermod.bot.BotController;
import com.izimi.aiplayermod.character.CharacterManager;
import com.izimi.aiplayermod.character.BehaviorObserver;
import com.izimi.aiplayermod.character.EvaluationCycle;
import com.izimi.aiplayermod.character.PersonalityStress;
import com.izimi.aiplayermod.character.ThresholdConfig;
import com.izimi.aiplayermod.command.AICommand;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.memory.MemoryManager;
import com.izimi.aiplayermod.memory.MemoryQuery;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.task.TaskManager;
import com.izimi.aiplayermod.task.TaskExecutor;
import com.izimi.aiplayermod.skill.SkillManager;
import com.izimi.aiplayermod.skill.ConditionedReflex;
import com.izimi.aiplayermod.log.ExecutionLogger;
import com.izimi.aiplayermod.learning.LearningSystem;
import com.izimi.aiplayermod.reflexes.InnateReflexes;
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
    private static CharacterManager characterManager;
    private static BehaviorObserver behaviorObserver;
    private static ExecutionLogger executionLogger;
    private static PersonalityStress personalityStress;
    private static IdleBrain idleBrain;
    private static LearningSystem learningSystem;
    private static FamiliarityTracker familiarityTracker;
    private static SocialObserver socialObserver;
    private static ThresholdConfig thresholdConfig;
    private static EvaluationCycle evaluationCycle;
    private static NaiveBayesClassifier socialClassifier;
    private static InnateReflexes innateReflexes;
    private static AIClient aiClient;
    private static AITaskPlanner aiTaskPlanner;
    private static AIChatHandler aiChatHandler;
    private static AIMemoryGenerator aiMemoryGenerator;

    @Override
    public void onInitialize() {
        LOGGER.info("[AI Player] 初始化开始...");

        try {
            FileUtil.ensureDirectories();
            LOGGER.info("[AI Player] 目录结构已创建");
        } catch (Exception e) {
            LOGGER.error("[AI Player] 目录创建失败", e);
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

        personalityStress = new PersonalityStress(config);
        LOGGER.info("[AI Player] 性格压力系统已初始化");

        botSpawner = new BotSpawner();
        stateManager = new StateManager();
        memoryManager = new MemoryManager(config);
        memoryQuery = new MemoryQuery(memoryManager);
        taskManager = new TaskManager();
        skillManager = new SkillManager();
        executionLogger = new ExecutionLogger();
        conditionedReflex = new ConditionedReflex(skillManager, config);
        taskExecutor = new TaskExecutor(taskManager, skillManager, stateManager, executionLogger);
        characterManager = new CharacterManager(config);
        behaviorObserver = new BehaviorObserver(characterManager, config, personalityStress);
        idleBrain = new IdleBrain(taskManager, skillManager);

        thresholdConfig = ThresholdConfig.load();
        evaluationCycle = new EvaluationCycle(thresholdConfig, characterManager);
        familiarityTracker = new FamiliarityTracker();
        socialObserver = new SocialObserver(familiarityTracker);
        socialClassifier = new NaiveBayesClassifier(thresholdConfig);
        innateReflexes = new InnateReflexes();

        botController = new BotController(botSpawner, taskManager, taskExecutor, stateManager,
                conditionedReflex, aiTaskPlanner, aiChatHandler, aiClient, idleBrain,
                socialClassifier, innateReflexes);

        learningSystem = new LearningSystem(conditionedReflex, skillManager);
        behaviorObserver.addLearningListener(learningSystem::onEvent);

        behaviorObserver.addLearningListener(socialObserver::onEvent);

        LOGGER.info("[AI Player] P1.5 社交镜像系统已初始化 (conformity={:.2f})",
                thresholdConfig.conformityCoefficient);

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
            if (personalityStress != null) {
                personalityStress.onTick();
            }
            if (evaluationCycle != null) {
                evaluationCycle.onTick();
            }
            updateNearbyPlayers(server);
        });

        behaviorObserver.register();

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

                    if (aiChatHandler != null && aiClient.isConfigured()) {
                        var state = stateManager.loadState();
                        var task = taskManager.getActiveTask();
                        var mems = memoryManager.getRecentMemories();
                        var prefs = characterManager.getPreferenceMap();

                        personalityStress.onPlayerInteraction(0.5);

                        aiChatHandler.handleChat(content, state, task, mems, prefs, personalityStress);
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
    public static CharacterManager getCharacterManager() { return characterManager; }
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
    public static PersonalityStress getPersonalityStress() { return personalityStress; }
    public static IdleBrain getIdleBrain() { return idleBrain; }
    public static LearningSystem getLearningSystem() { return learningSystem; }
    public static FamiliarityTracker getFamiliarityTracker() { return familiarityTracker; }
    public static SocialObserver getSocialObserver() { return socialObserver; }
    public static ThresholdConfig getThresholdConfig() { return thresholdConfig; }
    public static EvaluationCycle getEvaluationCycle() { return evaluationCycle; }
    public static InnateReflexes getInnateReflexes() { return innateReflexes; }

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
