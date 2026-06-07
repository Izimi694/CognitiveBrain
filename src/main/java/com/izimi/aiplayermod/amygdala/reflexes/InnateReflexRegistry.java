package com.izimi.aiplayermod.amygdala.reflexes;

import com.google.gson.reflect.TypeToken;
import com.izimi.aiplayermod.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InnateReflexRegistry {

    private final List<InnateReflex> reflexes = new ArrayList<>();
    private final MinecraftReflexEvaluator evaluator;

    public InnateReflexRegistry(MinecraftReflexEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void register(InnateReflex reflex) {
        reflexes.add(reflex);
    }

    public void loadFromJson(Path path) {
        List<InnateReflex> loaded = JsonUtil.readFromFileSafe(path,
                new TypeToken<List<InnateReflex>>(){}.getType());
        if (loaded != null && !loaded.isEmpty()) {
            reflexes.clear();
            reflexes.addAll(loaded);
        }
    }

    public void saveToJson(Path path) {
        JsonUtil.writeToFileSafeAtomic(path, reflexes);
    }

    public void loadDefaults() {
        reflexes.clear();
        register(InnateReflex.create("critical", 0, true,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 2.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 3)),
                new ReflexAction("flee", Map.of("speed", 0.3))));
        register(InnateReflex.create("flee", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 10.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 10)),
                new ReflexAction("flee", Map.of("speed", 0.3))));
        register(InnateReflex.create("eat", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HUNGER_BELOW, 6.0, 0)),
                new ReflexAction("eat", Map.of())));
        register(InnateReflex.create("retreat", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 6.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 20)),
                new ReflexAction("retreat", Map.of("speed", 0.25))));
        register(InnateReflex.create("avoid_lava", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.LAVA_NEARBY, 0.0, 3)),
                new ReflexAction("avoidLava", Map.of("speed", 0.2))));
        register(InnateReflex.create("seek_shelter", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.TIME_OF_DAY, 0.0, 5)),
                new ReflexAction("seekShelter", Map.of("speed", 0.1))));
        register(InnateReflex.create("collect_item", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.ITEM_NEARBY, 0.0, 5)),
                new ReflexAction("collectItem", Map.of("speed", 0.15))));
    }

    public List<InnateReflex> match(ServerPlayerEntity bot) {
        if (bot == null) return List.of();
        return reflexes.stream()
                .filter(InnateReflex::enabled)
                .filter(r -> evaluator.matchesAll(r.triggers(), bot))
                .sorted(Comparator.comparingInt(InnateReflex::priority))
                .collect(Collectors.toList());
    }

    public InnateReflex highest(ServerPlayerEntity bot) {
        return match(bot).stream().findFirst().orElse(null);
    }

    public InnateReflex highest(ServerPlayerEntity bot, int maxPriority) {
        return match(bot).stream()
                .filter(r -> r.priority() <= maxPriority)
                .findFirst().orElse(null);
    }

    public int size() {
        return reflexes.size();
    }

    public List<InnateReflex> all() {
        return new ArrayList<>(reflexes);
    }
}
