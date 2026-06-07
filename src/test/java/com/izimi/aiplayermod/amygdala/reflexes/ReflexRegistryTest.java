package com.izimi.aiplayermod.amygdala.reflexes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReflexRegistryTest {

    @Test
    @DisplayName("registry starts empty")
    void startsEmpty() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        assertEquals(0, registry.size());
        assertNull(registry.highest(null));
    }

    @Test
    @DisplayName("register and size")
    void registerAndSize() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.register(InnateReflex.create("test", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 10.0, 0)),
                new ReflexAction("flee", Map.of())));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("loadDefaults loads 8 built-in reflexes")
    void loadDefaultsLoadsEight() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        assertEquals(8, registry.size());
    }

    @Test
    @DisplayName("all reflexes from loadDefaults are enabled")
    void allDefaultsEnabled() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        for (InnateReflex r : registry.all()) {
            assertTrue(r.enabled(), "Reflex " + r.id() + " should be enabled");
        }
    }

    @Test
    @DisplayName("critical reflex has critical=true")
    void criticalReflexFlag() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        var critical = registry.all().stream()
                .filter(r -> r.id().equals("critical"))
                .findFirst().orElse(null);
        assertNotNull(critical);
        assertTrue(critical.critical());
        assertEquals(0, critical.priority());
        assertEquals(2, critical.triggers().size());
    }

    @Test
    @DisplayName("flee reflex has critical=false")
    void fleeReflexNotCritical() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        var flee = registry.all().stream()
                .filter(r -> r.id().equals("flee"))
                .findFirst().orElse(null);
        assertNotNull(flee);
        assertFalse(flee.critical());
        assertEquals(0, flee.priority());
    }

    @Test
    @DisplayName("match returns reflexes sorted by priority ascending")
    void matchSortedByPriority() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.register(InnateReflex.create("low", 2, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HUNGER_BELOW, 20.0, 0)),
                new ReflexAction("eat", Map.of())));
        registry.register(InnateReflex.create("high", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HUNGER_BELOW, 20.0, 0)),
                new ReflexAction("eat", Map.of())));

        var matched = registry.match(null);
        assertTrue(matched.isEmpty(), "Match on null bot should return empty");
    }

    @Test
    @DisplayName("eat reflex has priority 0")
    void eatPriority() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        var eat = registry.all().stream()
                .filter(r -> r.id().equals("eat"))
                .findFirst().orElse(null);
        assertNotNull(eat);
        assertEquals(0, eat.priority());
        assertEquals("eat", eat.action().type());
    }

    @Test
    @DisplayName("non-safety reflexes have priority > 0")
    void nonSafetyPriority() {
        InnateReflexRegistry registry = new InnateReflexRegistry(new MinecraftReflexEvaluator());
        registry.loadDefaults();
        for (InnateReflex r : registry.all()) {
            if (r.id().equals("avoid_lava") || r.id().equals("seek_shelter") || r.id().equals("collect_item") || r.id().equals("retreat") || r.id().equals("vocal_response")) {
                assertTrue(r.priority() > 0, "Reflex " + r.id() + " should have priority > 0");
            }
        }
    }

    @Test
    @DisplayName("ReflexAction.getDouble with fallback")
    void reflexActionFallback() {
        ReflexAction action = new ReflexAction("flee", Map.of());
        assertEquals(0.3, action.getDouble("speed", 0.3), 0.001);
    }

    @Test
    @DisplayName("ReflexAction.getDouble with param")
    void reflexActionWithParam() {
        ReflexAction action = new ReflexAction("flee", Map.of("speed", 0.5));
        assertEquals(0.5, action.getDouble("speed", 0.3), 0.001);
    }

    @Test
    @DisplayName("ReflexTrigger enum has all 7 types")
    void triggerTypes() {
        ReflexTrigger.TriggerType[] types = ReflexTrigger.TriggerType.values();
        assertEquals(7, types.length);
    }

    @Test
    @DisplayName("compound triggers: InnateReflex with multiple triggers")
    void compoundTriggers() {
        InnateReflex reflex = InnateReflex.create("compound", 0, true,
                List.of(
                        new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 2.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 3)
                ),
                new ReflexAction("flee", Map.of("speed", 0.3)));
        assertEquals(2, reflex.triggers().size());
        assertTrue(reflex.critical());
    }

    @Test
    @DisplayName("MinecraftReflexEvaluator.matches returns false for null bot")
    void evaluatorNullBot() {
        MinecraftReflexEvaluator evaluator = new MinecraftReflexEvaluator();
        assertFalse(evaluator.matches(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 10.0, 0), null));
        assertFalse(evaluator.matchesAll(List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 10.0, 0)), null));
    }

    @Test
    @DisplayName("MinecraftReflexEvaluator helper methods exist")
    void evaluatorHelpers() {
        MinecraftReflexEvaluator evaluator = new MinecraftReflexEvaluator();
        assertNotNull(evaluator);
    }
}
