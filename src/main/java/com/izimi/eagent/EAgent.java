package com.izimi.eagent;

import com.izimi.eagent.api.CognitiveBrainAPI;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.api.impl.CognitiveBrain;
import com.izimi.eagent.api.impl.WorldContextImpl;
import com.izimi.eagent.cortex.planner.PlanManager;
import com.izimi.eagent.cortex.planner.KnowledgeBase;
import com.izimi.eagent.cortex.planner.LocalTaskDecomposer;
import com.izimi.eagent.cortex.chat.LocalChatHandler;
import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.brainstem.adapter.MinecraftActionAdapter;
import com.izimi.eagent.brainstem.scheduler.*;

import com.izimi.eagent.cortex.api.AIClient;
import com.izimi.eagent.cortex.api.AIChatHandler;
import com.izimi.eagent.cortex.api.AITaskPlanner;
import com.izimi.eagent.cortex.api.DeepSeekClient;
import com.izimi.eagent.cortex.api.AIConfig;
import com.izimi.eagent.cortex.api.TemplateManager;
import com.izimi.eagent.cortex.api.PersonaManager;
import com.izimi.eagent.cortex.api.TemplateMatcher;
import com.izimi.eagent.cortex.inhibitor.InhibitoryControl;
import com.izimi.eagent.amygdala.FamiliarityTracker;
import com.izimi.eagent.brainstem.IdleBrain;
import com.izimi.eagent.amygdala.NaiveBayesClassifier;
import com.izimi.eagent.amygdala.SocialObserver;
import com.izimi.eagent.brainstem.bot.BotSpawner;
import com.izimi.eagent.brainstem.bot.BotManager;
import com.izimi.eagent.brainstem.bot.BotInstance;
import com.izimi.eagent.brainstem.bot.BotController;
import com.izimi.eagent.amygdala.character.BehaviorEventHandler;
import com.izimi.eagent.amygdala.character.BehaviorStats;
import com.izimi.eagent.amygdala.character.EvaluationCycle;
import com.izimi.eagent.amygdala.ThresholdConfig;
import com.izimi.eagent.command.AICommand;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.hippocampus.MemoryManager;
import com.izimi.eagent.hippocampus.MemoryQuery;
import com.izimi.eagent.state.StateManager;
import com.izimi.eagent.cortex.task.TaskManager;
import com.izimi.eagent.cortex.task.TaskExecutor;
import com.izimi.eagent.brainstem.skill.SkillManager;
import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.log.ExecutionLogger;
import com.izimi.eagent.amygdala.learning.LearningSystem;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.brainstem.innate.MinecraftReflexEvaluator;
import com.izimi.eagent.util.FileUtil;
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

public class EAgent implements ModInitializer {
    public static final String MOD_ID = "e-agent";
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
    private static BasicActionAdapter actionAdapter;
    private static PlanManager planManager;
    private static InhibitoryControl inhibitor;
    private static KnowledgeBase knowledgeBase;
    private static LocalTaskDecomposer localTaskDecomposer;
    private static LocalChatHandler localChatHandler;
    private static BayesianModule bayesianModule;
    private static TemplateManager templateManager;
    private static PersonaManager personaManager;
    private static WorldContext worldContext;
    private static CognitiveBrainAPI cognitiveBrain;

    public record PendingChat(String message, String stateStr, String taskStr, String memsStr) {}
    private static PendingChat pendingChat;
    private static long pendingChatTime;

