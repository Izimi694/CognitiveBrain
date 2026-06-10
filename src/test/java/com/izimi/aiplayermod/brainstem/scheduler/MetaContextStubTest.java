package com.izimi.aiplayermod.brainstem.scheduler;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.FamiliarityTracker;
import com.izimi.aiplayermod.amygdala.SocialObserver;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.hormonal.HormonalSystem;

class MetaContextStubTest {

    private MetaContext ctx;
    private HormonalSystem hormones;
    private BotParams params;
    private SocialObserver socialObserver;
    private FamiliarityTracker familiarityTracker;
    private ConditionedReflex conditionedReflex;
    private DispatchReflex dispatchReflex;

    @BeforeEach
    void setUp() {
        hormones = new HormonalSystem();
        params = BotParams.generate();
        socialObserver = mock(SocialObserver.class);
        familiarityTracker = mock(FamiliarityTracker.class);
        conditionedReflex = mock(ConditionedReflex.class);
        dispatchReflex = mock(DispatchReflex.class);

        when(socialObserver.getNearbyPlayerCount()).thenReturn(0);

        ctx = new MetaContext(
                UUID.randomUUID(),
                "TestBot",
                params,
                hormones,
                null,
                conditionedReflex,
                dispatchReflex,
                null,
                null,
                null,
                null,
                null,
                mock(IdleBrain.class),
                null,
                null,
                null,
                socialObserver,
                familiarityTracker,
                null,
                null
        );
    }

    @Test
    @DisplayName("hasGroupActivity returns false when no nearby players")
    void noGroupActivityWhenAlone() {
        when(socialObserver.getNearbyPlayerCount()).thenReturn(0);
        assertFalse(ctx.hasGroupActivity());
    }

    @Test
    @DisplayName("hasGroupActivity returns true when multiple nearby players")
    void groupActivityWhenMultipleNearby() {
        when(socialObserver.getNearbyPlayerCount()).thenReturn(3);
        assertTrue(ctx.hasGroupActivity());
    }

    @Test
    @DisplayName("hasGroupActivity returns false for single nearby player")
    void noGroupActivityForSinglePlayer() {
        when(socialObserver.getNearbyPlayerCount()).thenReturn(1);
        assertFalse(ctx.hasGroupActivity());
    }

    @Test
    @DisplayName("hasHighProficiencyReflex returns false when no conditioned reflex")
    void noHighProficiencyWhenNoReflex() {
        var ctxNoReflex = new MetaContext(
                UUID.randomUUID(), "TestBot", params, hormones, null,
                null, dispatchReflex, null, null, null, null, null,
                mock(IdleBrain.class), null, null, null, socialObserver, familiarityTracker,
                null, null
        );
        assertFalse(ctxNoReflex.hasHighProficiencyReflex(null));
    }

    @Test
    @DisplayName("hasHighProficiencyReflex returns true when proficiency >= 0.8")
    void highProficiencyWhenTopReflex() {
        when(conditionedReflex.getHighestProficiency()).thenReturn(0.85);
        assertTrue(ctx.hasHighProficiencyReflex(null));
    }

    @Test
    @DisplayName("hasHighProficiencyReflex returns false when proficiency < 0.8")
    void lowProficiencyWhenBelowThreshold() {
        when(conditionedReflex.getHighestProficiency()).thenReturn(0.6);
        assertFalse(ctx.hasHighProficiencyReflex(null));
    }

    @Test
    @DisplayName("hasRecentNovelty uses dynamic threshold")
    void hasRecentNoveltyDynamic() {
        assertFalse(ctx.hasRecentNovelty());
    }

    @Test
    @DisplayName("hasRecentNovelty returns true when curiosity > threshold")
    void recentNoveltyWhenCuriosityHigh() {
        hormones.onNovelDiscovery();
        hormones.onNovelDiscovery();
        hormones.onNovelDiscovery();
        assertTrue(ctx.hasRecentNovelty());
    }

    @Test
    @DisplayName("getLastProficiency delegates to conditionedReflex")
    void lastProficiencyDelegates() {
        when(conditionedReflex.getHighestProficiency()).thenReturn(0.75);
        assertEquals(0.75, ctx.getLastProficiency());
    }

    @Test
    @DisplayName("getConsecutiveFailures delegates to conditionedReflex")
    void consecutiveFailuresDelegates() {
        when(conditionedReflex.getConsecutiveFailures()).thenReturn(3);
        assertEquals(3, ctx.getConsecutiveFailures());
    }

