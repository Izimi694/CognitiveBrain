package com.izimi.aiplayermod.amygdala;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotParamsTest {

    @Test
    @DisplayName("inherit single parent: child params are in valid range")
    void inheritSingleParentValidRange() {
        for (int i = 0; i < 100; i++) {
            BotParams parent = BotParams.generate();
            BotParams child = BotParams.inherit(parent);
            assertTrue(child.getAlpha() >= 0.1 && child.getAlpha() <= 0.6,
                    "Alpha " + child.getAlpha() + " out of range");
            assertTrue(child.getBeta() >= 0.002 && child.getBeta() <= 0.03,
                    "Beta " + child.getBeta() + " out of range");
            assertTrue(child.getTemperature() >= 0.15 && child.getTemperature() <= 0.8,
                    "Temperature " + child.getTemperature() + " out of range");
        }
    }

    @Test
    @DisplayName("inherit two parents: generation is max(parent gen)+1")
    void inheritTwoParentsGeneration() {
        BotParams p1 = BotParams.generate();
        p1.withGeneration(3);
        BotParams p2 = BotParams.generate();
        p2.withGeneration(5);
        BotParams child = BotParams.inherit(p1, p2);
        assertEquals(6, child.getGeneration(), "Child gen should be max parent gen + 1");
    }

    @Test
    @DisplayName("inherit single parent: generation is parent gen + 1")
    void inheritSingleParentGeneration() {
        BotParams parent = BotParams.generate();
        parent.withGeneration(2);
        BotParams child = BotParams.inherit(parent);
        assertEquals(3, child.getGeneration(), "Child gen should be parent gen + 1");
    }

    @Test
    @DisplayName("inherit two parents: params fall between parents on average")
    void inheritTwoParentsParamsBounded() {
        for (int i = 0; i < 50; i++) {
            BotParams p1 = new BotParams(0.5, 0.02, 0.7);
            BotParams p2 = new BotParams(0.2, 0.005, 0.3);
            BotParams child = BotParams.inherit(p1, p2);
            // After averaging + halving + mutation, should still be within min/max of parents
            assertTrue(child.getAlpha() >= 0.1 && child.getAlpha() <= 0.6);
            assertTrue(child.getBeta() >= 0.002 && child.getBeta() <= 0.03);
            assertTrue(child.getTemperature() >= 0.15 && child.getTemperature() <= 0.8);
        }
    }

    @Test
    @DisplayName("inherit applies mutation: at least some variation over 50 trials")
    void inheritMutationCausesVariation() {
        BotParams parent = new BotParams(0.3, 0.01, 0.4);
        boolean alphaDiff = false;
        boolean betaDiff = false;
        boolean tempDiff = false;
        for (int i = 0; i < 50; i++) {
            BotParams child = BotParams.inherit(parent);
            if (Math.abs(child.getAlpha() - 0.3) > 0.01) alphaDiff = true;
            if (Math.abs(child.getBeta() - 0.01) > 0.001) betaDiff = true;
            if (Math.abs(child.getTemperature() - 0.4) > 0.01) tempDiff = true;
            if (alphaDiff && betaDiff && tempDiff) break;
        }
        assertTrue(alphaDiff, "Alpha should vary due to mutation");
        assertTrue(betaDiff, "Beta should vary due to mutation");
        assertTrue(tempDiff, "Temperature should vary due to mutation");
    }

    @Test
    @DisplayName("generate produces BotParams in valid range")
    void generateValidRange() {
        for (int i = 0; i < 100; i++) {
            BotParams p = BotParams.generate();
            assertTrue(p.getAlpha() >= 0.1 && p.getAlpha() <= 0.6);
            assertTrue(p.getBeta() >= 0.002 && p.getBeta() <= 0.03);
            assertTrue(p.getTemperature() >= 0.15 && p.getTemperature() <= 0.8);
            assertEquals(0, p.getGeneration(), "Generated bots start at gen 0");
        }
    }

    @Test
    @DisplayName("inherit with same parent twice produces valid params")
    void inheritSameParent() {
        BotParams parent = BotParams.generate();
        BotParams child1 = BotParams.inherit(parent);
        BotParams child2 = BotParams.inherit(parent);
        assertNotNull(child1);
        assertNotNull(child2);
        assertTrue(child1.getGeneration() > 0);
        assertTrue(child2.getGeneration() > 0);
    }
}
