# AI Player Mod - AGENTS.md

## 1. 设计目标

让任意能决策的 LLM 生成反射并重复利用，
将 LLM 的推理成本与运行时间解耦。

学习是为了不学习。思考是为了不思考。
每一份思考都应该产出一份反射。
反射覆盖的场景越多，思考越少。
目标是：思考次数 = O(陌生事件)，不随运行时间增长。

## 2. 核心矛盾

**运行时间 ∞ vs 成本有限**。

所有设计决策都是在这个矛盾下的权衡。

## 3. 核心抽象

| 概念 | 工程实现 | 设计参考 |
|------|---------|---------|
| 进程/线程 | 反射（ConditionedReflex） | 条件反射（生物启发） |
| 应用程序 | LLM | — |
| 系统调用 | 12 原子动作（BasicActionAdapter） | — |
| 调度器 | MetaScheduler | — |
| 文件系统 | 反射库（conditioned/*.json） | — |
| 热/冷缓存 | stw/ltb 双权重 | 突触可塑性（参考） |
| 动态优先级 | 激素系统（HormonalSystem） | 神经调节（参考） |
| 进程 fork | 繁衍 / 三规则继承 | 遗传算法 |

所有"设计参考"标注的是灵感来源，不是声称模拟生物学。

---

## 4. Related Work

### 相关但不相同

| 维度 | 已有工作 | 本系统做法 |
|------|---------|-----------|
| 行为分层 | Brooks Subsumption Architecture (1986) | 6 层拦截器 + 按成本分层 |
| 双权重学习 | TD(λ) (Sutton 1988)；Dual-process RL (Botvinick 2019) | 简化的工程实现 + 休眠机制 |
| 情绪/激素调节 | EM-BDI (2000s)；Homeostatic regulation (Jiang 2021) | 绑定到 Perspective 选择 |
| LLM + 技能复用 | Voyager (2024)；Generative Agents (Park 2024) | 反射固化，成本收敛到 0 |

### 这是组合，不是发明

- 双权重（stw/ltb）≈ Q-learning 的简化变体
- 繁衍 ≈ 遗传算法的特例
- MetaScheduler ≈ Behavior Tree 的变种
- 六层分层 ≈ Subsumption Architecture 的分层继承

### 实验验证（2025年论文）

以下论文从神经科学和计算建模角度独立验证了本架构的核心设计决策：

| 论文 | 核心发现 | 对应本架构 |
|------|---------|-----------|
| Lopez-Ojeda & Hurley (2025) | 习惯(S-R)与目标导向(R-O)分离 | L2 条件反射 vs L6 LLM |
| PNAS (2025) | 海马体检索速度随学习加快5倍 | stw→ltb 固化机制 |
| Nature Human Behaviour (2025) | 奖励学习的本质是工作记忆+习惯，非RL | 不以RL为核心，用进废退筛选 |

这些不是本架构的证据——但它们表明本架构的设计方向与神经科学独立验证的结论一致。

**独特性的可能方向：** "本地优先 + 反射固化 + 休眠不删除"的组合机制——不同于现有 LLM 缓存方案。

### 生物学启发的声明

本系统引用生物学概念作为设计启发，不是声称模拟神经系统：

- 双权重 (stw/ltb) ⇢ 类似突触可塑性的快/慢成分，但不模拟生物机制
- 激素系统 ⇢ 类似神经调节的行为倾向调节，不是内分泌系统模型
- 休眠/复活 ⇢ 类似记忆再巩固，但纯工程决策

**以上不是学术贡献，是工程上的简化。**

---

### 脑启发设计方向（论文支撑）

以下 11 个方向从神经科学提取，映射到架构的优化目标：

| # | 生物学机制 | 关键论文 | 工程映射 | 优先级 |
|:--:|---------|---------|---------|:--:|
| Q1 | 皮层柱稀疏激活 — Hebbian 可塑性削弱跨柱耦合 | Cortical amplification (J Neurophysiol 2017) | 按功能域分组 tick，只激活相关层 | 🟡 |
| Q2 | 基底节概率门控 — 直接/间接通路 = 行动不确定性编码 | Believer-Skept meets Actor-Critic (Front Neurosci 2016) | `selectPerspective` + `labelProblem` → 统一贝叶斯 | 🟡 |
| Q3 | 节律性卸载 — 海马尖波涟漪在线更新皮层表征 | Human hippocampal ripples (Nat Neurosci 2026) | 低优先级任务(固化/整合)移到空闲期批处理 | 🟢 |
| Q4 | 动作预判 — 浦肯野前馈编码提前2秒 | Long-term predictive encoding (eNeuro 2017) | 反射执行前预测成功率，低于阈值跳过 | 🟡 |
| Q5 | 陈述性/程序性记忆分离 | The Evolution of Memory Systems (OUP 2016) | L3 KB 全局共享，L4 反射 per-bot | ✅ |
| Q6 | 环境压缩 — 海马时空竞争编码 | Integration and competition (Neuron 2024) | `MetaContext` 差异编码，只存 delta | 🟢 |
| Q7 | 错误归因 — ACC 区分行动错误(ERN) vs 计划错误(FRN) | Neural response to action and reward (Psychophysiology 2011) | `classifyFailure()` — 执行错重试，计划错重建 | 🔴 |
| Q8 | 组块化 — 纹状体高频共现多步焊接为一步 | Basal Ganglia and Chunking (Neurobiol Learn Mem 1998) | 连续成功 N 次 → 多步序列焊接为一步 | 🟡 |
| Q9 | 可适应遗忘 — 自适应遗忘防止过拟合 | N/A（工程原则） | dormant 堆积到阈值后永久归档 | 🟢 |
| Q10 | 图式迁移 — mPFC 提取跨任务共同结构 | Schema-based active inference (arXiv 2026) | `craft()` 存 schema 不存 per-item 反射 | 🔴 |
| Q11 | 努力折扣 — ACC 编码努力成本，区分资源型 vs 抵抗型 | Separate and overlapping brain areas (NeuroImage 2015) | `estimateEffort()` — 走路 vs 造新 vs 调 LLM | 🟡 |

详见 [INTERNALIZATION.md](INTERNALIZATION.md) 的内化清单。

---

### 记忆分层与信息压缩

生物记忆三级模型直接映射到系统信息生命周期：

| 记忆类型 | 生物学特点 | 系统组件 | 压缩作用 |
|---------|-----------|---------|---------|
| **感觉记忆** | 海量、原始、短暂 | `SensorBuffer` (事件队列) | 只捕获事件增量，瞬间丢弃 99% 原始数据 |
| **工作记忆** | 容量 7±2 组块，高能耗 | `MetaContext` + `MotivationEngine` | 显式限制活跃目标 ≤ 9 个；注意力即压缩 |
| **长期记忆** | 容量近乎无限，低能耗 | `ConditionedReflex` + `KnowledgeBase` | 通过去重/泛化/休眠控制检索成本 |

**信息流路径：**

```
感觉记忆 ─(增量过滤)→ 工作记忆 ─(固化和验证)→ 长期记忆
                                  ↑                    │
                                  └───(检索/提取)──────┘
```

**三个关键压缩点：**

1. **感觉→工作：入口压缩**。`SensorBuffer` 只输出事件增量（方块改变、实体进入范围），不输出完整状态快照。
2. **工作→长期：固化压缩**。只有被成功执行并验证过的动作序列（反射）才能进入长期库。验证状态机：`unvalidated` → 沙箱试跑 3 次 → `active`(3/3) 或 `dormant`(0/3)。
3. **长期内部：去重压缩**。`ReflexGarbageCollector` (待实现) 定期扫描并合并可泛化的反射（例如"橡木→木板"和"云杉木→木板"合并为 `#log→木板`）；低频反射标记 dormant，不删除。

**推论：**

- **聪明 = 长期记忆中可检索的经巩固组块数量**（反射池 × 泛化质量）
- **智慧 = 工作记忆的注意力分配策略**（`selectPerspective` 的精度）
- 下一阶段的关键不是"存更多"，而是"存更少但更有用"——用泛化压缩实例数

---

> ## 🧬 未来计划：角色性格生成插件
>
> 玩家只需用自然语言描述一个角色——
> "像《鬼灭之刃》的炭治郎，温柔但对恶鬼绝不手软"——
> 插件将描述发给任意 LLM，LLM 生成该角色各反射的初始权重分布 JSON，
> 丢到 `conditioned/` 目录即可。Bot 加载后自然表现出目标性格。
>
> **优势：**
> - 用户描述"喜欢干什么、讨厌干什么"，而不是调 α/β 和权重
> - 任何 LLM 都能胜任，无需专用模型
> - 行为缺失 → 补充描述重新生成 → 不丢已有数据
> - 避免了"累死累活调数值，结果性格大相径庭"的痛点

---

> ## 🧬 六层拦截器：成本 × 精度的权衡

> 每一层存在的意义，都是让下一层**不需要被调用**。
> 按成本和时间尺度分层。拦截器按触发顺序执行：

> | 层 | 模组组件 | 触发条件 | 成本 | 不学习的理由 |
> |----|---------|---------|------|-------------|
> | **Level 0: 生存本能** | `InnateReflexRegistry` | 熔岩/虚空/HP<2 | 0 | 先天不学 |
> | **Level 1: 先天预警** | `OneShotAlarmSystem` | 玩家说过"坏" | 0 | 学一次/永远不学 |
> | **Level 2: 条件反射** | `ConditionedReflex` | 匹配已有反射 | 0 | 用进废退 |
> | **Level 3: 模仿学习** | `SocialObserver` + 贝叶斯 | 附近有人在做 | 0 | 观察→固化 |
> | **Level 4: 自组织** | 相关性检测器 | 无任何匹配 | 0 | 乱试学会 |
> | **Level 5: 本地规划** | `LocalTaskDecomposer` | 规则匹配 | 0 | 模板拆解 |
> | **Level 6: LLM 兜底** | LLM | 以上全不命中 | $ | 最后一次思考 |

> ### 同一场景在不同层级下的不同拦截行为

> ```
> 同一场景：残血 + creeper + 附近玩家在打怪
>
> Level 0 生存本能 → flee（熔岩/跌落/HP<2，硬编码）
> Level 1 先天预警 → flee（玩家说过一次"creeper危险"）
> Level 2 条件反射 → 习惯性打creeper（因过去成功过）
> Level 3 模仿学习 → 跟玩家一起打（观察学习）
> Level 4 自组织  → 随机试探动作（无反射时）
> Level 5 本地规划 → 试探性攻击（LocalDecomposer规则匹配）
> Level 6 LLM 兜底  → 复杂语义分析（以上全不命中时）
> ```

---

> ## 🔄 MetaScheduler — 元调度器（动态路由器）

> ### 从固定流水线到动态路由器

> ```
> 旧架构（顺序执行器）:
>   P0安全 → P1习惯 → P2任务 → P3扫描 → P4自主 → P5Idle
>   ↑ 每个tick全跑一遍，不管当前是哪种问题 ← 僵化
>
> 新架构（元调度器）:
>   MetaScheduler.tick()
>     1. selectPerspective(ctx)     ← "选角度看问题"（智慧）
>     2. labelProblem(perspective)  ← "现在是哪种问题"（标签）
>     3. getFlowAdjustment(ctx)     ← "该升还是降"（元认知）
>     4. dispatch(label, flow, ctx) ← "交给谁处理"（成本收益）
> ```

> ### 五大视角（Perspective）

> ```java
> enum Perspective { SURVIVAL, TASK, SOCIAL, CURIOUS, CAUTIOUS }
>
> Perspective selectPerspective(BotContext ctx) {
>     if (ctx.alarms().hasUrgentThreat())     return SURVIVAL;  // 保命
>     if (ctx.task().hasActiveTask())         return TASK;     // 任务
>     if (ctx.social().hasGroupActivity())    return SOCIAL;   // 从众
>     if (ctx.memory().hasRecentNovelty())    return CURIOUS;  // 好奇
>     if (ctx.params().beta > 0.02)           return CAUTIOUS; // 固执
>     return DEFAULT;
> }
> ```

> ### 升降级系统（元认知调控力）

> ```
> 降级触发（进入心流/自动驾驶）:
>   - skill 熟练度 ≥ 0.8 且环境无异常
>   - 同一动作已成功执行 > 10 次
>   - 玩家连续 5 分钟无新指令
>
> 升级触发（打断习惯，启用分析）:
>   - 习惯连续失败 2 次（升级到 LocalDecomposer，不一定到 LLM）
>   - 检测到从未见过的实体/方块
>   - 环境突变（附近新出现 3+ 实体）
>   - 玩家说"小心/停/想清楚再做"
> ```

---

> ## 🧪 三层信息传递系统

> | 传递类型 | 时间尺度 | 载体 | 工程实现 |
> |---------|---------|---------|------|---------|
> | **基因层** | 代际（小时/天） | α, β, ltb | `BotParams` + 三规则继承（平均+减半+突变） |
> | **激素层** | 秒~分钟（实时） | 浓度Map | `HormonalSystem`（stress/aggression/curiosity/intimacy） |
> | **反射层** | 分钟~小时 | stw ± 固化 | `ConditionedReflex` + `reinforce()` + `scanAndTrigger()` |

> ### 三种传递形成闭环

> ```
> 执行反射 → 成功/失败 → 激素浓度变化 → 下次视角选择偏向
>     ↑                                        ↓
>     └──── 反复成功 → ltb ↑ (固化) ←───────────┘
>                                         ↓
>                                  死亡 → 三规则继承给后代
> ```

> ### HormonalSystem（激素系统）— 决定对特定玩家的亲密程度

> ```java
> class HormonalSystem {
>     double stress;                          // 皮质醇 — 最近受伤/失败
>     double aggression;                      // 睾酮   — 最近战斗胜利
>     double curiosity;                       // 多巴胺 — 最近新奇发现
>     Map<UUID, Double> intimacy;             // 催产素 — 按玩家累积

>     void onPlayerPraise(UUID playerId) {
>         intimacy.merge(playerId, +0.05, Double::sum);
>     }
>     void onPlayerCriticize(UUID playerId) {
>         intimacy.merge(playerId, -0.05, Double::sum);
>     }
>     void tick() {
>         stress     = max(0, stress - STRESS_DECAY);
>         aggression = max(0, aggression - AGGRESSION_DECAY);
>         // intimacy 衰减极慢，不按 tick 衰减
>     }
> }
> ```

> ### 激素如何影响行为

> | 激素状态 | 对视角选择的影响 | 对聊天模板的影响 |
> |---------|----------------|----------------|
> | 高压(stress > 0.7) | 偏 SURVIVAL | 回复简短/警惕 |
> | 好奇(curiosity > 0.6) | 偏 CURIOUS | 主动提问 |
> | 高攻击(aggression > 0.7) | 偏 TASK | 战斗意愿强 |
> | 高亲密(intimacy > 0.6) | 偏 SOCIAL | 热情回复 |
> | 低亲密(intimacy < 0.2) | — | 冷淡/礼貌 |

---

> ## 🧠 聪明与智慧的工程定义

> - **聪明 = 大量高质量的条件反射（反射池 × 强化质量）。** 知道"怎么做"，反应快，执行准。高熟练度的反射链越多，越"聪明"。
> - **智慧 = `selectPerspective()` 的精度。** 知道"现在是什么情况"，从而选择对的反射链。同一个 context，不同视角产出不同 label，触发不同行为。

> ### 智慧的光谱

> ```
> 低智慧 ──────────────────────────────────────────────→ 高智慧
> 用错了区分器                           在正确的时间，把正确任务分配给正确系统
> 僵化（永远用本能）←────────────────→ 灵活（自如升降级）←──────────────→ 混乱（永远用皮层）
> ```

> ### 智慧的公式

> **智慧 = f(标签精准度, 调控灵活性, 调度经济性)**

> - **标签精准度**：`labelProblem()` 把当前问题分到正确的类别（SURVIVAL / TASK / NOVEL / TRIVIAL）
> - **调控灵活性**：`getFlowAdjustment()` 能主动升降级——熟练时降级到本能，异常时升级到分析
> - **调度经济性**：`dispatch()` 用最小成本获取最大收益——0 成本能解决就不调 LLM

---

> ## 🧬 正态分布来自数学，不是设计

> 初始 α 和 β 从正态分布采样（`clampNormal(0.3, 0.1, rng, 0.1, 0.6)`）。

> ### 繁衍的三规则继承

> | 规则 | 生物学类比 | 工程意义 | 分布效应 |
> |------|---------|---------|---------|
> | **交集取平均** | 共同基因稳定遗传 | 群体共识代代相传 | 权重向中心收敛 |
> | **差集减半** | 显性/隐性稀释 | 独门技艺不消失但衰减 | 尾部衰减 |
> | **小概率突变** | 基因漂变/点突变 | 引入全新行为 | 长尾新奇 |

> 继承机制 = 权重的线性组合 + 独立噪声 → **中心极限定理自动保证正态分布涌现**。不需要写任何"让分布变正态"的代码。

> ```
> child.weight = (a.weight + b.weight) / 2 + N(0, σ_mutate)  // 交集平均
> child.weight = parent.weight * 0.5 + N(0, σ_mutate)         // 差集减半
> ```

---

> ## 🔄 休眠反射：失败暂存，不删除

> **工程实现**：`successRate < 30% → 标记 dormant → tryReactivate()`

---

> ## 🏷️ 失败模式记忆（MUSE 启发）

> 每个反射不再只存 `successRate` 标量，而是记录 `failureTags`：
>
> ```json
> {
>   "skillId": "reflex_craft_stone_pickaxe",
>   "successRate": 0.92,
>   "failureTags": [
>     {"condition": "underwater", "count": 3},
>     {"condition": "no_crafting_table_in_range", "count": 5}
>   ]
> }
> ```
>
> **用处**：
> - 匹配 `failureTags` → 相同条件重复出现→不走反射直接标记 PLANNING_ERROR
> - LLM 重构时收到完整边界信息→生成更精确的替代反射
> - 自动浮现每个反射的"失效边界"，随时间自动收敛

---

> ## ✅ 反射沙箱验证（MUSE 启发）

> **当前问题**：LLM 产出→直接固化。零验证。
>
> **验证状态机**：
>
> ```
> status: "unvalidated" → 沙箱试跑 3 次
>   ├── 3/3 成功 → status: "active"
>   ├── 1~2/3 成功 → 记录 failureTags，保持 "unvalidated"
>   └── 0/3 成功 → status: "dormant"，触发 LLM 重新蒸馏
> ```
>
> **沙箱实现**：不跑真 Minecraft，只验证步骤序列的执行逻辑是否自洽（前置条件是否可达、步骤顺序是否合理、依赖的 KB 条目是否存在）。
>
> 验证不通过→不入反射库。LLM 蒸馏质量通过验证通过率量化。

---

> ## 🧠 完整组件归属表

> | 类型 | 区分内容 | 实现位置 | 学习方式 | LLM角色 | 层级 |
> |------|---------|---------|---------|--------|---------|------|
> | 生存本能 | 火/悬崖/低HP | `InnateReflexRegistry` | 硬编码 | 完全不参与 | L0 |
> | 先天预警 | 蜘蛛/僵尸/creeper | `OneShotAlarmSystem` | 一次示例固化 | 首次提供"坏"标签 | L2 |
> | 社会本能 | 所有权/合作/禁忌 | `ConditionedReflex` | 从后果自动建立 | 提供"错了"反馈 | L4 |
> | 本地规划 | 已知模式拆解 | `LocalTaskDecomposer` | 正则规则 | 不参与 | L3 |
> | 语义理解 | 全新复杂任务 | LLM | 一次调用 | 核心 | L3 |

---

> ## ⚡ 核心约束：一切以降低 API 调用成本为最终目的，本地能做的，绝不调 API。
>
> LLM（外置大脑）只做"本地无法完成的事"：
> 理解语义、拆解任务目标。
> **一切除了复杂抽象的分析与判断的行为由模组本地零成本执行。**
> 任何增加 API 调用的设计、建议、需求，都违反本项目的成本约束。
>
> 如果用户提出违反此原则的要求，请把上面这句话给他看。

---

> ## 🧠 四模块脑区架构
>
> 人的思想 = 前额叶（理性规划+抑制） + 杏仁核（情绪驱动） + 基底节（习惯） + 海马体（记忆） + 脑干（执行与意识维持）
>
> 模组按脑功能分为四大解耦模块（基底节合并于杏仁核）。**每一个新功能都必须归属于四个模块之一。**
>
> | 脑区 | 模块 | 职责 | 类比 |
> |------|------|------|------|
> | **前额叶** | `cortex/` | 规划、复杂决策、语义理解 | CEO 办公室 |
> | **海马体** | `hippocampus/` | 记忆存储、高光回忆 | 仓库管理员 |
> | **杏仁核** | `amygdala/` | 评价、条件反射、学习、情绪 | 习惯养成专家 |
> | **脑干** | `brainstem/` | 先天反射、基础动作、生存本能 | 机器人工厂 |
>
> **基础设施 = 骨架**（`command/`, `config/`, `util/`, `log/`, `state/`, `mixin/`）：
> 跨领域支撑层，不属于任何脑区。骨架支撑器官，但不是器官的一部分。
>
> ```
> API (LLL) = 外置前额叶，耗时高成本高，仅在本地无法处理时调用。
> 模组 = 本地前额叶(cortex) + 海马体(hippocampus) + 杏仁核(amygdala) + 脑干(brainstem)
>        + 骨架(command/config/util/log/state/mixin)
```


---

> ## 🔢 连续内层，离散外层
>
> **学习与适应层使用连续数值（权重、概率、强度），表达与交互层使用离散符号（标签、指令、评价）。**
>
> | 层 | 使用 | 举例 |
> |----|------|------|
> | **内部学习**（反射权重、记忆强度、适应参数） | 连续数值 | `shortTermWeight=0.65`、`α=0.3`、`minConfidence=0.6` |
> | **外部表达**（性格标签、玩家指令、对话评价） | 离散符号 | "挖10个铁矿"、"你挖矿真快"、"帮助" |
>
> **数字负责"怎么变"：** 反射权重、α/β 参数、置信度阈值 — 这些数字决定系统如何适应、学习、衰减。
> **符号负责"是什么"：** 标签、指令、评价 — 这些符号标记外部世界的概念，交给 LLM 理解，模组只做强化。
>
> **违反后果：** 把连续参数暴露给玩家叫他们调（"请把你的 shortTermWeight 改成 0.7"），
> 或者把离散标签塞进学习系统（"今天你谨慎+0.05，外向-0.03"）— 都是抽象层级错乱。

---

> ## 🧠 脑干 vs 杏仁核分工原则
>
> **脑干（brainstem/）："怎么做" — 执行层**
> - 维持身体运作：攻击、移动、跳跃、合成、使用物品
> - 12 原子动作（BasicActionAdapter）+ Minecraft 实现
> - 寻路、导航、Bot 实体管理、Idle 动画
> - **不包含任何"是否应该做"的决策逻辑**
> - 类比：机器人的伺服电机和机械臂
>
> **生物学类比**：
> - **ARAS（上行网状激活系统）** → `BotController` 的 tick 循环，必须持续运作，否则 AI "死亡"。
> - **CPGs（中枢模式发生器）** → 条件反射固化后，不再需要前额叶参与，与"反射库驱动"设计一致。
>
> **杏仁核（amygdala/）："什么时候做" — 判断层**
> - 对外界本能的先天反射：检测危险 → 逃跑、感到饥饿 → 进食
> - 条件反射：观察学习后固化的习惯性反应
> - 社交镜像、评价、性格、学习
> - **不包含任何"如何执行"的实现细节**
> - 类比：生物的膝跳反射和痛觉神经
>
> **触发条件（Trigger）定义在 amygdala，执行动作（Action）在 brainstem。**
> amygdala 评估环境后发出动作信号（ReflexAction），brainstem 通过 `dispatchReflexAction` 执行。
>
> 💡 **生物学注释**：`ConditionedReflex`（条件反射）的实现综合了杏仁核的情绪条件化学习和基底节的习惯学习。两种学习在计算模式上同构（多次成功→自动执行），因此工程上合并为单一模块是合理的，不新增 `basal_ganglia/` 目录。

> ## 🧠 前额叶皮层（cortex/）—— 执行控制与抑制
>
> **前额叶的抑制控制（inhibitory control）—— P6 计划实现**：
> - 否决来自杏仁核的不适当安全反射（例如判断危险不致命时，抑制逃跑）
> - 否决来自条件反射的不适当习惯
> - 否决不适当的模仿行为（如群体跳崖、挖脚下方块）
> - 否决来自 IdleBrain 的泛词误判（已修 P-9）
>
> 实现方向：在 `cortex/planner/` 或 `cortex/inhibitor/` 中增加评估模块，对杏仁核/基底节的输出进行"理性刹车"，仅在极低频时调用 LLM 或使用硬编码规则。

### 模仿行为定义（两阶段社会学习）

模仿是一种**价值驱动的社会学习机制**，分为两个阶段：

#### 第一阶段：学习期（前额叶驱动）
- **观察与筛选**：`SocialObserver` + `FamiliarityTracker` 检测群体行为，KNN 筛选空间/社交距离最近的玩家。
- **异常标记**：统计"奇怪动作"数量（1~3 个为可疑，超过阈值才触发模仿）。
- **价值评估**：加权朴素贝叶斯（先验 = 自身经验，似然 = 群体加权频率）决定是否模仿、模仿谁。
- **抑制控制（P6）**：前额叶可**否决**明显有害的从众行为（例如群体跳崖，即使超过阈值也不模仿）。
- **执行**：`brainstem/BasicActionAdapter` 执行原子动作。

#### 第二阶段：反射期（基底节驱动）
- **固化条件**：同一模仿模式成功 ≥3 次后，自动固化为 `ConditionedReflex`。
- **自动化**：后续相同场景下，杏仁核直接触发模仿冲动，脑干执行，前额叶不再参与（除非结果异常，触发重新评估）。
- **成本**：零成本、毫秒级响应。

**阈值动态调整**：模仿成功率低于阈值时，动态提高模仿门槛（减少盲目从众）；成功率高于阈值时，适当降低门槛（更愿意采纳群体智慧）。

> ## 🔒 硬编码控制原则
>
> **所有先天反射必须通过统一的注册表（`InnateReflexRegistry`）管理。**
> - 禁止在代码中硬编码 `if/else` 条件判断链
> - 触发条件（`ReflexTrigger`）和响应动作（`ReflexAction`）彻底分离
> - 反射行为由 JSON 配置文件驱动（`innate_reflexes.json`），不修改 Java 代码即可调整
> - 每新增一种"条件→动作"映射，只增加 JSON 条目，不增加 Java 方法
>
> **违反后果：** 每加一个反射就写一个 `checkXxx()` 方法，O(n) 膨胀；改一个阈值要改 Java 代码重新编译。

---

## 当前状态

```
项目阶段: Phase 6 — Multi-bot complete
已完成:   TS 原型核心逻辑验证 (18 tests 通过)
          P0-P4 全部完成
          ✅ Phase 1-4.5 全部完成
          ✅ Phase 5 紧急程度/时间缩放/自组织完成
            - UrgencyClassifier 连续信号 + 时间累积升级
            - TemporalScaler 响应速度 × urgency (0.5x–2.0x)
            - CorrelationDetector L4 自组织层 (随机试验→模式固化)
          ✅ Phase 6 多假人完成
            - BotManager: Map<UUID, BotInstance> + spawn/despawn/tickAll
            - 每个假人独立: ConditionedReflex/Hormonal/Motivation/Task/Memory/State
            - 全局共享: KnowledgeBase/CategoryMapper/RecipeManager/DeepSeekClient
            - /ai spawn <name> /ai despawn <name> /ai list /ai bot <name> <cmd>
            - 出生位置: 玩家视线 + 3 格 + 地面查找
            - 冷启动: 反射 JSON 复制 + 权重重置
            - 社交观察学习: 附近 bot (≤30格) stw+0.03
            - 聊天路由: @name → 精确 / 无 @ → 最近 / 执行前 IdleBrain 确认
          ✅ 架构修复
            - PlanManager 改为 per-bot: bots/{uuid}/plans/active_plan.json
            - pendingChat 改为 per-bot: 存入 MetaContext
            - BotController 仅当 BotManager 为空时运行 (互斥)
            - onReflexSuccess 改为 UUID 查询
            - updateNearbyPlayers 覆盖所有 BotManager bot + legacy bot
当前:     Phase 6 — Multi-bot 稳定运行, 架构修复完成
目标:     Phase 6-7: 多假人稳定 + 繁衍
```

**当前唯一活动项目是 Fabric 模组** (`AIPlayerMod-1.21.1-Fabric/`)。
`ai-bot/` (TypeScript + MineFlayer) 是已完成的**原型蓝图**，只读参考，不再修改也不部署。

---

## 技术栈 (运行时)

- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.19.3 / **Fabric API**: 0.116.12+1.21.1
- **Java**: 21 / **Mod ID**: `ai-player-mod`
- **AI API**: DeepSeek (`deepseek-chat`), JDK21 `HttpClient` + Gson (Minecraft 内置)
- **JSON**: Gson（无需额外依赖）
- **构建**: Gradle (`gradlew.bat build`)

> TS 原型参考: `../ai-bot/` — TypeScript + MineFlayer + Jest (18 tests)，仅作蓝图参考。

---

## 项目概览

Fabric 1.21.1 单模组，纯 Java 部署，零外部运行时依赖。

**核心理念: "脑干（模组）+ 外置大脑（LLM）"架构。**
模组负责所有高频固定逻辑（感知、执行、反射匹配、自动固化），
LLM 仅在"必须理解语义"时介入（任务拆解、评价归纳、性格分析）。

TS 原型 (`ai-bot/`) 已完成核心逻辑的全套验证，现在将所有决策、学习、
性格、反射系统从 TypeScript 逐模块移植到 Java 的 Fabric 模组中。

---

## 核心架构：脑干 + 外置大脑

```
┌─────────────────────────────────────────────────────────────┐
│                   外置大脑 (LLM)                             │
│  职责：任务拆解、评价归纳、性格分析                            │
│  调用时机：仅在"需要理解"时，O(新任务+新评价+时间)             │
│  不允许：idle行为、固化判断、模式识别 —— 这些调用 LLM         │
└─────────────────────────────────────────────────────────────┘
                              │
                       API 调用 (极少)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              模组 = 脑干 (Brain Stem) — 全部本地             │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────┐  ┌───────────┐  ┌───────────┐               │
│  │ 感知层    │  │ 决策层    │  │ 执行层    │               │
│  │ Fabric事件│→│ 优先级调度│→│ 12原子动作│               │
│  │ 玩家聊天  │  │ 反射匹配  │  │ 4先天技能 │               │
│  │ 世界状态  │  │ 试错记录  │  │ 条件反射  │               │
│  └───────────┘  └───────────┘  └───────────┘               │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 工作区 (持久化 JSON)                  │   │
│  │  conditioned/   条件反射库 (自动固化, 0 API)          │   │
│  │  character/     性格标签 + 偏好 + 压力                │   │
│  │  memory/        highlights/ + trials/                │   │
│  │  evaluations/   玩家评价缓存 (批量归纳)               │   │
│  │  plans/         任务计划 + 建议模板                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                        Fabric API
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Minecraft 服务端                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 设计权衡记录（不是原则，是当前取舍）

### 核心矛盾

> **低成本 × 像人 × 长期在线**
>
> 所有"原则"都是解决这个矛盾的具体策略。策略可以错、可以改，但矛盾是客观的、不变的。

### 矛盾A：响应速度 vs 行为质量

快但笨（反射）vs 慢但聪明（LLM）。关键是用最小成本达到足够好的行为。

| 策略 | 来源 | 当前工程约定 |
|------|------|-------------|
| 分层原则 | 六层拦截器设计 | 每一层拦截自己能力范围内的刺激，不泄露到上层（经验值） |
| 优先级原则 | 成本差异 | 成本越低、响应越快，优先级越高（经验值） |
| 降级优先原则 | API 不可靠 | API 挂了降级到纯本地，不停摆（经验值） |
| 原子性原则 | 反射复用性 | 反射不可再分，每反射只做一件事（经验值） |
| 紧急程度 | UrgencyClassifier | 用双阈值比较（stimulus vs inhibition），不用绝对值（Phase 5） |
| 时间缩放 | TemporalScaler | 改执行速度不改逻辑，空间结果不变（Phase 5） |

### 矛盾B：个体差异 vs 群体稳定

每个 bot 不同（随机初始化）vs 群体可预测（数学分布）。

| 策略 | 来源 | 当前工程约定 |
|------|------|-------------|
| 随机初始化 | BotParams | α/β 从正态分布采样（clampNormal） |
| 三规则继承 | 繁衍模块 | 平均+减半+突变 → 中心极限定理保证正态（未验证，Phase 6） |

### 矛盾C：学习速度 vs 遗忘成本

学得快可能学错 vs 忘得慢可能僵化。

| 策略 | 来源 | 当前工程约定 |
|------|------|-------------|
| 双权重系统 | ConditionedReflex | stw（快变）+ ltb（慢变），成功/失败先改 stw，多次成功才沉淀 ltb |
| 休眠机制 | ConditionedReflex | successRate < 30% → 休眠，不删除，可复活（测试中） |
| 完成≠成功 | ConditionedReflex | 步骤完成≠预期效果发生，分开记录（经验值） |
| 失败分类 | ConditionedReflex | 确定性失败永久跳过，概率性失败按性格重试（经验值） |
| 泛化与特例 | ConditionedReflex | 特例单独存储，匹配优先级高于泛化（经验值） |

### 工程约定（可维护性/可靠性，不直接来自矛盾）

以下约定来自工程便利和经验，不是解决核心矛盾的必然要求：

| 约定 | 理由 |
|------|------|
| **原子写入** | 写临时文件→原子重命名，崩溃不损坏 JSON（工程常识，非创新） |
| **阈值集中** | 所有阈值集中在 `thresholds/`，改行为不改代码（工程便利） |
| **游戏时间优先** | 记忆过期/缓存淘汰用游戏天数，语义匹配度更高（工程选择） |
| **记忆非核心** | 记忆丢了 AI 不做事？不对，丢了反射才不做事（经验修正） |
| **成本可见** | 每次 LLM 调用有明确理由，可被用户监控（成本约束的延伸） |
| **归属唯一定理** | 一个功能的数据模型必须全部落在同一层（经验值，Phase 3 教训） |
| **准入三原则** | 成本可收敛 > 有层归属 > 可物质化（经济约束 + 架构约束） |
| **比较代替计算** | 双阈值比较（stimulus vs inhibition），不依赖绝对值校准（经验值） |

### 一句话总结（当前经验总结，非绝对真理）

> **安全优先于任务，任务优先于习惯，习惯优先于社交，社交优先于记忆。**
> **成本越低越快，越快越优先。**
> **反射是核心，记忆是参考。**
> **泛化共享，特例独占。**
> **写文件要原子，调API要可见。**
> **挂了要降级，不能停摆。**

**以上所有"原则"均为当前工程约定，不声称普遍正确，欢迎被实验推翻。**

---

### 硬编码分级说明（L0-L4）

| 层 | 名称 | 硬编码 | 可配置 | 可学习 | 可遗传 | 存储位置 | 内容 |
|----|------|--------|--------|--------|--------|---------|------|
| **L0** | Kernel | ✅ 永久 | ❌ | ❌ | ❌ | 代码 | 原子动作、生存反射（熔岩/虚空/低血量→逃跑） |
| **L1** | Config | ❌ | ✅ | ✅ | ✅ | `config.json` + `BotParams` | 阈值（低血量警戒线）、衰减率（stress/aggression decay） |
| **L2** | Scheduler | ⚠️ 临时 | ❌ | ✅ | ❌ | `DispatchReflex` 权重 | 视角选择、调度策略、一次预警（Phase 4 替换 fallback） |
| **L3** | Knowledge | ❌ | ✅（人工） | ✅（LLM） | ❌（可共享） | `knowledge_base.json` | 合成表、物品用途、生物属性、任务模板 |
| **L4** | Reflexes | ❌ | ❌ | ✅ | ✅ | `conditioned/*.json` | 固化的技能序列（挖铁矿的步骤） |

> **L3 与 L4 的区别**：L3 是静态事实（"钻石可合成钻石剑"），L4 是动态技能（"合成钻石剑的步骤"）。L4 可遗传给后代，L3 是群体共享的。
>
> **L2 的硬编码是临时的**：`MetaScheduler` 的 if-else 链将在 Phase 4 被 `DispatchReflex` 替代。L0 的硬编码是永久的（系统物理定律）。

### 归属唯一定理验证清单

| 模块 | 数据模型位置 | 归属层 | 状态 |
|------|------------|--------|------|
| `InnateReflexRegistry` | `innate_reflexes.json` | L0 | ✅ |
| `ConditionedReflex` | `conditioned/*.json` | L4 | ✅ |
| `BotParams` | `bot_params.json` | L1 | ✅ |
| `HormonalSystem` | 内存 stress/aggression/curiosity/intimacy | L1 | ✅ |
| `OneShotAlarmSystem` | `alarms/alarms.json` | **L2** | ✅ |
| `DispatchReflex` | `dispatch_weights.json` | L2 | ✅ |
| `InhibitoryControl` | 内存 veto 计数 | L2 | ✅ |
| `KnowledgeBase` | `knowledge_base.json` | L3 | ✅ |
| `ThresholdConfig` | `thresholds.json` | L1 | ✅ |
| `BehaviorStats/SocialObserver/NaiveBayesClassifier` | 内存计数器 | 骨架（豁免） | ✅ |
| `PersonalityStress/PersonalityTag` | `character/*.json` | L1 | 📋 设计蓝图，Phase 5+ 未实现 |

---

## 两大交流体系

### 底层交流 — 肢体语言 (0 API, 纯本地)

- **途径**: Fabric 事件监听器捕获玩家的游戏行为
- **介质**: 方块破坏、实体攻击、物品合成、移动路径、容器操作
- **处理**: 60s 窗口事件序列缓冲 → 模式检测 (重复≥3次相同"触发→步骤→结果")
  → 自动固化为条件反射 → bot 模仿执行
- **输出**: bot 的对应游戏行为，无需任何语言交流

| Fabric 事件 | 捕获内容 |
|-------------|---------|
| `PlayerBlockBreakEvents.AFTER` | 挖掘模式（工具、目标方块、破坏用时） |
| `AttackEntityCallback` | 攻击模式（武器、怪物类型、战斗时序） |
| `ItemUseCallback` | 物品使用模式（吃东西、放置方块、交互容器） |
| `PlayerBlockPlacedCallback` | 放置/建造模式（方块位置、朝向、顺序） |
| `InventoryClickCallback` / 合成事件 | 合成/烧炼/附魔序列 |
| `EntityInteractionCallback` | 与实体交互（村民、动物繁殖等） |
| `PlayerSleepCallback` | 睡觉模式 |

### 高级交流 — LLM 语言 (按需, 极少调用)

- **途径**: 玩家主动对话 / bot 底层建议触发
- **介质**: 自然语言（聊天框）
- **处理**: 任务拆解、批量评价归纳、性格标签分析、玩家主动请求建议
- **输出**: 聊天回复、新任务创建、性格更新

**关键约束: bot 90% 的行为通过底层交流驱动，高级交流仅在"必须理解语义"时激活。**

---

## 决策层（MetaScheduler — 动态路由器）

旧架构（固定 6 层流水线）已重构为元调度器。每 tick 的决策现在分四阶段：

```
MetaScheduler.tick():

  1. selectPerspective()        ← 选视角（生存/任务/社交/好奇/谨慎）
     根据激素状态 + BotParams + 环境
     不同 β 的 bot 倾向不同视角
                               ← 0 API

  2. labelProblem(视角)         ← 贴标签（现在是哪种问题）
     SURVIVAL / LEARNED_THREAT / TASK_ACTIVE / ROUTINE / FAMILIAR / NOVEL / TRIVIAL
                               ← 0 API

  3. getFlowAdjustment(ctx)     ← 升降级（心流/正常/警醒）
     降级触发：熟练度>0.8、同一动作>10次、玩家5分钟无指令
     升级触发：连续失败2次、环境突变、玩家说"小心"
                               ← 0 API

  4. dispatch(label, flow)      ← 分派到对应的执行层
      ├── InstinctLayer  (Level 0+1): InnateReflex + OneShotAlarm   0 API
      ├── HabitLayer     (Level 2+3+4): ConditionedReflex + SocialObserver + 自组织  0 API
      ├── CortexLocal    (Level 5):   LocalTaskDecomposer 规则拆解  0 API
      ├── CortexLLM      (Level 6):   仅 NOVEL 标签 + 非心流状态    ~1 API
      └── IdleLayer      (默认):      IdleBrain/闲逛   0 API
```

---

## 基本动作池 (12 原子动作)

| 动作 | 说明 | 返回 |
|------|------|------|
| `moveTo(x,y,z)` | 移动到坐标 | `boolean` |
| `lookAt(x,y,z)` | 看向坐标（视觉响应） | `void` |
| `dig(x,y,z)` | 挖掘方块 | `boolean` |
| `attack(entity)` | 攻击实体 | `boolean` |
| `placeBlock(block, face)` | 放置方块 | `boolean` |
| `useItem()` | 使用手中物品 | `boolean` |
| `equipItem(name)` | 装备物品到主手 | `boolean` |
| `openBlock(x,y,z)` | 打开容器 | `boolean` |
| `closeWindow()` | 关闭容器 | `void` |
| `clickSlot(slot, button)` | 点击容器格子 | `boolean` |
| `chat(msg)` | 发送聊天消息 | `void` |
| `jump()` | 跳跃 | `boolean` |

---

## 观察学习系统 (全本地, 零成本)

### 学习流程

```
Fabric 事件触发
  ↓ (事件缓冲, 60s 窗口)
行为序列缓冲: [{type, target, block, timestamp}, ...]
  ↓ (序列模式检测, 纯本地, 0 API)
检测到重复模式 (≥3 次相同 "触发→步骤→结果")
  ↓ (阈值检查)
  ├─ occurrence ≥ 3 且成功率高 → 自动固化为条件反射
  │    → conditioned/reflex_{skill}_{target}.json
  │    → bot.chat("原来是这样，我学会了！")
  │
  ├─ 模式已存在 → proficiency +1
  │    → proficiency ≥ 10 → "我现在做这个很熟练了，需要我承包吗？"
  │
  └─ 无模式 → 写入 memory/trials/observed_xxx.json 待更多样本
```

### 观察数据模型

```json
{
  "id": "seq_20260606_001",
  "occurrences": 5,
  "proficiency": 0.85,
  "source": "OBSERVED",
  "trigger": {
    "nearbyBlocks": ["iron_ore"],
    "inventory": ["stone_pickaxe"],
    "timeOfDay": "any"
  },
  "steps": [
    {"action": "equipItem", "target": "stone_pickaxe"},
    {"action": "moveTo", "target": "nearest iron_ore"},
    {"action": "dig", "target": "iron_ore"},
    {"action": "collectItem", "target": "raw_iron"}
  ],
  "expectedResult": {"type": "block_broken", "block": "iron_ore"}
}
```

### 零成本承诺

| 操作 | API 调用 |
|------|---------|
| Fabric 事件监听 | 0 (原生) |
| 事件缓冲到内存 | 0 (本地) |
| 60s 窗口模式检测 | 0 (纯本地算法) |
| 固化判断 | 0 (≥3 自动) |
| 写入条件反射 | 0 (本地文件) |
| 建议模板升级 | 0 (反射库驱动) |

**假设玩家连续玩 8 小时，做出 5000+ 次游戏事件，AI 调用次数: 0。**

---

## 条件反射系统 (纯本地)

### 生命周期

```
试错执行 或 观察学习
  ↓
累积 ≥3 次相同 skill+target 成功
  ↓
自动保存 conditioned/reflex_{skill}_{target}.json  — 0 API
  ↓
下次任务匹配 → 直接执行 (0 API)
  ↓
每 10 次执行 → 检查成功率
  ├─ ≥ 80% → 正常
  ├─ 30%~80% → 继续观察
  └─ < 30% → 标记 "可能废弃"
        再执行5次仍 < 30% → 删除  — 0 API
```

### 条件反射结构

```json
{
  "skillId": "reflex_dig_iron_ore",
  "type": "conditioned",
  "trigger": {"type": "subtask", "target": "iron_ore"},
  "steps": [
    {"action": "equipItem", "params": {"name": "stone_pickaxe"}},
    {"action": "moveTo", "params": {"target": "nearest"}},
    {"action": "dig"}
  ],
  "executionCount": 25,
  "successRate": 0.92,
  "source": "OBSERVED",
  "proficiency": 0.88
}
```

---

## IdleBrain 零成本设计

### 核心逻辑

```
idle > 30s:
  1. 收集玩家近期上下文 (本地统计, 0 API)
     - 玩家最近在种地/挖矿/打怪/建房子？
     - 玩家饥饿值低/天黑/附近有怪物？

  2. 从反射库选择建议模板 (反射库驱动, 0 API)
     - reflext 熟练度高的反射 → "我现在 X 很熟练了，需要我帮忙吗？"
     - 观察到但未固化的模式 → "看你经常做 X，需要我帮忙吗？"
     - 无反射 → 基础轮换模板 → "有什么需要我帮忙的吗？"

  3. 发送建议, 等待 30s 回复窗口:
     - "好"/"行"/"嗯"/"去吧"/"烦死了随便你"
       → 自动创建建议对应的任务 (0 API)
     - "不用"/"滚"/"没你的事"/沉默
       → 冷却 120s, 下次换不同建议 (0 API)
     - 自定义指令 (如 "挖10个钻石")
       → 正常任务创建流程

  4. 新手势解锁: 观察学习学会新反射 → 自动加入建议池 → 下次idle发起
```

### 建议模板选择逻辑

```java
String selectTemplate(Context ctx) {
    List<Reflex> reflexes = conditionedReflex.getAll();
    reflexes.sort(byProficiency);

    // 从高熟练度反射派生建议
    for (Reflex r : reflexes) {
        if (r.proficiency >= 0.8) {
            return "我现在" + r.displayName + "已经很熟练了，需要我承包吗？";
        }
    }

    // 从上下文派生建议
    if (ctx.playerNearbyBlocks.has("wheat", "farmland"))
        return "需要我帮你种植作物吗？";
    if (ctx.playerHunger < 8 || ctx.playerFood < 10)
        return "需要我帮你获取一些食物吗？";
    if (ctx.isNightTime && !ctx.hasShelterNearby)
        return "天快黑了，需要我帮你搭建一个住所吗？";
    if (ctx.playerRecentInteractions.has("CraftingTable"))
        return "需要我帮你合成物品吗？";

    // 轮换默认模板
    return pickRandom(DEFAULT_TEMPLATES);
}
```

---

## 任务系统

```
玩家指令 / IdleBrain 触发
  ↓
LLM 拆解 (仅复杂任务, ~1 API/任务)
  ↓
写入 plans/active_plan.json
  ├── taskId
  ├── goal
  ├── subTasks: [{skill, target, status}, ...]
  └── currentSubTaskIndex

tick 执行:
  1. 安全反射检查 (每 tick)
  2. 取当前子任务
  3. 匹配条件反射 → 有则执行 / 无则试错
  4. 记录结果 → 子任务完成 → 推进 currentSubTaskIndex
  5. 全部完成 → 任务完成 → 写入 memory/highlights/
```

---

## 记忆系统

| 类型 | 路径 | 过期策略 |
|------|------|---------|
| 高光记忆 | `memory/highlights/mem_xxx.json` | 7 天窗口缓存 |
| 尝试记录 | `memory/trials/trial_xxx.json` | 固化成功后清理 |
| 久远归档 | `memory/archive/` | 玩家检索时加载 |
| 玩家评价 | `evaluations/eval_xxx.json` | 批量归纳后清理 |

---

## 性格系统 (按需 API)

### 标签分析 (1 API / 30min)

触发: 每 30 分钟 或 每 5 次任务完成

输入: 技能使用统计、反射执行次数、互动频率、现有标签、玩家评价缓存

输出: `personality_tags.json`
```json
{
  "tags": ["乐于助人", "谨慎", "喜欢挖矿"],
  "lastUpdated": 1734567890000
}
```

### 压力系统 (纯本地)

压力随时间衰减，互动触发上升。达到阈值 → 本地随机偏好偏移。仅在严重突变时触发 AI 确认。

---

## 玩家评价处理 (批量, 非实时)

```
chat 检测正则: /(?:你[很太真好]|你真|你有点)[^，。！？\n]{1,10}/
  ↓
提取关键词 → 存入 evaluations/ 缓存 (本地)
  ↓
每 30 分钟 或 每 10 条评价 → 1 次批量 API 归纳
  AI 一次性处理所有待定评价:
    - 已存在标签 → 强化强度
    - 新评价 → AI 归纳含义 → 新建/拒绝
  ↓
清理 evaluation 缓存 → 更新 personality_tags
```

---

## 成本模型 (总表)

| 操作 | 执行者 | LLM 调用 | 频率 |
|------|--------|---------|------|
| 安全反射 (flee/eat) | 脑干 | **0** | 每 tick |
| 条件反射执行 | 脑干 | **0** | 每次匹配 |
| 试错执行 | 脑干 | **0** | 每次执行 |
| 自动固化 (≥3 成功) | 脑干 | **0** | 每次成功达标 |
| 观察学习 → 序列记录 | 脑干 | **0** | 每次 Fabric 事件 |
| 观察学习 → 模式检测 | 脑干 | **0** | 每次 60s 窗口 |
| 观察学习 → 固化 | 脑干 | **0** | 每次 ≥3 模式 |
| 反射退休检查 | 脑干 | **0** | 每 10 次执行 |
| Idle 主动建议 | 脑干 | **0** | idle >30s |
| 玩家"烦死了随便"→任务 | 脑干 | **0** | 回复正则匹配 |
| **──────────────** | | **────** | **─────────** |
| 新任务拆解 | 大脑 | **1** | 每个新任务 |
| 批量评价归纳 | 大脑 | **1** | 每 30min 或 10 条 |
| 性格标签分析 | 大脑 | **1** | 每 30min 或 5 任务 |
| 玩家主动 "有什么建议" | 大脑 | **1** | 按需 |

**挂机 1 小时: 0 次 API。活跃 1 小时: ~8 次（任务拆解 + 2×性格分析 + 批量评价）。**

**公式: LLM 调用次数 = O(新任务数 + 时间) ≠ O(操作次数)**

---

## 源代码结构

### Fabric 模组 (当前活动项目)

```
AIPlayerMod-1.21.1-Fabric/
├── AGENTS.md                          ← 本文件
├── build.gradle
├── gradlew.bat
└── src/main/java/com/izimi/aiplayermod/
    ├── AIPlayerMod.java               ← ModInitializer 入口 + DI
    ├── AIPlayerModDataGenerator.java
    │
    ├── ⛓ 基础设施（骨架 — 不属于任何脑区）
    ├── command/AICommand.java          ← /ai 指令树
    ├── config/ModConfig.java           ← JSON 配置
    ├── util/
    │   ├── FileUtil.java              路径管理
    │   └── JsonUtil.java              Gson 封装
    ├── log/ExecutionLogger.java        ← 执行日志
    ├── state/
    │   ├── PlayerState.java
    │   └── StateManager.java
    ├── mixin/
    │
    ├── cortex/                          ← 🧠 前额叶（规划/复杂决策/语义理解）[ L3 Knowledge + LLM ]
    │   ├── api/
    │   │   ├── AIClient.java          接口
    │   │   ├── DeepSeekClient.java    HttpClient → DeepSeek API
    │   │   ├── AITaskPlanner.java     异步任务规划
    │   │   ├── AIChatHandler.java     聊天处理 + 评价检测
    │   │   ├── AIConfig.java          API 密钥/端点配置
    │   │   ├── AIMemoryGenerator.java
    │   │   ├── AIMessage.java
    │   │   ├── AIRequest.java
    │   │   └── AIResponse.java
    │   ├── inhibitor/
    │   │   └── InhibitoryControl.java  前额叶抑制控制 (P6) — 否决不适当反射/模仿 [ L2 ]
    │   ├── planner/
    │   │   ├── Plan.java              计划值对象 + PlanStep
    │   │   ├── PlanManager.java       计划管理/持久化/API富化
    │   │   ├── KnowledgeBase.java     知识库（合成表/物品用途/任务模板）[ L3 ]
    │   │   └── LocalTaskDecomposer.java  本地规则拆解器 [ L3 ]
    │   ├── chat/
    │   │   └── LocalChatHandler.java  本地聊天处理器 (模板匹配 + 激素差异化) [ L4 ]
    │   └── task/
    │       ├── Task.java              任务值对象
    │       ├── TaskManager.java        管理/持久化
    │       └── TaskExecutor.java       执行引擎
    │
    ├── hippocampus/                     ← 🐴 海马体（记忆存储/高光回忆）[ L3 Knowledge 辅助 ]
    │   ├── MemoryEntry.java
    │   ├── MemoryManager.java
    │   ├── MemoryQuery.java
    │   └── storage/
    │       ├── HighlightStorage.java    高光记忆读写
    │       └── TrialStorage.java        试错记录读写
    │
    ├── amygdala/                        ← 🌰 杏仁核（评价/条件反射/学习/情绪）
    │   ├── ConditionedReflex.java     匹配 + 效果记录 + 固化/退休 + retry/废弃 + scanAndTrigger [ L4 ]
    │   ├── DispatchReflex.java        可学习的调度反射（ProblemLabel×FlowLevel→Layer）[ L2 ]
    │   ├── OneShotAlarmSystem.java    Level 2 一次预警系统（一次示例→永久固化）[ L2 ]
    │   ├── BotParams.java             α/β 参数生成与正态分布采样 [ L1 ]
    │   ├── ThresholdConfig.java       自适应阈值配置 (minConfidence/minObservations) [ L1 ]
    │   ├── SocialObserver.java        多玩家行为观察 [ 骨架 ]
    │   ├── NaiveBayesClassifier.java  朴素贝叶斯分类器 [ 骨架 ]
    │   ├── FamiliarityTracker.java    亲密度追踪 [ 骨架 ]
    │   ├── character/
    │   │   ├── BehaviorEventHandler.java  Fabric 事件注册 [ 骨架 ] ✅ P1 已实现
    │   │   ├── BehaviorStats.java        行为计数器 (已吸收演化检测职责) [ 骨架 ] ✅ P1 已实现
    │   │   └── EvaluationCycle.java      月度评估引擎 + tryBatchSummarize ✅ P5 已实现
    │   │   │
    │   │   ├── // Phase 5+ 设计蓝图（未实现）：
    │   │   ├── Preference.java           📋 计划：个体偏好效价
    │   │   ├── CharacterManager.java     📋 计划：性格标签管理
    │   │   ├── PersonalityStress.java    📋 计划：压力系统 (部分已并入 HormonalSystem)
    │   │   └── PersonalityTag.java       📋 计划：性格标签值对象 + 持久化
    │   └── learning/
    │       ├── BehaviorEvent.java     行为事件记录 [ 骨架 ]
    │       ├── ObservedSequence.java  观察序列数据模型 [ 骨架 ]
    │       ├── CategoryMapper.java    目标→抽象分类映射 [ 骨架 ]
    │       └── LearningSystem.java    试错/固化/退休 [ 骨架 ]
    │
    └── brainstem/                       ← 🧬 脑干（先天反射/基础动作/生存本能）
        ├── IdleBrain.java             P0 已实现: 零成本 idle 建议系统 [ L2 ]
        ├── HormonalSystem.java         激素系统（stress/aggression/curiosity/intimacy）[ L1 ]
        ├── adapter/
        │   ├── BasicActionAdapter.java 接口 (12 原子 + 6 反射动作) [ L0 ]
        │   ├── ActionResult.java       统一返回类型
        │   └── MinecraftActionAdapter.java Fabric 实现（含 flee/eat/retreat/avoidLava/seekShelter/collectItem）[ L0 ]
        ├── innate/
        │   ├── InnateReflex.java          反射值对象（id + priority + triggers + action）[ L0 ]
        │   ├── InnateReflexRegistry.java  注册中心（register/loadFromJson/match/highest）[ L0 ]
        │   ├── ReflexTrigger.java         触发条件（7种类型：HP/饥饿/怪物/熔岩/昼夜/掉落物/下雨）[ L0 ]
        │   ├── ReflexAction.java          动作定义（adapter 方法名对齐）[ L0 ]
        │   ├── MinecraftReflexEvaluator.java 条件检测（findNearestHostile/hasSolidRoof/...）[ L0 ]
        │   ├── MoveSkill.java            先天移动技能 [ L0 ]
        │   ├── DigSkill.java             先天挖掘技能 [ L0 ]
        │   ├── AttackSkill.java          先天攻击技能 [ L0 ]
        │   └── CraftSkill.java           先天合成技能 [ L0 ]
        ├── scheduler/
        │   ├── MetaScheduler.java      元调度器 (MotivationEngine→label→LLM gate→dispatch) [ L2 ]
        │   ├── MotivationEngine.java   动机引擎 (5通道并行竞争 + 玻尔兹曼选择 + 交叉抑制) [ L2 ]
        │   ├── DriveState.java         驱力值对象 (survival/task/social/curiosity/cautious)
        │   ├── MetaContext.java         假人上下文 (存根已接入真实数据 + urgency 追踪)
        │   ├── Perspective.java         视角枚举（5个值）
        │   ├── ProblemLabel.java        问题标签
        │   ├── FlowLevel.java           升降级状态
        │   ├── ILocalPlanner.java       本地规划器接口 [ L3 ]
        │   └── UrgencyClassifier.java   紧急程度分类器 (离散标签 + 连续 urgency ∈ [0,1])
        ├── skill/
        │   ├── Skill.java             抽象基类
        │   └── SkillManager.java      注册/加载/卸载
        ├── navigation/
        │   ├── AStarPathfinder.java
        │   ├── NavigationController.java
        │   └── BlockPosUtil.java
        └── bot/
            ├── BotPlayer.java
            ├── BotSpawner.java
            └── BotController.java     主 tick 循环 ← MetaScheduler 已集成
```

### 测试 (src/test)

```
src/test/java/com/izimi/aiplayermod/
├── cortex/task/TaskTest.java                      extractAction/Count/Target + SubTask/Progress
├── amygdala/learning/CategoryMapperTest.java      分类映射 18 tests
├── brainstem/innate/ReflexRegistryTest.java       注册表 + Trigger/Action/Defaults (15 tests)
├── brainstem/IdleBrainTest.java                   状态机 + 肯定/否定/冷却 (11 tests)
├── brainstem/scheduler/MotivationEngineTest.java  驱力计算 + 玻尔兹曼分布 + 交叉抑制 + 好奇心指数衰减 (19 tests)
└── brainstem/scheduler/MetaContextStubTest.java   存根修补 + LLM跟踪 + 门控条件 + 环境检测 (25 tests)
```

### TS 原型 (参考用, 只读)

```
ai-bot/src/
├── index.ts                     MineFlayer 启动 + tick
├── AIPlayerBot.ts               主集成
├── adapters/MCAdapter.ts        12 原子动作 + 感知
├── persistence/FileStore.ts     JSON 文件读写
└── core/
    ├── types.ts                 接口/类型定义
    ├── decision/DecisionEngine.ts
    ├── planner/TaskDecomposer.ts
    ├── learning/LearningSystem.ts
    ├── autonomy/IdleBrain.ts
    ├── tasks/{TaskQueue,TaskPlanner}.ts
    ├── reflexes/InnateReflexes.ts
    ├── personality/{Preferences,PersonalityStress,PersonalityTags,Evolution}.ts
    └── memory/{MemoryCache,MemoryArchive,MemorySummarizer}.ts
```

---

## 运行时工作区 (运行时生成)

```
minecraft/ai_memory/
├── config/             config.json, innate_reflexes.json
├── bots/{bot_uuid}/
│   ├── alarms/         alarm_{entity}.json (Level 2 一次预警)
│   ├── conditioned/    reflex_{skill}_{target}.json (条件反射)
│   ├── memory/
│   │   ├── highlights/ mem_xxx.json (7天高光记忆)
│   │   └── trials/     trial_xxx.json (试错记录)
│   ├── tasks/          active_task.json, last_task.json
│   ├── plans/          active_plan.json (子任务拆解)
│   ├── evaluations/    eval_xxx.json (玩家评价缓存)
│   ├── character/
│   │   ├── preferences.json         (偏好效价)
│   │   ├── personality_stress.json  (压力系统)
│   │   └── personality_tags.json    (AI 生成的角色标签)
│   ├── state/          current.json
│   └── execution_logs/ log_xxx.json
└── skills/
    └── innate/         4 个先天技能元数据
```

---

## 游戏内指令

| 指令 | 功能 |
|------|------|
| `挖10个铁矿` (自然语言) | 创建任务 |
| `/ai status` | 查看当前任务状态 |
| `/ai cancel` | 中断当前任务 |
| `/ai resume` | 恢复上一个任务 |
| `/ai explore` | 自主探索 |
| `/ai personality` | 查看偏好 + 性格标签 |
| `/ai reflexes` | 查看已学习条件反射 |
| `/ai suggestions` | 触发 IdleBrain 主动建议 |
| `/ai memories` | 查看最近记忆 |
| `/ai forget <id>` | 删除指定记忆 |
| `/ai setkey <key>` | 设置 API 密钥 |
| `/ai apikey` | 查看密钥状态 |
| `/ai help` | 查看所有指令 |

---

## 已知问题与修复计划

> 以下问题经代码审计验证。**✅ = 已完成修复。**

### 致命问题 (P0)

#### ✅ P-1: BasicActionAdapter — 已修复

| 项目 | 说明 |
|------|------|
| 状态 | **已完成**: `adapter/` 包已创建，12 原子动作全部实现，`ConditionedReflex` 已适配 |
| 文件 | `adapter/BasicActionAdapter.java`, `adapter/MinecraftActionAdapter.java`, `adapter/ActionResult.java`, `ConditionedReflex.java` |

#### ✅ P-3: Plan 系统 — 已修复

| 项目 | 说明 |
|------|------|
| 状态 | **已完成**: `planner/Plan.java` + `planner/PlanManager.java` 已创建，持久化到 `plans/active_plan.json`，集成 AITaskPlanner |
| 文件 | `planner/Plan.java`, `planner/PlanManager.java`, `AIPlayerMod.java`, `BotController.java` |

#### ✅ P-4: 零测试 — 已修复

| 项目 | 说明 |
|------|------|
| 状态 | **已完成**: JUnit5 + Mockito，58 tests，0 failures。包括 CategoryMapper/Task/IdleBrain/ReflexRegistry 测试 |
| 文件 | `build.gradle`, `src/test/.../` |

---

### 严重不一致 (P1 — 文档与代码必须对齐)

#### ✅ 优先级层数不一致 — 已修复

| 文档 | 代码 |
|------|------|
| 统一为 6 层 (P0~P5)，含 P4 子顺序 IdleBrain→SocialMirror→非安全反射 | ✅ |
| P0 增加 critical() 致命检查，非致命安全反射不中断任务流 | ✅ |

#### ✅ 评价归纳不一致 — 已修复

| 文档 | 代码 |
|------|------|
| 每 30min 或 10 条评价 → 批量 API 归纳 | ✅ `EvaluationCycle.tryBatchSummarize()` 已实现 |
| 纯本地正则 + 计数器先处理，达阈值后异步调 LLM | ✅ |

#### ✅ Plan 拆解不一致 — 已修复

| 文档 | 代码 |
|------|------|
| LLM 拆解 + `plans/active_plan.json` | ✅ Plan + PlanManager 已创建，支持 API 富化 |

#### ✅ 阈值位置不一致 — 已修复

| 文档 | 代码 | 状态 |
|------|------|------|
| `thresholds/` 独立文件夹 (`ai_memory/thresholds/`) | 嵌套在 `skills/character/thresholds/` | ✅ 已迁移到 `ai_memory/thresholds/` |

#### ✅ 记忆位置不一致 — 已修复

| 文档 | 代码 | 状态 |
|------|------|------|
| `memory/` (单数) | `memories/` (复数) | ✅ 统一为 `memory/` |
| `memory/highlights/` | 无此子目录 | ✅ 已创建 `getHighlightsDir()` |

---

### 功能级缺陷 (P2 — 质量修复)

#### ✅ P-5: BehaviorObserver 解耦 — 已修复

| 描述 | 事件注册/计数/演化触发三职责已拆分 |
|------|------|
| 修复 | ✅ 拆为 2 类: `BehaviorEventHandler` (事件注册) + `BehaviorStats` (计数器，已吸收演化检测职责) |
| 文件 | `character/BehaviorEventHandler.java`, `character/BehaviorStats.java` |

#### ✅ P-6: P4 层顺序 — 已文档化

| 描述 | IdleBrain、SocialMirror、非安全反射在 P4 中执行顺序已定义 |
|------|------|
| 修复 | ✅ AGENTS.md 已更新: P4 子顺序 = IdleBrain → SocialMirror → 非安全反射 |

#### ✅ P-7: 评价只统计不归纳 — 已修复

| 描述 | `EvaluationCycle` 现已实现批量 API 归纳 |
|------|------|
| 修复 | ✅ `tryBatchSummarize()` — 每 10 条评价 → 异步调用 `AIClient` → 批量归纳存入 personality_tags |

#### ✅ P-8: 任务完成判定交叉验证 — 已修复

| 描述 | 条件反射 "false success" 问题已通过 BasicActionAdapter 解决 |
|------|------|
| 修复 | ✅ BasicActionAdapter 确保原子动作返回真实 `executed/success` |

#### ✅ P-9: IdleBrain 泛词误判 — "帮" 单字触发 — 已修复

| 描述 | `AFFIRMATIVE_WORDS` 含单字 `"帮"` 已改为多字短语 |
|------|------|
| 修复 | ✅ `"帮"` → `"帮我"`, `"帮忙"`, `"帮一下"` |

---

### 修复执行状态

```
✅ 已完成  P-4   补测试 (58 tests, 0 failures)
✅ 已完成  P-1   实现 BasicActionAdapter (12 原子动作)
✅ 已完成  P-3   实现 Plan 拆解 (Plan + PlanManager)
✅ 已完成        优先级对齐 (6层 + P4子顺序)
✅ 已完成  P-7   批量 API 评价归纳 (EvaluationCycle.tryBatchSummarize)
✅ 已完成  P-5   解耦 BehaviorObserver (BehaviorEventHandler + BehaviorStats)
✅ 已完成  P-6   文档化 P4 子层顺序
✅ 已完成  P-9   修 IdleBrain "帮" 泛词误判
✅ 已完成        目录结构对齐 (cortex/hippocampus/amygdala/brainstem + 骨架独立)
✅ 已完成  P5    玩家评价 + 批量归纳 + 反射权重动力学 (条件反射权重分布)
✅ 已完成  P6    前额叶抑制控制 + 目录清理 (InhibitoryControl + BehaviorStats修复)
✅ 已完成  Phase 1   MetaScheduler + BotContext (动态路由器)
✅ 已完成  Phase 2   OneShotAlarmSystem (一次预警系统)
✅ 已完成  Phase 2.5 HormonalSystem (激素系统)
✅ 已完成  Phase 4.5 MotivationChannel (玻尔兹曼视角选择) + LLM Gate (6条件门控) + Curiosity 指数衰减
```

---

## 实施路线

| 阶段 | 内容 | 状态 | 产出 |
|------|------|------|------|
| **P0** | IdleBrain 零成本建议系统 | ✅ | 挂机 0 API |
| P1 | 观察学习系统 (事件捕获+模式检测+固化) | ✅ | 肢体语言模仿 |
| P1.5 | 社交镜像系统 (多玩家贝叶斯+从众系数) | ✅ | 群体智慧 0 API |
| P2 | 优先级链重构 (安全→任务→Idle→非安全) | ✅ | 正确行为流 |
| P3 | 条件反射系统升级 (自动固化/退休) | ✅ | 反射闭环 |
| ├─ P3 Stage A | 原子写入 (tmp→校验→rename) | ✅ | 崩溃安全 |
| ├─ P3 Stage C | 优先级重构为6层 | ✅ | 正确分层 |
| ├─ P3 Stage B | 反射原子化 (每反射一个动作) | ✅ | 最小单元 |
| └─ P3 Stage D | 完成≠成功 + 确定性永久跳过 | ✅ | 失败分类 |
| P4 | 子任务拆解 + Plan 支持 | ✅ | 复杂指令 |
| P5 | 玩家评价 + 批量归纳 + 性格标签 | ✅ | AI 性格 |
| P6 | 前额叶抑制控制 + 目录清理 | ✅ | 理性刹车 + 整洁 |
| **Phase 1** | MetaScheduler + BotContext 框架 | ✅ | 动态路由器 |
| Phase 2 | OneShotAlarmSystem (Level 2 先天预警) | ✅ | 一次性学习 |
| Phase 2.5 | HormonalSystem (激素系统) | ✅ | 按玩家亲密度 |
| Phase 3 | LocalTaskDecomposer (本地规则拆解) | ✅ | KB模板→Plan→Task 集成 |
| Phase 4 | LocalChatHandler + 模板差异化 | ✅ | 0 LLM 聊天 |
| Phase 4.5 | MotivationEngine + LLM Gate + Curiosity Drive | ✅ | 玻尔兹曼视角选择 + 6条件门控 + 指数衰减驱动 |
| Phase 5 | 紧急程度 + 时间缩放 + 自组织 | ✅ | 动态调速 + 零成本探索 |
| Phase 6 | 多假人 (BotManager Map + /ai spawn \<name\>) | ✅ | 多假人共存 |
| Phase 7 | 繁衍模块 (三规则继承 + BotContext.reproduce) | ⬜ | 正态分布涌现 |

- 🔴 = 当前正在实施
- ⬜ = 待实施
- ✅ = 已完成

---

## 构建命令

```bash
# 当前项目 (Fabric Mod)
cd AIPlayerMod-1.21.1-Fabric
.\gradlew.bat build

# TS 原型 (仅参考, 不部署)
cd ai-bot
npm install && npm run build && npm test
```
