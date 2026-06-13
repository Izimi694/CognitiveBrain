package com.izimi.eagent.cortex.chat;

import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.cortex.chat.ChatSessionManager.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Deque;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChatSessionManagerTest {

    @Mock
    private BayesianModule bayesianModule;

    private ChatSessionManager newManager() {
        lenient().when(bayesianModule.getCurrentDirection()).thenReturn("测试方向");
        return new ChatSessionManager(bayesianModule, UUID.randomUUID());
    }

    @Test
    @DisplayName("empty window buildPrompt includes direction and goal")
    void buildPromptEmptyWindow() {
        ChatSessionManager manager = newManager();
        manager.setCurrentGoal("测试目标");
        String prompt = manager.buildPrompt();
        assertTrue(prompt.contains("测试方向"));
        assertTrue(prompt.contains("测试目标"));
        assertFalse(prompt.contains("[最近对话]"));
    }

    @Test
    @DisplayName("addMessage appends and buildPrompt includes messages")
    void addMessageAndBuildPrompt() {
        ChatSessionManager manager = newManager();
        manager.addMessage(new Message("player", "你好"));
        manager.addMessage(new Message("bot", "你好"));
        String prompt = manager.buildPrompt();
        assertTrue(prompt.contains("player: 你好"));
        assertTrue(prompt.contains("bot: 你好"));
        assertEquals(2, manager.getWindowSize());
    }

    @Test
    @DisplayName("window never exceeds MAX_WINDOW_SIZE of 6")
    void windowMaxSize() {
        ChatSessionManager manager = newManager();
        for (int i = 0; i < 9; i++) {
            manager.addMessage(new Message("user", "msg" + i));
        }
        assertEquals(6, manager.getWindowSize());
        Deque<Message> window = manager.getWindow();
        assertEquals("msg3", window.getFirst().content());
        assertEquals("msg8", window.getLast().content());
    }

    @Test
    @DisplayName("refresh clears window")
    void refreshClearsWindow() {
        ChatSessionManager manager = newManager();
        manager.addMessage(new Message("user", "hello"));
        manager.refresh();
        assertEquals(0, manager.getWindowSize());
        assertTrue(manager.getWindow().isEmpty());
    }

    @Test
    @DisplayName("setCurrentGoal with null sets empty string")
    void setCurrentGoalNull() {
        ChatSessionManager manager = newManager();
        manager.setCurrentGoal(null);
        String prompt = manager.buildPrompt();
        assertTrue(prompt.contains("[当前目标] "));
    }

    @Test
    @DisplayName("getWindow returns copy not reference")
    void getWindowReturnsCopy() {
        ChatSessionManager manager = newManager();
        manager.addMessage(new Message("user", "hello"));
        Deque<Message> window = manager.getWindow();
        window.addFirst(new Message("hacker", "injected"));
        assertEquals(1, manager.getWindowSize());
    }

    @Test
    @DisplayName("bayesianModule null fallback")
    void nullBayesianModuleDirection() {
        ChatSessionManager noBayes = new ChatSessionManager(null, UUID.randomUUID());
        String prompt = noBayes.buildPrompt();
        assertTrue(prompt.contains("暂无经验数据"));
    }
}
