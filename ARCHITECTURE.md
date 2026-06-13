# CognitiveBrain — 架构详细说明

> 本文件是 CognitiveBrain 技术架构的完整参考。设计原理见 [AGENTS.md](./AGENTS.md)，当前状态见 [DEVELOPMENT.md](./DEVELOPMENT.md)，理论背景见 [THEORY.md](./THEORY.md)。

---

## 1. 六层拦截器

每一层存在的意义，都是让下一层**不需要被调用**。

| 层 | 模组组件 | 触发条件 | 成本 |
|----|---------|---------|:---:|
| **L0** 生存本能 | `InnateReflexRegistry` | 熔岩/虚空/HP<2 | 0 |
| **L1** 先天预警 | `OneShotAlarmSystem` | 玩家说过"坏" | 0 |
| **L2** 条件反射 | `ConditionedReflex` | 匹配已有反射 | 0 |
| **L3** 模仿学习 | `SocialObserver` + 贝叶斯 | 附近有人在做 | 0 |
| **L4** 自组织 | `CorrelationDetector` | 无任何匹配 | 0 |
| **L5** 本地规划 | `LocalTaskDecomposer` | 规则匹配 | 0 |
| **L6** LLM 兜底 | `TemplateManager` → LLM | 以上全不命中 | 高 |

同一场景在不同层级下的不同拦截行为：

```
残血 + creeper + 附近玩家在打怪
L0  → flee (硬编码)
L1  → flee (玩家说过"creeper危险")
L2  → 习惯性打creeper (过去成功过)
L3  → 跟玩家一起打 (观察学习)
L4  → 随机试探 (无反射时)
L5  → 试探性攻击 (规则匹配)
L6  → 复杂语义分析 (以上全不命中)
```

---

## 2. MetaScheduler — 元调度器

每 tick 的决策分五阶段：

```
MetaScheduler.tick()
  1. computeDrives()            ← 5通道驱力计算 (BotContext + WorldContext + 环境)
                                   激素状态 + BotParams + 环境 → 5维 DriveState
  2. select()                   ← 玻尔兹曼选择 + 交叉抑制 (BotContext)
  3. labelProblem(perspective)  ← 贴标签: SURVIVAL/LEARNED_THREAT/TASK_ACTIVE/ROUTINE/FAMILIAR/NOVEL/TRIVIAL
  4. getFlowAdjustment(bot, state) ← 升降级: AUTOPILOT/NORMAL/OVERRIDE (BotContext + MetaState)
  5. dispatch(label, flow)      ← 分派到对应执行层
```

### 2.1 五大视角 (Perspective)

```java
enum Perspective { SURVIVAL, TASK, SOCIAL, CURIOUS, CAUTIOUS }

SURVIVAL:  alarms.hasUrgentThreat()
TASK:      task.hasActiveTask()
SOCIAL:    hasGroupActivity()
CURIOUS:   hasRecentNovelty()
CAUTIOUS:  params.beta > 0.02
```

### 2.2 升降级条件

| 降级触发 | 升级触发 |
|---------|---------|
| 熟练度 ≥ 0.8 且环境无异常 | 连续失败 2 次 |
| 同一动作成功 > 10 次 | 检测到从未见过的实体 |
| 玩家 5 分钟无指令 | 环境突变（3+ 新实体） |
| | 玩家说"小心/停" |

### 2.3 时间片分配（e 切割）

任务切换使用 63.2% 执行 + 36.8% 缓冲：

```java
public long computeTimeSlice(long totalLatencyBound, int taskCount) {
    long available = totalLatencyBound - (switchOverheadMs * taskCount);
    return (long) (available / taskCount * 0.632);  // 63.2% = 1 - 1/e
}
```

抢占阈值使用 1/e 增量：

```java
if (newTask.priority > currentTask.priority * (1 + (1.0 / Math.E))) {
    preempt();
}
```

---

## 3. 三大信息传递系统

| 传递类型 | 时间尺度 | 载体 | 工程实现 |
|---------|---------|------|---------|
| **基因层** | 代际 | α, β, ltb | `BotParams` + 三规则继承 |
| **激素层** | 秒~分钟 | 浓度 Map | `HormonalSystem` (stress/aggression/curiosity/intimacy) |
| **反射层** | 分钟~小时 | stw/ltb 固化 | `ConditionedReflex` + reinforce + solidify |

三者形成闭环：

```
执行反射 → 成功/失败 → 激素浓度变化 → 下次视角选择偏移
    ↑                                        ↓
    └──── 反复成功 → ltb ↑ (固化) ←───────────┘
                                        ↓
                                 死亡 → 三规则继承给后代
```

### 3.1 激素系统 (HormonalSystem)

| 变量 | 触发方式 | 作用 |
|------|---------|------|
| stress | 受伤/失败 | 倾向生存行为 |
| aggression | 战斗胜利 | 倾向攻击 |
| curiosity | 新奇发现 | 倾向探索 (指数衰减，半衰期 ~30min) |
| intimacy | 玩家表扬/批评 | 影响社交倾向 |

激素对视角选择的影响：

| 激素状态 | 视角偏向 | 聊天语气 |
|---------|---------|---------|
| stress > 0.7 | SURVIVAL | 简短/警惕 |
| curiosity > 0.6 | CURIOUS | 主动提问 |
| aggression > 0.7 | TASK | 战斗意愿强 |
| intimacy > 0.6 | SOCIAL | 热情回复 |

### 3.2 探索/利用比例（e 切割）

基于 37% 法则：

```java
public boolean shouldExplore() {
    double exploreBias = curiosity / maxCuriosity;
    return exploreBias > (1.0 / Math.E);
}
```

