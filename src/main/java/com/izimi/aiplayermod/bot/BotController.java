package com.izimi.aiplayermod.bot;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.api.*;
import com.izimi.aiplayermod.autonomy.IdleBrain;
import com.izimi.aiplayermod.autonomy.NaiveBayesClassifier;
import com.izimi.aiplayermod.reflexes.InnateReflexes;
import com.izimi.aiplayermod.skill.ConditionedReflex;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.task.TaskExecutor;
import com.izimi.aiplayermod.task.TaskManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BotController {
    private final BotSpawner botSpawner;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final StateManager stateManager;
    private final ConditionedReflex conditionedReflex;
    private final AITaskPlanner aiTaskPlanner;
    private final AIChatHandler aiChatHandler;
    private final AIClient aiClient;
    private final IdleBrain idleBrain;
    private final NaiveBayesClassifier socialClassifier;
    private final InnateReflexes innateReflexes;

    private int tickCounter = 0;
    private int stateSaveInterval = 200;
    private int aiPollInterval = 20;

    public BotController(BotSpawner botSpawner, TaskManager taskManager,
                         TaskExecutor taskExecutor, StateManager stateManager,
                         ConditionedReflex conditionedReflex,
                         AITaskPlanner aiTaskPlanner, AIChatHandler aiChatHandler,
                         AIClient aiClient, IdleBrain idleBrain,
                         NaiveBayesClassifier socialClassifier,
                         InnateReflexes innateReflexes) {
        this.botSpawner = botSpawner;
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
        this.stateManager = stateManager;
        this.conditionedReflex = conditionedReflex;
        this.aiTaskPlanner = aiTaskPlanner;
        this.aiChatHandler = aiChatHandler;
        this.aiClient = aiClient;
        this.idleBrain = idleBrain;
        this.socialClassifier = socialClassifier;
        this.innateReflexes = innateReflexes;
    }

    public void onTick(MinecraftServer server) {
        tickCounter++;

        if (!botSpawner.isSpawned()) return;

        BotPlayer botPlayer = botSpawner.getBot();
        if (botPlayer == null) return;

        ServerPlayerEntity bot = botPlayer.asEntity();

        if (tickCounter % stateSaveInterval == 0) {
            stateManager.saveState(bot);
        }

        if (tickCounter % aiPollInterval == 0 && aiClient != null && aiClient.isConfigured()) {
            pollAIResults(bot);
        }

        // Priority 0: Safety reflexes — always first, every tick, 0 API
        if (innateReflexes != null) {
            InnateReflexes.ReflexResult safety = innateReflexes.checkSafety(bot);
            if (safety.handled()) {
                AIPlayerMod.LOGGER.debug("[BotController] P0安全反射: {}", safety.reason());
                return;
            }
        }

        // Priority 1: Player task → conditioned reflex / trial & error
        var activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditionedReflex.match(activeTask);
            if (reflexSkill != null) {
                conditionedReflex.executeReflex(reflexSkill, bot);
            } else {
                taskExecutor.executeTask(bot, activeTask);
            }
            return;
        }

        // Priority 2: IdleBrain — idle >30s, send suggestion, 0 API
        if (idleBrain != null) {
            IdleBrain.SuggestionTemplate suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + suggestion.text()));
                return;
            }
        }

        // Priority 3: Social mirror — naive bayes crowd wisdom, 0 API
        if (trySocialMirror(bot)) return;

        // Priority 4: Non-safety reflexes — collect, avoid lava, shelter, 0 API
        if (innateReflexes != null) {
            InnateReflexes.ReflexResult nonSafety = innateReflexes.checkNonSafety(bot);
            if (nonSafety.handled()) {
                AIPlayerMod.LOGGER.debug("[BotController] P4非安全反射: {}", nonSafety.reason());
                return;
            }
        }

        // Priority 5: Idle animation — look around, random wander, 0 API
        if (innateReflexes != null) {
            innateReflexes.doIdleAnimation(bot);
        }
    }

    private boolean trySocialMirror(ServerPlayerEntity bot) {
        if (socialClassifier == null) return false;

        var socialObs = AIPlayerMod.getSocialObserver();
        var familiarity = AIPlayerMod.getFamiliarityTracker();
        if (socialObs == null || familiarity == null) return false;

        int nearbyCount = socialObs.getNearbyPlayerCount();
        if (nearbyCount == 0) return false;

        var windows = socialObs.getPlayerWindows();
        NaiveBayesClassifier.Classification result = socialClassifier.classify(windows, familiarity);

        if (result == null || !result.meetsThreshold()) return false;

        String taskGoal = result.toTaskGoal();
        taskManager.createTask(taskGoal);
        bot.sendMessage(Text.literal("§b[AI_Assistant] §7(观察" + nearbyCount +
                "名玩家，置信度" + String.format("%.2f", result.confidence()) + ")"));
        return true;
    }

    private void pollAIResults(ServerPlayerEntity bot) {
        var chatHandler = AIPlayerMod.getAiChatHandler();
        if (chatHandler != null && chatHandler.hasPendingResponse()) {
            AIResponse response = chatHandler.pollResponse();
            if (response != null && response.isChat() && !response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
                if (response.getMemoryNote() != null && !response.getMemoryNote().isEmpty()) {
                    var mem = AIPlayerMod.getMemoryManager();
                    if (mem != null) {
                        var entry = new com.izimi.aiplayermod.memory.MemoryEntry(
                                "mem_chat_" + System.currentTimeMillis(),
                                response.getMemoryNote());
                        mem.generateMemory(new com.izimi.aiplayermod.task.Task(
                                "chat", "instant", response.getMemoryNote()));
                    }
                }
                if (response.personalityDelta != null && !response.personalityDelta.isEmpty()) {
                    var charMgr = AIPlayerMod.getCharacterManager();
                    if (charMgr != null) {
                        charMgr.evolvePreferences(
                                new java.util.HashMap<>(),
                                new java.util.HashMap<>(),
                                response.personalityDelta);
                    }
                }
            }
        }

        var taskPlanner = AIPlayerMod.getAiTaskPlanner();
        if (taskPlanner != null && taskPlanner.hasPendingResult()) {
            AIResponse response = taskPlanner.pollResult();
            if (response != null) {
                processTaskResponse(bot, response);
            }
        }
    }

    private void processTaskResponse(ServerPlayerEntity bot, AIResponse response) {
        if (response.isChat()) {
            if (!response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
            }
            return;
        }

        if (response.isAction()) {
            String goal = response.getAction() + " " + (response.getSkill() != null ? response.getSkill() : "");
            if (response.params != null && response.params.target != null) {
                goal = response.params.target;
                if (response.params.amount > 0) {
                    goal = "挖" + response.params.amount + "个" + goal;
                }
            }
            if (!"wait".equals(response.getAction()) && !goal.isEmpty()) {
                taskManager.createTask(goal);
                if (!response.getMessage().isEmpty()) {
                    bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
                }
            }
        }
    }
}
