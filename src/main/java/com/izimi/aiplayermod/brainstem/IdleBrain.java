package com.izimi.aiplayermod.brainstem;

import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.brainstem.skill.Skill;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.AIPlayerMod;

import java.util.*;

public class IdleBrain {

    public enum State { IDLE, WAITING, COOLDOWN }

    public record SuggestionTemplate(String text, String reflexId) {
        public String defaultGoal() {
            return reflexId != null ? reflexId : "帮忙";
        }
    }

    public record IdleResponse(Type type, String message, String taskGoal) {
        public enum Type { AFFIRMATIVE, NEGATIVE, IRRELEVANT }

        public static IdleResponse affirmative(String message, String taskGoal) {
            return new IdleResponse(Type.AFFIRMATIVE, message, taskGoal);
        }

        public static IdleResponse negative(String message) {
            return new IdleResponse(Type.NEGATIVE, message, null);
        }

        public static IdleResponse irrelevant() {
            return new IdleResponse(Type.IRRELEVANT, null, null);
        }
    }

    private static final long IDLE_THRESHOLD_MS = 30_000;
    private static final long REPLY_WINDOW_MS = 30_000;
    private static final long COOLDOWN_MS = 120_000;
    private static final int HISTORY_SIZE = 10;

    private static final List<String> AFFIRMATIVE_WORDS = List.of(
            "好", "行", "嗯", "可以", "去吧", "需要", "随便", "烦死了",
            "要", "来", "帮我", "帮忙", "帮一下", "搞", "做", "干", "开始", "yes", "ok", "sure", "行吧"
    );

    private static final List<String> NEGATIVE_WORDS = List.of(
            "不用", "不需要", "不要", "别", "算了", "滚蛋", "滚",
            "没你的事", "闭嘴", "安静", "走开", "no", "没事", "没有了"
    );

    private State state = State.IDLE;
    private long idleStartTime = 0;
    private long waitingStartTime = 0;
    private long cooldownEndTime = 0;
    private SuggestionTemplate currentSuggestion = null;
    private int templateRoundRobin = 0;
    private final Deque<String> suggestionHistory = new ArrayDeque<>();

    private final TaskManager taskManager;
    private final SkillManager skillManager;
    private final List<SuggestionTemplate> defaultTemplates;

    public IdleBrain(TaskManager taskManager, SkillManager skillManager) {
        this.taskManager = taskManager;
        this.skillManager = skillManager;
        this.defaultTemplates = initDefaultTemplates();
    }

    public SuggestionTemplate onTick() {
        long now = System.currentTimeMillis();
        boolean hasActiveTask = taskManager.getActiveTask() != null;

        if (hasActiveTask) {
            state = State.IDLE;
            idleStartTime = 0;
            return null;
        }

        switch (state) {
            case IDLE:
                if (idleStartTime == 0) {
                    idleStartTime = now;
                }
                if (now - idleStartTime >= IDLE_THRESHOLD_MS) {
                    SuggestionTemplate tmpl = selectTemplate();
                    if (tmpl != null) {
                        currentSuggestion = tmpl;
                        state = State.WAITING;
                        waitingStartTime = now;
                        return tmpl;
                    }
                }
                break;

            case WAITING:
                if (now - waitingStartTime >= REPLY_WINDOW_MS) {
                    enterCooldown(now);
                }
                break;

            case COOLDOWN:
                if (now >= cooldownEndTime) {
                    state = State.IDLE;
                    idleStartTime = 0;
                }
                break;
        }

        return null;
    }

    public IdleResponse handlePlayerChat(String message) {
        if (state != State.WAITING) {
            return IdleResponse.irrelevant();
        }

        String lower = message.trim().toLowerCase();

        if (isNegative(lower)) {
            enterCooldown(System.currentTimeMillis());
            return IdleResponse.negative("好的，有需要叫我。");
        }

        if (isAffirmative(lower)) {
            String goal = currentSuggestion != null ? currentSuggestion.defaultGoal() : "帮忙";
            resetIdle();
            return IdleResponse.affirmative("好的，我来！", goal);
        }

        resetIdle();
        return IdleResponse.irrelevant();
    }

    public SuggestionTemplate forceSuggest() {
        state = State.IDLE;
        idleStartTime = 0;
        cooldownEndTime = 0;

        SuggestionTemplate tmpl = selectTemplate();
        if (tmpl != null) {
            currentSuggestion = tmpl;
            state = State.WAITING;
            waitingStartTime = System.currentTimeMillis();
        }
        return tmpl;
    }

    public void resetIdle() {
        state = State.IDLE;
        idleStartTime = 0;
        currentSuggestion = null;
    }

    public State getState() {
        return state;
    }

    private SuggestionTemplate selectTemplate() {
        for (Skill skill : skillManager.getSkills().values()) {
            if ("conditioned".equals(skill.getType())) {
                String text = "我现在" + skill.getName() + "已经很熟练了，需要我承包吗？";
                if (!suggestionHistory.contains(text)) {
                    addToHistory(text);
                    return new SuggestionTemplate(text, skill.getSkillId());
                }
            }
        }

        if (!defaultTemplates.isEmpty()) {
            SuggestionTemplate tmpl = defaultTemplates.get(templateRoundRobin % defaultTemplates.size());
            templateRoundRobin++;

            if (suggestionHistory.contains(tmpl.text())) {
                for (int i = 0; i < defaultTemplates.size(); i++) {
                    SuggestionTemplate alt = defaultTemplates.get((templateRoundRobin + i) % defaultTemplates.size());
                    if (!suggestionHistory.contains(alt.text())) {
                        tmpl = alt;
                        break;
                    }
                }
            }

            addToHistory(tmpl.text());
            return tmpl;
        }

        return null;
    }

    private void enterCooldown(long now) {
        state = State.COOLDOWN;
        cooldownEndTime = now + COOLDOWN_MS;
        currentSuggestion = null;
    }

    private void addToHistory(String text) {
        suggestionHistory.addLast(text);
        if (suggestionHistory.size() > HISTORY_SIZE) {
            suggestionHistory.removeFirst();
        }
    }

    private boolean isAffirmative(String msg) {
        for (String word : AFFIRMATIVE_WORDS) {
            if (msg.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNegative(String msg) {
        for (String word : NEGATIVE_WORDS) {
            if (msg.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<SuggestionTemplate> initDefaultTemplates() {
        return new ArrayList<>(List.of(
                new SuggestionTemplate("有什么需要我帮忙的吗？", "帮忙"),
                new SuggestionTemplate("需要我帮忙挖矿吗？", "挖矿"),
                new SuggestionTemplate("需要我帮忙采集资源吗？", "采集资源"),
                new SuggestionTemplate("需要我帮忙打怪吗？", "打怪"),
                new SuggestionTemplate("需要我去探索一下周围吗？", "自由探索"),
                new SuggestionTemplate("有什么任务要交给我吗？", "帮忙")
        ));
    }
}
