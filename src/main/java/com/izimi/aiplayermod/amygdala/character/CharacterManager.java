package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.util.*;

public class CharacterManager {
    private final ModConfig config;
    private final List<Preference> preferences = new ArrayList<>();
    private long lastUpdated = 0;

    public CharacterManager(ModConfig config) {
        this.config = config;
        load();
    }

    public List<Preference> getPreferences() {
        return Collections.unmodifiableList(preferences);
    }

    public Preference getPreference(String target) {
        for (Preference pref : preferences) {
            if (pref.target.equalsIgnoreCase(target)) return pref;
        }
        return null;
    }

    public void updatePreference(String target, double delta, String origin) {
        Preference existing = getPreference(target);
        if (existing != null) {
            existing.valence = Math.max(-1.0, Math.min(1.0, existing.valence + delta));
            existing.reinforcementCount++;
            existing.origin = origin;
        } else {
            Preference newPref = new Preference(target,
                    Math.max(-1.0, Math.min(1.0, delta)),
                    origin);
            preferences.add(newPref);
        }
        lastUpdated = System.currentTimeMillis() / 1000;
        save();
        AIPlayerMod.LOGGER.info("[CharacterManager] 偏好更新: {} -> delta={} origin={}", target, delta, origin);
    }

    public void evolvePreferences(Map<String, Double> behaviorUpdates,
                                  Map<String, Double> commandUpdates,
                                  Map<String, Double> chatUpdates) {
        for (Map.Entry<String, Double> entry : behaviorUpdates.entrySet()) {
            updatePreference(entry.getKey(), entry.getValue() * config.behaviorWeight, "behavior");
        }
        for (Map.Entry<String, Double> entry : commandUpdates.entrySet()) {
            updatePreference(entry.getKey(), entry.getValue() * config.commandWeight, "command");
        }
        for (Map.Entry<String, Double> entry : chatUpdates.entrySet()) {
            updatePreference(entry.getKey(), entry.getValue() * config.chatWeight, "chat");
        }
    }

    public Map<String, Double> getPreferenceMap() {
        Map<String, Double> map = new HashMap<>();
        for (Preference pref : preferences) {
            map.put(pref.target, pref.valence);
        }
        return map;
    }

    public boolean hasEvolvedPast(String target, double threshold) {
        Preference pref = getPreference(target);
        return pref != null && pref.valence >= threshold && pref.reinforcementCount >= config.preferenceEvolutionThreshold;
    }

    public void triggerStressEvolution() {
        if (preferences.isEmpty()) return;

        Random random = new Random();
        int changeCount = random.nextInt(3) + 1;
        for (int i = 0; i < changeCount && i < preferences.size(); i++) {
            Preference pref = preferences.get(random.nextInt(preferences.size()));
            double delta = (random.nextDouble() * 0.2) - 0.05;
            pref.valence = Math.max(-1.0, Math.min(1.0, pref.valence + delta));
            pref.reinforcementCount++;
            AIPlayerMod.LOGGER.info("[CharacterManager] 压力触发偏好修改: {} -> {}", pref.target, String.format("%.2f", pref.valence));
        }
        save();
    }

    private void load() {
        var container = JsonUtil.readFromFileSafe(FileUtil.getPreferencesPath(), PreferenceContainer.class);
        if (container != null && container.preferences != null) {
            preferences.clear();
            preferences.addAll(container.preferences);
            lastUpdated = container.lastUpdated;
        }
    }

    private void save() {
        PreferenceContainer container = new PreferenceContainer();
        container.preferences = new ArrayList<>(preferences);
        container.lastUpdated = lastUpdated;
        JsonUtil.writeToFileSafeAtomic(FileUtil.getPreferencesPath(), container);
    }

    private static class PreferenceContainer {
        public List<Preference> preferences;
        public long lastUpdated;
    }
}
