package com.izimi.aiplayermod.brainstem.adapter;

public record ActionResult(boolean executed, boolean success, double effectiveness, String message) {

    public static ActionResult success(String message) {
        return new ActionResult(true, true, 1.0, message);
    }

    public static ActionResult fail(String message) {
        return new ActionResult(true, false, 0.0, message);
    }

    public static ActionResult partial(double effectiveness, String message) {
        return new ActionResult(true, false, effectiveness, message);
    }

    public static ActionResult unable(String message) {
        return new ActionResult(false, false, 0.0, message);
    }

    public static ActionResult ok() {
        return new ActionResult(true, true, 1.0, "");
    }
}
