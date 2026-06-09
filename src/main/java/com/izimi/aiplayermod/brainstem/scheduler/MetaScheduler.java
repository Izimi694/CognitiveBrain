package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.amygdala.learning.CorrelationDetector;
import com.izimi.aiplayermod.brainstem.adapter.TemporalScaler;
import com.izimi.aiplayermod.brainstem.innate.InnateReflex;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
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

    private ProblemLabel labelProblem(MetaContext ctx, Perspective perspective) {
        InnateReflexRegistry reflex = ctx.reflexRegistry();
        OneShotAlarmSystem alarms = ctx.alarms();
        ServerPlayerEntity bot = ctx.bot();

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
                if (ctx.activeTask() != null)           return ProblemLabel.TASK_ACTIVE;
                if (ctx.hasHighProficiencyReflex(bot))  return ProblemLabel.ROUTINE;
                return ProblemLabel.TRIVIAL;
            }
            case SOCIAL -> {
                if (ctx.hasGroupActivity()) return ProblemLabel.SOCIAL;
                return ProblemLabel.TRIVIAL;
            }
            case CURIOUS -> {
                if (ctx.hasRecentNovelty()) return ProblemLabel.NOVEL;
                return ProblemLabel.FAMILIAR;
            }
            case CAUTIOUS -> {
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                return ProblemLabel.ROUTINE;
            }
        }
        return ProblemLabel.TRIVIAL;
    }

    private FlowLevel getFlowAdjustment(MetaContext ctx) {
        if (ctx.getLastProficiency() >= 0.8 && !ctx.hasEnvironmentAnomaly())
            return FlowLevel.AUTOPILOT;
        if (ctx.getLastActionSuccessCount() > 10)
            return FlowLevel.AUTOPILOT;
        if (ctx.getPlayerInactiveMinutes() > 5)
            return FlowLevel.AUTOPILOT;
        if (ctx.getConsecutiveFailures() >= 2)
            return FlowLevel.OVERRIDE;
        if (ctx.hasNovelEntity())
            return FlowLevel.OVERRIDE;
        if (ctx.hasSuddenEnvironmentChange())
            return FlowLevel.OVERRIDE;
        if (ctx.hasUrgentPlayerMessage())
            return FlowLevel.OVERRIDE;

        if (ctx.getTicksInCurrentLabel() > FLOW_STUCK_THRESHOLD)
            return FlowLevel.OVERRIDE;

        if (ctx.getTicksInCurrentLabel() > TIME_ESCALATION_TICKS) {
            double urgency = urgencyClassifier.computeUrgency(
                    ctx.hormones(), ctx.bot(), ctx.getTicksInCurrentLabel());
            if (urgency > 0.5) return FlowLevel.OVERRIDE;
        }

        return FlowLevel.NORMAL;
    }

    public void tick(MetaContext ctx, MinecraftServer server) {
        ctx.incrementTickSinceLastLLM();
        ctx.tickNovelEntities();

        DriveState drives = motivationEngine.computeDrives(ctx);
        Perspective perspective = motivationEngine.select(ctx, drives);

        ProblemLabel label = labelProblem(ctx, perspective);
        ctx.setCurrentProblemLabel(label);

        FlowLevel flow = getFlowAdjustment(ctx);

        temporalScaler.update(ctx.hormones(), ctx.bot(), ctx.getTicksInCurrentLabel());

        DispatchReflex.DispatchAction action = null;
        if (ctx.dispatchReflex() != null) {
            action = ctx.dispatchReflex().match(label, flow);
        }

        if (action == null) {
            action = fallbackDispatch(label, flow);
        }

        if (isLLMAction(action) && !shouldInvokeLLM(ctx, label, flow)) {
            AIPlayerMod.LOGGER.debug("[MetaScheduler] LLM gate denied: {} {}, falling back to HABIT", label, flow);
            action = new DispatchReflex.DispatchAction("HABIT", "llm_gate");
        }

        boolean success = execute(action, ctx, server);

        if (ctx.dispatchReflex() != null) {
            if ("llm_gate".equals(action.reason())) {
                ctx.dispatchReflex().recordGateEvent(success);
            }
            ctx.dispatchReflex().recordOutcome(label, flow, action, success);
        }

        ctx.hormones().tick();
    }

    private boolean isLLMAction(DispatchReflex.DispatchAction action) {
        return action != null && "CORTEX_LLM".equals(action.layer());
    }

    private boolean shouldInvokeLLM(MetaContext ctx, ProblemLabel label, FlowLevel flow) {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;
        if (ctx.getTickSinceLastLLM() < LLM_COOLDOWN_TICKS) return false;
        if (ctx.hasRecentLLMFailure()) return false;
        if (label != ProblemLabel.NOVEL && flow != FlowLevel.OVERRIDE) return false;

        ILocalPlanner lp = ctx.localPlanner();
        if (lp != null && lp.canHandle(ctx.lastPlayerMessage())) return false;

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

    private boolean execute(DispatchReflex.DispatchAction action, MetaContext ctx, MinecraftServer server) {
        ServerPlayerEntity bot = ctx.bot();
        if (bot == null) return false;

        switch (action.layer()) {
            case "INSTINCT" -> {
                return executeInstinctLayer(ctx, bot);
            }
            case "HABIT" -> {
                return executeHabitLayer(ctx, bot);
            }
            case "CORTEX_LOCAL" -> {
                return executeCortexLocal(ctx, bot);
            }
            case "CORTEX_LLM" -> {
                return executeCortexLLM(ctx, bot, server);
            }
            case "IDLE" -> {
                return executeIdle(ctx, bot);
            }
        }
        return false;
    }

    private boolean executeInstinctLayer(MetaContext ctx, ServerPlayerEntity bot) {
        InnateReflexRegistry reg = ctx.reflexRegistry();
        InhibitoryControl inhibitor = ctx.inhibitor();
        OneShotAlarmSystem alarms = ctx.alarms();

        if (reg != null) {
            InnateReflex safety = reg.highest(bot, 0);
            if (safety != null) {
                if (inhibitor != null && inhibitor.shouldVetoSafety(safety, bot,
                        null, AIPlayerMod.getBehaviorStats())) {
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
        }
    }

    private boolean executeHabitLayer(MetaContext ctx, ServerPlayerEntity bot) {
        var conditioned = ctx.conditionedReflex();
        var taskManager = ctx.taskManager();
        if (conditioned == null || taskManager == null) return false;

        Task activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditioned.match(activeTask);
            if (reflexSkill != null) {
                conditioned.executeReflex(reflexSkill, bot);
                return true;
            }
        }

        if (ctx.getP3Cooldown() <= 0) {
            var autoReflex = conditioned.scanAndTrigger(bot);
            if (autoReflex != null) {
                conditioned.executeReflex(autoReflex, bot);
                return true;
            }
        }

        if (correlationDetector != null) {
            return correlationDetector.tryExplore(bot, ctx);
        }
        return false;
    }

    private boolean executeCortexLocal(MetaContext ctx, ServerPlayerEntity bot) {
        String msg = ctx.peekPendingChat();
        if (msg == null) msg = AIPlayerMod.peekPendingChatMessage();
        if (msg == null) return false;

        ILocalPlanner planner = ctx.localPlanner();

        if (planner != null && planner.canHandle(msg)) {
            ctx.consumePendingChat();
            AIPlayerMod.consumePendingChat();
            var response = planner.decompose(msg);
            if (response != null && response.isAction()) {
                var planManager = ctx.planManager();
                if (planManager != null) {
                    var plan = planManager.getActivePlan();
                    if (plan != null && !plan.subSteps.isEmpty()) {
                        ctx.taskManager().createTaskFromPlan(msg, plan);
                        AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 从Plan创建任务: {} → {}步",
                                msg, plan.subSteps.size());
                        return true;
                    }
                }
                ctx.taskManager().createTask(msg);
                return true;
            }
        }

        LocalChatHandler chatHandler = ctx.localChatHandler();
        if (chatHandler != null && chatHandler.canHandle(msg)) {
            ctx.consumePendingChat();
            AIPlayerMod.consumePendingChat();
            UUID playerId = ctx.botId();
            String response = chatHandler.getResponse(msg, ctx.hormones(), playerId);
            if (response != null) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response));
                AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 本地聊天: \"{}\" → \"{}\"",
                        msg, response);
                return true;
            }
        }

        return false;
    }

    private boolean executeCortexLLM(MetaContext ctx, ServerPlayerEntity bot, MinecraftServer server) {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;

        String msg = ctx.consumePendingChat();
        if (msg == null) {
            var pc = AIPlayerMod.consumePendingChat();
            if (pc == null) return false;
            msg = pc.message();
        }

        var aiChatHandler = AIPlayerMod.getAiChatHandler();
        if (aiChatHandler == null) return false;

        ctx.resetTickSinceLastLLM();
        try {
            aiChatHandler.handleChat(msg,
                    ctx.stateManager().loadState(),
                    ctx.taskManager().getActiveTask(),
                    ctx.memoryManager().getRecentMemories());
            ctx.setRecentLLMFailure(false);
            return true;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[MetaScheduler] LLM call failed: {}", e.getMessage());
            ctx.setRecentLLMFailure(true);
            return false;
        }
    }

    private boolean executeIdle(MetaContext ctx, ServerPlayerEntity bot) {
        var idleBrain = ctx.idleBrain();
        if (idleBrain != null) {
            var suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(net.minecraft.text.Text.literal("§b[AI_Assistant] §f" + suggestion.text()));
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