    @Override
    public void onInitialize() {
        LOGGER.info("[E-Agent] 初始化开始...");

        try {
            FileUtil.ensureDirectories();
            FileUtil.cleanupTempFiles();
            LOGGER.info("[E-Agent] 目录结构已创建, 已清理残留tmp文件");
        } catch (Exception e) {
            LOGGER.error("[E-Agent] 目录创建/清理失败", e);
        }

        config = ModConfig.load();
        LOGGER.info("[E-Agent] 配置已加载");

        aiClient = new DeepSeekClient();
        AIConfig.load();
        LOGGER.info("[E-Agent] AI客户端已初始化 (模式: {})",
                aiClient.isConfigured() ? "AI" : "规则引擎");

        templateManager = new TemplateManager(aiClient);
        personaManager = new PersonaManager(templateManager);
        aiTaskPlanner = new AITaskPlanner(aiClient);
        aiChatHandler = new AIChatHandler(aiClient);
        planManager = new PlanManager(aiTaskPlanner);
        knowledgeBase = KnowledgeBase.load();
        localTaskDecomposer = new LocalTaskDecomposer(knowledgeBase, planManager);
        localChatHandler = new LocalChatHandler();
        LOGGER.info("[E-Agent] KnowledgeBase + LocalTaskDecomposer + LocalChatHandler 已初始化 ({} 条知识)", knowledgeBase.allKeys().size());

        botSpawner = new BotSpawner();
        botManager = new BotManager();
        stateManager = new StateManager();
        memoryManager = new MemoryManager(config);
        memoryQuery = new MemoryQuery(memoryManager);
        taskManager = new TaskManager();
        skillManager = new SkillManager();
        executionLogger = new ExecutionLogger();
        var reflexEvaluator = new MinecraftReflexEvaluator();
        reflexRegistry = new InnateReflexRegistry(reflexEvaluator);
        var reflexesPath = FileUtil.getInnateReflexesPath();
        reflexRegistry.loadFromJson(reflexesPath);
        if (reflexRegistry.size() == 0) {
            reflexRegistry.loadDefaults();
            reflexRegistry.saveToJson(reflexesPath);
            LOGGER.info("[E-Agent] 已创建默认先天反射配置: {}", reflexesPath);
        }
        LOGGER.info("[E-Agent] 先天反射注册表已初始化, {} 个反射", reflexRegistry.size());
        reflexEvaluator.setPendingChatChecker((bot, timeout) -> {
            if (pendingChat == null) return false;
            long elapsed = (System.currentTimeMillis() - pendingChatTime) / 1000;
            return elapsed < (timeout > 0 ? timeout : 30);
        });
        actionAdapter = new MinecraftActionAdapter();
        conditionedReflex = new ConditionedReflex(skillManager, config, actionAdapter);
        taskExecutor = new TaskExecutor(taskManager, skillManager, executionLogger);
        behaviorStats = new BehaviorStats();
        behaviorEventHandler = new BehaviorEventHandler(behaviorStats);
        idleBrain = new IdleBrain(taskManager, skillManager);

        thresholdConfig = ThresholdConfig.load();
        evaluationCycle = new EvaluationCycle(conditionedReflex, aiClient);
        familiarityTracker = new FamiliarityTracker();
        socialObserver = new SocialObserver(familiarityTracker);
        bayesianModule = new BayesianModule(null);
        conditionedReflex.setBayesianModule(bayesianModule);
        socialClassifier = new NaiveBayesClassifier(thresholdConfig, bayesianModule);
        inhibitor = new InhibitoryControl();

        worldContext = new WorldContextImpl(
                reflexRegistry, actionAdapter, inhibitor,
                socialObserver, familiarityTracker,
                localTaskDecomposer, localChatHandler, templateManager, knowledgeBase,
                aiClient, aiChatHandler, aiTaskPlanner,
                skillManager, behaviorStats, config, executionLogger
        );
        botManager.setWorldContext(worldContext);
        worldContext.setBotManager(botManager);
        planManager.setWorldContext(worldContext);
        cognitiveBrain = new CognitiveBrain(worldContext, botManager);

        botController = new BotController(botSpawner, taskManager, taskExecutor, stateManager,
                conditionedReflex, aiChatHandler, aiClient, idleBrain,
                socialClassifier, reflexRegistry, inhibitor, memoryManager);
        botController.setWorldContext(worldContext);

        MotivationEngine motivationEngine = new MotivationEngine();
        MetaScheduler metaScheduler = new MetaScheduler(motivationEngine);
        botController.setMetaScheduler(metaScheduler);

        var correlationDetector = new com.izimi.eagent.amygdala.learning.CorrelationDetector(
                worldContext);
        metaScheduler.setCorrelationDetector(correlationDetector);

        LOGGER.info("[E-Agent] MetaScheduler 已初始化 (MotivationEngine + LLM Gate)");

        learningSystem = new LearningSystem(conditionedReflex, skillManager);
        behaviorEventHandler.addLearningListener(learningSystem::onEvent);

        behaviorEventHandler.addLearningListener(socialObserver::onEvent);

        LOGGER.info("[E-Agent] P1.5 社交镜像系统已初始化");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AICommand.setWorldContext(worldContext);
            AICommand.register(dispatcher);
            LOGGER.info("[E-Agent] 指令已注册");
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            LOGGER.info("[E-Agent] 世界已加载");
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
            if (sender == null) return;
            String content = message.getSignedContent();
            if (content == null || content.startsWith("/")) return;

            if (handleAtBotRoute(content, sender)) return;
            if (handleIdleBrainRoute(content, sender)) return;
            handleFallbackRoute(content, sender);
        });

        LOGGER.info("[E-Agent] 初始化完成");
    }

    private static boolean handleAtBotRoute(String content, ServerPlayerEntity sender) {
        if (!content.startsWith("@")) return false;
        int spaceIdx = content.indexOf(' ');
        String namePart = spaceIdx > 0 ? content.substring(1, spaceIdx) : content.substring(1);
        String msgBody = spaceIdx > 0 ? content.substring(spaceIdx + 1) : "";
        BotInstance target = botManager != null ? botManager.getByName(namePart) : null;
        if (target == null || !target.isSpawned()) return false;
        target.sendMessage("§7收到来自 " + sender.getName().getString() + " 的指令");
        routeChatToBot(target, sender, msgBody);
        return true;
    }

    private static boolean handleIdleBrainRoute(String content, ServerPlayerEntity sender) {
        if (botManager != null && !botManager.isEmpty()) {
            return handleMultiBotIdle(content, sender);
        }
        if (idleBrain != null) {
            return handleLegacyIdle(content);
        }
        return false;
    }

    private static boolean handleMultiBotIdle(String content, ServerPlayerEntity sender) {
        BotInstance nearest = botManager.getNearest(sender);
        if (nearest == null) return false;
        var idleResponse = nearest.getIdleBrain().handlePlayerChat(content);
        switch (idleResponse.type()) {
            case AFFIRMATIVE:
                nearest.getTaskManager().createTask(idleResponse.taskGoal());
                nearest.sendMessage(idleResponse.message());
                return true;
            case NEGATIVE:
                nearest.sendMessage(idleResponse.message());
                return true;
            case IRRELEVANT:
                return false;
        }
        return false;
    }

    private static boolean handleLegacyIdle(String content) {
        IdleBrain.IdleResponse response = idleBrain.handlePlayerChat(content);
        switch (response.type()) {
            case AFFIRMATIVE:
                taskManager.createTask(response.taskGoal());
                sendBotMessage("§b[E-Agent] §f" + response.message());
                return true;
            case NEGATIVE:
                sendBotMessage("§b[E-Agent] §f" + response.message());
                return true;
            case IRRELEVANT:
                return false;
        }
        return false;
    }

    private static void sendBotMessage(String text) {
        if (botController == null) return;
        var bot = botSpawner.getBotEntity();
        if (bot != null) {
            bot.sendMessage(Text.literal(text));
        }
    }

    private static void handleFallbackRoute(String content, ServerPlayerEntity sender) {
        if (evaluationCycle != null) {
            evaluationCycle.checkMessage(content);
        }

        BotInstance chatTarget = botManager != null && sender != null ? botManager.getNearest(sender) : null;
        if (chatTarget != null) {
            chatTarget.setPendingChat(content);
        } else {
            if (botController != null) {
                botController.setPendingChatMessage(content);
            }
            pendingChat = new PendingChat(content, "", "", "");
            pendingChatTime = System.currentTimeMillis();
        }
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

    public static MemoryManager getMemoryManager() { return memoryManager; }

    public static CognitiveBrainAPI getAPI() { return cognitiveBrain; }

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
