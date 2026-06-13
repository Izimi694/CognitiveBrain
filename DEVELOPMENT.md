# CognitiveBrain — 开发 & 状态

> 本文件记录当前实施状态、路线图、已知问题。架构说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)，设计原理见 [AGENTS.md](./AGENTS.md)。

---

## 1. 当前状态

```
项目阶段: Phase MG — MemoryGraph 记忆关系图
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
| **Phase 2.5 (Decoupling)** | **TemplateManagerTest (16 个测试), BayesianModuleTest (16 个测试)** | ✅ |
| **Phase G-M** | **ReflexChain DAG/死路检测/环境可控性/双向推理/共享池/参数绑定/L1/L3 门控/前置条件门控 (99 tests)** | ✅ |
| **Phase LLM (P0-P5)** | **四模板LLM调用重构: TemplateMatcher + CLARIFICATION/CHAT_RESPONSE + PersonaManager + MetaScheduler接入** | ✅ |
| **Phase MG** | **MemoryGraph 记忆关系图 (inferEdges/遍历/持久化/贝叶斯排重)** | ✅ |
| **Decoupling #7/#11/#12/#13** | **BotController/EAgent.LOGGER 全局替换/ConditionedReflex/MinecraftReflexEvaluator 解耦** | ✅ |

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

Phase G-M (全部已完成): ReflexChain/TaskDAG/Loop/死路/可控性/双向推理/共享池/门控/绑定

Phase LLM (四模板LLM调用重构):
  ├── P0 — MetaScheduler.executeCortexLLM() 接入 TemplateManager
  ├── P1 — CLARIFICATION 模板 + TemplateMatcher 路由
  ├── P2 — CHAT_RESPONSE 模板 + 独立预算
  ├── P3 — PersonaManager + 注入系统提示
  ├── P4 — DAG_TASK_PLAN 合并到 TASK_PLAN
  └── P5 — 删除 CHAT_DIRECTION 模板

Phase MG (MemoryGraph 记忆关系图):
  ├── MG1 — MemoryNode record + MemoryEdge mutable class + MemoryGraph 核心 (CRUD/save/load)
  ├── MG2 — inferEdges 显著性门控(computeSalience) + TEMPORAL/CAUSAL/SIMILARITY/CONTRAST 边推断
  ├── MG3 — 图遍历API (traverse/traceCausalChain/findSimilar/getTimeline/rankEdges)
  ├── MG4 — 持久化 memory_graph.json (+ version/no MissingNode)
  ├── MG5 — MemoryManager 集成(setMemoryGraph) + rebuildReflexToNodesIndex
  ├── MG6 — Hebbian 强化: reflexToNodes 索引 + findNodeIdsByReflex(前缀匹配) + reinforcePath
  │   └── ConditionedReflex 集成 (贝叶斯更新后调用)
  ├── MG7 — 扩散激活: traverse 重载(maxDepth/minWeight) + MetaScheduler 三路候选合并
  └── MG8 — 骨骼导出/导入: exportSkeleton + importSkeleton + ReflexPackManager 集成
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

### ~~P-5: TemplateManager.fill() 未接入主循环~~ ✅ 已修复
`MetaScheduler.executeCortexLLM()` 现在通过 `TemplateMatcher.match()` 路由到对应的模板，再通过 `TemplateManager.fill()` 调用 LLM。不再直接调用 `AIChatHandler.handleChat()`。

### P-6: MetaContext 已物理删除
Phase 2 已将 MetaContext 物理删除（268 行代码 + 271 行测试移除）。`pendingChatMessage` 状态已合入 `BotInstance` 直管。`BotController.setMetaScheduler()` 的 `MetaContext` 参数也已移除。

---

## 4. 构建

```bash
# 环境: JDK 21, Gradle 8+
cd AIPlayerMod-1.21.1-Fabric
.\gradlew.bat build
# 输出: build/libs/e-agent-1.21.1.jar
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
src/main/java/com/izimi/eagent/
├── EAgent.java                  DI 入口
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
│   ├── MemoryGraph/MemoryNode/MemoryEdge  记忆关系图 (§25)
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
│   ├── scheduler/                    MetaScheduler/MotivationEngine/UrgencyClassifier
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
minecraft/eagent/
├── config/                全局配置/先天反射 JSON
├── thresholds/            自适应阈值
├── bayesian/              全局共享先验 (shared_prior.json)
├── reflex_packs/          导入/导出的反射包 (*.json)
├── bots/genomes/          死亡Bot基因组存档 (*.json)
└── bots/{bot_uuid}/
    ├── conditioned/       条件反射库 (reflex_*.json)
    ├── conditioned/archived/  休眠反射归档
    ├── alarms/            L1 一次预警
    ├── memory/            高光记忆 (day_*.mem)
    │   ├── latest.mem     最新记忆快照
    │   ├── memory_graph.json  记忆关系图 (MemoryGraph)
    │   ├── trials/        观察学习试验 (ObservedSequence)
    │   └── highlights/    高光记忆 (独立文件)
    ├── bayesian/          per-bot 后验 (posterior.json)
    ├── bot_params.json    per-bot 参数 (α/β/temperature/gen)
    ├── dispatch_weights.json  DispatchReflex 权重
    ├── plans/             任务计划 (active_plan.json)
    ├── evaluations/       玩家评价缓存
    └── execution_logs/    执行日志 (log_*.json)
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
| `BotParamsTest.java` | 7 | 三规则继承/参数范围/变异 |
| `BayesianModuleTest.java` | 16 | 三层存储/概率预测/收敛判断/FileSystem注入 |
| `TemplateManagerTest.java` | 15 | 模板填空/解析/钩子/速率限制/异常传播/CLARIFICATION/CHAT_RESPONSE |
| `MemoryGraphTest.java` | 38 | 节点/边 CRUD、显著性门控、边推断、图遍历 BFS、因果链、持久化、贝叶斯重排、Hebbian 强化、扩散激活、骨骼导出/导入 |
| `ChatSessionManagerTest.java` | 7 | 窗口限制/方向回退/null安全/防御拷贝 |
| `TemplateMatcherTest.java` | 14 | 路由: CLARIFICATION/TASK_PLAN/REFLEX_CREATE/CHAT_RESPONSE/拦截 |
| **合计 (含新增)** | **273** | **全部通过** |

**新增测试计划：**

| Phase | 测试文件 | 数量 | 内容 |
|:-----:|---------|:---:|------|
| G | `ReflexChainTest.java` | 22 | DAG 构建/遍历/瓶颈检测/共享权重/链约束 |
| H | `MetaSchedulerPhaseHTest.java` | 13 | 时间片/抢占/死路三条件/麦穗连续置信度 |
| I | `BayesianModulePhaseITest.java` | 14 | 环境可控性计算/置信度/后验稳定性/BotState |
| J | `SharedPoolConfigTest.java` + `BayesianModulePhaseJTest.java` | 12+7 | 共享池常量/inferForward/inferBackward/回退阶段 |
| K | `TaskDAGTest.java` | 16 | LLM JSON 解析/DAG 遍历/瓶颈检测/格式校验 |
| L | `ConditionedReflexPhaseLTest.java` | 4 | precondition guard skip/wait/defer |
| M | `ParameterBinderTest.java` | 12 | bindings 绑定/transform/绑定失败→回退 |
| | `AlarmGatingTest.java` | 11 | L1/L3 门控 (OneShotAlarmSystem/SocialObserver/HormonalSystem) |