探索倾向高于 1/e ≈ 36.8% 时探索，否则利用。

---

## 4. 四模块脑区架构

| 脑区 | 模块 | 职责 |
|------|------|------|
| **前额叶** | `cortex/` | 规划、复杂决策、语义理解 |
| **海马体** | `hippocampus/` | 记忆存储、高光回忆、记忆关系图 (MemoryGraph) |
| **杏仁核** | `amygdala/` | 评价、条件反射、学习、情绪 |
| **脑干** | `brainstem/` | 先天反射、基础动作、生存本能 |

**基础设施 = 骨架** (`command/`, `config/`, `util/`, `log/`, `state/`, `mixin/`, `bayesian/`, `hormonal/`)：跨领域支撑层，不属于任何脑区。

### 4.1 脑干 vs 杏仁核分工

| | 脑干 (brainstem/) | 杏仁核 (amygdala/) |
|---|---|---|
| 职责 | "怎么做" — 执行层 | "什么时候做" — 判断层 |
| 内容 | 12 原子动作、寻路、Idle 动画 | 条件反射、社会镜像、评价 |
| 不含 | 任何"是否应该做"的决策 | 任何"如何执行"的实现 |
| 类比 | 伺服电机和机械臂 | 膝跳反射和痛觉神经 |

### 4.2 前额叶抑制控制 (InhibitoryControl)

否决不适当行为：
- 否决不必要的安全反射（误判危险不致命时抑制逃跑）
- 否决不恰当的习惯反射
- 否决不合适的模仿行为（如群体跳崖）
- 否决 IdleBrain 泛词误判

---

## 5. 条件反射系统

### 5.1 双权重学习 (stw/ltb)

| 权重 | 性质 | 更新频率 | 类比 |
|------|------|---------|------|
| stw (short-term) | 快变 | 每次执行 +5% 成功/-3% 失败 | 突触可塑性快成分 |
| ltb (long-term) | 慢变 | 反复成功才更新 | 突触可塑性慢成分 |

### 5.2 生命周期

```
试错执行/观察学习
  ↓
累积 ≥3 次成功 → 固化 (conditioned/reflex_{skill}_{target}.json)
  ↓
每次执行 → 检查成功率
  ├─ > 40% → 正常
  ├─ 20~40% → watching（继续观察）
  └─ ≤ 20% 且 executionCount > 20 → 休眠 (dormant)，不删除，可复活
```


## 6. 观察学习系统

### 6.1 学习流程

```
Fabric 事件触发
  ↓ (60s 窗口缓冲)
行为序列缓冲
  ↓ (模式检测，≥3 次相同"触发→步骤→结果")
自动固化为条件反射 → conditioned/reflex_{skill}_{target}.json
```

### 6.2 观察数据模型

```json
{
  "id": "seq_xxx",
  "occurrences": 5,
  "proficiency": 0.85,
  "source": "OBSERVED",
  "trigger": {"nearbyBlocks": ["iron_ore"], "inventory": ["stone_pickaxe"]},
  "steps": [
    {"action": "equipItem", "target": "stone_pickaxe"},
    {"action": "moveTo", "target": "nearest iron_ore"},
    {"action": "dig", "target": "iron_ore"}
  ]
}
```

### 6.3 社会学习（两阶段）

**阶段 1 — 学习期（前额叶驱动）**：观察 + KNN 筛选 + 朴素贝叶斯价值评估 + 前额叶抑制控制

**阶段 2 — 反射期（杏仁核驱动）**：同一模式成功 ≥3 次 → 固化 → 零成本自动执行

---

## 7. 模板填空架构 (Template Fill-in)

> **当前状态**：TemplateManager 已接入。MetaScheduler.executeCortexLLM() 通过 `TemplateMatcher.match()` 路由 → `TemplateManager.fill()`。

LLM 的唯⼀工作：**看到 JSON 模板，填空**。

```
伺候层 (TemplateManager)          LLM (老爷)
  ┌─────────────────┐              ┌──────────┐
  │ 1. 选模板 (题)   │ ────JSON──→ │ 2. 填空   │
  │ 3. 解析结果      │ ←───JSON─── │          │
  │ 4. 路由执行      │              └──────────┘
  └─────────────────┘
```

### 7.1 六种模板 (四种用户交互 + 两种后台)

| 模板 | 用途 | LLM 填入内容 | 下游消费者 | 是否固化 |
|------|------|-------------|-----------|:--------:|
| CLARIFICATION | 用户输入模糊时生成澄清问题 | `{ambiguity_detected, possible_interpretations, missing_info, suggested_question}` | 直接返回用户 | 否 |
| TASK_PLAN | 生成带依赖关系的任务DAG | `{task_id, subtasks[{id, name, action, target, depends_on[{id, type, weight, bindings}]}], bottleneck_nodes}` | TaskDAG → ReflexChain | 是 |
| REFLEX_CREATE | 生成新的条件反射 | `{reflex_id, display_name, steps[{action, target}]}` | ConditionedReflex | 是 |
| CHAT_RESPONSE | 生成对玩家的回复文本 | `{reply_text, suggested_emote, tone}` | 直接返回用户 | 否 |
| EVALUATION_BATCH | 批量评价反射效果 | `[{reflexId, delta}]` | EvaluationCycle | 否 (内部) |
| FAILURE_CLASSIFY | 分析失败原因 | `{featureKey, outcome}` | BayesianModule | 否 (内部) |

### 7.2 TemplateManager 核心接口

