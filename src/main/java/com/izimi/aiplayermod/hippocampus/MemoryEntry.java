package com.izimi.aiplayermod.hippocampus;

import java.util.List;
import java.util.Map;

public class MemoryEntry {
    public String id;
    public String summary;
    public List<String> keyLearnings;
    public List<String> relatedSkills;
    public Map<String, Double> preferencesUpdated;
    public long timestamp;
    public int gameDay;

    public MemoryEntry() {}

    public MemoryEntry(String id, String summary) {
        this.id = id;
        this.summary = summary;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getSummary() { return summary; }
    public long getTimestamp() { return timestamp; }
    public int getGameDay() { return gameDay; }
}
