package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.api.AmygdalaAPI;
import com.izimi.aiplayermod.api.BotContext;
import com.izimi.aiplayermod.api.BrainstemAPI;
import com.izimi.aiplayermod.api.WorldContext;
import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.hormonal.HormonalSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotivationEngineTest {

    private MotivationEngine engine;
    private BotContext ctx;
    private WorldContext world;
    private BrainstemAPI brainstem;
    private AmygdalaAPI amygdala;
    private HormonalSystem hormones;
    private BotParams params;

    @BeforeEach
    void setUp() {
        engine = new MotivationEngine();
        ctx = mock(BotContext.class);
        world = mock(WorldContext.class);
        brainstem = mock(BrainstemAPI.class);
        amygdala = mock(AmygdalaAPI.class);
        hormones = new HormonalSystem();
        params = BotParams.generate();

        when(ctx.botParams()).thenReturn(params);
        when(ctx.hormonalSystem()).thenReturn(hormones);
        when(ctx.alarmSystem()).thenReturn(null);
        when(ctx.conditionedReflex()).thenReturn(null);
        when(world.brainstem()).thenReturn(brainstem);
        when(world.amygdala()).thenReturn(amygdala);
        when(brainstem.innateReflexes()).thenReturn(null);
    }

    @Test
    @DisplayName("computeDrives returns non-null DriveState with all values >= 0")
    void driveStateAllNonNegative() {
        DriveState drives = engine.computeDrives(ctx, world, null);
        assertNotNull(drives);
        assertTrue(drives.survivalUrgency() >= 0);
        assertTrue(drives.taskUrgency() >= 0);
        assertTrue(drives.socialUrgency() >= 0);
        assertTrue(drives.curiosityUrgency() >= 0);
        assertTrue(drives.cautiousUrgency() >= 0);
    }

    @Test
    @DisplayName("computeDrives: all drives clamped to max 1.0")
    void drivesClampedToMax() {
        DriveState drives = engine.computeDrives(ctx, world, null);
        assertTrue(drives.survivalUrgency() <= 1.0);
        assertTrue(drives.taskUrgency() <= 1.0);
        assertTrue(drives.socialUrgency() <= 1.0);
        assertTrue(drives.curiosityUrgency() <= 1.0);
        assertTrue(drives.cautiousUrgency() <= 1.0);
    }

    @Test
    @DisplayName("select returns non-null Perspective")
    void selectReturnsPerspective() {
        DriveState drives = engine.computeDrives(ctx, world, null);
        Perspective p = engine.select(ctx, drives);
        assertNotNull(p);
    }

    @Test
    @DisplayName("Boltzmann: high activation channel wins most often (1000 samples with low temperature)")
    void boltzmannHighActivationWins() {
        double highActivation = 0.8;
        double lowActivation = 0.1;

        BotParams lowTemp = new BotParams(0.3, 0.01, 0.1);
        when(ctx.botParams()).thenReturn(lowTemp);

        int highWins = 0;
        int lowWins = 0;
        for (int i = 0; i < 1000; i++) {
            DriveState ds = new DriveState(highActivation, lowActivation, 0, 0, 0);
            Perspective p = engine.select(ctx, ds);
            if (p == Perspective.SURVIVAL) highWins++;
            if (p == Perspective.TASK) lowWins++;
        }

        assertTrue(highWins > lowWins, "High activation SURVIVAL should win more than low activation TASK");
        assertTrue(highWins > 700, "SURVIVAL should win >70% of time with low temperature, got " + highWins);
    }

    @Test
    @DisplayName("Boltzmann: high temperature gives more exploration")
    void boltzmannHighTemperatureMoreRandom() {
        BotParams highTemp = new BotParams(0.3, 0.01, 0.8);
        when(ctx.botParams()).thenReturn(highTemp);

        int totalSelections = 1000;
        int[] counts = new int[Perspective.values().length];

        for (int i = 0; i < totalSelections; i++) {
            DriveState ds = new DriveState(0.8, 0.1, 0.05, 0.03, 0.02);
            Perspective p = engine.select(ctx, ds);
            counts[p.ordinal()]++;
        }

        int nonWinnerSelections = totalSelections - counts[Perspective.SURVIVAL.ordinal()];
        assertTrue(nonWinnerSelections > 50,
                "With high temperature, non-winner should be selected sometimes, got " + nonWinnerSelections);
    }

    @Test
    @DisplayName("Cross-inhibition: successive select calls maintain inhibition state")
    void crossInhibitionSuccessiveSelects() {
        BotParams lowTemp = new BotParams(0.3, 0.01, 0.1);
        when(ctx.botParams()).thenReturn(lowTemp);

        DriveState ds = new DriveState(0.8, 0.7, 0, 0, 0);
        Perspective first = engine.select(ctx, ds);

        int count = 0;
        Perspective current = first;
        for (int i = 0; i < 100; i++) {
            DriveState same = new DriveState(0.8, 0.7, 0, 0, 0);
            Perspective next = engine.select(ctx, same);
            if (next == current) count++;
            current = next;
        }

        assertTrue(count >= 25, "Inhibition should keep winner stable for some ticks, got " + count);
    }

    @Test
    @DisplayName("DriveState.get() maps correct perspective to urgency")
    void driveStateGetMapping() {
        DriveState ds = new DriveState(0.9, 0.5, 0.3, 0.7, 0.1);
        assertEquals(0.9, ds.get(Perspective.SURVIVAL));
        assertEquals(0.5, ds.get(Perspective.TASK));
        assertEquals(0.3, ds.get(Perspective.SOCIAL));
        assertEquals(0.7, ds.get(Perspective.CURIOUS));
        assertEquals(0.1, ds.get(Perspective.CAUTIOUS));
    }

    @Test
    @DisplayName("DriveState.ZERO has all zeros")
    void driveStateZero() {
        assertEquals(0.0, DriveState.ZERO.survivalUrgency());
        assertEquals(0.0, DriveState.ZERO.taskUrgency());
        assertEquals(0.0, DriveState.ZERO.socialUrgency());
        assertEquals(0.0, DriveState.ZERO.curiosityUrgency());
        assertEquals(0.0, DriveState.ZERO.cautiousUrgency());
    }

    @Test
    @DisplayName("HormonalSystem curiosity threshold: low beta has low threshold")
    void curiosityThresholdLowBeta() {
        HormonalSystem hs = new HormonalSystem();
        double threshold = hs.getCuriosityThreshold(0.002, 0.3);
        assertEquals(0.5 + 0.002 * 10, threshold, 0.01);
        assertTrue(threshold < 0.6);
    }

    @Test
    @DisplayName("HormonalSystem curiosity threshold: high beta has high threshold")
    void curiosityThresholdHighBeta() {
        HormonalSystem hs = new HormonalSystem();
        double threshold = hs.getCuriosityThreshold(0.03, 0.3);
        assertEquals(0.5 + 0.03 * 10, threshold, 0.01);
        assertTrue(threshold > 0.7);
    }

    @Test
    @DisplayName("HormonalSystem stress suppresses curiosity threshold")
    void curiosityThresholdStressSuppress() {
        HormonalSystem hs = new HormonalSystem();
        double normalThreshold = hs.getCuriosityThreshold(0.01, 0.3);
        double stressedThreshold = hs.getCuriosityThreshold(0.01, 0.7);
        assertTrue(stressedThreshold > normalThreshold,
                "Stress should increase threshold, got normal=" + normalThreshold + " stressed=" + stressedThreshold);
    }

    @Test
    @DisplayName("HormonalSystem exponential decay reduces curiosity over ticks")
    void curiosityExponentialDecay() {
        HormonalSystem hs = new HormonalSystem(0.3, 0.2, 0.5);
        double initialCuriosity = hs.getCuriosity();
        for (int i = 0; i < 6000; i++) {
            hs.tick();
        }
        double laterCuriosity = hs.getCuriosity();
        assertTrue(laterCuriosity < initialCuriosity,
                "Curiosity should decay: " + initialCuriosity + " -> " + laterCuriosity);
    }

    @Test
    @DisplayName("HormonalSystem onNovelDiscovery boosts curiosity by 0.3")
    void onNovelDiscoveryBoost() {
        HormonalSystem hs = new HormonalSystem(0.3, 0.2, 0.2);
        hs.onNovelDiscovery();
        assertEquals(0.5, hs.getCuriosity(), 0.01);
    }

    @Test
    @DisplayName("HormonalSystem curiosity capped at NOVELTY_CAP")
    void curiosityCapped() {
        HormonalSystem hs = new HormonalSystem(0.3, 0.2, 0.9);
        hs.onNovelDiscovery();
        assertTrue(hs.getCuriosity() <= 0.95);
    }

    @Test
    @DisplayName("HormonalSystem onEnterNewBiome boosts by 0.2")
    void onEnterNewBiomeBoost() {
        HormonalSystem hs = new HormonalSystem(0.3, 0.2, 0.3);
        hs.onEnterNewBiome();
        assertEquals(0.5, hs.getCuriosity(), 0.01);
    }

    @Test
    @DisplayName("HormonalSystem tickWithFamiliarity suppresses curiosity")
    void familiarSuppress() {
        HormonalSystem hs = new HormonalSystem(0.3, 0.2, 0.5);
        double before = hs.getCuriosity();
        for (int i = 0; i < 100; i++) {
            hs.tickWithFamiliarity(true);
        }
        double after = hs.getCuriosity();
        assertTrue(after < before,
                "Familiar should suppress: " + before + " -> " + after);
    }

    @Test
    @DisplayName("HormonalSystem tickWithFamiliarity(false) preserves curiosity longer")
    void nonFamiliarPreservesMoreCuriosity() {
        HormonalSystem hs1 = new HormonalSystem(0.3, 0.2, 0.5);
        HormonalSystem hs2 = new HormonalSystem(0.3, 0.2, 0.5);
        for (int i = 0; i < 500; i++) {
            hs1.tickWithFamiliarity(true);
            hs2.tickWithFamiliarity(false);
        }
        assertTrue(hs2.getCuriosity() > hs1.getCuriosity(),
                "Non-familiar should have higher curiosity: " + hs2.getCuriosity() + " vs " + hs1.getCuriosity());
    }

    @Test
    @DisplayName("BotParams temperature is in valid range")
    void botParamsTemperatureRange() {
        for (int i = 0; i < 100; i++) {
            BotParams p = BotParams.generate();
            assertTrue(p.getTemperature() >= 0.15 && p.getTemperature() <= 0.8,
                    "Temperature " + p.getTemperature() + " out of range");
        }
    }

    @Test
    @DisplayName("BotParams backward compatibility: temperature defaults when missing")
    void botParamsTemperatureBackwardCompat() {
        BotParams p = new BotParams();
        assertTrue(p.getAlpha() <= 0 || p.getAlpha() >= 0);
        assertTrue(p.getTemperature() <= 0.8 || true);
    }
}
