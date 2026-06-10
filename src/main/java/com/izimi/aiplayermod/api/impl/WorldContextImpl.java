package com.izimi.aiplayermod.api.impl;

import com.izimi.aiplayermod.api.AmygdalaAPI;
import com.izimi.aiplayermod.api.BrainstemAPI;
import com.izimi.aiplayermod.api.CortexAPI;
import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.amygdala.SocialObserver;
import com.izimi.aiplayermod.amygdala.character.BehaviorStats;
import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.brainstem.scheduler.ILocalPlanner;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.cortex.api.AIClient;
import com.izimi.aiplayermod.cortex.api.TemplateManager;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.planner.KnowledgeBase;

public class WorldContextImpl implements WorldContext {

    private final InnateReflexRegistry innateReflexes;
    private final BasicActionAdapter basicActions;
    private final InhibitoryControl inhibitor;
    private final SocialObserver socialObserver;
    private final FamiliarityTracker familiarityTracker;
    private final ILocalPlanner localPlanner;
    private final LocalChatHandler chatHandler;
    private final TemplateManager templateManager;
    private final KnowledgeBase knowledgeBase;
    private final AIClient aiClient;
    private final SkillManager skillManager;
    private final BehaviorStats behaviorStats;
    private final ModConfig modConfig;

    private final BrainstemAPI brainstemAPI;
    private final AmygdalaAPI amygdalaAPI;
    private final CortexAPI cortexAPI;

    public WorldContextImpl(
            InnateReflexRegistry innateReflexes,
            BasicActionAdapter basicActions,
            InhibitoryControl inhibitor,
            SocialObserver socialObserver,
            FamiliarityTracker familiarityTracker,
            ILocalPlanner localPlanner,
            LocalChatHandler chatHandler,
            TemplateManager templateManager,
            KnowledgeBase knowledgeBase,
            AIClient aiClient,
            SkillManager skillManager,
            BehaviorStats behaviorStats,
            ModConfig modConfig
    ) {
        this.innateReflexes = innateReflexes;
        this.basicActions = basicActions;
        this.inhibitor = inhibitor;
        this.socialObserver = socialObserver;
        this.familiarityTracker = familiarityTracker;
        this.localPlanner = localPlanner;
        this.chatHandler = chatHandler;
        this.templateManager = templateManager;
        this.knowledgeBase = knowledgeBase;
        this.aiClient = aiClient;
        this.skillManager = skillManager;
        this.behaviorStats = behaviorStats;
        this.modConfig = modConfig;

        this.brainstemAPI = new BrainstemAPI() {
            @Override public InnateReflexRegistry innateReflexes() { return WorldContextImpl.this.innateReflexes; }
            @Override public BasicActionAdapter basicActions() { return WorldContextImpl.this.basicActions; }
            @Override public InhibitoryControl inhibitor() { return WorldContextImpl.this.inhibitor; }
        };

        this.amygdalaAPI = new AmygdalaAPI() {
            @Override public SocialObserver socialObserver() { return WorldContextImpl.this.socialObserver; }
            @Override public FamiliarityTracker familiarityTracker() { return WorldContextImpl.this.familiarityTracker; }
        };

        this.cortexAPI = new CortexAPI() {
            @Override public ILocalPlanner localPlanner() { return WorldContextImpl.this.localPlanner; }
            @Override public LocalChatHandler chatHandler() { return WorldContextImpl.this.chatHandler; }
            @Override public TemplateManager templateManager() { return WorldContextImpl.this.templateManager; }
            @Override public KnowledgeBase knowledgeBase() { return WorldContextImpl.this.knowledgeBase; }
            @Override public AIClient aiClient() { return WorldContextImpl.this.aiClient; }
        };
    }

    @Override public BrainstemAPI brainstem() { return brainstemAPI; }
    @Override public AmygdalaAPI amygdala() { return amygdalaAPI; }
    @Override public CortexAPI cortex() { return cortexAPI; }
    @Override public SkillManager skillManager() { return skillManager; }
    @Override public BehaviorStats behaviorStats() { return behaviorStats; }
    @Override public ModConfig modConfig() { return modConfig; }
}
