package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.nio.file.Path;
import java.util.*;

public class OneShotAlarmSystem {

    private final UUID botId;
    private final List<AlarmEntry> alarms = new ArrayList<>();
    private static final double DEFAULT_CONFIDENCE = 1.0;

    public OneShotAlarmSystem(UUID botId) {
        this.botId = botId;
        load();
    }

    public void labelEntity(String entityMatcher, AlarmType type, String action, String source) {
        String alarmId = "alarm_" + entityMatcher.replace(":", "_").replace("*", "any");
        alarms.removeIf(a -> a.alarmId().equals(alarmId));

        AlarmEntry entry = new AlarmEntry(alarmId, entityMatcher, type, action,
                DEFAULT_CONFIDENCE, true, source, System.currentTimeMillis(), 0, 0L);
        alarms.add(entry);
        save();
        AIPlayerMod.LOGGER.info("[Alarm] 已标记: {} -> {} ({})", alarmId, type, action);
    }

    public AlarmEntry matchNearest(ServerPlayerEntity bot) {
        if (bot == null || alarms.isEmpty()) return null;
        ServerWorld world = bot.getServerWorld();
        if (world == null) return null;

        for (AlarmEntry alarm : alarms) {
            if (!alarm.onceLabeled()) continue;

            var entities = world.getEntitiesByClass(LivingEntity.class,
                    bot.getBoundingBox().expand(12.0),
                    e -> e.isAlive() && e != bot);

            for (var entity : entities) {
                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (matches(entityId, alarm.matcher())) {
                    double dist = bot.squaredDistanceTo(entity);
                    if (dist < 100.0) {
                        var updated = alarm.trigger();
                        alarms.replaceAll(a -> a.alarmId().equals(alarm.alarmId()) ? updated : a);
                        return updated;
                    }
                }
            }
        }
        return null;
    }

    public boolean hasThreatMatchNearby(ServerPlayerEntity bot) {
        AlarmEntry match = matchNearest(bot);
        return match != null && match.type() == AlarmType.THREAT;
    }

    public List<AlarmEntry> all() {
        return new ArrayList<>(alarms);
    }

    private boolean matches(String entityId, String matcher) {
        if (matcher.startsWith("*") && matcher.endsWith("*")) {
            return entityId.contains(matcher.substring(1, matcher.length() - 1));
        }
        return entityId.equals(matcher);
    }

    private void save() {
        try {
            JsonUtil.writeToFileSafeAtomic(getPath(), alarms);
        } catch (Exception e) {
            AIPlayerMod.LOGGER.debug("[Alarm] save skipped: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            Path path = getPath();
            if (java.nio.file.Files.exists(path)) {
                List<Map<String, Object>> raw = JsonUtil.readFromFileSafe(path, List.class);
                if (raw != null) {
                    alarms.clear();
                    for (Map<String, Object> map : raw) {
                        alarms.add(AlarmEntry.fromMap(map));
                    }
                }
            }
        } catch (Exception e) {
            AIPlayerMod.LOGGER.debug("[Alarm] load skipped: {}", e.getMessage());
        }
    }

    private Path getPath() {
        return FileUtil.getBotAlarmsDir(botId).resolve("alarms.json");
    }

    public enum AlarmType { THREAT, SAFE, WARNING }

    public record AlarmEntry(
            String alarmId,
            String matcher,
            AlarmType type,
            String action,
            double confidence,
            boolean onceLabeled,
            String source,
            long createdAt,
            int triggerCount,
            Long lastTriggered
    ) {
        public AlarmEntry trigger() {
            return new AlarmEntry(alarmId, matcher, type, action, confidence,
                    onceLabeled, source, createdAt, triggerCount + 1, System.currentTimeMillis());
        }

        static AlarmEntry fromMap(Map<String, Object> map) {
            String alarmId = (String) map.get("alarmId");
            String matcher = (String) map.get("matcher");
            AlarmType type = AlarmType.valueOf((String) map.getOrDefault("type", "THREAT"));
            String action = (String) map.getOrDefault("action", "flee");
            double confidence = ((Number) map.getOrDefault("confidence", 1.0)).doubleValue();
            boolean onceLabeled = (Boolean) map.getOrDefault("onceLabeled", true);
            String source = (String) map.getOrDefault("source", "UNKNOWN");
            long createdAt = ((Number) map.getOrDefault("createdAt", 0L)).longValue();
            int triggerCount = ((Number) map.getOrDefault("triggerCount", 0)).intValue();
            Long lastTriggered = map.containsKey("lastTriggered")
                    ? ((Number) map.get("lastTriggered")).longValue() : null;
            return new AlarmEntry(alarmId, matcher, type, action, confidence,
                    onceLabeled, source, createdAt, triggerCount, lastTriggered);
        }
    }
}
