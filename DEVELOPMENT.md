# CognitiveBrain — 开发 & 状态

> 本文件记录当前实施状态、路线图、已知问题。架构说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)，设计原理见 [AGENTS.md](./AGENTS.md)。

---

## 1. 当前状态

```
项目阶段: Phase A — BayesianModule 地基完成
Phase A-F 实施中
```

### 完成情况

| Phase | 内容 | 状态 |
|:----:|------|:----:|
| P0-P6 | 基础系统 (IdleBrain/观察/条件反射/记忆/任务/计划) | ✅ |
| Phase 1 | MetaScheduler + BotContext | ✅ |
| Phase 2 | OneShotAlarmSystem | ✅ |
| Phase 2.5 | HormonalSystem | ✅ |
| Phase 3 | LocalTaskDecomposer | ✅ |
| Phase 4 | LocalChatHandler | ✅ |
| Phase 4.5 | MotivationEngine + LLM Gate | ✅ |
| Phase 5 | Urgency + Temporal scaling + CorrelationDetector | ✅ |
| Phase 6 | Multi-bot (BotManager + BotInstance) | ✅ |
| **Phase A** | **BayesianModule 三层存储 + 收敛判断(e)** | ✅ |
| **Phase B** | **错误蒸馏闭环 (ConditionedReflex ↔ BayesianModule)** | ✅ |
| **Phase C** | **三阶段记忆检索 + shouldExplore(e)** | ✅ |
| **Phase D** | **ChatSessionManager + loop 刷新** | ✅ |
| **Phase F** | **激素解耦 hormonal/ 包 + MetaScheduler 时间片(e)** | ✅ |
| **Phase E** | **TemplateManager 模板填空统一入口 + 全局冷却** | ✅ |
| **Phase RP** | **ReflexPackManager 反射包导入/导出/合并** | ✅ |
| **Phase 7** | **繁衍模块 (三规则继承 + trial-first 脚手架)** | ✅ |

---

## 2. 实施路线图

```
Phase A (Bayesian地基) ── ✅
  ├── Phase B (错误蒸馏)    ── ConditionedReflex ↔ BayesianModule
  ├── Phase C (记忆检索)    ── MemoryManager 三阶段 + shouldExplore(e)
  ├── Phase D (ChatSession) ── 6条窗口 + 贝叶斯方向 + loop刷新
  └── Phase F (激素解耦)    ── hormonal/ 独立包 + MetaScheduler 时间片(e)
        ├── Phase E (模板填空) ── TemplateManager 统一 LLM 入口
        └── Phase RP (反射包)  ── ReflexPackManager 导出/导入/合并
Phase 7 (繁衍) ── 三规则继承 (平均+脚手架 trial-first + 突变) + 基因组存档
```

---

## 3. 已知问题

### P-1: MemoryManager.memoriesDir() 无限递归 (line 36)
当 `botId != null` 时，`memoriesDir()` 递归调用自身而不是调用 `FileUtil.getBotMemoriesDir(botId)`。

### P-2: TaskManager.activeTaskPath() 无限递归 (line 32-33)
同上，当 `botId != null` 时递归调用自身。

### P-3: 寻路未接入主循环
AStarPathfinder 存在但未集成到 MetaScheduler 中。

### P-4: 导航使用 velocity-based 移动
`MinecraftActionAdapter` 使用 `setVelocity` 而非正式的寻路 AI。

---

## 4. 构建

```bash
# 环境: JDK 21, Gradle 8+
cd AIPlayerMod-1.21.1-Fabric
.\gradlew.bat build
# 输出: build/libs/ai-player-mod-1.21.1.jar
```

### 部署

1. 将 jar 放入 `mods/` 目录
2. 启动 Fabric 服务端 (Loader 0.19.3+)
3. 配置 API 密钥: `/ai setkey <your-api-key>`
4. 自然语言指令: "帮我挖 10 个铁矿"

---

## 5. 游戏内指令

