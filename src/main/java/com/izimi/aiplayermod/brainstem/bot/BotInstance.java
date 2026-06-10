package com.izimi.aiplayermod.brainstem.bot;

import java.util.UUID;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.amygdala.learning.CorrelationDetector;
import com.izimi.aiplayermod.amygdala.learning.LearningSystem;
import com.izimi.aiplayermod.bayesian.BayesianModule;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.brainstem.adapter.TemporalScaler;
import com.izimi.aiplayermod.brainstem.scheduler.MetaContext;
import com.izimi.aiplayermod.brainstem.scheduler.MetaScheduler;
import com.izimi.aiplayermod.brainstem.scheduler.MotivationEngine;
import com.izimi.aiplayermod.cortex.api.AITaskPlanner;
import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.cortex.task.TaskExecutor;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.hippocampus.MemoryManager;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.util.FileUtil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BotInstance {
    private final UUID botId;
    private final String botName;
    private final BotPlayer botPlayer;

    // Per-bot components
    private final BotParams botParams;
    private final ConditionedReflex conditionedReflex;
    private final OneShotAlarmSystem alarms;
    private final HormonalSystem hormonalSystem;
    private final MotivationEngine motivationEngine;
    private final MetaScheduler metaScheduler;
    private final MetaContext metaContext;
    private final StateManager stateManager;
    private final TemporalScaler temporalScaler;
    private final CorrelationDetector correlationDetector;
    private final DispatchReflex dispatchReflex;
    private final BayesianModule bayesianModule;
    private final IdleBrain idleBrain;
    private final LearningSystem learningSystem;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final MemoryManager memoryManager;
    private final PlanManager planManager;

    private int tickCounter = 0;
    private static final int STATE_SAVE_INTERVAL = 200;
    private boolean deathGenomeSaved = false;

    public BotInstance(UUID botId, String botName, BotPlayer botPlayer) {
        this(botId, botName, botPlayer, null);
    }

    public BotInstance(UUID botId, String botName, BotPlayer botPlayer, BotParams inheritedParams) {
        this.botId = botId;
        this.botName = botName;
        this.botPlayer = botPlayer;

        var config = AIPlayerMod.getConfig();
        var skillManager = AIPlayerMod.getSkillManager();
        var actionAdapter = AIPlayerMod.getActionAdapter();
        var reflexRegistry = AIPlayerMod.getReflexRegistry();
        var inhibitor = AIPlayerMod.getInhibitor();
        var localTaskDecomposer = AIPlayerMod.getLocalTaskDecomposer();
        var localChatHandler = AIPlayerMod.getLocalChatHandler();

        this.botParams = inheritedParams != null ? inheritedParams : BotParams.generate();
        this.botParams.withBotId(botId);
        this.conditionedReflex = new ConditionedReflex(skillManager, config, actionAdapter, botId);
        this.alarms = new OneShotAlarmSystem(botId);
        this.hormonalSystem = new HormonalSystem();
        this.motivationEngine = new MotivationEngine();
        this.metaScheduler = new MetaScheduler(motivationEngine);
        this.stateManager = new StateManager(botId);
        this.temporalScaler = this.metaScheduler.getTemporalScaler();
        this.dispatchReflex = new DispatchReflex(botParams, botId);
        this.bayesianModule = new BayesianModule(botId);
        this.conditionedReflex.setBayesianModule(bayesianModule);
        this.taskManager = new TaskManager(botId);
        this.taskExecutor = new TaskExecutor(taskManager, skillManager, AIPlayerMod.getExecutionLogger());
        this.memoryManager = new MemoryManager(config, botId);
        this.correlationDetector = new CorrelationDetector(skillManager, actionAdapter);
        this.learningSystem = new LearningSystem(conditionedReflex, skillManager);
        this.planManager = new PlanManager(new AITaskPlanner(AIPlayerMod.getAIClient()), botId);

        this.idleBrain = new IdleBrain(taskManager, skillManager);
        this.metaScheduler.setCorrelationDetector(correlationDetector);

        this.metaContext = new MetaContext(
                botId, botName, botParams, hormonalSystem, alarms,
                conditionedReflex, dispatchReflex, reflexRegistry, inhibitor,
                taskManager, memoryManager, stateManager, idleBrain,
                localTaskDecomposer, localChatHandler, planManager,
                AIPlayerMod.getSocialObserver(), AIPlayerMod.getFamiliarityTracker(),
                bayesianModule,
                botPlayer.asEntity()
            );
    }

    public void tick(MinecraftServer server) {
        ServerPlayerEntity bot = botPlayer.asEntity();
        if (bot == null) return;

        if (bot.isRemoved()) {
            saveDeathGenome("removed");
            return;
        }

        if (bot.getHealth() <= 0 && !deathGenomeSaved) {
            saveDeathGenome("killed");
        }

        tickCounter++;

        metaScheduler.tick(metaContext, server);

        hormonalSystem.tick();

        if (tickCounter % STATE_SAVE_INTERVAL == 0) {
            stateManager.saveState(bot);
        }
    }

    public void saveDeathGenome(String cause) {
        if (deathGenomeSaved) return;
        deathGenomeSaved = true;
        botParams.saveToPath(FileUtil.getBotParamsPath(botId));
        GenomeArchivist.saveGenome(botId, botParams, botName, cause);
    }

    public void sendMessage(String message) {
        if (botPlayer != null && !botPlayer.isRemoved()) {
            botPlayer.asEntity().sendMessage(Text.literal("§b[" + botName + "] §f" + message));
        }
    }

    // Per-bot component getters
    public UUID getBotId() { return botId; }
    public String getBotName() { return botName; }
    public BotPlayer getBotPlayer() { return botPlayer; }
    public ServerPlayerEntity asEntity() { return botPlayer != null ? botPlayer.asEntity() : null; }
    public BotParams getBotParams() { return botParams; }
    public ConditionedReflex getConditionedReflex() { return conditionedReflex; }
    public OneShotAlarmSystem getAlarms() { return alarms; }
    public HormonalSystem getHormonalSystem() { return hormonalSystem; }
    public MotivationEngine getMotivationEngine() { return motivationEngine; }
    public MetaScheduler getMetaScheduler() { return metaScheduler; }
    public MetaContext getMetaContext() { return metaContext; }
    public StateManager getStateManager() { return stateManager; }
    public TemporalScaler getTemporalScaler() { return temporalScaler; }
    public CorrelationDetector getCorrelationDetector() { return correlationDetector; }
    public DispatchReflex getDispatchReflex() { return dispatchReflex; }
    public BayesianModule getBayesianModule() { return bayesianModule; }
    public IdleBrain getIdleBrain() { return idleBrain; }
    public LearningSystem getLearningSystem() { return learningSystem; }
    public TaskManager getTaskManager() { return taskManager; }
    public TaskExecutor getTaskExecutor() { return taskExecutor; }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public PlanManager getPlanManager() { return planManager; }

    public boolean isSpawned() {
        return botPlayer != null && !botPlayer.isRemoved();
    }
}
