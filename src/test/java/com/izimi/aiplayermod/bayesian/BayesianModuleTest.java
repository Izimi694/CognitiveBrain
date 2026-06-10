package com.izimi.aiplayermod.bayesian;

import com.izimi.aiplayermod.util.api.MemoryFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BayesianModuleTest {

    private static final UUID BOT_ID = UUID.randomUUID();
    private BayesianModule module;
    private MemoryFileSystem fs;

    @BeforeEach
    void setUp() {
        BayesianModule.resetSharedState();
        fs = new MemoryFileSystem();
        module = new BayesianModule(BOT_ID, fs);
    }

    @Test
    @DisplayName("predictSuccess returns prior when no features given")
    void predictSuccessWithNoFeatures() {
        double result = module.predictSuccess("reflex_mine_iron", null);
        assertEquals(0.5, result, 0.001, "Default prior should be 0.5");
    }

    @Test
    @DisplayName("predictSuccess returns prior with empty features")
    void predictSuccessWithEmptyFeatures() {
        double result = module.predictSuccess("reflex_mine_iron", List.of());
        assertEquals(0.5, result, 0.001, "Default prior should be 0.5");
    }

    @Test
    @DisplayName("predictSuccess adjusts posterior after update")
    void predictSuccessAfterUpdate() {
        var features = List.of(new BayesianFeature("block_type=stone", true));
        double before = module.predictSuccess("reflex_mine_stone", features);
        module.update("reflex_mine_stone", features, true);

        double result = module.predictSuccess("reflex_mine_stone", features);
        assertNotEquals(before, result, "Posterior should change after update");
    }

    @Test
    @DisplayName("predict returns detailed prediction with contributions")
    void predictReturnsContributions() {
        var features = List.of(new BayesianFeature("block_type=stone", true));
        module.update("reflex_mine_stone", features, true);
        module.update("reflex_mine_stone", features, true);

        BayesianPrediction pred = module.predict("reflex_mine_stone", features);
        assertNotNull(pred);
        assertEquals("reflex_mine_stone", pred.reflexId());
        assertFalse(pred.contributions().isEmpty(), "Should have feature contributions");
    }

    @Test
    @DisplayName("update records success outcome")
    void updateRecordsSuccess() {
        var features = List.of(new BayesianFeature("block_type=stone", true));
        module.update("reflex_mine_stone", features, true);

        assertEquals(1, module.getTrackedReflexCount(), "One reflex tracked");
        assertEquals(1, module.getTrackedFeatureCount(), "One feature tracked");
        assertTrue(module.getSharedPrior().get("reflex_mine_stone") > 0.5,
                "Prior should increase after success");
    }

    @Test
    @DisplayName("update records failure outcome")
    void updateRecordsFailure() {
        var features = List.of(new BayesianFeature("mob_type=creeper", true));
        module.update("reflex_attack_creeper", features, false);

        assertTrue(module.getSharedPrior().getOrDefault("reflex_attack_creeper", 0.5) < 0.5,
                "Prior should decrease after failure");
    }

    @Test
    @DisplayName("isConverged returns false with insufficient samples")
    void isConvergedReturnsFalseWithFewSamples() {
        module.update("reflex_test", List.of(new BayesianFeature("test", true)), true);
        assertFalse(module.isConverged("reflex_test"),
                "Should not be converged after 1 sample");
    }

    @Test
    @DisplayName("isConverged returns true after many consistent updates")
    void isConvergedReturnsTrueAfterManyUpdates() {
        var features = List.of(new BayesianFeature("test", true));
        for (int i = 0; i < 10; i++) {
            module.update("reflex_test", features, true);
        }
        assertTrue(module.isConverged("reflex_test"),
                "Should converge after many consistent updates");
    }

    @Test
    @DisplayName("getConvergence returns snapshot with change rate")
    void getConvergenceReturnsSnapshot() {
        for (int i = 0; i < 6; i++) {
            module.update("reflex_test", List.of(new BayesianFeature("test", true)), true);
        }

        BayesianModule.PosteriorSnapshot snap = module.getConvergence("reflex_test");
        assertNotNull(snap);
        assertTrue(snap.sampleCount() >= 5);
        assertTrue(snap.changeRate() >= 0);
    }

    @Test
    @DisplayName("getCurrentDirection returns summary when priors exist")
    void getCurrentDirectionReturnsSummary() {
        module.update("reflex_mine_iron", List.of(new BayesianFeature("ore=iron", true)), true);
        String direction = module.getCurrentDirection();

        assertNotNull(direction);
        assertFalse(direction.isEmpty());
        assertTrue(direction.contains("reflex_mine_iron"),
                "Direction should mention high-priority reflex");
    }

    @Test
    @DisplayName("getCurrentDirection returns no-data message when empty")
    void getCurrentDirectionWhenEmpty() {
        assertEquals("暂无经验数据", module.getCurrentDirection());
    }

    @Test
    @DisplayName("anchoring context is set and retrieved")
    void anchoringContext() {
        module.setAnchoringContext("mining");
        assertEquals("mining", module.getAnchoringContext());

        module.setAnchoringContext(null);
        assertEquals("", module.getAnchoringContext());
    }

    @Test
    @DisplayName("tracked feature count reflects unique features")
    void trackedFeatureCount() {
        var f1 = List.of(new BayesianFeature("ore=iron", true));
        var f2 = List.of(new BayesianFeature("ore=gold", true));

        module.update("reflex_mine_iron", f1, true);
        module.update("reflex_mine_gold", f2, true);

        assertEquals(2, module.getTrackedFeatureCount());
    }

    @Test
    @DisplayName("predictRelevance returns non-zero score for matching keywords")
    void predictRelevance() {
        module.update("reflex_mine_iron", List.of(new BayesianFeature("ore=iron", true)), true);

        double score = module.predictRelevance("mine iron ore", "Found iron ore in cave");
        assertTrue(score > 0, "Relevance should be positive for matching keywords");
    }

    @Test
    @DisplayName("predictRelevance returns zero for non-matching query")
    void predictRelevanceNoMatch() {
        double score = module.predictRelevance("combat", "Finding diamonds");
        assertEquals(0, score, "Relevance should be zero for non-matching keywords");
    }

    @Test
    @DisplayName("getTopPredictions returns correct number of results")
    void getTopPredictions() {
        module.update("reflex_a", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_b", List.of(new BayesianFeature("y", true)), true);

        var top = module.getTopPredictions(1);
        assertEquals(1, top.size());
    }
}
