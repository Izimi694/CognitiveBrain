package com.izimi.aiplayermod.api.impl;

import com.izimi.aiplayermod.api.BotContext;
import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.amygdala.learning.CorrelationDetector;
import com.izimi.aiplayermod.amygdala.learning.LearningSystem;
import com.izimi.aiplayermod.bayesian.BayesianModule;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.cortex.chat.ChatSessionManager;
import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.cortex.task.TaskExecutor;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.hippocampus.MemoryManager;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import com.izimi.aiplayermod.state.StateManager;

import java.util.UUID;

public class BotContextImpl implements BotContext {

    private final UUID botId;
    private final String botName;
    private final WorldContext world;
    private final BotParams botParams;
    private final HormonalSystem hormonalSystem;
    private final ConditionedReflex conditionedReflex;
    private final OneShotAlarmSystem alarmSystem;
    private final BayesianModule bayesianModule;
    private final DispatchReflex dispatchReflex;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final StateManager stateManager;
    private final MemoryManager memoryManager;
    private final PlanManager planManager;
    private final IdleBrain idleBrain;
    private final CorrelationDetector correlationDetector;
    private final LearningSystem learningSystem;
    private final ChatSessionManager chatSessionManager;

    public BotContextImpl(
            UUID botId, String botName, WorldContext world,
            BotParams botParams, HormonalSystem hormonalSystem,
            ConditionedReflex conditionedReflex, OneShotAlarmSystem alarmSystem,
            BayesianModule bayesianModule, DispatchReflex dispatchReflex,
            TaskManager taskManager, TaskExecutor taskExecutor,
            StateManager stateManager, MemoryManager memoryManager,
            PlanManager planManager, IdleBrain idleBrain,
            CorrelationDetector correlationDetector, LearningSystem learningSystem,
            ChatSessionManager chatSessionManager
    ) {
        this.botId = botId;
        this.botName = botName;
        this.world = world;
        this.botParams = botParams;
        this.hormonalSystem = hormonalSystem;
        this.conditionedReflex = conditionedReflex;
        this.alarmSystem = alarmSystem;
        this.bayesianModule = bayesianModule;
        this.dispatchReflex = dispatchReflex;
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
        this.stateManager = stateManager;
        this.memoryManager = memoryManager;
        this.planManager = planManager;
        this.idleBrain = idleBrain;
        this.correlationDetector = correlationDetector;
        this.learningSystem = learningSystem;
        this.chatSessionManager = chatSessionManager;
    }

    @Override public UUID botId() { return botId; }
    @Override public String botName() { return botName; }
    @Override public WorldContext world() { return world; }
    @Override public BotParams botParams() { return botParams; }
    @Override public HormonalSystem hormonalSystem() { return hormonalSystem; }
    @Override public ConditionedReflex conditionedReflex() { return conditionedReflex; }
    @Override public OneShotAlarmSystem alarmSystem() { return alarmSystem; }
    @Override public BayesianModule bayesianModule() { return bayesianModule; }
    @Override public DispatchReflex dispatchReflex() { return dispatchReflex; }
    @Override public TaskManager taskManager() { return taskManager; }
    @Override public TaskExecutor taskExecutor() { return taskExecutor; }
    @Override public StateManager stateManager() { return stateManager; }
    @Override public MemoryManager memoryManager() { return memoryManager; }
    @Override public PlanManager planManager() { return planManager; }
    @Override public IdleBrain idleBrain() { return idleBrain; }
    @Override public CorrelationDetector correlationDetector() { return correlationDetector; }
    @Override public LearningSystem learningSystem() { return learningSystem; }
    @Override public ChatSessionManager chatSessionManager() { return chatSessionManager; }
}