```java
public class TemplateManager {
    public CompletableFuture<JsonObject> fill(TemplateType type, Map<String, Object> context);
    void registerPostFillHook(TemplateType type, Consumer<JsonObject> hook);
    void setActivePersona(String persona);
    String injectPersona(String basePrompt, String persona);
    enum TemplateType { CLARIFICATION, TASK_PLAN, REFLEX_CREATE, CHAT_RESPONSE, EVALUATION_BATCH, FAILURE_CLASSIFY }
}
```

### 7.3 TemplateMatcher 路由

`TemplateMatcher.match(message, botCtx, worldCtx)` 按以下顺序路由用户输入：

1. **LocalChatHandler** 正则匹配 → `null` (0 成本)
2. **关键词分类**:
   - 明确任务请求 (挖/打/建+N) → `TASK_PLAN`
   - 明确学习请求 (学/记住/如果...就) → `REFLEX_CREATE`
   - 纯社交 (你好/谢谢/喵~) → `CHAT_RESPONSE`
   - 模糊输入 (怎么/如何/能不能/?) → `CLARIFICATION`
   - 含具体名词 → `TASK_PLAN`, 否则 `CHAT_RESPONSE`

### 7.4 CLARIFICATION 调用限制

- 同一对话轮次最多调用一次 CLARIFICATION
- 用户二次模糊 → 返回预设 "请更具体地描述你的需求"

### 7.5 CHAT_RESPONSE 预算隔离

- 独立预算配额 (最多 50 次), 与核心循环完全隔离
- 预算耗尽 → 回退到 LocalChatHandler 预设回复

### 7.6 PersonaManager

PersonaManager 管理角色设定注入:

- `setPersona(description)` → 覆盖 `TemplateManager.activePersona`
- 只影响 `CHAT_RESPONSE` 和 `REFLEX_CREATE` 的系统提示
- 不影响 `TASK_PLAN`、`CLARIFICATION`、贝叶斯统计、反射权重、激素系统
- 持久化到 `eagent/skills/character/active_persona.txt`

---

## 8. 基本动作池 (12 原子动作)

| 动作 | 说明 | 返回 |
|------|------|------|
| `moveTo(x,y,z)` | 移动到坐标 | boolean |
| `lookAt(x,y,z)` | 看向坐标 | void |
| `dig(x,y,z)` | 挖掘方块 | boolean |
| `attack(entity)` | 攻击实体 | boolean |
| `placeBlock(block, face)` | 放置方块 | boolean |
| `useItem()` | 使用手中物品 | boolean |
| `equipItem(name)` | 装备物品 | boolean |
| `openBlock(x,y,z)` | 打开容器 | boolean |
| `closeWindow()` | 关闭容器 | void |
| `clickSlot(slot, button)` | 点击容器格子 | boolean |
| `chat(msg)` | 发送聊天 | void |
| `jump()` | 跳跃 | boolean |

> **注**：上表为 12 个核心原子动作。`BasicActionAdapter` 实际有 19 个方法，另有 7 个复合动作：`craft`、`flee`、`eat`、`retreat`、`avoidLava`、`seekShelter`、`collectItem`。

---

## 9. 贝叶斯模块 (BayesianModule)

> **注意**：本系统中的贝叶斯模块是被动数据仓库，不参与实时决策门控。它提供成功率统计、记忆相关性排序、社交镜像筛选等服务。决策由 Subsumption Architecture (L0-L5) + 玻尔兹曼驱力竞争完成。这符合生物学事实——大脑不输出后验概率 (Nature 2025)。

### 9.1 三层存储

| 存储区 | 内容 | 更新频率 | 公式对应 |
|--------|------|---------|---------|
| 先验条件表 | `P(success)` | 极慢 (平滑更新) | 先验概率 |
| 后验观测链 | `P(feature\|outcome)` | 快 (每次错误) | 似然度 |
| 主题锚定区 | 当前任务上下文 | 中 | 条件过滤器 |

### 9.2 核心公式

```
P(成功 | 特征) ∝ P(特征 | 成功) × P(成功)
```

### 9.3 收敛判断（e 切割）

```java
public boolean isConverged(Posterior posterior) {
    double changeRate = Math.abs(posterior.newProb - posterior.oldProb) / posterior.initialVariance;
    return changeRate < (1.0 / Math.E);  // 1/e ≈ 0.3679
}
```

---

## 10. 成本模型

| 操作 | 执行者 | LLM 调用 | 频率 |
|------|--------|:-------:|:----:|
| 安全反射 (flee/eat) | 脑干 | 0 | 每 tick |
| 条件反射执行 | 脑干 | 0 | 每次匹配 |
| 试错执行 | 脑干 | 0 | 每次执行 |
| 自动固化 | 脑干 | 0 | ≥3 成功 |
| 观察学习 | 脑干 | 0 | 每次 Fabric 事件 |
| Idle 主动建议 | 脑干 | 0 | idle > 30s |
| 本地聊天 (LocalChatHandler) | 脑干 | 0 | 正则匹配 |
| **────** | | **────** | **────** |
| 澄清问题 (CLARIFICATION) | LLM | 1 | 模糊输入 (同一轮次只一次) |
| 任务分解 (TASK_PLAN) | LLM | 1 | 每个新任务 |
| 反射创建 (REFLEX_CREATE) | LLM | 1 | 每个新行为 |
| 闲聊回复 (CHAT_RESPONSE) | LLM | 1 | 按需 (独立预算 50次) |
| 批量评价 (EVALUATION_BATCH) | LLM | 1 | 每 30min (内部) |
| 失败分类 (FAILURE_CLASSIFY) | LLM | 1 | 每次未覆盖的失败 (内部) |

