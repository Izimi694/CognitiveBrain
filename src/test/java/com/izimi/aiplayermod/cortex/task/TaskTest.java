package com.izimi.aiplayermod.cortex.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    @DisplayName("extractCount: '挖10个铁矿' → 10")
    void extractCountTen() {
        assertEquals(10, Task.extractCount("挖10个铁矿"));
    }

    @Test
    @DisplayName("extractCount: '挖矿' → 1 (default)")
    void extractCountDefault() {
        assertEquals(1, Task.extractCount("挖矿"));
    }

    @Test
    @DisplayName("extractCount: '挖23个钻石' → 23")
    void extractCountTwentyThree() {
        assertEquals(23, Task.extractCount("挖23个钻石"));
    }

    @Test
    @DisplayName("extractCount: empty string → 1")
    void extractCountEmpty() {
        assertEquals(1, Task.extractCount(""));
    }

    @Test
    @DisplayName("extractCount: no digits → 1")
    void extractCountNoDigits() {
        assertEquals(1, Task.extractCount("探索一下周围"));
    }

    @Test
    @DisplayName("extractAction: 挖矿 → dig")
    void extractActionDig() {
        assertEquals("dig", Task.extractAction("挖10个铁矿"));
        assertEquals("dig", Task.extractAction("砍树"));
        assertEquals("dig", Task.extractAction("采集小麦"));
    }

    @Test
    @DisplayName("extractAction: 打怪 → attack")
    void extractActionAttack() {
        assertEquals("attack", Task.extractAction("打5只僵尸"));
        assertEquals("attack", Task.extractAction("消灭骷髅"));
        assertEquals("attack", Task.extractAction("击败史莱姆"));
    }

    @Test
    @DisplayName("extractAction: 合成 → craft")
    void extractActionCraft() {
        assertEquals("craft", Task.extractAction("合成一把铁镐"));
        assertEquals("craft", Task.extractAction("制作面包"));
    }

    @Test
    @DisplayName("extractAction: 探索 → move")
    void extractActionExplore() {
        assertEquals("move", Task.extractAction("探索周围"));
        assertEquals("move", Task.extractAction("去海边"));
    }

    @Test
    @DisplayName("extractAction: unknown → move (default)")
    void extractActionDefault() {
        assertEquals("move", Task.extractAction("帮我看看"));
    }

    @Test
    @DisplayName("extractTarget removes digits and action verbs")
    void extractTargetRemovesDigitsAndVerbs() {
        assertEquals("铁", Task.extractTarget("挖10个铁矿"));
        assertEquals("僵尸", Task.extractTarget("打5只僵尸"));
        assertEquals("周围", Task.extractTarget("探索周围"));
    }

    @Test
    @DisplayName("extractTarget: no specific target returns goal as-is")
    void extractTargetNoSpecific() {
        assertEquals("逛逛", Task.extractTarget("逛逛"));
    }

    @Test
    @DisplayName("Task.SubTask defaults to pending status")
    void subTaskDefaultsPending() {
        Task.SubTask st = new Task.SubTask("mine_iron", "dig");
        assertEquals("pending", st.status);
        assertEquals(0, st.attemptCount);
    }

    @Test
    @DisplayName("Task.Progress defaults: completedCount=0, targetCount=1")
    void progressDefaults() {
        Task.Progress p = new Task.Progress();
        assertEquals(0, p.completedCount);
        assertEquals(1, p.targetCount);
        assertEquals("init", p.currentStep);
    }

    @Test
    @DisplayName("getProgressSummary formats correctly")
    void getProgressSummary() {
        Task t = new Task("t1", "sustained", "挖10个铁矿");
        t.progress.completedCount = 3;
        t.progress.targetCount = 10;
        t.progress.currentStep = "digging";
        assertEquals("3/10 (digging)", t.getProgressSummary());
    }
}
