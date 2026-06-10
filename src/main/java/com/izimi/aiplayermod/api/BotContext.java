package com.izimi.aiplayermod.api;

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

public interface BotContext {
    UUID botId();
    String botName();
    WorldContext world();

    BotParams botParams();
    HormonalSystem hormonalSystem();
    ConditionedReflex conditionedReflex();
    OneShotAlarmSystem alarmSystem();
    BayesianModule bayesianModule();
    DispatchReflex dispatchReflex();

    TaskManager taskManager();
    TaskExecutor taskExecutor();
    StateManager stateManager();
    MemoryManager memoryManager();
    PlanManager planManager();

    IdleBrain idleBrain();
    CorrelationDetector correlationDetector();
    LearningSystem learningSystem();
    ChatSessionManager chatSessionManager();
}