**挂机 1 小时: 0 次 API。活跃 1 小时: ~4-8 次。**

### 10.1 反射包 (ReflexPack) — 零成本的批量移植

反射包 = JSON 文件，打包所有 `conditioned/*.json` + 可选贝叶斯先验。纯本地文件操作，不触发 LLM。

| 操作 | 成本 | 场景 |
|------|:----:|------|
| `export bot packname` | 0 | 备份/分享当前反射经验 |
| `export bot packname noprior` | 0 | 只分享步骤，不含成功率 |
| `import bot packname` | 0 | 合并（保留本地新经验） |
| `import bot packname reset` | 0 | 冷启动（完全覆盖） |

**核心原则**：导入后不自闭，Bot 继续用自己的贝叶斯模块去适应新环境。包只是初始信念。

---

## 11. 聪明与智慧的工程定义

- **聪明 = 大量高质量的条件反射（反射池 × 强化质量）**。知道"怎么做"，反应快，执行准。
- **智慧 = `selectPerspective()` 的精度**。知道"现在是什么情况"，从而选择对的反射链。

```
低智慧 ─────────────────────────────→ 高智慧
僵化（永远用本能）←────→ 灵活（自如升降级）←────→ 混乱（永远用LLM）
```

**智慧 = f(标签精准度, 调控灵活性, 调度经济性)**

---

## 12. 硬编码分级

| 层 | 名称 | 硬编码 | 可配置 | 可学习 | 可遗传 | 存储位置 |
|:--:|------|:------:|:------:|:------:|:------:|---------|
| L0 | Kernel | ✅ 永久 | ❌ | ❌ | ❌ | 代码 |
| L1 | Config | ❌ | ✅ | ✅ | ✅ | `config.json` + `BotParams` |
| L2 | Scheduler | ⚠️ 临时 | ❌ | ✅ | ❌ | `DispatchReflex` 权重 |
| L3 | Knowledge | ❌ | ✅ (人工) | ✅ (LLM) | ❌ (共享) | `knowledge_base.json` |
| L4 | Reflexes | ❌ | ❌ | ✅ | ✅ | `conditioned/*.json` |

---

## 13. 繁衍系统 (三规则继承)

Bot 死亡或 despawn 时，其参数 (α/β/temperature) + 谱系数据被存入 `bots/genomes/` 基因组存档。新 Bot spawn 时自动从存档继承：

| 规则 | 应用于 | 实现 |
|------|--------|------|
| **1. 平均 (Intersection Average)** | BotParams | 双亲参数取均值 |
| **2. 减半 (Random Halving)** | BotParams | α/β/temperature 各 50% 概率减半 |
| **3. 突变 (Mutation)** | BotParams | α/β/temperature + gaussian(σ=0.05/0.003/0.08) |

> 脚手架式反射继承是独立机制，见 §13.2。与三规则并列：三规则操作 BotParams（遗传参数），脚手架操作 ConditionedReflex（继承反射先验后自试验证）。

中心极限定理保证：多次继承后种群参数趋于正态分布。

### 13.1 遗传流程

```
Bot死亡/despawn
  ↓
saveDeathGenome(cause)
  ├── botParams → bots/{uuid}/bot_params.json    (per-bot参数归档)
  └── GenomeArchivist → bots/genomes/{uuid}.json  (种群基因组存档)
        ↓
新Bot spawn
  ├── inheritGenome=true? → GenomeArchivist.getLatestGenomeParams()
  ├── 有祖先 → BotParams.inherit(parent[, p2])   (平均+突变)
  ├── 无祖先 → BotParams.generate()               (随机初始化)
  └── copyReflexesFromMentor()                    (脚手架 trial-first)
        └── bot自试3次 → healthy/dormant
```

### 13.2 脚手架式反射继承 (Trial-First)

继承的反射不直接加入——bot 必须先试 3 次，通过了才算自己的：

```
copyReflexesFromMentor()
  │
  ├── 复制完整模板 (不截断、不减半)
  ├── status = "trial"
  ├── trialSuccesses = 0, trialFailures = 0
  └── 中性权重 (stw/ltb = 0.5, proficiency = 0.3)
        │
        ▼ bot 触发反射
  ConditionedReflex.executeReflex()
        │
        ├── 成功 → trialSuccesses++
        │        ├── ≥ 3 → status = "healthy", proficiency = 0.5  → 正式加入
        │        └── < 3 → 继续试炼
        │
        └── 失败 → trialFailures++
                 ├── ≥ 3 → status = "dormant", 移入 archived → "不适合我"
                 └── < 3 → 继续试炼
```

试炼期内不更新 `stw/ltb`（权重中性），不触发 `handleReflexFailure` 的 watching/dormant 降级链。bot 在"安全区"内自己试，成不成都由自己的经历决定。

### 13.3 基因组存档格式

```json
{
  "botId": "uuid",
  "name": "miner_bot",
  "alpha": 0.35, "beta": 0.012, "temperature": 0.4,
  "generation": 3,
  "parentId": "parent_uuid",
  "cause": "killed/despawned/removed",
  "diedAt": 1718000000000
}
```

存档仅保留 BotParams（遗传特质），不保留反射文件（反射通过 trial-first 脚手架继承）。

---


## 14. 组件归属表

> **注**：此处使用 §12 的硬编码分级维度（L0-L4 对应 Kernel/Config/Scheduler/Knowledge/Reflexes），与 §1 的运行时拦截层维度（L0-L6 六层拦截器）不同。ConditionedReflex 在 §1 是 L2（时机匹配执行），在 §12/§14 是 L4（可学习可遗传的反射文件）。

