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

每 tick 的决策分四阶段：

```
MetaScheduler.tick()
  1. selectPerspective()        ← 5视角 (SURVIVAL/TASK/SOCIAL/CURIOUS/CAUTIOUS)
                                  激素状态 + BotParams + 环境 → 玻尔兹曼选择
  2. labelProblem(perspective)  ← 贴标签: SURVIVAL/LEARNED_THREAT/TASK_ACTIVE/ROUTINE/FAMILIAR/NOVEL/TRIVIAL
  3. getFlowAdjustment(ctx)     ← 升降级: AUTOPILOT/NORMAL/OVERRIDE
  4. dispatch(label, flow)      ← 分派到对应执行层
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
| **海马体** | `hippocampus/` | 记忆存储、高光回忆 |
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
  ├─ ≥ 80% → 正常
  ├─ 30~80% → 继续观察
  └─ < 30% → 休眠 (dormant)，不删除，可复活
```

### 5.3 失败模式 (failureTags)

```json
{
  "skillId": "reflex_craft_stone_pickaxe",
  "successRate": 0.92,
  "failureTags": [
    {"condition": "underwater", "count": 3},
    {"condition": "no_crafting_table_in_range", "count": 5}
  ]
}
```

连续失败 ≥2 → 升级到 LocalDecomposer。确定性失败永久跳过。

---

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

LLM 的唯⼀工作：**看到 JSON 模板，填空**。

```
伺候层 (TemplateManager)          LLM (老爷)
  ┌─────────────────┐              ┌──────────┐
  │ 1. 选模板 (题)   │ ────JSON──→ │ 2. 填空   │
  │ 3. 解析结果      │ ←───JSON─── │          │
  │ 4. 路由执行      │              └──────────┘
  └─────────────────┘
```

### 7.1 五大模板

| 模板 | LLM 填入内容 | 下游消费者 |
|------|-------------|-----------|
| REFLEX_CREATE | `reflex_{skill}_{target}`，步骤链 | ConditionedReflex |
| TASK_PLAN | `steps[{action, target, params}]` | TaskManager |
| EVALUATION_BATCH | `[{reflexId, delta}]` | EvaluationCycle |
| FAILURE_CLASSIFY | `{featureKey, outcome}` | BayesianModule |
| CHAT_DIRECTION | `{perspective, priority}` | ChatSessionManager |

### 7.2 TemplateManager 核心接口

```java
public class TemplateManager {
    public CompletableFuture<JsonObject> fill(TemplateType type, Map<String, Object> context);
    void registerPostFillHook(TemplateType type, Consumer<JsonObject> hook);
    enum TemplateType { REFLEX_CREATE, TASK_PLAN, EVALUATION_BATCH, FAILURE_CLASSIFY, CHAT_DIRECTION }
}
```

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

---

## 9. 贝叶斯模块 (BayesianModule)

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
| **────** | | **────** | **────** |
| 新任务拆解 (TASK_PLAN) | LLM | 1 | 每个新任务 |
| 批量评价 (EVALUATION_BATCH) | LLM | 1 | 每 30min |
| 失败分类 (FAILURE_CLASSIFY) | LLM | 1 | 每次未覆盖的失败 |
| 对话方向 (CHAT_DIRECTION) | LLM | 1 | 按需 |

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
| **2. 脚手架 (Scaffolding)** | 反射 | 继承反射标记 trial，bot 自试 3 次成功才正式加入 |
| **3. 突变 (Mutation)** | BotParams | α/β/temperature + gaussian(σ=0.05/0.003/0.08) |

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
| ReflexPackManager | 骨架 (brainstem/bot) | `reflex_packs/*.json` |

---

> 设计原理与理念详见 [AGENTS.md](./AGENTS.md)
> 当前实施状态与路线图详见 [DEVELOPMENT.md](./DEVELOPMENT.md)
> 理论背景与论文详见 [THEORY.md](./THEORY.md)
