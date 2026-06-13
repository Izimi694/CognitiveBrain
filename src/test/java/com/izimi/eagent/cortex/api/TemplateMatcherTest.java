package com.izimi.eagent.cortex.api;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.CortexAPI;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.cortex.api.TemplateManager.TemplateType;
import com.izimi.eagent.cortex.chat.LocalChatHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateMatcherTest {

    @Mock
    private BotContext botCtx;

    @Mock
    private WorldContext worldCtx;

    @Mock
    private CortexAPI cortexAPI;

    @Mock
    private LocalChatHandler chatHandler;

    private TemplateMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new TemplateMatcher();
        lenient().when(worldCtx.cortex()).thenReturn(cortexAPI);
    }

    @Test
    @DisplayName("null returns null")
    void nullMessage() {
        assertNull(matcher.match(null, botCtx, worldCtx));
    }

    @Test
    @DisplayName("blank returns null")
    void blankMessage() {
        assertNull(matcher.match("   ", botCtx, worldCtx));
    }

    @Test
    @DisplayName("task pattern: 挖")
    void taskPatternDig() {
        assertSame(TemplateType.TASK_PLAN, matcher.match("挖石头", botCtx, worldCtx));
    }

    @Test
    @DisplayName("task pattern: 打")
    void taskPatternAttack() {
        assertSame(TemplateType.TASK_PLAN, matcher.match("打怪", botCtx, worldCtx));
    }

    @Test
    @DisplayName("task pattern: number+个")
    void taskPatternCount() {
        assertSame(TemplateType.TASK_PLAN, matcher.match("我要5个铁", botCtx, worldCtx));
    }

    @Test
    @DisplayName("reflex pattern: 学")
    void reflexPatternLearn() {
        assertSame(TemplateType.REFLEX_CREATE, matcher.match("学挖石头", botCtx, worldCtx));
    }

    @Test
    @DisplayName("reflex pattern: 如果...就")
    void reflexPatternIfThen() {
        assertSame(TemplateType.REFLEX_CREATE, matcher.match("如果遇到怪物就打", botCtx, worldCtx));
    }

    @Test
    @DisplayName("social pattern: 你好")
    void socialPatternHello() {
        assertSame(TemplateType.CHAT_RESPONSE, matcher.match("你好", botCtx, worldCtx));
    }

    @Test
    @DisplayName("social pattern: 喵")
    void socialPatternMew() {
        assertSame(TemplateType.CHAT_RESPONSE, matcher.match("喵", botCtx, worldCtx));
    }

    @Test
    @DisplayName("clarification pattern: 怎么")
    void clarificationPatternHow() {
        assertSame(TemplateType.CLARIFICATION, matcher.match("怎么做剑", botCtx, worldCtx));
    }

    @Test
    @DisplayName("clarification pattern: ?")
    void clarificationPatternQuestionMark() {
        assertSame(TemplateType.CLARIFICATION, matcher.match("这是什么？", botCtx, worldCtx));
    }

    @Test
    @DisplayName("concrete noun triggers TASK_PLAN")
    void concreteNounTaskPlan() {
        assertSame(TemplateType.TASK_PLAN, matcher.match("铁", botCtx, worldCtx));
    }

    @Test
    @DisplayName("unknown text falls back to CHAT_RESPONSE")
    void unknownFallback() {
        assertSame(TemplateType.CHAT_RESPONSE, matcher.match("呵呵", botCtx, worldCtx));
    }

    @Test
    @DisplayName("localChatHandler interception returns null")
    void localChatHandlerIntercepts() {
        when(cortexAPI.chatHandler()).thenReturn(chatHandler);
        when(chatHandler.canHandle("custom_cmd")).thenReturn(true);
        assertNull(matcher.match("custom_cmd", botCtx, worldCtx));
    }
}