| 组件 | 归属层 | 存储位置 |
|------|:------:|---------|
| InnateReflexRegistry | L0 | `innate_reflexes.json` |
| ConditionedReflex | L4 | `conditioned/*.json` |
| BotParams | L1 | `bot_params.json` |
| HormonalSystem | L1 | 内存 |
| OneShotAlarmSystem | L2 | `alarms/*.json` |
| DispatchReflex | L2 | `dispatch_weights.json` |
| InhibitoryControl | L2 | 内存 |
| KnowledgeBase | L3 | `knowledge_base.json` |
| ThresholdConfig | L1 | `thresholds.json` |
| BayesianModule | 骨架 | `bayesian/shared_prior.json` + per-bot posterior |
| TemplateManager | 骨架 (cortex/api) | 模板定义 |
| SocialObserver | 骨架 | 内存 |
| BehaviorStats | 骨架 | 内存 |
| ChatSessionManager | 骨架 (cortex/chat) | 内存窗口 |
| ReflexPackManager | 骨架 (brainstem/bot) | 实例注入（botId + BayesianModule） `reflex_packs/*.json` |
| WorldContext | 骨架 (api) | `WorldContext` 接口 / `WorldContextImpl` |
| BotContext | 骨架 (api) | `BotContext` 接口 / `BotContextImpl` |
| MetaState | 骨架 (api) | `MetaState` 类（每 tick 新建） |
| BrainstemAPI | 骨架 (api) | 脑干门面接口（innateReflexes/basicActions/inhibitor） |
| AmygdalaAPI | 骨架 (api) | 杏仁核门面接口（socialObserver/familiarityTracker） |
| CortexAPI | 骨架 (api) | 前额叶门面接口（localPlanner/chatHandler/templateManager/aiClient/chatAI） |
| ReflexChain | L4 (时序/执行) | `dag/` 索引 + 反射节点间关系 |
| TaskDAG | 骨架 | 内存 (cortex/planner) |
| ParameterBinder | 骨架 | 内存 |
| SharedPoolConfig | 骨架 (config) | `config/` |
| MemoryGraph | L4 (hippocampus) | `memory_graph.json` |
| MemoryNode / MemoryEdge | L4 (hippocampus) | 内存 + `memory_graph.json` |
| MemoryQuery | L4 (hippocampus) | 内存 |
| LearningSystem | L4 (amygdala/learning) | `memory/trials/observed_*.json` |
| TrialStorage | L4 (hippocampus/storage) | `memory/trials/` |
| HighlightStorage | L4 (hippocampus/storage) | `memory/highlights/` |
| BehaviorEventHandler | L2 (amygdala/character) | 内存 |
| EvaluationCycle | L2 (amygdala/character) | 内存 |
| CategoryMapper | L3 (amygdala/learning) | 硬编码 |
| NaiveBayesClassifier | L3 (amygdala) | 内存 |
| PlanManager | L5 (cortex/planner) | `active_plan.json` |
| Plan | L5 (cortex/planner) | `active_plan.json` |
| AITaskPlanner | L6 (cortex/api) | 内存 |
| AIClient / AIConfig / AIResponse | L6 (cortex/api) | 内存 + `config/api_key.json` |
| DeepSeekClient | L6 (cortex/api) | 内存 |
| AIChatHandler | L6 (cortex/api) | 内存 |
| TemplateMatcher | L6 (cortex/api) | 内存 |
| AIMemoryGenerator | L6 (cortex/api) | 内存 |
| LocalChatHandler | L5 (cortex/chat) | 内存 |
| ChatSessionManager | 骨架 (cortex/chat) | 内存窗口 |
| KnowledgeBase | L3 (cortex/planner) | `config/knowledge_base.json` |
| UrgencyClassifier | L2 (brainstem/scheduler) | 内存 |
| ReflexChain | L4 (brainstem/scheduler) | `dag/` 索引 + 反射节点间关系 |
| TaskDAG | 骨架 (cortex/planner) | 内存 |
| ParameterBinder | 骨架 (brainstem/scheduler) | 内存 |
| BotSpawner | L2 (brainstem/bot) | 内存 |
| BotPlayer | L2 (brainstem/bot) | 内存 (Minecraft 假人) |
| GreedyNavigator | L3 (brainstem/navigation) | 内存 |
| NavigationController | L3 (brainstem/navigation) | 内存 |
| Skill / SkillManager | L2 (brainstem/skill) | 内存 |
| InnateReflex | L0 (brainstem/innate) | 硬编码 + `innate_reflex_weights.json` |

---

## 15. DAG 任务依赖图 (数据/逻辑层)

DAG 描述任务子步骤之间的**数据/逻辑依赖关系**，与 ReflexChain 的时序/执行链分层独立。

### 15.1 TaskDAG 数据模型

```json
{
  "task_id": "mine_10_iron",
  "subtasks": [
    {
      "id": "a1", "name": "找到铁矿", "action": "moveTo", "target": "iron_ore",
      "depends_on": []
    },
    {
      "id": "a2", "name": "挖掘铁矿", "action": "dig", "target": "iron_ore",
      "count": 10,
      "depends_on": [{
        "id": "a1", "type": "hard", "weight": 0.95,
        "bindings": [
          {"from": "output.position", "to": "target_position"},
          {"from": "output.block_type", "to": "block_type"}
        ]
      }]
    }
  ],
  "bottleneck_nodes": ["a2"]
}
```

### 15.2 依赖类型

| 类型 | 失败后果 | 回退策略 |
|------|---------|---------|
| `hard` | 下游不可执行 | 重试/回溯上游 |
| `soft` | 可尝试替代方案 | 查 weight 最高替代 |

