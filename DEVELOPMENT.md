# CognitiveBrain — 开发 & 状态

> 本文件记录当前实施状态、路线图、已知问题。架构说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)，设计原理见 [AGENTS.md](./AGENTS.md)。

---

## 1. 当前状态

```
项目阶段: Phase 1-2 — 接口解耦 + 依赖注入
Phase 1-2 (Decoupling) ✅ 已完成
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
| **Phase 1 (Decoupling)** | **api/ 接口定义 (WorldContext/BotContext/MetaState/BrainstemAPI/AmygdalaAPI/CortexAPI)** | ✅ |
| **Phase 2 (Decoupling)** | **MetaScheduler 重构 (BotContext+WorldContext+MetaState 注入), CorrelationDetector 解耦** | ✅ |
| **Phase 2.5 (Decoupling)** | **TemplateManagerTest (14 个测试), BayesianModuleTest (16 个测试)** | ✅ |

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

### ~~P-1: MemoryManager.memoriesDir() 无限递归 (line 36)~~ ✅ 已修复
### ~~P-2: TaskManager.activeTaskPath() 无限递归 (line 32-33)~~ ✅ 已修复

### ~~P-3: 寻路未接入主循环~~ ✅ 已修复
`GreedyNavigator` (贪心一步导航) 取代 `AStarPathfinder`，接入 `NavigationController` → `MinecraftActionAdapter.moveTo()`。

### ~~P-4: 导航使用 velocity-based 移动~~ ✅ 已修复
`moveTo()` 每 tick 通过 `GreedyNavigator.getBestStep()` 选最优下一步方向，velocity 层执行。

### ~~P-6: "我替环境判你能不能" — 预判反模式~~ ✅ 已修复
12 处预判逻辑改为"先行动，让环境反馈裁决"：
- `jump()` 不再检查 `isOnGround()`，直接跳
- `flee()`/`retreat()` 不再要求 `HostileEntity` 存在才跑
- `eat()` 找不到食物 → `partial` 而非 `unable`
- `seekShelter()`/`collectItem()` 无目标时继续探索而非放弃
- `dig()`/`attack()` 简化目标过滤 (删 `hardness≥0`/`BEDROCK`/`instanceof` 白名单)
- `isWalkableLike()` 从 `isAir||WATER` 白名单 → `!isSolidBlock()` 碰撞检测
- `Skill.canExecute()` 从 `abstract` → `default true`，消除双重扫描
- `AttackSkill` 删 `instanceof HostileEntity||AnimalEntity` 白名单

### P-5: TemplateManager.fill() 未接入主循环
TemplateManager 已实现但未被 MetaScheduler 调用。当前 L6 路径直接调用 `AIChatHandler.handleChat()`。远期目标：统一到 TemplateManager 作为唯一 LLM 出入口。

### P-6: MetaContext 已弃用，BotController 待删除
Phase 2 已将 MetaScheduler 和 CorrelationDetector 中的 `MetaContext` 依赖全部移除。`MetaContext` 已标记 `@Deprecated`。`BotController`（旧单 bot 路径）仍引用它，将在 Phase 3 随 BotController 一起物理删除。

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
├── api/                              跨脑区接口定义 (Phase 1 Decoupling)
│   ├── WorldContext.java             世界级共享组件门面
│   ├── BotContext.java               per-bot 组件门面
│   ├── MetaState.java                每 tick 可变状态
│   ├── BrainstemAPI.java             脑干门面
│   ├── AmygdalaAPI.java              杏仁核门面
│   └── CortexAPI.java                前额叶门面
├── api/impl/                         实现类
│   ├── WorldContextImpl.java
│   └── BotContextImpl.java
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
│   ├── innate/                       InnateReflexRegistry/9 先天技能 (含 sneak)
│   ├── scheduler/                    MetaScheduler/MotivationEngine/MetaContext/UrgencyClassifier
│   ├── skill/                        Skill + SkillManager
│   ├── navigation/                   GreedyNavigator + NavigationController
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
| `TaskTest.java` | 15 | 任务拆解 |
| `IdleBrainTest.java` | 10 | 状态机 |
| `ReflexRegistryTest.java` | 15 | 先天反射注册表 |
| `MotivationEngineTest.java` | 19 | 驱力/玻尔兹曼/交叉抑制 |
| `MetaContextStubTest.java` | 25 | MetaContext 查询/门控/环境检测 |
| `BotParamsTest.java` | 7 | 三规则继承/参数范围/变异 |
| `BayesianModuleTest.java` | 16 | 三层存储/概率预测/收敛判断/FileSystem注入 |
| `TemplateManagerTest.java` | 14 | 模板填空/解析/钩子/速率限制/异常传播 |
| **合计** | **~139** | **全部通过** |

**待补充测试：** `ChatSessionManager`
