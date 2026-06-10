package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.amygdala.SocialObserver;
import com.izimi.aiplayermod.bayesian.BayesianModule;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.hippocampus.MemoryManager;
import com.izimi.aiplayermod.state.StateManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MetaContext {

    private final UUID botId;
    private final String botName;
    private final BotParams params;
    private final HormonalSystem hormones;
    private final OneShotAlarmSystem alarms;
    private final ConditionedReflex conditionedReflex;
    private final DispatchReflex dispatchReflex;
    private final InnateReflexRegistry reflexRegistry;
    private final InhibitoryControl inhibitor;
    private final TaskManager taskManager;
    private final MemoryManager memoryManager;
    private final StateManager stateManager;
    private final IdleBrain idleBrain;
    private final ILocalPlanner localPlanner;
    private final LocalChatHandler localChatHandler;
    private final PlanManager planManager;
    private final SocialObserver socialObserver;
    private final FamiliarityTracker familiarityTracker;
    private final BayesianModule bayesianModule;
    private final ServerPlayerEntity bot;

    private String pendingChatMessage;
    private String lastPlayerMessage = "";
    private int p3Cooldown = 0;
    private int tickSinceLastLLM = 0;
    private boolean recentLLMFailure = false;
    private long lastPlayerMessageTime = 0;
    private String currentBiomeId = "";
    private int lastTickEntityCount = 0;
    private int thisTickEntityCount = 0;
    private int lastActionSuccessCount = 0;

    private ProblemLabel currentProblemLabel = ProblemLabel.TRIVIAL;
    private int ticksInCurrentLabel = 0;
    private final Map<String, Integer> novelEntityTicks = new HashMap<>();
    private static final int NOVEL_ENTITY_WINDOW = 6000;
    private String lastBlockFingerprint = "";

    public MetaContext(UUID botId, String botName, BotParams params, HormonalSystem hormones,
                       OneShotAlarmSystem alarms, ConditionedReflex conditionedReflex,
                       DispatchReflex dispatchReflex, InnateReflexRegistry reflexRegistry,
                       InhibitoryControl inhibitor, TaskManager taskManager,
                       MemoryManager memoryManager, StateManager stateManager,
                       IdleBrain idleBrain, ILocalPlanner localPlanner,
                       LocalChatHandler localChatHandler,
                       PlanManager planManager,
                       SocialObserver socialObserver,
                       FamiliarityTracker familiarityTracker,
                       BayesianModule bayesianModule,
                       ServerPlayerEntity bot) {
        this.botId = botId;
        this.botName = botName;
        this.params = params;
        this.hormones = hormones;
        this.alarms = alarms;
        this.conditionedReflex = conditionedReflex;
        this.dispatchReflex = dispatchReflex;
        this.reflexRegistry = reflexRegistry;
        this.inhibitor = inhibitor;
        this.taskManager = taskManager;
        this.memoryManager = memoryManager;
        this.stateManager = stateManager;
        this.idleBrain = idleBrain;
        this.localPlanner = localPlanner;
        this.localChatHandler = localChatHandler;
        this.planManager = planManager;
        this.socialObserver = socialObserver;
        this.familiarityTracker = familiarityTracker;
        this.bayesianModule = bayesianModule;
        this.bot = bot;
    }

    public UUID botId() { return botId; }
    public String botName() { return botName; }
    public BotParams params() { return params; }
    public HormonalSystem hormones() { return hormones; }
    public OneShotAlarmSystem alarms() { return alarms; }
    public ConditionedReflex conditionedReflex() { return conditionedReflex; }
    public DispatchReflex dispatchReflex() { return dispatchReflex; }
    public InnateReflexRegistry reflexRegistry() { return reflexRegistry; }
    public InhibitoryControl inhibitor() { return inhibitor; }
    public TaskManager taskManager() { return taskManager; }
    public MemoryManager memoryManager() { return memoryManager; }
    public StateManager stateManager() { return stateManager; }
    public IdleBrain idleBrain() { return idleBrain; }
    public ILocalPlanner localPlanner() { return localPlanner; }
    public LocalChatHandler localChatHandler() { return localChatHandler; }
    public PlanManager planManager() { return planManager; }
    public SocialObserver socialObserver() { return socialObserver; }
    public FamiliarityTracker familiarityTracker() { return familiarityTracker; }
    public BayesianModule bayesianModule() { return bayesianModule; }
    public ServerPlayerEntity bot() { return bot; }

    public Task activeTask() {
        return taskManager != null ? taskManager.getActiveTask() : null;
    }

    public String lastPlayerMessage() { return lastPlayerMessage; }
    public void setLastPlayerMessage(String msg) {
        this.lastPlayerMessage = msg;
        if (msg != null && !msg.isEmpty()) {
            lastPlayerMessageTime = System.currentTimeMillis();
        }
    }

    public boolean hasPendingChat() { return pendingChatMessage != null; }
    public String peekPendingChat() { return pendingChatMessage; }
    public String consumePendingChat() {
        String msg = pendingChatMessage;
        pendingChatMessage = null;
        return msg;
    }
    public void setPendingChat(String message) { this.pendingChatMessage = message; }

    public int getP3Cooldown() { return p3Cooldown; }
    public void setP3Cooldown(int c) { this.p3Cooldown = c; }
    public void tickP3Cooldown() { if (p3Cooldown > 0) p3Cooldown--; }

    public String currentBiomeId() { return currentBiomeId; }
    public void setCurrentBiomeId(String id) { this.currentBiomeId = id; }

    public int getTickSinceLastLLM() { return tickSinceLastLLM; }
    public void incrementTickSinceLastLLM() { tickSinceLastLLM++; }
    public void resetTickSinceLastLLM() { tickSinceLastLLM = 0; }

    public boolean hasRecentLLMFailure() { return recentLLMFailure; }
    public void setRecentLLMFailure(boolean v) { this.recentLLMFailure = v; }

    public int getLastEntityCount() { return lastTickEntityCount; }
    public void setLastEntityCount(int c) { this.lastTickEntityCount = c; }
    public int getThisEntityCount() { return thisTickEntityCount; }
    public void setThisEntityCount(int c) { thisTickEntityCount = c; }
    public void cycleEntityCount() { lastTickEntityCount = thisTickEntityCount; thisTickEntityCount = 0; }

    public ProblemLabel getCurrentProblemLabel() { return currentProblemLabel; }
    public void setCurrentProblemLabel(ProblemLabel label) {
        if (label == currentProblemLabel) {
            ticksInCurrentLabel++;
        } else {
            currentProblemLabel = label;
            ticksInCurrentLabel = 0;
        }
    }
    public int getTicksInCurrentLabel() { return ticksInCurrentLabel; }

    public void recordNovelEntity(String entityType) {
        novelEntityTicks.put(entityType, NOVEL_ENTITY_WINDOW);
    }

    public void tickNovelEntities() {
        novelEntityTicks.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }

    public boolean hasGroupActivity() {
        return socialObserver != null && socialObserver.getNearbyPlayerCount() > 1;
    }

    public boolean hasHighProficiencyReflex(ServerPlayerEntity b) {
        if (conditionedReflex == null) return false;
        return conditionedReflex.getHighestProficiency() >= 0.8;
    }

    public boolean hasRecentNovelty() {
        return hormones.getCuriosity() > hormones.getCuriosityThreshold(params.getBeta(), hormones.getStress());
    }

    public double getLastProficiency() {
        return conditionedReflex != null ? conditionedReflex.getHighestProficiency() : 0;
    }

    public boolean hasEnvironmentAnomaly() {
        return hasSuddenEnvironmentChange();
    }

    public int getLastActionSuccessCount() {
        return lastActionSuccessCount;
    }

    public void setLastActionSuccessCount(int c) { this.lastActionSuccessCount = c; }

    public int getPlayerInactiveMinutes() {
        if (lastPlayerMessageTime == 0) return 60;
        return (int) ((System.currentTimeMillis() - lastPlayerMessageTime) / 60_000);
    }

    public int getConsecutiveFailures() {
        return conditionedReflex != null ? conditionedReflex.getConsecutiveFailures() : 0;
    }

    public boolean hasNovelEntity() {
        return !novelEntityTicks.isEmpty();
    }

    public boolean hasSuddenEnvironmentChange() {
        if (lastTickEntityCount == 0) return false;
        if (thisTickEntityCount == 0) return false;
        double change = Math.abs(thisTickEntityCount - lastTickEntityCount) / (double) lastTickEntityCount;
        return change > 0.3 && Math.abs(thisTickEntityCount - lastTickEntityCount) >= 3;
    }

    public boolean hasUrgentPlayerMessage() {
        if (lastPlayerMessage == null || lastPlayerMessage.isEmpty()) return false;
        String lower = lastPlayerMessage.toLowerCase();
        return lower.contains("小心") || lower.contains("停") || lower.contains("想清楚再做")
                || lower.contains("等等") || lower.contains("别");
    }

    public void setBlockFingerprint(String fp) { this.lastBlockFingerprint = fp; }
    public String getBlockFingerprint() { return lastBlockFingerprint; }

    public boolean hasBlockChange(String newFingerprint) {
        if (lastBlockFingerprint.isEmpty()) return false;
        return !lastBlockFingerprint.equals(newFingerprint);
    }

    public MetaContext withBot(ServerPlayerEntity newBot) {
        var ctx = new MetaContext(botId, botName, params, hormones, alarms,
                conditionedReflex, dispatchReflex, reflexRegistry, inhibitor,
                taskManager, memoryManager, stateManager, idleBrain, localPlanner,
                localChatHandler, planManager, socialObserver, familiarityTracker,
                bayesianModule, newBot);
        ctx.p3Cooldown = this.p3Cooldown;
        ctx.lastPlayerMessage = this.lastPlayerMessage;
        ctx.pendingChatMessage = this.pendingChatMessage;
        ctx.tickSinceLastLLM = this.tickSinceLastLLM;
        ctx.recentLLMFailure = this.recentLLMFailure;
        ctx.lastPlayerMessageTime = this.lastPlayerMessageTime;
        ctx.currentBiomeId = this.currentBiomeId;
        ctx.lastTickEntityCount = this.lastTickEntityCount;
        ctx.thisTickEntityCount = this.thisTickEntityCount;
        ctx.currentProblemLabel = this.currentProblemLabel;
        ctx.ticksInCurrentLabel = this.ticksInCurrentLabel;
        ctx.novelEntityTicks.putAll(this.novelEntityTicks);
        ctx.lastBlockFingerprint = this.lastBlockFingerprint;
        return ctx;
    }
}