### 15.3 瓶颈节点识别

- LLM 粗分解时主动标记语义瓶颈（一次性）
- 运行时入度检测自动标记结构瓶颈（入度 ≥ 3 → `isBottleneck = true`）

---

## 16. ReflexChain 执行链表 (时序/执行层)

ReflexChain 描述反射的**时序执行顺序**，与 DAG 的依赖关系分层独立。

### 16.1 树形多指针

```java
class ReflexNode {
    String id;
    String reflexId;
    Set<String> prev;  // 前置节点 (多指针)
    Set<String> next;  // 后续节点 (多指针)
    boolean isBottleneck;
    double baseWeight;
    double getSharedWeight(String taskId) {
        return baseWeight * getTaskConfidence(taskId);
    }
}
```

### 16.2 共享节点权重动态调整

- 多任务均成功 → 增加共享权重
- 仅一任务成功、其他失败 → 降低该节点在失败任务中的置信度，保留基础权重

---

## 17. Loop 事件驱动刷新循环

```

记忆(当前进度)
  → 激素粗筛 (candidate_set ≤ 5)                    [D — 发散]
    → 贝叶斯精筛 (sorted by posterior)              [C — 收敛]
      → 取 top candidate
        → DAG 依赖检查 (depends_on 全满足?)
          不满足 → 五阶段回退 (§21)
          满足 →
            → 参数绑定 (bindings → input_slots)      [填空]
              绑定失败 → 依赖回退
              绑定成功 →
                → 前置条件检查 (preconditions 全通过?)
                  贝叶斯预判: posterior < 0.05 → 提前返回 (不更新贝叶斯)
                  不通过 → fail_strategy: skip/wait/defer
                  通过 → 反射执行(params)
                    → 成功 → 推进进度, 存储输出, 更新贝叶斯
                    → 失败 → 贝叶斯更新, 死路检测
                             死路 → 五阶段回退/LLM 兜底
                             非死路 → 下一轮
      → 无候选 → LLM 兜底                                    [L6]
```

### 17.1 事件驱动机制

- 每次反射完成触发一轮刷新，非独立 tick
- 最大 4 轮 reflection loops
- 当前反射执行中不触发新决策

---

## 18. 环境可控性指数

### 18.1 计算公式

```java
public double computeControllability(ReflexId reflexId, BotContext context) {
    Posterior posterior = bayesian.getPosterior(reflexId, context);
    double variance = posterior.variance;
    double controllability = 1.0 / (1.0 + variance / varianceScale);
    if (envChangeRate > threshold) controllability *= 0.5;
    return clamp(controllability, 0, 1);
}
```

### 18.2 分层仲裁

| 方差 | 后验分布 | 可控性 | L1/L3 行为 |
|:----:|---------|:------:|-----------|
| 低 | 集中尖锐 | 高 | 需贝叶斯验证后固化 |
| 高 | 分散平坦 | 低 | 允许直接固化 |

---

## 19. 死路三条件

按优先级排序，任一满足即触发回退或 LLM 兜底：

```java
public DeadEndResult isDeadEnd(Reflex reflex, BotContext context) {
    if (reflex.consecutiveFailures >= 5)
        return new DeadEndResult(true, "CONSECUTIVE_FAILURES");
    if (bayesian.posteriorSuccessRate(reflex, context) < 0.1)
        return new DeadEndResult(true, "LOW_POSTERIOR");
    if (reflex.attempts > 37 && !reflex.isStable())
        return new DeadEndResult(true, "EXPLORATION_EXHAUSTED");
    return new DeadEndResult(false, null);
}
```

---

## 20. 贝叶斯双向推理

### 20.1 顺推 (inferForward)

从当前状态预测最大可能下一步，贝叶斯原生能力：

```java
public List<Candidate> inferForward(BotState state, Evidence evidence) {
    return getPosteriorSorted(state, evidence);
}
```

### 20.2 倒推 (inferBackward)

从目标反推前置条件，贝叶斯仅做概率排序，因果关系来自外部：

```java
public List<Precondition> inferBackward(Goal goal, Evidence evidence) {
    // 1. 历史归纳: getFrequentPredecessors(goal)
    // 2. 反射元数据: goal.prev
    // 3. LLM 按需生成
    return sortByPrior(getPreconditions(goal, evidence));
}
```

---

## 21. 回退五阶段

```java
public void rollback(Node node, Failure failure) {
    // Stage 1: 本地重试
    if (node.retryCount < MAX_RETRY) { retry(node); return; }
    // Stage 2: 替代方案
    Reflex alt = findAlternative(node);
    if (alt != null && bayesian.posterior(alt) > MIN_ALT_THRESHOLD) { tryAlternative(alt); return; }
    // Stage 3: 回溯上游
    Node upstream = traceToUpstream(node);
    if (upstream != null && bayesian.hasDegradedOutput(upstream)) { rollback(upstream, failure); return; }
    // Stage 4: 麦穗探索
    if (wheatEarExplore(node)) { markExploration(node); return; }
    // Stage 5: LLM 重新规划
    llmReplan(memento);
}
```

---

## 22. 四类共享池形式化参数

| 池 | 生物对应 | 约束 | 配置键 |
|----|---------|:----:|--------|
| 囊泡超级池 | 相邻突触共享囊泡 | 反射链长度 ≤ 5 | `chain_max_length` |
| 工作记忆绑定池 | 有限特征绑定 | 贝叶斯候选集 ≤ 5 | `bayesian_candidate_limit` |
| 跨脑共享子空间 | dmPFC 社交对齐 | 共享先验比例 10-30% | `shared_prior_ratio` |
| 归一化网络池 | 总活动恒定 | 总驱力 = 1.0 | 硬编码归一化 |

