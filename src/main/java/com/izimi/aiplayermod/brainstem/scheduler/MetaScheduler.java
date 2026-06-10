package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.api.BotContext;
import com.izimi.aiplayermod.api.MetaState;
import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.amygdala.learning.CorrelationDetector;
import com.izimi.aiplayermod.brainstem.adapter.TemporalScaler;
import com.izimi.aiplayermod.brainstem.innate.InnateReflex;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.task.Task;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

public class MetaScheduler {

    private static final int LLM_COOLDOWN_TICKS = 400;
    private static final int TIME_ESCALATION_TICKS = 200;
    private static final int FLOW_STUCK_THRESHOLD = 600;

    // ── e-based timing constants: 63.2% execution / 36.8% buffer ──
    private static final double EXECUTION_RATIO = 1.0 - (1.0 / Math.E); // ≈ 0.632
    private static final double PREEMPT_THRESHOLD = 1.0 / Math.E;

    /** 计算可用时间片: 63.2% 执行, 36.8% 缓冲 */
    public static long computeTimeSlice(long totalLatencyBound, int taskCount, long switchOverheadMs) {
        if (taskCount <= 0) return totalLatencyBound;
        long available = totalLatencyBound - (switchOverheadMs * taskCount);
        if (available <= 0) return Math.max(1, totalLatencyBound / taskCount);
        return (long) (available / taskCount * EXECUTION_RATIO);
    }

    /** 当新任务优先级超出当前任务优先级 × (1 + 1/e) 时触发抢占 */
    public static boolean shouldPreempt(double currentPriority, double newPriority) {
        return newPriority > currentPriority * (1.0 + PREEMPT_THRESHOLD);
    }

    private final MotivationEngine motivationEngine;
    private final UrgencyClassifier urgencyClassifier;
    private final TemporalScaler temporalScaler;
    private CorrelationDetector correlationDetector;

    public MetaScheduler(MotivationEngine motivationEngine) {
        this.motivationEngine = motivationEngine;
        this.urgencyClassifier = new UrgencyClassifier();
        this.temporalScaler = new TemporalScaler(this.urgencyClassifier);
    }

    public void setCorrelationDetector(CorrelationDetector cd) {
        this.correlationDetector = cd;
    }

    private ProblemLabel labelProblem(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, Perspective perspective) {
        InnateReflexRegistry reflex = worldCtx.brainstem().innateReflexes();
        OneShotAlarmSystem alarms = botCtx.alarmSystem();

        switch (perspective) {
            case SURVIVAL -> {
                if (reflex != null) {
                    InnateReflex critical = reflex.highest(bot, 0);
                    if (critical != null && critical.critical()) return ProblemLabel.SURVIVAL;
                }
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                if (reflex != null && reflex.highest(bot, 0) != null) return ProblemLabel.SURVIVAL;
                return ProblemLabel.TRIVIAL;
            }
            case TASK -> {
                if (botCtx.taskManager() != null && botCtx.taskManager().getActiveTask() != null)
                    return ProblemLabel.TASK_ACTIVE;
                if (botCtx.conditionedReflex() != null && botCtx.conditionedReflex().getHighestProficiency() >= 0.8)
                    return ProblemLabel.ROUTINE;
                return ProblemLabel.TRIVIAL;
            }
            case SOCIAL -> {
                var social = worldCtx.amygdala().socialObserver();
                if (social != null && social.getNearbyPlayerCount() > 1) return ProblemLabel.SOCIAL;
                return ProblemLabel.TRIVIAL;
            }
            case CURIOUS -> {
                var h = botCtx.hormonalSystem();
                if (h.getCuriosity() > h.getCuriosityThreshold(botCtx.botParams().getBeta(), h.getStress()))
                    return ProblemLabel.NOVEL;
                return ProblemLabel.FAMILIAR;
            }
            case CAUTIOUS -> {
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                return ProblemLabel.ROUTINE;
            }
        }
        return ProblemLabel.TRIVIAL;
    }

