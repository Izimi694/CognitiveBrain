package com.izimi.aiplayermod.amygdala.learning;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.brainstem.skill.SkillManager;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.util.*;

public class LearningSystem {

    private static final long WINDOW_MS = 60_000;
    private static final long SCAN_INTERVAL_MS = 60_000;
    private static final int SOLIDIFY_THRESHOLD = 3;

    private final ConditionedReflex conditionedReflex;
    private final SkillManager skillManager;
    private final Deque<BehaviorEvent> eventWindow = new ArrayDeque<>();
    private long lastScanTime = 0;

    public LearningSystem(ConditionedReflex conditionedReflex, SkillManager skillManager) {
        this.conditionedReflex = conditionedReflex;
        this.skillManager = skillManager;
    }

    public void onEvent(BehaviorEvent event) {
        eventWindow.addLast(event);
        pruneWindow();

        long now = System.currentTimeMillis();
        if (now - lastScanTime >= SCAN_INTERVAL_MS) {
            scanWindow();
            lastScanTime = now;
        }
    }

    private void pruneWindow() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!eventWindow.isEmpty() && eventWindow.peekFirst().timestamp() < cutoff) {
            eventWindow.pollFirst();
        }
    }

    private void scanWindow() {
        if (eventWindow.size() < SOLIDIFY_THRESHOLD) return;

        List<BehaviorEvent> snapshot = new ArrayList<>(eventWindow);

        Map<String, List<BehaviorEvent>> byCategory = new HashMap<>();
        for (BehaviorEvent e : snapshot) {
            String category = CategoryMapper.getCategory(e.action(), e.target());
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<BehaviorEvent>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<BehaviorEvent> events = entry.getValue();

            if (events.size() < SOLIDIFY_THRESHOLD) {
                saveAsTrial(category, events);
                continue;
            }

            String representativeTarget = extractRepresentativeTarget(events);
            List<String> nearbyBlocks = extractContributedTargets(events);
            List<String> inventory = extractInventory(events);
            String timeOfDay = events.get(0).timeOfDay();

            String skillId = "reflex_" + category;

            if (skillManager.getSkill(skillId) != null) {
                conditionedReflex.incrementProficiency(skillId);
                AIPlayerMod.LOGGER.info("[LearningSystem] 分类强化: {} → proficiency+ ({}次观察)",
                        CategoryMapper.getCategoryName(category), events.size());
            } else {
                ObservedSequence sequence = ObservedSequence.create(representativeTarget, events,
                        nearbyBlocks, inventory, timeOfDay);
                conditionedReflex.solidifySequence(sequence, category);
                AIPlayerMod.LOGGER.info("[LearningSystem] 分类固化: {} → {} ({}次观察)",
                        CategoryMapper.getCategoryName(category), skillId, events.size());
            }
        }
    }

    private void saveAsTrial(String category, List<BehaviorEvent> events) {
        if (events.isEmpty()) return;

        String target = extractRepresentativeTarget(events);
        ObservedSequence sequence = ObservedSequence.create(target, events,
                List.of(), List.of(), events.get(0).timeOfDay());

        String filename = "observed_" + sanitize(category) + "_"
                + System.currentTimeMillis() / 1000 + ".json";
        JsonUtil.writeToFileSafeAtomic(FileUtil.getTrialsDir().resolve(filename), sequence);

        AIPlayerMod.LOGGER.debug("[LearningSystem] 保存trial: {} ({}次观察)",
                CategoryMapper.getCategoryName(category), events.size());
    }

    public int getWindowSize() {
        return eventWindow.size();
    }

    private String extractRepresentativeTarget(List<BehaviorEvent> events) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BehaviorEvent e : events) {
            counts.merge(e.target(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(events.get(0).target());
    }

    private List<String> extractContributedTargets(List<BehaviorEvent> events) {
        Set<String> targets = new LinkedHashSet<>();
        for (BehaviorEvent e : events) {
            targets.add(e.target());
        }
        return new ArrayList<>(targets);
    }

    private List<String> extractInventory(List<BehaviorEvent> events) {
        List<String> items = new ArrayList<>();
        for (BehaviorEvent e : events) {
            if (e.heldItem() != null && !"unknown".equals(e.heldItem())
                    && !items.contains(e.heldItem())) {
                items.add(e.heldItem());
            }
        }
        return items;
    }

    private String sanitize(String s) {
        return s.replace(":", "_").replace("/", "_").replace("\\", "_");
    }
}
