package com.izimi.aiplayermod.api;

import com.izimi.aiplayermod.amygdala.character.BehaviorStats;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.config.ModConfig;

public interface WorldContext {
    BrainstemAPI brainstem();
    AmygdalaAPI amygdala();
    CortexAPI cortex();
    SkillManager skillManager();
    BehaviorStats behaviorStats();
    ModConfig modConfig();
}
