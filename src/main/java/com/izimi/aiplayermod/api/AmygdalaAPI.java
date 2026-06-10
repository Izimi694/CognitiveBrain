package com.izimi.aiplayermod.api;

import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.amygdala.SocialObserver;

public interface AmygdalaAPI {
    SocialObserver socialObserver();
    FamiliarityTracker familiarityTracker();
}
