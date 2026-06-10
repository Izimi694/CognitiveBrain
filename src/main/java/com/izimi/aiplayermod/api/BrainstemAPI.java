package com.izimi.aiplayermod.api;

import com.izimi.aiplayermod.brainstem.adapter.BasicActionAdapter;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;

public interface BrainstemAPI {
    InnateReflexRegistry innateReflexes();
    BasicActionAdapter basicActions();
    InhibitoryControl inhibitor();
}