| 指令 | 功能 |
|------|------|
| `/ai spawn <name>` | 生成指定名字的假人 |
| `/ai despawn [name]` | 移除假人 |
| `/ai list` | 列出所有假人 |
| `/ai bot <name> <cmd>` | 指定假人执行 |
| `/ai status` | 当前任务状态 |
| `/ai model [name]` | 查看/设置模型 |
| `/ai reflexes` | 已学习反射 |
| `/ai setkey <key>` | 设置 API 密钥 |
| `/ai reflexpack export <bot> <name> [noprior]` | 导出反射包 (默认含先验) |
| `/ai reflexpack import <bot> <name> [reset]` | 导入反射包 (合并/冷启动) |
| `/ai reflexpack list` | 列出已导入反射包 |
| `/ai reflexpack delete <name>` | 删除反射包 |
| `/ai help` | 全部指令 |

`@bot_name <消息>` — 精确路由。无 `@` 时路由最近假人。

---

## 6. 源代码结构

```
src/main/java/com/izimi/aiplayermod/
├── AIPlayerMod.java                  DI 入口
├── bayesian/                         BayesianModule + value objects
├── hormonal/                         HormonalSystem (Phase F 移入)
├── cortex/                           前额叶
│   ├── api/                          LLM 接口 + TemplateManager
│   ├── planner/                      Plan/KnowledgeBase/LocalTaskDecomposer
│   ├── chat/                         LocalChatHandler + ChatSessionManager
│   └── task/                         Task/TaskManager/TaskExecutor
├── hippocampus/                      海马体
│   ├── MemoryEntry/MemoryManager/MemoryQuery
│   └── storage/                      HighlightStorage/TrialStorage
├── amygdala/                         杏仁核
│   ├── ConditionedReflex/DispatchReflex/OneShotAlarmSystem
│   ├── SocialObserver/NaiveBayesClassifier/FamiliarityTracker
│   ├── character/                    BehaviorEventHandler/BehaviorStats/EvaluationCycle
│   └── learning/                     BehaviorEvent/ObservedSequence/CategoryMapper/CorrelationDetector
├── brainstem/                        脑干
│   ├── adapter/                      12 原子动作 (MinecraftActionAdapter)
│   ├── bot/                          BotManager/BotInstance/BotSpawner/BotController
│   ├── innate/                       InnateReflexRegistry/4 先天技能
│   ├── scheduler/                    MetaScheduler/MotivationEngine/MetaContext/UrgencyClassifier
│   ├── skill/                        Skill + SkillManager
│   ├── navigation/                   AStarPathfinder (未集成)
│   └── IdleBrain.java
├── command/                          AICommand
├── config/                           ModConfig
├── util/                             FileUtil/JsonUtil
├── log/                              ExecutionLogger
└── state/                            PlayerState/StateManager
```

---

## 7. 运行时数据目录

```
minecraft/ai_memory/
├── config/                全局配置/先天反射 JSON
├── thresholds/            自适应阈值
├── bayesian/              全局共享先验 (shared_prior.json)
├── reflex_packs/          导入/导出的反射包 (*.json)
├── bots/genomes/          死亡Bot基因组存档 (*.json)
└── bots/{bot_uuid}/
    ├── conditioned/       条件反射库 (reflex_*.json)
    ├── alarms/            L1 一次预警
    ├── memory/            高光记忆 (day_*.mem)
    ├── bayesian/          per-bot 后验 (posterior.json)
    ├── bot_params.json    per-bot 参数 (α/β/temperature/gen)
    ├── plans/             任务计划
    ├── evaluations/       玩家评价缓存
    └── execution_logs/    执行日志
```

---

## 8. 测试

| 测试文件 | 数量 | 内容 |
|---------|:---:|------|
| `CategoryMapperTest.java` | 18 | 分类映射 |
| `TaskTest.java` | 11 | 任务拆解 |
| `IdleBrainTest.java` | 11 | 状态机 |
| `ReflexRegistryTest.java` | 15 | 先天反射注册表 |
| `MotivationEngineTest.java` | 19 | 驱力/玻尔兹曼/交叉抑制 |
| `MetaContextStubTest.java` | 25 | MetaContext 查询/门控/环境检测 |
| `BotParamsTest.java` | 8 | 三规则继承/参数范围/变异 |
| **合计** | **~107** | **全部通过** |

**待补充测试：** `BayesianModule`, `TemplateManager`, `ChatSessionManager`
