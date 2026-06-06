package com.izimi.aiplayermod.learning;

import java.util.*;

public record ObservedSequence(
        String id,
        int occurrences,
        double proficiency,
        String source,
        String target,
        Trigger trigger,
        List<Step> steps,
        ExpectedResult expectedResult,
        long firstSeen,
        long lastSeen
) {
    public record Trigger(
            List<String> nearbyBlocks,
            List<String> inventory,
            String timeOfDay
    ) {}

    public record Step(
            String action,
            String target
    ) {}

    public record ExpectedResult(
            String type,
            String block
    ) {}

    public static ObservedSequence create(
            String target,
            List<BehaviorEvent> events,
            List<String> nearbyBlocks,
            List<String> inventory,
            String timeOfDay
    ) {
        String id = "seq_" + System.currentTimeMillis() / 1000;
        Trigger trigger = new Trigger(nearbyBlocks, inventory, timeOfDay);
        ExpectedResult result = new ExpectedResult(events.get(0).action(), target);

        List<Step> steps = new ArrayList<>();
        for (BehaviorEvent e : events) {
            steps.add(new Step(e.action(), e.target()));
        }

        long firstSeen = events.stream().mapToLong(BehaviorEvent::timestamp).min().orElse(System.currentTimeMillis());
        long lastSeen = events.stream().mapToLong(BehaviorEvent::timestamp).max().orElse(System.currentTimeMillis());

        return new ObservedSequence(
                id, events.size(), 0.3, "OBSERVED",
                target, trigger, steps, result, firstSeen, lastSeen
        );
    }

    public ObservedSequence withIncrementedProficiency() {
        return new ObservedSequence(
                id, occurrences, Math.min(1.0, proficiency + 0.05),
                source, target, trigger, steps, expectedResult, firstSeen, lastSeen
        );
    }
}