    @Test
    @DisplayName("getConsecutiveFailures returns 0 when no reflex")
    void consecutiveFailuresZeroWhenNoReflex() {
        var ctxNoReflex = new MetaContext(
                UUID.randomUUID(), "TestBot", params, hormones, null,
                null, dispatchReflex, null, null, null, null, null,
                mock(IdleBrain.class), null, null, null, socialObserver, familiarityTracker,
                null, null
        );
        assertEquals(0, ctxNoReflex.getConsecutiveFailures());
    }

    @Test
    @DisplayName("hasUrgentPlayerMessage detects 小心")
    void urgentMessageCareful() {
        ctx.setLastPlayerMessage("小心那个苦力怕");
        assertTrue(ctx.hasUrgentPlayerMessage());
    }

    @Test
    @DisplayName("hasUrgentPlayerMessage detects 停")
    void urgentMessageStop() {
        ctx.setLastPlayerMessage("停下来别挖了");
        assertTrue(ctx.hasUrgentPlayerMessage());
    }

    @Test
    @DisplayName("hasUrgentPlayerMessage returns false for normal chat")
    void notUrgentForNormalChat() {
        ctx.setLastPlayerMessage("你好啊");
        assertFalse(ctx.hasUrgentPlayerMessage());
    }

    @Test
    @DisplayName("hasUrgentPlayerMessage returns false for empty message")
    void notUrgentForEmpty() {
        assertFalse(ctx.hasUrgentPlayerMessage());
    }

    @Test
    @DisplayName("getPlayerInactiveMinutes returns 60 when no message")
    void inactiveMinutesDefault() {
        assertEquals(60, ctx.getPlayerInactiveMinutes());
    }

    @Test
    @DisplayName("setLastPlayerMessage updates time tracking")
    void lastMessageTimeUpdated() {
        ctx.setLastPlayerMessage("hello");
        assertTrue(ctx.getPlayerInactiveMinutes() < 1);
    }

    @Test
    @DisplayName("tickSinceLastLLM increments correctly")
    void llmTickCounter() {
        assertEquals(0, ctx.getTickSinceLastLLM());
        ctx.incrementTickSinceLastLLM();
        assertEquals(1, ctx.getTickSinceLastLLM());
        ctx.incrementTickSinceLastLLM();
        assertEquals(2, ctx.getTickSinceLastLLM());
        ctx.resetTickSinceLastLLM();
        assertEquals(0, ctx.getTickSinceLastLLM());
    }

    @Test
    @DisplayName("recentLLMFailure flag works")
    void recentLLMFailureFlag() {
        assertFalse(ctx.hasRecentLLMFailure());
        ctx.setRecentLLMFailure(true);
        assertTrue(ctx.hasRecentLLMFailure());
        ctx.setRecentLLMFailure(false);
        assertFalse(ctx.hasRecentLLMFailure());
    }

    @Test
    @DisplayName("hasEnvironmentAnomaly always returns false (stub)")
    void environmentAnomalyStub() {
        assertFalse(ctx.hasEnvironmentAnomaly());
    }

    @Test
    @DisplayName("hasNovelEntity always returns false (stub)")
    void novelEntityStub() {
        assertFalse(ctx.hasNovelEntity());
    }

    @Test
    @DisplayName("hasSuddenEnvironmentChange uses entity count comparison")
    void suddenEnvironmentChange() {
        ctx.setThisEntityCount(10);
        ctx.cycleEntityCount();
        ctx.setThisEntityCount(5);
        assertTrue(ctx.hasSuddenEnvironmentChange());
    }

    @Test
    @DisplayName("hasSuddenEnvironmentChange detects >30% change with >=3 delta")
    void suddenEnvironmentChangeDetected() {
        ctx.setThisEntityCount(5);
        ctx.cycleEntityCount();
        ctx.setThisEntityCount(10);
        assertTrue(ctx.hasSuddenEnvironmentChange());
    }

    @Test
    @DisplayName("hasSuddenEnvironmentChange returns false for small changes")
    void noSuddenChangeForSmallDelta() {
        ctx.setThisEntityCount(100);
        ctx.cycleEntityCount();
        ctx.setThisEntityCount(101);
        assertFalse(ctx.hasSuddenEnvironmentChange());
    }

    @Test
    @DisplayName("withBot copies all mutable state")
    void withBotCopiesState() {
        ctx.setLastPlayerMessage("test");
        ctx.incrementTickSinceLastLLM();
        ctx.incrementTickSinceLastLLM();
        ctx.setRecentLLMFailure(true);
        ctx.setCurrentBiomeId("plains");

        MetaContext copy = ctx.withBot(null);
        assertEquals("test", copy.lastPlayerMessage());
        assertEquals(2, copy.getTickSinceLastLLM());
        assertTrue(copy.hasRecentLLMFailure());
        assertEquals("plains", copy.currentBiomeId());
    }
}
