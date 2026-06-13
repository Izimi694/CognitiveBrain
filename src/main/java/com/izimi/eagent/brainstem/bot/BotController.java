package com.izimi.eagent.brainstem.bot;

import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.brainstem.innate.InnateReflex;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.brainstem.scheduler.MetaScheduler;
import com.izimi.eagent.cortex.api.*;
import com.izimi.eagent.brainstem.IdleBrain;
import com.izimi.eagent.amygdala.NaiveBayesClassifier;
import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.brainstem.skill.Skill;
import com.izimi.eagent.amygdala.character.BehaviorStats;
import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.amygdala.FamiliarityTracker;
import com.izimi.eagent.amygdala.SocialObserver;
import com.izimi.eagent.cortex.inhibitor.InhibitoryControl;
import com.izimi.eagent.state.StateManager;
import com.izimi.eagent.cortex.task.TaskExecutor;
import com.izimi.eagent.cortex.task.TaskManager;
import com.izimi.eagent.hippocampus.MemoryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotController {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private final BotSpawner botSpawner;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final StateManager stateManager;
    private final ConditionedReflex conditionedReflex;
    private final AIChatHandler aiChatHandler;
    private final AIClient aiClient;
    private final IdleBrain idleBrain;
    private final NaiveBayesClassifier socialClassifier;
    private final InnateReflexRegistry reflexRegistry;
    private final InhibitoryControl inhibitor;
    private final MemoryManager memoryManager;

    private MetaScheduler metaScheduler;
    private WorldContext worldContext;

    private int tickCounter = 0;
    private int stateSaveInterval = 200;
    private int aiPollInterval = 20;
    private int p3Cooldown = 0;
    private String pendingChatMessage;

    public BotController(BotSpawner botSpawner, TaskManager taskManager,
                         TaskExecutor taskExecutor, StateManager stateManager,
                         ConditionedReflex conditionedReflex,
                         AIChatHandler aiChatHandler,
                         AIClient aiClient, IdleBrain idleBrain,
                          NaiveBayesClassifier socialClassifier,
                          InnateReflexRegistry reflexRegistry,
                          InhibitoryControl inhibitor,
                          MemoryManager memoryManager) {
        this.botSpawner = botSpawner;
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
        this.stateManager = stateManager;
        this.conditionedReflex = conditionedReflex;
        this.aiChatHandler = aiChatHandler;
        this.aiClient = aiClient;
        this.idleBrain = idleBrain;
        this.socialClassifier = socialClassifier;
        this.reflexRegistry = reflexRegistry;
        this.inhibitor = inhibitor;
        this.memoryManager = memoryManager;
    }

    public void onTick(MinecraftServer server) {
        tickCounter++;

        if (!botSpawner.isSpawned()) return;

        BotPlayer botPlayer = botSpawner.getBot();
        if (botPlayer == null) return;

        ServerPlayerEntity bot = botPlayer.asEntity();

        if (tickCounter % stateSaveInterval == 0) {
            stateManager.saveState(bot);
        }

        if (tickCounter % aiPollInterval == 0 && aiClient != null && aiClient.isConfigured()) {
            pollAIResults(bot);
        }

        // P0: 安全反射 — 永远优先, 每 tick 检查, 0 API
        // P0.5: 前额叶抑制审核 — 否决不适当的安全反射
        if (reflexRegistry != null && executeSafetyReflex(bot, server)) return;

        // P1: 玩家任务 → 固化反射匹配 → 本地执行, 0 API
        var activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditionedReflex.match(activeTask);
            if (reflexSkill != null) {
                LOGGER.debug("[BotController] P1固化反射: {}", reflexSkill.getSkillId());
                conditionedReflex.executeReflex(reflexSkill, bot);
                return;
            }

            // P2: 玩家任务 → 子任务迭代 → 无匹配反射 → 走 TaskExecutor, 0 API
            LOGGER.debug("[BotController] P2子任务执行: {} (子任务进度 {}/{})",
                    activeTask.getGoal(),
                    activeTask.progress.completedCount,
                    activeTask.progress.targetCount);
            taskExecutor.executeTask(bot, activeTask);
            return;
        }

        // P3: 条件反射自动触发（无任务时), 每100tick扫描, 0 API
        if (p3Cooldown > 0) {
            p3Cooldown--;
        } else if (conditionedReflex != null) {
            p3Cooldown = 100;
            Skill autoReflex = conditionedReflex.scanAndTrigger(bot);
            if (autoReflex != null) {
                LOGGER.info("[BotController] P3条件反射自动触发: {}", autoReflex.getSkillId());
                conditionedReflex.executeReflex(autoReflex, bot);
                return;
            }
        }

        // P4: AI自主 (IdleBrain 建议 + SocialMirror + 非安全反射), 0 API
        if (idleBrain != null) {
            IdleBrain.SuggestionTemplate suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + suggestion.text()));
                return;
            }
        }

        if (trySocialMirror(bot)) return;

        if (reflexRegistry != null) {
            InnateReflex nonSafety = reflexRegistry.matchWeighted(bot).stream()
                    .filter(r -> r.priority() > 0)
                    .findFirst().orElse(null);
            if (nonSafety != null) {
                LOGGER.debug("[BotController] P4非安全反射: {}", nonSafety.id());
                dispatchReflexAction(bot, nonSafety);
                return;
            }
        }

        // P5: Idle 动画 (lookAt + 随机游荡), 0 API
        doIdleAnimation(bot);
    }

    private void dispatchReflexAction(ServerPlayerEntity bot, InnateReflex reflex) {
        BasicActionAdapter adapter = worldContext.brainstem().basicActions();
        if (adapter == null) return;
        var action = reflex.action();
        boolean executed = false;
        boolean success = true;
        switch (action.type()) {
            case "flee" -> { adapter.flee(bot, action.getDouble("speed", 0.3)); executed = true; }
            case "eat" -> { var r = adapter.eat(bot); executed = r.executed(); success = r.success(); }
            case "retreat" -> { adapter.retreat(bot, action.getDouble("speed", 0.25)); executed = true; }
            case "avoidLava" -> { adapter.avoidLava(bot, action.getDouble("speed", 0.2)); executed = true; }
            case "seekShelter" -> { adapter.seekShelter(bot, action.getDouble("speed", 0.1)); executed = true; }
            case "collectItem" -> { var r = adapter.collectItem(bot, action.getDouble("speed", 0.15)); executed = r.executed(); success = r.success(); }
            case "sneak" -> { adapter.sneak(bot, true); executed = true; }
            case "invokeLLM" -> {
                if (pendingChatMessage != null && aiChatHandler != null && aiClient.isConfigured()) {
                    var state = stateManager.loadState();
                    var task = taskManager.getActiveTask();
                    var mems = memoryManager.getRecentMemories();
                    aiChatHandler.handleChat(pendingChatMessage, state, task, mems);
                    pendingChatMessage = null;
                    executed = true;
                }
            }
            default -> LOGGER.warn("[BotController] 未知反射动作: {}", action.type());
        }
        if (executed && reflexRegistry != null) {
            reflexRegistry.reinforceOnDispatch(reflex.id(), success);
        }
    }

    private void doIdleAnimation(ServerPlayerEntity bot) {
        long tick = bot.age;
        if (tick % 40 < 20) {
            float yaw = bot.getYaw() + (float) (Math.sin(tick * 0.05) * 15);
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);
        }
        if (tick % 100 == 0) {
            Vec3d pos = bot.getPos();
            double angle = Math.random() * Math.PI * 2;
            double dist = 2.0 + Math.random() * 3.0;
            Vec3d target = pos.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                bot.setVelocity(new Vec3d(dx / len * 0.15, 0.08, dz / len * 0.15));
                bot.velocityModified = true;
            }
        }
    }

    private boolean trySocialMirror(ServerPlayerEntity bot) {
        if (socialClassifier == null) return false;

        SocialObserver socialObs = worldContext.amygdala().socialObserver();
        FamiliarityTracker familiarity = worldContext.amygdala().familiarityTracker();
        if (socialObs == null || familiarity == null) return false;

        int nearbyCount = socialObs.getNearbyPlayerCount();
        if (nearbyCount == 0) return false;

        var windows = socialObs.getPlayerWindows();
        NaiveBayesClassifier.Classification result = socialClassifier.classify(windows, familiarity);

        if (result == null || !result.meetsThreshold()) return false;

        String taskGoal = result.toTaskGoal();
        taskManager.createTask(taskGoal);
        bot.sendMessage(Text.literal("§b[E-Agent] §7(观察" + nearbyCount +
                "名玩家，置信度" + String.format("%.2f", result.confidence()) + ")"));
        return true;
    }

    private void pollAIResults(ServerPlayerEntity bot) {
        var chatHandler = this.aiChatHandler;
        if (chatHandler != null && chatHandler.hasPendingResponse()) {
            AIResponse response = chatHandler.pollResponse();
            if (response != null && response.isChat() && !response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + response.getMessage()));
                if (response.getMemoryNote() != null && !response.getMemoryNote().isEmpty()) {
                    if (memoryManager != null) {
                        memoryManager.generateMemory(new com.izimi.eagent.cortex.task.Task(
                                "chat", "instant", response.getMemoryNote()));
                    }
                }
                if (response.personalityDelta != null && !response.personalityDelta.isEmpty()) {
                    var condReflex = this.conditionedReflex;
                    if (condReflex != null) {
                        for (var entry : response.personalityDelta.entrySet()) {
                            condReflex.reinforce(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        }

        var taskPlanner = worldContext.cortex().taskPlanner();
        if (taskPlanner != null && taskPlanner.hasPendingResult()) {
            AIResponse response = taskPlanner.pollResult();
            if (response != null) {
                processTaskResponse(bot, response);
            }
        }
    }

    private void processTaskResponse(ServerPlayerEntity bot, AIResponse response) {
        if (response.isChat()) {
            if (!response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + response.getMessage()));
            }
            return;
        }

        if (response.isAction()) {
            String goal = response.getAction() + " " + (response.getSkill() != null ? response.getSkill() : "");
            if (response.params != null && response.params.target != null) {
                goal = response.params.target;
                if (response.params.amount > 0) {
                    goal = "挖" + response.params.amount + "个" + goal;
                }
            }
            if (!"wait".equals(response.getAction()) && !goal.isEmpty()) {
                taskManager.createTask(goal);
                if (!response.getMessage().isEmpty()) {
                    bot.sendMessage(Text.literal("§b[E-Agent] §f" + response.getMessage()));
                }
            }
        }
    }

    public void setWorldContext(WorldContext worldContext) {
        this.worldContext = worldContext;
    }

    private boolean executeSafetyReflex(ServerPlayerEntity bot, MinecraftServer server) {
        InnateReflex safety = reflexRegistry.highest(bot, 0);
        if (safety == null) return false;
        if (inhibitor != null && inhibitor.shouldVetoSafety(safety, bot, server, worldContext.behaviorStats())) {
            LOGGER.debug("[BotController] P0.5前额叶否决安全反射: {}", safety.id());
            return false;
        }
        LOGGER.debug("[BotController] P0安全反射: {} critical={}", safety.id(), safety.critical());
        dispatchReflexAction(bot, safety);
        return safety.critical();
    }

    public void setMetaScheduler(MetaScheduler scheduler) {
        this.metaScheduler = scheduler;
    }

    public MetaScheduler getMetaScheduler() { return metaScheduler; }

    public void setPendingChatMessage(String message) { this.pendingChatMessage = message; }
}