    private FlowLevel getFlowAdjustment(BotContext botCtx, ServerPlayerEntity bot, MetaState state) {
        double proficiency = botCtx.conditionedReflex() != null ? botCtx.conditionedReflex().getHighestProficiency() : 0;
        if (proficiency >= 0.8 && !state.hasSuddenEnvironmentChange())
            return FlowLevel.AUTOPILOT;
        if (state.getLastActionSuccessCount() > 10)
            return FlowLevel.AUTOPILOT;
        if (state.getPlayerInactiveMinutes() > 5)
            return FlowLevel.AUTOPILOT;
        if (botCtx.conditionedReflex() != null && botCtx.conditionedReflex().getConsecutiveFailures() >= 2)
            return FlowLevel.OVERRIDE;
        if (state.hasNovelEntity())
            return FlowLevel.OVERRIDE;
        if (state.hasSuddenEnvironmentChange())
            return FlowLevel.OVERRIDE;
        if (state.hasUrgentPlayerMessage())
            return FlowLevel.OVERRIDE;

        if (state.getTicksInCurrentLabel() > FLOW_STUCK_THRESHOLD)
            return FlowLevel.OVERRIDE;

        if (state.getTicksInCurrentLabel() > TIME_ESCALATION_TICKS) {
            double urgency = urgencyClassifier.computeUrgency(
                    botCtx.hormonalSystem(), bot, state.getTicksInCurrentLabel());
            if (urgency > 0.5) return FlowLevel.OVERRIDE;
        }

        return FlowLevel.NORMAL;
    }

    public void tick(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, MetaState state, MinecraftServer server) {
        state.incrementTickSinceLastLLM();
        state.tickNovelEntities();

        DriveState drives = motivationEngine.computeDrives(botCtx, worldCtx, bot);
        Perspective perspective = motivationEngine.select(botCtx, drives);

        ProblemLabel label = labelProblem(botCtx, worldCtx, bot, perspective);
        state.setCurrentProblemLabel(label);

        FlowLevel flow = getFlowAdjustment(botCtx, bot, state);

        temporalScaler.update(botCtx.hormonalSystem(), bot, state.getTicksInCurrentLabel());

        DispatchReflex.DispatchAction action = null;
        if (botCtx.dispatchReflex() != null) {
            action = botCtx.dispatchReflex().match(label, flow);
        }

        if (action == null) {
            action = fallbackDispatch(label, flow);
        }

        if (isLLMAction(action) && !shouldInvokeLLM(worldCtx, state, label, flow)) {
            AIPlayerMod.LOGGER.debug("[MetaScheduler] LLM gate denied: {} {}, falling back to HABIT", label, flow);
            action = new DispatchReflex.DispatchAction("HABIT", "llm_gate");
        }

        boolean success = execute(action, botCtx, worldCtx, bot, state, server);

        if (botCtx.dispatchReflex() != null) {
            if ("llm_gate".equals(action.reason())) {
                botCtx.dispatchReflex().recordGateEvent(success);
            }
            botCtx.dispatchReflex().recordOutcome(label, flow, action, success);
        }

        botCtx.hormonalSystem().tick();
    }

    private boolean isLLMAction(DispatchReflex.DispatchAction action) {
        return action != null && "CORTEX_LLM".equals(action.layer());
    }

    private boolean shouldInvokeLLM(WorldContext worldCtx, MetaState state, ProblemLabel label, FlowLevel flow) {
        var aiClient = worldCtx.cortex().aiClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;
        if (state.getTickSinceLastLLM() < LLM_COOLDOWN_TICKS) return false;
        if (state.hasRecentLLMFailure()) return false;
        if (label != ProblemLabel.NOVEL && flow != FlowLevel.OVERRIDE) return false;

        var lp = worldCtx.cortex().localPlanner();
        if (lp != null && lp.canHandle(state.getLastPlayerMessage())) return false;

        return true;
    }

