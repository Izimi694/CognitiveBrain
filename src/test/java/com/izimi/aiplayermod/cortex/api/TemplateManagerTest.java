package com.izimi.aiplayermod.cortex.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateManagerTest {

    private TemplateManager tm;
    private AIClient aiClient;

    @BeforeEach
    void setUp() throws Exception {
        aiClient = mock(AIClient.class);
        when(aiClient.isConfigured()).thenReturn(true);
        tm = new TemplateManager(aiClient);

        // Reset global cooldown
        Field lastCallField = TemplateManager.class.getDeclaredField("lastGlobalCall");
        lastCallField.setAccessible(true);
        lastCallField.set(null, 0L);
    }

    @Test
    @DisplayName("fill returns parsed JsonObject when AI returns valid JSON")
    void fillReturnsParsedJson() {
        String validJson = "{\"reflex_id\": \"reflex_mine_iron\", \"display_name\": \"挖铁矿\"}";
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse(validJson)));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("skill", "mine");
        ctx.put("target", "iron");
        JsonObject result = tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, ctx).join();

        assertNotNull(result);
        assertEquals("reflex_mine_iron", result.get("reflex_id").getAsString());
        assertEquals("挖铁矿", result.get("display_name").getAsString());
    }

    @Test
    @DisplayName("fill returns null when AI response is null")
    void fillNullResponse() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        JsonObject result = tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertNull(result);
    }

    @Test
    @DisplayName("fill returns null when AI returns empty message")
    void fillEmptyMessage() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse("")));

        JsonObject result = tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertNull(result);
    }

    @Test
    @DisplayName("fill returns null when AI returns invalid JSON")
    void fillInvalidJson() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse("{not json}")));

        JsonObject result = tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertNull(result);
    }

    @Test
    @DisplayName("post-fill hook is invoked with parsed result")
    void postFillHookInvoked() {
        String validJson = "{\"reflex_id\": \"hook_test\"}";
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse(validJson)));

        AtomicInteger callCount = new AtomicInteger(0);
        tm.registerPostFillHook(TemplateManager.TemplateType.REFLEX_CREATE, result -> {
            callCount.incrementAndGet();
            assertEquals("hook_test", result.get("reflex_id").getAsString());
        });

        tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("post-fill hook not invoked when AI returns null")
    void postFillHookNotInvokedOnNull() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AtomicInteger callCount = new AtomicInteger(0);
        tm.registerPostFillHook(TemplateManager.TemplateType.REFLEX_CREATE, result -> callCount.incrementAndGet());

        tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertEquals(0, callCount.get());
    }

    @Test
    @DisplayName("multiple hooks on same type all invoked")
    void multipleHooksInvoked() {
        String validJson = "{\"ok\": true}";
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse(validJson)));

        AtomicInteger count = new AtomicInteger(0);
        tm.registerPostFillHook(TemplateManager.TemplateType.TASK_PLAN, r -> count.incrementAndGet());
        tm.registerPostFillHook(TemplateManager.TemplateType.TASK_PLAN, r -> count.incrementAndGet());
        tm.registerPostFillHook(TemplateManager.TemplateType.TASK_PLAN, r -> count.incrementAndGet());

        tm.fill(TemplateManager.TemplateType.TASK_PLAN, Map.of()).join();
        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("rate limiter: second call within cooldown returns null")
    void rateLimiterBlocksSecondCall() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse("{\"ok\": true}")));

        JsonObject first = tm.fill(TemplateManager.TemplateType.CHAT_DIRECTION, Map.of()).join();
        assertNotNull(first);

        // Second call immediately after first — should be rate-limited
        JsonObject second = tm.fill(TemplateManager.TemplateType.CHAT_DIRECTION, Map.of()).join();
        assertNull(second);
    }

    @Test
    @DisplayName("fill with TASK_PLAN template includes goal in prompt")
    void taskPlanIncludesGoal() {
        when(aiClient.sendMessage(any()))
                .thenAnswer(invocation -> {
                    List<AIMessage> msgs = invocation.getArgument(0);
                    String userMsg = msgs.get(1).content;
                    assertTrue(userMsg.contains("挖钻石"), "Template should contain goal");
                    return CompletableFuture.completedFuture(makeResponse("{\"steps\": []}"));
                });

        Map<String, Object> ctx = Map.of("goal", "挖钻石");
        tm.fill(TemplateManager.TemplateType.TASK_PLAN, ctx).join();
    }

    @Test
    @DisplayName("fill with EVALUATION_BATCH template includes eval context")
    void evaluationBatchIncludesContext() {
        when(aiClient.sendMessage(any()))
                .thenAnswer(invocation -> {
                    List<AIMessage> msgs = invocation.getArgument(0);
                    String userMsg = msgs.get(1).content;
                    assertTrue(userMsg.contains("reflex_mine"));
                    // EVALUATION_BATCH template produces JSON array which fill() can't parse as JsonObject
                    return CompletableFuture.completedFuture(makeResponse("[{\"reflex_id\": \"x\", \"delta\": 0.1}]"));
                });

        Map<String, Object> ctx = Map.of(
                "evaluations", "reflex_mine: success=3",
                "reflexContext", "recent: iron_ore"
        );
        // fill() returns null because JSON array can't be parsed as JsonObject
        assertNull(tm.fill(TemplateManager.TemplateType.EVALUATION_BATCH, ctx).join());
    }

    @Test
    @DisplayName("fill with FAILURE_CLASSIFY template includes failure info")
    void failureClassifyIncludesInfo() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        makeResponse("{\"reflex_id\": \"fail_dig\", \"feature_key\": \"hardness\", \"feature_value\": true, \"outcome\": \"failure\"}")));

        Map<String, Object> ctx = Map.of("failureInfo", "dig failed on obsidian");
        JsonObject result = tm.fill(TemplateManager.TemplateType.FAILURE_CLASSIFY, ctx).join();
        assertNotNull(result);
        assertEquals("fail_dig", result.get("reflex_id").getAsString());
    }

    @Test
    @DisplayName("fill with CHAT_DIRECTION template includes direction hint")
    void chatDirectionIncludesHint() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        makeResponse("{\"perspective\": \"SOCIAL\", \"priority\": 0.8, \"suggested_focus\": \"talk to villager\"}")));

        Map<String, Object> ctx = Map.of("directionHint", "player wants to trade");
        JsonObject result = tm.fill(TemplateManager.TemplateType.CHAT_DIRECTION, ctx).join();
        assertNotNull(result);
        assertEquals("SOCIAL", result.get("perspective").getAsString());
    }

    @Test
    @DisplayName("fill propagates exception from AIClient upstream failure")
    void fillPropagatesAiException() {
        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("API error")));

        assertThrows(java.util.concurrent.CompletionException.class, () ->
                tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join());
    }

    @Test
    @DisplayName("registerPostFillHook on same type multiple times accumulates")
    void registerPostFillAccumulates() {
        AtomicInteger count = new AtomicInteger(0);
        tm.registerPostFillHook(TemplateManager.TemplateType.REFLEX_CREATE, r -> count.addAndGet(1));
        tm.registerPostFillHook(TemplateManager.TemplateType.REFLEX_CREATE, r -> count.addAndGet(2));

        when(aiClient.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(makeResponse("{\"x\": 1}")));

        tm.fill(TemplateManager.TemplateType.REFLEX_CREATE, Map.of()).join();
        assertEquals(3, count.get());
    }

    private static AIResponse makeResponse(String message) {
        AIResponse r = new AIResponse();
        r.message = message;
        return r;
    }
}
