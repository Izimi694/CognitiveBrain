package com.izimi.aiplayermod.brainstem;

import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdleBrainTest {

    private TaskManager taskManager;
    private SkillManager skillManager;
    private IdleBrain idleBrain;

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        skillManager = mock(SkillManager.class);
        idleBrain = new IdleBrain(taskManager, skillManager);
    }

    @Test
    @DisplayName("initial state is IDLE")
    void initialStateIsIdle() {
        assertEquals(IdleBrain.State.IDLE, idleBrain.getState());
    }

    @Test
    @DisplayName("onTick returns null when has active task")
    void onTickWithActiveTaskReturnsNull() {
        when(taskManager.getActiveTask()).thenReturn(mock(com.izimi.aiplayermod.cortex.task.Task.class));
        assertNull(idleBrain.onTick());
        assertEquals(IdleBrain.State.IDLE, idleBrain.getState());
    }

    @Test
    @DisplayName("onTick returns null before IDLE_THRESHOLD_MS")
    void onTickBeforeThresholdReturnsNull() {
        when(taskManager.getActiveTask()).thenReturn(null);
        assertNull(idleBrain.onTick());
    }

    @Test
    @DisplayName("handlePlayerChat ignores chat when not in WAITING state")
    void handlePlayerChatInIdleReturnsIrrelevant() {
        IdleBrain.IdleResponse r = idleBrain.handlePlayerChat("好");
        assertEquals(IdleBrain.IdleResponse.Type.IRRELEVANT, r.type());
    }

    @Test
    @DisplayName("'帮' single char in AFFIRMATIVE_WORDS is matched by contains()")
    void singleCharBangMatchesTooBroadly() {
        idleBrain.forceSuggest();
        idleBrain.handlePlayerChat("好的");
        idleBrain.resetIdle();

        idleBrain.forceSuggest();
        IdleBrain.IdleResponse r = idleBrain.handlePlayerChat("帮忙搬一下木板");
        assertEquals(IdleBrain.IdleResponse.Type.AFFIRMATIVE, r.type());
    }

    @Test
    @DisplayName("forceSuggest transitions to WAITING and returns template")
    void forceSuggestReturnsTemplate() {
        when(taskManager.getActiveTask()).thenReturn(null);
        when(skillManager.getSkills()).thenReturn(java.util.Map.of());

        IdleBrain.SuggestionTemplate tmpl = idleBrain.forceSuggest();
        assertNotNull(tmpl);
        assertNotNull(tmpl.text());
        assertEquals(IdleBrain.State.WAITING, idleBrain.getState());
    }

    @Test
    @DisplayName("negative words trigger cooldown after affirmative suggestion")
    void negativeWordsTriggerCooldown() {
        when(taskManager.getActiveTask()).thenReturn(null);
        when(skillManager.getSkills()).thenReturn(java.util.Map.of());

        idleBrain.forceSuggest();
        IdleBrain.IdleResponse r = idleBrain.handlePlayerChat("不用了谢谢");
        assertEquals(IdleBrain.IdleResponse.Type.NEGATIVE, r.type());
        assertEquals(IdleBrain.State.COOLDOWN, idleBrain.getState());
    }

    @Test
    @DisplayName("resetIdle returns to IDLE and clears current suggestion")
    void resetIdleWorks() {
        when(taskManager.getActiveTask()).thenReturn(null);
        when(skillManager.getSkills()).thenReturn(java.util.Map.of());

        idleBrain.forceSuggest();
        idleBrain.resetIdle();
        assertEquals(IdleBrain.State.IDLE, idleBrain.getState());
    }

    @Test
    @DisplayName("irrelevant chat in WAITING resets to IDLE")
    void irrelevantChatResetsToIdle() {
        when(taskManager.getActiveTask()).thenReturn(null);
        when(skillManager.getSkills()).thenReturn(java.util.Map.of());

        idleBrain.forceSuggest();
        IdleBrain.IdleResponse r = idleBrain.handlePlayerChat("今天天气不错");
        assertEquals(IdleBrain.IdleResponse.Type.IRRELEVANT, r.type());
        assertEquals(IdleBrain.State.IDLE, idleBrain.getState());
    }

    @Test
    @DisplayName("affirmative words trigger task creation")
    void affirmativeWordsCreateTask() {
        when(taskManager.getActiveTask()).thenReturn(null);
        when(skillManager.getSkills()).thenReturn(java.util.Map.of());

        idleBrain.forceSuggest();
        IdleBrain.IdleResponse r = idleBrain.handlePlayerChat("好的去吧");
        assertEquals(IdleBrain.IdleResponse.Type.AFFIRMATIVE, r.type());
        assertNotNull(r.taskGoal());
        assertEquals(IdleBrain.State.IDLE, idleBrain.getState());
    }
}
