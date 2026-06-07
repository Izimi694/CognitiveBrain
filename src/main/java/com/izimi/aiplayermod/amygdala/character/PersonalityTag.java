package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class PersonalityTag {
    public List<String> tags = new ArrayList<>();
    public long lastUpdated = 0;

    public PersonalityTag() {}

    public PersonalityTag(List<String> tags) {
        this.tags = new ArrayList<>(tags);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void updateTags(List<String> newTags) {
        this.tags = new ArrayList<>(newTags);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void mergeTags(List<String> newTags, double reinforceStrength) {
        for (String tag : newTags) {
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) continue;
            if (!tags.contains(trimmed)) {
                tags.add(trimmed);
            }
        }
        this.lastUpdated = System.currentTimeMillis();
    }

    public String format() {
        if (tags.isEmpty()) return "暂无标签";
        return String.join(", ", tags);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public static PersonalityTag load() {
        try {
            PersonalityTag pt = JsonUtil.readFromFileSafe(
                    FileUtil.getPersonalityTagsPath(), PersonalityTag.class);
            if (pt != null) return pt;
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[PersonalityTag] 加载失败, 使用默认: {}", e.getMessage());
        }
        return new PersonalityTag();
    }

    public void save() {
        try {
            JsonUtil.writeToFileAtomic(FileUtil.getPersonalityTagsPath(), this);
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[PersonalityTag] 保存失败: {}", e.getMessage());
        }
    }
}