四池对应不同生物结构，约束独立，不交叉合并。

---

## 23. 边界条件门控与提前返回

### 23.1 门控位置

在贝叶斯精筛后、反射执行前插入：

```
贝叶斯精筛 → 依赖检查 → 参数绑定 → 前置条件门控 → 反射执行
                                          ↓ 不通过
                                      提前返回
```

### 23.2 三类前置条件

| 类型 | 例子 | 检查方式 | 成本 |
|------|------|---------|:----:|
| 物品条件 | `requires_item: "wooden_pickaxe"` | 查背包 | 0 |
| 环境条件 | `block_reachable(x,y,z)` | 调导航 | 0 |
| 状态条件 | `stress < 0.8` | 读激素 | 0 |

### 23.3 依赖检查 ≠ 前置条件检查

| 检查 | 归属 | 失败后果 |
|------|------|---------|
| 依赖检查 (DAG) | depends_on | 回退五阶段 (§21) |
| 前置条件 (反射) | preconditions[] | skip/wait/defer |

### 23.4 贝叶斯预判提前返回

```java
if (bayesian.posteriorMean(reflex, context) < 0.05) {
    return EarlyReturn(reason: "posterior_too_low", updateBayesian: false);
}
```

### 23.5 fail_strategy 三种策略

| 策略 | 行为 | 适用场景 |
|:----:|------|---------|
| `skip` | 尝试下一个候选反射 | 同类候选多 (多种攻击方式) |
| `wait` | 挂起当前任务，等待条件满足 | 临时条件 (等天亮) |
| `defer` | 标记阻塞，推进不相关子任务 | DAG 并行场景 |

默认值: `skip`

### 23.6 反射 JSON 新字段

```json
{
  "reflex_id": "reflex_dig_iron_ore",
  "input_slots": [
    {"name": "target_position", "type": "BlockPos", "required": true},
    {"name": "block_type", "type": "string", "optional": true}
  ],
  "capabilities": ["break_block", "collect_item"],
  "preconditions": [
    {"type": "item", "key": "main_hand", "match": "pickaxe", "fail_strategy": "skip"},
    {"type": "state", "key": "stress", "operator": "<", "value": 0.8, "fail_strategy": "defer"}
  ]
}
```

---

## 24. 参数绑定 (ParameterBinding) — 填空题显式化

### 24.1 存储方案 (方案 B)

- **反射 JSON**: 声明 `input_slots`（参数槽位定义）
- **TaskDAG**: `depends_on` 中 `bindings` 字段声明从上游输出到下游输入的映射

### 24.2 支持变换类型

| 变换 | 格式 | 例子 |
|------|------|------|
| 直接传递 | `from → to` | `output.position → target_position` |
| 变换 | `transform` 字段 | `"transform": "offset(x, y+2, z)"` |

### 24.3 绑定时机

```
依赖检查 → 参数绑定 → 前置条件检查 → 反射执行
```

绑定失败 → 视为依赖不满足 → 回退五阶段

### 24.4 绑定执行逻辑

```java
public Map<String, Object> bindParameters(SubtaskNode node, Map<String, Object> upstreamOutputs) {
    Map<String, Object> params = new HashMap<>();
    for (Binding binding : node.getBindings()) {
        Object value = upstreamOutputs.get(binding.from);
        if (value == null) throw new BindingError("Missing upstream output: " + binding.from);
        if (binding.transform != null) value = applyTransform(value, binding.transform);
        params.put(binding.to, value);
    }
    return params;
}
```

---

## 25. 记忆关系图 (MemoryGraph)

MemoryGraph 是陈述性记忆的关系层，与 MemoryManager 的切片存储分层独立。它只记录记忆之间的**关系**，不记录记忆内容本身。

### 25.1 数据模型

```java
// 节点：轻量引用，不污染 MemoryEntry
record MemoryNode(String memoryId, String summary, long timestamp, int gameDay) {}

// 边：带类型的加权有向边 (mutable class, weight ∈ [0.1, 1.0])
class MemoryEdge {
    String fromId(), String toId(), RelationType type();  // 不可变
    double weight();                                       // 可变, updateWeight(delta) 夹具 [0.1,1.0]
    long createdAt(), long lastReinforcedAt();             // 创建/最后强化时间戳
}

enum RelationType { CAUSAL, TEMPORAL, SIMILARITY, CONTRAST }
```

### 25.2 存储格式

独立文件 `eagent/bots/{uuid}/memory/memory_graph.json`：

```json
{
  "version": "1.0",
  "lastSavedDay": 42,
  "nodes": [
    {"memoryId": "mem_001", "summary": "挖了5个铁矿", "timestamp": 1718300000, "gameDay": 1}
  ],
  "edges": [
    {"fromId": "mem_001", "toId": "mem_002", "type": "CAUSAL", "weight": 0.85, "createdAt": 1718300000000, "lastReinforcedAt": 1718300050000}
  ]
}
```

### 25.3 边推断 (inferEdges)

每次创建新记忆时同步推断，但受显著性门控：

| 推断条件 | 结果类型 | 权重公式 |
|---------|---------|---------|
| 同 gameDay，时间相邻 | TEMPORAL | `1.0 - gap / avgInterval` |
| 新 learning 含已有 skill | CAUSAL | `0.5 + overlap * 0.15` |
| 贝叶斯相似度 > 0.7 | SIMILARITY | `bayes.predictRelevance(a, b)` |
| 含"成功"/"失败"冲突词 | CONTRAST | `0.3`（固定） |