    private DispatchReflex.DispatchAction fallbackDispatch(ProblemLabel label, FlowLevel flow) {
        if (flow == FlowLevel.OVERRIDE) {
            return new DispatchReflex.DispatchAction("CORTEX_LOCAL", "override");
        }
        return switch (label) {
            case SURVIVAL, LEARNED_THREAT -> new DispatchReflex.DispatchAction("INSTINCT", label.name());
            case TASK_ACTIVE, ROUTINE -> new DispatchReflex.DispatchAction("HABIT", label.name());
            case FAMILIAR -> new DispatchReflex.DispatchAction("CORTEX_LOCAL", "familiar");
            case NOVEL -> new DispatchReflex.DispatchAction("HABIT", "novel_fallback");
            case SOCIAL, TRIVIAL -> new DispatchReflex.DispatchAction("IDLE", label.name());
        };
    }

    private boolean execute(DispatchReflex.DispatchAction action, BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, MetaState state, MinecraftServer server) {
        if (bot == null) return false;

        switch (action.layer()) {
            case "INSTINCT" -> {
                return executeInstinctLayer(botCtx, worldCtx, bot);
            }
            case "HABIT" -> {
                return executeHabitLayer(botCtx, state, bot);
            }
            case "CORTEX_LOCAL" -> {
                return executeCortexLocal(botCtx, worldCtx, state, bot);
            }
            case "CORTEX_LLM" -> {
                return executeCortexLLM(botCtx, worldCtx, state);
            }
            case "IDLE" -> {
                return executeIdle(botCtx, bot);
            }
        }
        return false;
    }

    private boolean executeInstinctLayer(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot) {
        InnateReflexRegistry reg = worldCtx.brainstem().innateReflexes();
        InhibitoryControl inhibitor = worldCtx.brainstem().inhibitor();
        OneShotAlarmSystem alarms = botCtx.alarmSystem();

        if (reg != null) {
            InnateReflex safety = reg.highest(bot, 0);
            if (safety != null) {
                if (inhibitor != null && inhibitor.shouldVetoSafety(safety, bot,
                        null, worldCtx.behaviorStats())) {
                    AIPlayerMod.LOGGER.debug("[MetaScheduler] P0.5 veto safety: {}", safety.id());
                } else {
                    dispatchReflexAction(bot, safety);
                    if (safety.critical()) return true;
                }
            }
        }

        if (alarms != null) {
            var threat = alarms.matchNearest(bot);
            if (threat != null && threat.type() == OneShotAlarmSystem.AlarmType.THREAT) {
                AIPlayerMod.LOGGER.debug("[MetaScheduler] Level2 threat: {}", threat.alarmId());
                dispatchReflexAction(bot,
                        new InnateReflex("alarm_" + threat.alarmId(), 0, false,
                                List.of(), new com.izimi.aiplayermod.brainstem.innate.ReflexAction(threat.action(),
                                java.util.Map.of("speed", 0.3)), true));
                return true;
            }
        }
        return false;
    }

    private void dispatchReflexAction(ServerPlayerEntity bot, InnateReflex reflex) {
        var adapter = AIPlayerMod.getActionAdapter();
        if (adapter == null) return;
        float speedMul = temporalScaler.getSpeed();
        switch (reflex.action().type()) {
            case "flee" -> adapter.flee(bot, reflex.action().getDouble("speed", 0.3) * speedMul);
            case "eat" -> adapter.eat(bot);
            case "retreat" -> adapter.retreat(bot, reflex.action().getDouble("speed", 0.25) * speedMul);
            case "avoidLava" -> adapter.avoidLava(bot, reflex.action().getDouble("speed", 0.2) * speedMul);
            case "seekShelter" -> adapter.seekShelter(bot, reflex.action().getDouble("speed", 0.1) * speedMul);
            case "collectItem" -> adapter.collectItem(bot, reflex.action().getDouble("speed", 0.15) * speedMul);
            case "sneak" -> adapter.sneak(bot, true);
        }
    }

    private boolean executeHabitLayer(BotContext botCtx, MetaState state, ServerPlayerEntity bot) {
        var conditioned = botCtx.conditionedReflex();
        var taskManager = botCtx.taskManager();
        if (conditioned == null || taskManager == null) return false;

        Task activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditioned.match(activeTask);
            if (reflexSkill != null) {
                conditioned.executeReflex(reflexSkill, bot);
                return true;
            }
        }

