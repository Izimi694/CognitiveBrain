package com.izimi.aiplayermod.api;

import com.izimi.aiplayermod.brainstem.scheduler.ILocalPlanner;
import com.izimi.aiplayermod.cortex.api.AIClient;
import com.izimi.aiplayermod.cortex.api.TemplateManager;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.cortex.planner.KnowledgeBase;

public interface CortexAPI {
    ILocalPlanner localPlanner();
    LocalChatHandler chatHandler();
    TemplateManager templateManager();
    KnowledgeBase knowledgeBase();
    AIClient aiClient();
}
