package com.izimi.aiplayermod.amygdala.reflexes;

import java.util.Collections;
import java.util.List;

public record InnateReflex(
    String id,
    int priority,
    boolean critical,
    List<ReflexTrigger> triggers,
    ReflexAction action,
    boolean enabled
) {
    public InnateReflex {
        triggers = Collections.unmodifiableList(triggers);
    }

    public static InnateReflex create(String id, int priority, boolean critical,
                                       List<ReflexTrigger> triggers, ReflexAction action) {
        return new InnateReflex(id, priority, critical, triggers, action, true);
    }
}
