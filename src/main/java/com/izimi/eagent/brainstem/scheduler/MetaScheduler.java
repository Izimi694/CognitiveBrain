package com.izimi.eagent.brainstem.scheduler;

import com.google.gson.JsonObject;
import com.izimi.eagent.api.BotContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.api.MetaState;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.amygdala.DispatchReflex;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.learning.CorrelationDetector;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.innate.InnateReflex;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.cortex.api.TemplateManager;
import com.izimi.eagent.cortex.api.TemplateMatcher;
import com.izimi.eagent.cortex.inhibitor.InhibitoryControl;
import com.izimi.eagent.cortex.task.Task;
import com.izimi.eagent.cortex.task.TaskManager;
import com.izimi.eagent.hormonal.HormonalSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.izimi.eagent.hippocampus.MemoryEntry;
import com.izimi.eagent.hippocampus.MemoryEdge;
import com.izimi.eagent.hippocampus.MemoryGraph;
import com.izimi.eagent.hippocampus.MemoryManager;

public class MetaScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int LLM_COOLDOWN_TICKS = 400;
    private static final int TIME_ESCALATION_TICKS = 200;
    private static final int FLOW_STUCK_THRESHOLD = 600;

    // ── e-based timing constants: 63.2% execution / 36.8% buffer ──
    private static final double EXECUTION_RATIO = 1.0 - (1.0 / Math.E); // ≈ 0.632
    private static final double PREEMPT_THRESHOLD = 1.0 / Math.E;

    // ── Dead-end detection ──
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final double MIN_POSTERIOR_THRESHOLD = 0.1;
    private static final int EXPLORATION_EXHAUST_THRESHOLD = 37;

    // ── Rollback ──
    private static final int MAX_RETRY_COUNT = 3;
    private static final double MIN_ALT_THRESHOLD = 0.3;

    // ── Loop ──
    private static final int MAX_REFLECTION_LOOPS = 4;

    public record DeadEndResult(boolean isDeadEnd, String reason) {}
    public record RollbackStage(int stage, String action) {}

    private int reflectionLoopCount = 0;
    private String lastExecutedNodeId = null;

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
    private final TemplateMatcher templateMatcher;
    private CorrelationDetector correlationDetector;

    public MetaScheduler(MotivationEngine motivationEngine) {
        this.motivationEngine = motivationEngine;
        this.urgencyClassifier = new UrgencyClassifier();
        this.temporalScaler = new TemporalScaler(this.urgencyClassifier);
        this.templateMatcher = new TemplateMatcher();
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

        // 检查模板结果 (异步 LLM)
        if (state.hasPendingTemplateResult()) {
            TemplateManager.TemplateType pendingType = state.getPendingTemplateType();
            String pendingMsg = state.getPendingTemplateMessage();
            CompletableFuture<JsonObject> future = state.consumePendingTemplateResult();
            if (future != null && future.isDone()) {
                processTemplateResult(future, botCtx, worldCtx, bot, state, pendingType, pendingMsg);
            } else if (future != null) {
                state.setPendingTemplateResult(future, pendingType, pendingMsg);
            }
        }

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
            LOGGER.debug("[MetaScheduler] LLM gate denied: {} {}, falling back to HABIT", label, flow);
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

    // ── Loop Event-Driven Execution ──

    public void executeLoop(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot,
                            MetaState state, MinecraftServer server, String reflexId) {
        if (bot == null) return;

        reflectionLoopCount = 0;
        while (reflectionLoopCount < MAX_REFLECTION_LOOPS) {
            reflectionLoopCount++;

            var conditioned = botCtx.conditionedReflex();
            var bayesian = botCtx != null ? botCtx.bayesianModule() : null;

            if (conditioned == null) break;

            // 激素粗筛 + 图扩散 (candidate generation)
            String candidateReflex = reflexId != null ? reflexId : conditioned.getLastExecutedReflexId();
            if (candidateReflex == null) {
                var autoReflex = conditioned.scanAndTrigger(bot);
                if (autoReflex == null) {
                    // 路3: 图扩散激活
                    candidateReflex = getGraphActivatedCandidate(botCtx);
                    if (candidateReflex == null) break;
                } else {
                    candidateReflex = autoReflex.getSkillId();
                }
            }

            // DAG 依赖检查 + 参数绑定 + 前置条件门控
            if (bayesian != null) {
                double posterior = bayesian.predictSuccess(candidateReflex, conditioned.extractContextFeatures(bot));
                if (posterior < 0.05) {
                    LOGGER.debug("[MetaScheduler] Loop: 贝叶斯预判提前返回 {} (posterior={})", candidateReflex, posterior);
                    break;
                }
            }

            var precond = conditioned.checkPreconditions(candidateReflex, bot, botCtx.hormonalSystem());
            if (!precond.passed()) {
                LOGGER.debug("[MetaScheduler] Loop: 前置条件不通过 {} → {}", candidateReflex, precond.reason());
                break;
            }

            // 反射执行
            var skill = conditioned.getSkill(candidateReflex);
            if (skill == null) break;

            lastExecutedNodeId = candidateReflex;
            conditioned.executeReflex(skill, bot);

            // 死路检测
            DeadEndResult deadEnd = isDeadEnd(botCtx, worldCtx, bot, candidateReflex);
            if (deadEnd.isDeadEnd()) {
                LOGGER.warn("[MetaScheduler] Loop: 死路检测触发 {} → {}, 回退中...", candidateReflex, deadEnd.reason());
                RollbackStage rb = rollback(botCtx, worldCtx, bot, candidateReflex, 0, 0);
                LOGGER.info("[MetaScheduler] Loop: 回退阶段 {} → {}", rb.stage(), rb.action());
                if (rb.stage() >= 5) {
                    // LLM 兜底
                    executeCortexLLM(botCtx, worldCtx, state, bot);
                }
                break;
            }

            // 麦穗判断: 探索窗口是否耗尽
            double confidence = conditioned.getConfidence(candidateReflex);
            double exploreProb = MotivationEngine.wheatEarExplore(confidence, botCtx.hormonalSystem());
            if (exploreProb <= 0.01 && reflectionLoopCount > 1) {
                LOGGER.debug("[MetaScheduler] Loop: 麦穗探索窗口耗尽 (conf={}), 停止探索", confidence);
                break;
            }

            // 进度推进 → 下一轮
            reflexId = null;
        }
    }

    private String getGraphActivatedCandidate(BotContext botCtx) {
        MemoryManager mem = botCtx.memoryManager();
        if (mem == null) return null;
        MemoryGraph mg = mem.getMemoryGraph();
        if (mg == null) return null;

        List<MemoryEntry> recent = mem.getRecentMemories();
        if (recent.isEmpty()) return null;
        List<String> seedIds = recent.stream().limit(3).map(e -> e.id).toList();

        Set<String> activated = new LinkedHashSet<>();
        for (String seed : seedIds) {
            activated.addAll(mg.traverse(seed, MemoryEdge.RelationType.SIMILARITY, 2, 0.5));
        }
        if (activated.isEmpty()) return null;

        for (String nodeId : activated) {
            MemoryEntry entry = mem.getEntry(nodeId);
            if (entry != null && entry.relatedSkills != null) {
                for (String skill : entry.relatedSkills) {
                    double conf = botCtx.conditionedReflex().getConfidence(skill);
                    if (conf > 0.1) return skill;
                }
            }
        }
        return null;
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
                return executeCortexLLM(botCtx, worldCtx, state, bot);
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
                    LOGGER.debug("[MetaScheduler] P0.5 veto safety: {}", safety.id());
                } else {
                    dispatchReflexAction(bot, safety, worldCtx);
                    if (safety.critical()) return true;
                }
            }
        }

        if (alarms != null) {
            var threat = alarms.matchNearest(bot);
            if (threat != null && threat.type() == OneShotAlarmSystem.AlarmType.THREAT) {
                LOGGER.debug("[MetaScheduler] Level2 threat: {}", threat.alarmId());
                dispatchReflexAction(bot,
                        new InnateReflex("alarm_" + threat.alarmId(), 0, false,
                                List.of(), new com.izimi.eagent.brainstem.innate.ReflexAction(threat.action(),
                                java.util.Map.of("speed", 0.3)), true), worldCtx);
                return true;
            }
        }
        return false;
    }

    private void dispatchReflexAction(ServerPlayerEntity bot, InnateReflex reflex, WorldContext worldCtx) {
        var adapter = worldCtx.brainstem().basicActions();
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
        String msg = state.consumePendingChat();
        if (msg == null) return false;

        var planner = worldCtx.cortex().localPlanner();

        if (planner != null && planner.canHandle(msg)) {
            var response = planner.decompose(msg);
            if (response != null && response.isAction()) {
                var planManager = botCtx.planManager();
                if (planManager != null) {
                    var plan = planManager.getActivePlan();
                    if (plan != null && !plan.subSteps.isEmpty()) {
                        botCtx.taskManager().createTaskFromPlan(msg, plan);
                        LOGGER.info("[MetaScheduler] CortexLocal 从Plan创建任务: {} → {}步",
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
            UUID playerId = botCtx.botId();
            String response = chatHandler.getResponse(msg, botCtx.hormonalSystem(), playerId);
            if (response != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + response));
                LOGGER.info("[MetaScheduler] CortexLocal 本地聊天: \"{}\" → \"{}\"",
                        msg, response);
                return true;
            }
        }

        return false;
    }

    private boolean executeCortexLLM(BotContext botCtx, WorldContext worldCtx, MetaState state, ServerPlayerEntity bot) {
        var templateManager = worldCtx.cortex().templateManager();
        if (templateManager == null) return false;

        String msg = state.consumePendingChat();
        if (msg == null) return false;
        state.setLastPlayerMessage(msg);

        // TemplateMatcher 路由
        TemplateManager.TemplateType templateType = templateMatcher.match(msg, botCtx, worldCtx);
        if (templateType == null) {
            // 已由 LocalChatHandler 或反射处理 — 返回成功
            LOGGER.debug("[MetaScheduler] 本地处理: {}", msg);
            return true;
        }

        // CHAT_RESPONSE 预算检查
        if (templateType == TemplateManager.TemplateType.CHAT_RESPONSE && state.isChatBudgetExhausted()) {
            // 预算耗尽 → 回退 LocalChatHandler
            var localChat = worldCtx.cortex().chatHandler();
            if (localChat != null && bot != null) {
                String fallback = localChat.getResponse(msg, botCtx.hormonalSystem(), botCtx.botId());
                if (fallback != null) {
                    bot.sendMessage(Text.literal("§b[E-Agent] §f" + fallback));
                }
            }
            return true;
        }

        // CLARIFICATION 调用限制: 同一对话轮次只调用一次
        if (templateType == TemplateManager.TemplateType.CLARIFICATION && state.hasRecentLLMFailure()) {
            // 用户二次模糊 → 预设回复
            if (bot != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f请更具体地描述你的需求。"));
            }
            return true;
        }

        // 构建模板上下文
        Map<String, Object> context = buildTemplateContext(templateType, msg, botCtx, worldCtx);

        state.resetTickSinceLastLLM();
        try {
            CompletableFuture<JsonObject> future = templateManager.fill(templateType, context);
            // 如果被限速返回 null, 直接回退
            if (future == null) {
                LOGGER.warn("[MetaScheduler] 模板被限速 {}", templateType);
                return false;
            }
            state.setPendingTemplateResult(future, templateType, msg);
            state.setRecentLLMFailure(false);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[MetaScheduler] LLM调用失败: {}", e.getMessage());
            state.setRecentLLMFailure(true);
            return false;
        }
    }

    private Map<String, Object> buildTemplateContext(TemplateManager.TemplateType type, String msg,
                                                     BotContext botCtx, WorldContext worldCtx) {
        Map<String, Object> ctx = new java.util.HashMap<>();
        double stress = botCtx.hormonalSystem() != null ? botCtx.hormonalSystem().getStress() : 0;
        double curiosity = botCtx.hormonalSystem() != null ? botCtx.hormonalSystem().getCuriosity() : 0;
        switch (type) {
            case CLARIFICATION -> {
                ctx.put("userInput", msg);
                ctx.put("availableTemplates", "TASK_PLAN, REFLEX_CREATE, CHAT_RESPONSE");
            }
            case TASK_PLAN -> {
                ctx.put("goal", msg);
                ctx.put("availableActions", "moveTo, dig, attack, placeBlock, useItem, equipItem, craft, chat, jump, lookAt, openBlock, closeWindow, clickSlot");
            }
            case REFLEX_CREATE -> {
                String[] parts = msg.split(" ");
                ctx.put("skill", parts.length > 1 ? parts[0] : "action");
                ctx.put("target", parts.length > 1 ? parts[1] : msg);
            }
            case CHAT_RESPONSE -> {
                ctx.put("playerMessage", msg);
                ctx.put("contextInfo", String.format("stress=%.2f, curiosity=%.2f", stress, curiosity));
            }
        }
        return ctx;
    }

    private void processTemplateResult(CompletableFuture<JsonObject> future, BotContext botCtx,
                                       WorldContext worldCtx, ServerPlayerEntity bot, MetaState state,
                                       TemplateManager.TemplateType type, String originalMsg) {
        JsonObject result;
        try {
            result = future.get();
        } catch (Exception e) {
            LOGGER.warn("[MetaScheduler] 模板结果获取失败: {}", e.getMessage());
            return;
        }
        if (result == null) return;

        switch (type) {
            case CLARIFICATION -> {
                boolean ambiguous = result.get("ambiguity_detected") != null && result.get("ambiguity_detected").getAsBoolean();
                if (ambiguous && bot != null) {
                    String question = result.has("suggested_question") ? result.get("suggested_question").getAsString() : "请更具体地描述你的需求。";
                    bot.sendMessage(Text.literal("§b[E-Agent] §f" + question));
                }
            }
            case CHAT_RESPONSE -> {
                if (bot != null) {
                    String reply = result.has("reply_text") ? result.get("reply_text").getAsString() : "";
                    if (!reply.isEmpty()) {
                        bot.sendMessage(Text.literal("§b[E-Agent] §f" + reply));
                    }
                    state.consumeChatBudget();
                }
            }
            case TASK_PLAN -> {
                // 将 LLM 填的 DAG 固化为 TaskDAG + ReflexChain
                String taskId = result.has("task_id") ? result.get("task_id").getAsString() : "task_" + System.currentTimeMillis();
                LOGGER.info("[MetaScheduler] 收到任务DAG: {} ({} subtasks)", taskId,
                    result.has("subtasks") ? result.getAsJsonArray("subtasks").size() : 0);
                // TaskDAG.fromLLMJson() 将被调用来解析;
                // 目前集成点在 TaskManager, 这里先创建普通任务
                var taskManager = botCtx.taskManager();
                if (taskManager != null) {
                    taskManager.createTask(originalMsg != null ? originalMsg : taskId);
                }
            }
            case REFLEX_CREATE -> {
                String reflexId = result.has("reflex_id") ? result.get("reflex_id").getAsString() : "";
                LOGGER.info("[MetaScheduler] 收到新反射: {}", reflexId);
                // 反射固化通过 ConditionedReflex.solidifySequence() 完成,
                // 这里先记录日志, 具体固化由填坑 hook 处理
            }
        }
    }

    private boolean executeIdle(BotContext botCtx, ServerPlayerEntity bot) {
        var idleBrain = botCtx.idleBrain();
        if (idleBrain != null) {
            var suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + suggestion.text()));
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

    // ── Dead-end Detection ──

    public DeadEndResult isDeadEnd(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, String reflexId) {
        var conditioned = botCtx.conditionedReflex();
        if (conditioned == null) return new DeadEndResult(false, null);

        if (conditioned.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            return new DeadEndResult(true, "CONSECUTIVE_FAILURES");
        }

        var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
        if (bayesian != null) {
            double posterior = bayesian.predictSuccess(reflexId, conditioned.extractContextFeatures(bot));
            if (posterior < MIN_POSTERIOR_THRESHOLD) {
                return new DeadEndResult(true, "LOW_POSTERIOR");
            }
        }

        double confidence = conditioned.getConfidence(reflexId);
        if (conditioned.getConsecutiveFailures() > 0 && confidence < 0.1) {
            return new DeadEndResult(true, "EXPLORATION_EXHAUSTED");
        }

        return new DeadEndResult(false, null);
    }

    // ── Rollback 5-Stage ──

    public RollbackStage rollback(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot,
                                  String nodeId, int retryCount, int stage) {
        switch (stage) {
            case 0: { // Stage 1: 本地重试
                if (retryCount < MAX_RETRY_COUNT) {
                    LOGGER.debug("[MetaScheduler] Rollback Stage1: 本地重试 {} (retry={})", nodeId, retryCount);
                    return new RollbackStage(1, "retry");
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 1);
            }
            case 1: { // Stage 2: 替代方案
                LOGGER.debug("[MetaScheduler] Rollback Stage2: 寻找替代 {}", nodeId);
                var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
                if (bayesian != null) {
                    var alternatives = bayesian.inferForward(
                            new BayesianModule.BotState(null, null, null, null),
                            List.of());
                    if (!alternatives.isEmpty()) {
                        String altId = alternatives.get(0).getKey();
                        double altScore = alternatives.get(0).getValue();
                        if (altScore > MIN_ALT_THRESHOLD) {
                            LOGGER.debug("[MetaScheduler] Rollback Stage2: 替代方案 {} (score={})", altId, altScore);
                            return new RollbackStage(2, "alternative:" + altId);
                        }
                    }
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 2);
            }
            case 2: { // Stage 3: 回溯上游
                LOGGER.debug("[MetaScheduler] Rollback Stage3: 回溯上游 {}", nodeId);
            var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
                if (bayesian != null && lastExecutedNodeId != null) {
                    String upId = lastExecutedNodeId;
                    double upConfidence = bayesian.getConfidence(upId);
                    if (upConfidence < 0.3) {
                        LOGGER.debug("[MetaScheduler] Rollback Stage3: 回溯到 {} (conf={})", upId, upConfidence);
                        return new RollbackStage(3, "backtrack:" + upId);
                    }
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 3);
            }
            case 3: { // Stage 4: 麦穗探索
                LOGGER.debug("[MetaScheduler] Rollback Stage4: 麦穗探索 {}", nodeId);
                HormonalSystem h = botCtx.hormonalSystem();
                double exploreProb = MotivationEngine.wheatEarExplore(0.3, h);
                if (exploreProb > Math.random()) {
                    return new RollbackStage(4, "explore");
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 4);
            }
            case 4: // Stage 5: LLM 重新规划
            default:
                LOGGER.warn("[MetaScheduler] Rollback Stage5: LLM重新规划 {}", nodeId);
                return new RollbackStage(5, "llm_replan");
        }
    }

    public TemporalScaler getTemporalScaler() { return temporalScaler; }
}
