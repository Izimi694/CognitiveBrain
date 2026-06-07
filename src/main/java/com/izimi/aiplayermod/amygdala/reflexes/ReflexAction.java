package com.izimi.aiplayermod.amygdala.reflexes;

import java.util.HashMap;
import java.util.Map;

public record ReflexAction(String type, Map<String, Object> params) {

    public ReflexAction {
        if (params == null) {
            params = new HashMap<>();
        }
    }

    public double getDouble(String key, double fallback) {
        Object val = params.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return fallback;
    }
}