新记忆必须先过显著性门控（`computeSalience() >= 0.6`）：

| 显著性来源 | 加分 | 条件 |
|-----------|:---:|------|
| keyLearnings 非空 | +0.3 | 有学习内容 = 重要性信号 |
| relatedSkills 数量 | +0.1/个，上限 +0.2 | 技能跨越多类别 |
| preferencesUpdated 非空 | +0.2 | 偏好变更 = 结果显著性 |
| 时间间隔 > 2× 平均间隔 | +0.3 | 长时间无记忆后突发 = 重大事件 |

### 25.4 图遍历 API

| 方法 | 说明 | 用途 |
|------|------|------|
| `traverse(startId, type, maxDepth)` | BFS 沿指定关系类型游走 | 记忆穿梭 |
| `traverse(startId, type, maxDepth, minWeight)` | BFS 低权重剪枝游走 | 扩散激活 |
| `traceCausalChain(memoryId, upstream)` | 因果链追溯（前因/后果） | 归因分析 |
| `findSimilar(memoryId, topK)` | 按相似边权重排序 | 相关推荐 |
| `getTimeline(gameDay)` | 某天时间线（按时间戳排序） | 日回顾 |
| `rankEdges(edges, queryContext, bayes)` | 贝叶斯对边关联节点重排 | 上下文排序 |

### 25.5 与 MemoryManager 的关系

```
MemoryManager              MemoryGraph
  ├── day_*.mem (切片)      ├── memory_graph.json (关系)
  ├── 高光片段存储           ├── 零侵入（不修改 MemoryEntry）
  └── 负责读写内容           └── 只记录节点间关系
```

MemoryGraph 通过 `setMemoryGraph()` 注入 MemoryManager，`enabled=false` 时完全降级。

### 25.6 Hebbian 强化 (Phase 1)

每次反射执行成功后，`ConditionedReflex.executeReflex()` 在贝叶斯更新后调用 `memoryGraph.reinforcePath(nodeIds, delta)`，强化反射相关记忆节点间的边权重。

```
反射执行成功 → Bayesian 更新
                ↓
    findNodeIdsByReflex(reflexId)  ← reflexToNodes 前缀索引
                ↓
      reinforcePath(nodeIds, delta)
                ↓
      edgeDelta = delta × 0.1
      ├── 已有边 → updateWeight(+0.005 on success / -0.003 on failure)
      └── 无此边 → addEdge(initWeight = 0.3 + delta × 0.05)
```

关键设计：

| 机制 | 说明 |
|------|------|
| `reflexToNodes` 索引 | `rebuildReflexToNodesIndex(MemoryManager)` 在 `setMemoryGraph()` 后自动重建 |
| 前缀匹配 | `"mine_iron"` 前缀匹配 `"mine"` — 支持一对多反射→记忆映射 |
| `updateWeight` | 夹具 `[0.1, 1.0]`，下限防止边因频繁失败消失 |
| 衰减因子 | `edgeDelta = delta × 0.1` — 每条边增量为主增量 1/10，大量重复才能显著变化 (Heisenberg 原则) |

### 25.7 扩散激活 (Phase 2)

当 MetaScheduler 无法从已有反射路径生成候选时，回退到记忆图的扩散激活，通过 BFS 遍历低权重剪枝的相邻节点，提取关联反射作为备选。

```
MetaScheduler.executeLoop() 候选生成:
  路径 1: 优先级精确匹配当前反射 (weight 1.0)
  路径 2: 最近执行反射 (weight 0.8)        ← 三路并行
  路径 3: 扩散激活 (weight × 0.6)         ←
```

扩散参数：

| 参数 | 默认值 | 说明 |
|------|:------:|------|
| `maxDepth` | 2 | 激活传播深度 |
| `minWeight` | 0.5 | 只遍历权重大于该阈值的边 |
| 衰减因子 | 0.6 | 每层激活值 × 0.6 |
| 种子节点 | 最近 3 条记忆 | 或全部（少于 3 条时） |

所有候选路径经玻尔兹曼选择竞争，扩散激活路径因 0.6 衰减系数天然处于较低优先级。

### 25.8 骨骼导出/导入 (Phase 3)

骨骼 (Skeleton) 是 MemoryGraph 的轻量摘要，仅保留高频连接节点，用于跨 bot 迁移记忆关系：

```
exportSkeleton()
  ├── incident edge count ≥ 2 (weight ≥ 0.5) → 高频节点
  ├── deinstanceLabel() 擦除坐标/时间戳
  └── 输出: skeleton_nodes + skeleton_edges

importSkeleton(skeleton)
  ├── 非重置合并: max(existingWeight, importedWeight)
  └── 标签冲突: 以 label.hashCode() 为稳定性 ID (sid = "skel_" + hashCode)
```

集成 ReflexPackManager：

| 方法 | 行为 |
|------|------|
| `exportPack()` | 写入 `memory_graph` 键 |
| `importPack()` | 调 `memoryGraph.importSkeleton(skeleton)` 合并 |

骨骼操作不调用 LLM，纯本地 JSON 合并。`deinstanceLabel` 擦除 ` at (x,y,z)`、`HH:mm:ss`、`YYYY-MM-DD`、`[tags]` 等实例化后缀。

---

> 设计原理与理念详见 [AGENTS.md](./AGENTS.md)
> 当前实施状态与路线图详见 [DEVELOPMENT.md](./DEVELOPMENT.md)
> 理论背景与论文详见 [THEORY.md](./THEORY.md)