        if (state.getP3Cooldown() <= 0) {
            var autoReflex = conditioned.scanAndTrigger(bot);
            if (autoReflex != null) {
                conditioned.executeReflex(autoReflex, bot);
                return true;
            }
        }

        if (correlationDetector != null) {
            return correlationDetector.tryExplore(bot);
        }
        return false;
    }

    private boolean executeCortexLocal(BotContext botCtx, WorldContext worldCtx, MetaState state, ServerPlayerEntity bot) {
        String msg = state.getPendingChatMessage();
        if (msg == null) msg = AIPlayerMod.peekPendingChatMessage();
        if (msg == null) return false;

        var planner = worldCtx.cortex().localPlanner();

        if (planner != null && planner.canHandle(msg)) {
            state.consumePendingChat();
            AIPlayerMod.consumePendingChat();
            var response = planner.decompose(msg);
            if (response != null && response.isAction()) {
                var planManager = botCtx.planManager();
                if (planManager != null) {
                    var plan = planManager.getActivePlan();
                    if (plan != null && !plan.subSteps.isEmpty()) {
                        botCtx.taskManager().createTaskFromPlan(msg, plan);
                        AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 从Plan创建任务: {} → {}步",
                                msg, plan.subSteps.size());
                        return true;
                    }
                }
                botCtx.taskManager().createTask(msg);
                return true;
            }
        }

        var chatHandler = worldCtx.cortex().chatHandler();
        if (chatHandler != null && chatHandler.canHandle(msg)) {
            state.consumePendingChat();
            AIPlayerMod.consumePendingChat();
            UUID playerId = botCtx.botId();
            String response = chatHandler.getResponse(msg, botCtx.hormonalSystem(), playerId);
            if (response != null) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response));
                AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 本地聊天: \"{}\" → \"{}\"",
                        msg, response);
                return true;
            }
        }

        return false;
    }

    private boolean executeCortexLLM(BotContext botCtx, WorldContext worldCtx, MetaState state) {
        var aiClient = worldCtx.cortex().aiClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;

        String msg = state.consumePendingChat();
        if (msg == null) {
            var pc = AIPlayerMod.consumePendingChat();
            if (pc == null) return false;
            msg = pc.message();
        }

        var aiChatHandler = AIPlayerMod.getAiChatHandler();
        if (aiChatHandler == null) return false;

        state.resetTickSinceLastLLM();
        try {
            aiChatHandler.handleChat(msg,
                    botCtx.stateManager().loadState(),
                    botCtx.taskManager().getActiveTask(),
                    botCtx.memoryManager().getRecentMemories());
            state.setRecentLLMFailure(false);
            return true;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[MetaScheduler] LLM call failed: {}", e.getMessage());
            state.setRecentLLMFailure(true);
            return false;
        }
    }

    private boolean executeIdle(BotContext botCtx, ServerPlayerEntity bot) {
        var idleBrain = botCtx.idleBrain();
        if (idleBrain != null) {
            var suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + suggestion.text()));
                return true;
            }
        }

        animateIdle(bot);
        return true;
    }

    private void animateIdle(ServerPlayerEntity bot) {
        float speedMul = temporalScaler.getSpeed();
        long tick = bot.age;
        if (tick % 40 < 20) {
            float yaw = bot.getYaw() + (float) (Math.sin(tick * 0.05) * 15);
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);
        }
        if (tick % Math.max(20, (int)(100 / speedMul)) == 0) {
            var pos = bot.getPos();
            double angle = Math.random() * Math.PI * 2;
            double dist = (2.0 + Math.random() * 3.0) * speedMul;
            var target = pos.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                bot.setVelocity(new Vec3d(dx / len * 0.15 * speedMul, 0.08, dz / len * 0.15 * speedMul));
                bot.velocityModified = true;
            }
        }
    }

    public TemporalScaler getTemporalScaler() { return temporalScaler; }
}
