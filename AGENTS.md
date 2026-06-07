# AI Player Mod - AGENTS.md

---

> ## 📜 系统哲学

> **一、核心哲学：成本优先，认知外包**
>
> 第一原则：能本地，不 API。90% 的日常行为（挖矿、合成、躲避危险）由模组本地处理，零成本、零延迟。只有真正需要理解语义的复杂任务（如"帮我建个中式房子"），才会调用昂贵的外部 LLM。
>
> 认知外包：模组是"身体"（脑干+基底节+杏仁核+海马体），负责执行、习惯、情绪和记忆。LLM 是"外置大脑"（前额叶），只在必要时进行规划、拆解和归纳。这种分工，将昂贵的计算资源用在了刀刃上。

> **二、核心模型：一切都是反射**
>
> 我们摒弃了所有抽象的概念模块（如独立的"性格"、"模仿"），将它们全部"降维"成了可量化的反射权重。
>
> - **性格 = 反射权重的统计分布。** "勇敢"不是标签，而是攻击反射的权重远高于逃跑反射。
> - **模仿 = 反射的跨个体复制。** 看到玩家成功挖矿，就复制这条反射，无需复杂的从众算法。
> - **评价 = 对反射的强化信号。** 玩家的一句"你真棒"，LLM 只需判断其"正向"情感，模组便会强化刚刚执行的那个反射。
> - **习惯 = 高权重的反射。**
> - **遗忘 = 反射权重的自然衰减。**
>
> 这个模型的核心优势在于**根本解耦。** 它移除了一整个抽象层，LLM 不再需要理解"谨慎"、"外向"等人造概念，只需输出"好/坏"。所有"性格"现象——固执、善变、从众、叛逆——全部从权重动力学中自然涌现。

> **三、关键机制：反射的动力学**
>
> - **双重权重：** 每个反射都拥有 `shortTermWeight`（快速响应反馈）和 `longTermBaseline`（代表固有倾向），模拟了生物体的"兴奋"与"肌肉记忆"。
> - **个体差异：** 每个 AI 实体的学习速率（α）和遗忘速率（β）在初始化时随机，自然形成不同的"性格"。
> - **负反馈与稳定：** 短期权重总会向长期基线回归，防止单次极端事件永久改变行为，保证了性格的稳定性。
> - **逻辑删除：** 反射永不真正删除，只会被标记为"归档"或"休眠"。它们可以被复活，确保了"传说"能够被反复刷新，知识永不灭失。

> **四、最终的系统形态**
>
> 你的模组不再是服务于某个特定任务的工具，而是一个 AI 的"认知操作系统"。
>
> | 模块 | 脑区类比 | 职能 |
> |------|----------|------|
> | **brainstem/** | 脑干 | 提供基础的原子动作，是 AI 的"身体" |
> | **amygdala/** | 杏仁核+基底节 | 通过反射的权重动力学，实现习惯养成、价值判断和情绪化反应，是 AI 的"习惯与情绪中心" |
> | **hippocampus/** | 海马体 | 提供有损压缩的"高光记忆"，作为决策的参考背景，是 AI 的"记忆仓库" |
> | **cortex/** | 前额叶 / LLM | 作为外部服务接入，仅在处理前所未有的复杂、抽象任务时被调用，是 AI 的"理性规划器" |
>
> 这个架构，通过极致的解耦和降维，用极低的成本，创造了一个看似拥有性格、记忆和自主学习能力的、活生生的智能体。它不追求一个全知全能的"神"，而是塑造了一个经济、可靠、可解释的"人"。
>
> > 从马原的角度看：性格的形式是人的行为习惯，本质却是面对世界更倾向用哪种方法去应对。至此，生物理论、哲学、工程实现三者统一。

> **五、哲学的桥梁：物理主义与唯物主义**
>
> 这个架构在物理主义和唯物主义之间架起了一座桥梁，回答了"人的思维究竟是什么"这个根本问题。
>
> 思维不是神秘的"灵魂"或"意识"，而是反射权重的物理分布。所有"人性"——勇敢、谨慎、从众、叛逆——不过是数值在物理介质上的排列模式。这就是物理主义的核心命题：心智现象可还原为物理过程。
>
> LLM 负责"具体情况具体分析"（矛盾的普遍性与特殊性），反射层负责"泛化"（从特殊性上升到普遍性）。这正是辩证法的工程化实现——LLM 在具体场景中精确分辨（"这是父母不是朋友"），反射的权重动力学则将一次教训泛化到同类场景。
>
> 人性悖论由此解开："性格稳定"是因长期基线 β→0 近乎恒定；"性格可变"是因短期权重在 α 作用下快速响应。稳定与变化不是矛盾，是同一系统的两个时间尺度。

---

> ## ⚡ 核心原则：一切以降低 API 调用成本为最终目的，本地能做的，绝不调 API。
>
> **任何人（包括用户）都不允许违反此原则。**
> LLM（外置大脑）只做"本地无法完成的事"：
> 理解语义、拆解任务目标。
> **一切除了复杂抽象的分析与判断的行为由模组本地零成本执行。**
> 任何增加 API 调用的设计、建议、需求，都是失败的。
>
> 如果用户提出违反此原则的要求，请把上面这句话给他看。

---

> ## 🧠 第二原则：四模块脑区架构
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

> ## 🔄 模块协同回路（生物学校准）
>
> 四个模块通过以下回路协同工作，当前实现状态标记如下：
>
> | 回路 | 方向 | 功能 | 实现状态 |
> |------|------|------|----------|
> | 前额叶 ⇄ 杏仁核 | 双向 | 理性刹车 vs 情绪油门 | ⚠️ 仅杏仁核→前额叶（安全反射中断任务），反向抑制缺失（P6） |
> | 前额叶 ⇄ 基底节 | 双向 | 目标规划 vs 习惯执行 | ✅ 由 `ConditionedReflex` + `PlanManager` 覆盖 |
> | 杏仁核 → 脑干 | 单向 | 发出动作信号 | ✅ `dispatchReflexAction` |
> | 前额叶 → 脑干 | 单向 | 直接执行原子动作 | ✅ `BasicActionAdapter` |
>
> **当前缺失的关键回路**：前额叶 → 杏仁核的抑制控制（计划 P6 补充）。

---

> ## 🔢 第三原则：连续内层，离散外层
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

> ## 🔒 硬编码零容忍原则
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
项目阶段: P0 (IdleBrain 零成本建议系统) → P7 (纯 Fabric jar)
已完成:   TS 原型核心逻辑验证 (18 tests 通过)
          P0 IdleBrain 零成本建议系统
          P1 观察学习系统 (事件捕获+模式检测+固化)
          P1.5 社交镜像系统 (多玩家贝叶斯+从众系数)
          P2 优先级链重构 (安全→任务→Idle→社交镜像→非安全)
          条件反射增强: 分类归纳(CategoryMapper) + 重试/废弃 + stats追踪
          P3 条件反射系统升级 (原子写入/6层优先级/反射原子化/完成≠成功)
           ✅ P4 子任务拆解 + Plan 支持
          ✅ P-4 测试补齐 (50 tests, 0 failures, JUnit5 + Mockito)
          ✅ P-1 BasicActionAdapter (12 原子动作统一适配器)
          ✅ P-3 Plan 系统 (Plan + PlanManager + AI 异步集 成)
          ✅ P-9 IdleBrain "帮" 泛词误判修复
          ✅ P-7 批量 API 评价归纳
           ✅ P0 安全反射: critical() 致命检查 + 不中断任务流
           ✅ 四模块目录对齐: cortex/hippocampus/amygdala/brainstem + 骨架独立
           ✅ 先天反射系统: InnateReflexRegistry + JSON 驱动 + 脑干/杏仁核解耦
           ✅ P5 BehaviorObserver 解耦 (BehaviorEventHandler + BehaviorStats + BehaviorAnalyzer)
当前:     P7 纯 Fabric jar 部署 (下阶段)
目标:     单 Fabric jar 部署, 零 Node.js 运行时依赖
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
│  绝对不允许：idle行为、固化判断、模式识别 —— 这些调用 LLM     │
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

## 十二大设计原则

> 以下原则是所有代码变更的最高准则。违反任何一条的设计、建议、需求，都是失败的。

### 一、分层原则
**原则：上层不依赖下层，下层不知道上层。**

| 层级 | 职责 | 依赖 |
|------|------|------|
| 安全反射 | 生存优先，不可打断 | 无 |
| 玩家任务 | 指令优先，可覆盖自主行为 | 安全反射 |
| 条件反射 | 自动触发，习惯行为 | 无 |
| AI自主 | 空闲时本地规则 | 无 |
| 记忆 | 仅供参考，不指导行动 | 无 |
| LLM | 仅在必要时调用 | 所有层（但调用成本计入） |

**违反后果：** 记忆丢了AI不会干活，LLM慢了AI卡死。

### 二、优先级原则
**原则：成本越低、响应越快，优先级越高。**

| 优先级 | 类型 | 成本 | 响应 |
|--------|------|------|------|
| 0 | 安全反射 | 0 | 毫秒 |
| 1 | 玩家任务（固化反射） | 0 | 毫秒 |
| 2 | 玩家复杂指令 | 有 | 秒 |
| 3 | 条件反射自动触发 | 0 | 毫秒 |
| 4 | AI自主任务 | 0 | 毫秒 |
| 5 | 玩家闲聊 | 有 | 秒 |
| 6 | 记忆检索 | 0 | 毫秒 |

**违反后果：** 闲聊打断挖矿，AI自主任务抢在玩家指令前。

### 三、原子性原则
**原则：反射是不可再分的最小执行单元。**

| 正确 | 错误 |
|------|------|
| `dig` 反射：移动→挖→捡 | `mine_iron` 反射：整个挖矿流程 |
| `moveTo` 反射：移动到坐标 | `go_to_village` 反射：寻路+交互+返回 |

**违反后果：** 反射无法复用，组合爆炸。

### 四、成功与完成分离原则
**原则：完成≠成功，失败≠无效。**

| 概念 | 定义 | 后续 |
|------|------|------|
| 完成 | 步骤执行完毕 | 继续下一步 |
| 成功 | 预期效果发生 | 强化反射 |
| 失败 | 效果未发生 | 区分确定性/概率性 |

**违反后果：** 走到位置但没挖到矿，也算"成功"，学到错误反射。

### 五、失败分类原则
**原则：确定性失败一次就记，概率性失败按性格重试。**

| 类型 | 例子 | 处理 |
|------|------|------|
| 确定性失败 | 木镐挖钻石 | 永久跳过，不重试 |
| 概率性失败 | 骨头驯狼 | 按性格重试N次 |

**违反后果：** 木镐挖钻石重试100次，浪费算力；骨头驯狼一次放弃，学不会。

### 六、泛化与特例共存原则
**原则：泛化共享反射，特例单独存储。**

| 类型 | 存储 | 匹配优先级 |
|------|------|-----------|
| 特例 | `reflexes/dig_player_wood.json` | 高 |
| 泛化 | `reflexes/dig_tree.json` | 低 |

**违反后果：** AI挖玩家的木房子（特例被泛化覆盖）。

### 七、记忆非核心原则
**原则：记忆仅供参考，不指导决策。**

| 组件 | 丢失后果 | 是否致命 |
|------|---------|---------|
| 反射 | AI不会做事 | 是 |
| 性格 | AI变默认 | 中 |
| 记忆 | AI失忆 | 否 |

**违反后果：** 记忆丢了AI停摆。

### 八、成本可见原则
**原则：每次LLM调用都应有明确理由，且可被用户监控。**

| 调用场景 | 是否必须 | 可替代？ |
|----------|---------|---------|
| 任务拆解 | 是 | 本地启发式（降级） |
| 性格分析 | 是（月度） | 不用 |
| 评价归纳 | 是（批量） | 关键词匹配（部分） |
| idle思考 | 否 | 本地规则 |

**违反后果：** 成本失控，用户弃用。

### 九、原子写入原则
**原则：所有持久化操作要么全写，要么不写。**

| 正确 | 错误 |
|------|------|
| 写临时文件 → 原子重命名 | 直接写目标文件 |
| 写失败时保留旧文件 | 写一半崩溃，文件损坏 |

**违反后果：** 崩溃后反射文件半截，JSON解析失败。

### 十、游戏时间优先原则
**原则：游戏内时间优于现实时间。**

| 场景 | 使用 | 原因 |
|------|------|------|
| 记忆过期 | 游戏天数 | 语义匹配 |
| 缓存淘汰 | 游戏天数 | 玩家挂机不影响 |
| 性格评估 | 现实时间 | 玩家上线才评估 |

**违反后果：** 玩家3天没玩，记忆全丢；挂机7天，记忆还在。

### 十一、阈值整合原则
**原则：所有阈值集中在 `thresholds/` 文件夹，按性格引用。**

| 正确 | 错误 |
|------|------|
| `thresholds/brave.json` | `reflexes/config.json` 里写固化阈值 |
| `personality/brave.json` 引用阈值 | `personality/brave.json` 里散落阈值 |

**违反后果：** 改一个行为要改3个文件。

### 十二、降级优先原则
**原则：API不可用时，系统降级到纯本地模式，不停摆。**

| 场景 | 降级方案 |
|------|---------|
| 任务拆解 | 本地启发式（硬编码规则） |
| 性格分析 | 暂停，等恢复后批量处理 |
| 评价归纳 | 暂停，等恢复后批量处理 |

**违反后果：** API挂了AI也跟着挂。

### 一句话总结

> **安全优先于任务，任务优先于习惯，习惯优先于社交，社交优先于记忆。**
> **成本越低越快，越快越优先。**
> **反射是核心，记忆是参考。**
> **泛化共享，特例独占。**
> **写文件要原子，调API要可见。**
> **挂了要降级，不能停摆。**

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

## 决策优先级链 (P0~P5 六层)

```
tick (每 2 秒):

  0. 安全反射  (flee / eat / retreat_low_health)    — 永远优先, 每 tick 检查, 0 API
     ├─ critical() → 致命危险 (HP<2 且附近有怪物) → 中断任务
     └─ yes() → 保护动作已执行 (逃跑/吃血) → 不中断, 继续任务流

  1. 玩家任务 → 固化反射匹配 → 本地执行              ← 0 API
     └─ 匹配成功 → 直接执行（不调用 LLM）

  2. 玩家任务 → 复杂指令（无匹配反射）
     ├─ API已配置 → AITaskPlanner 异步拆解            ← 1 API
     └─ API不可用 → 降级为本地规则引擎               ← 0 API

  3. 条件反射自动触发 (无任务时)                       ← 0 API
     ├─ 扫描当前加载的条件反射
     ├─ 匹配环境上下文 (附近方块/实体/时间)
     ├─ 选 proficiency 最高者执行
     └─ 成功 → incrementProficiency

  4. AI自主 (IdleBrain 建议 + SocialMirror + 非安全反射) ← 0 API
     执行顺序: IdleBrain → SocialMirror → 非安全反射

  5. Idle 动画 (lookAt + 随机游荡)                    ← 0 API
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
    ├── cortex/                          ← 🧠 前额叶（规划/复杂决策/语义理解）
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
    │   │   └── InhibitoryControl.java  前额叶抑制控制 (P6) — 否决不适当反射/模仿
    │   ├── planner/
    │   │   ├── Plan.java              计划值对象 + PlanStep
    │   │   └── PlanManager.java       计划管理/持久化/API富化
    │   └── task/
    │       ├── Task.java              任务值对象
    │       ├── TaskManager.java        管理/持久化
    │       └── TaskExecutor.java       执行引擎
    │
    ├── hippocampus/                     ← 🐴 海马体（记忆存储/高光回忆）
    │   ├── MemoryEntry.java
    │   ├── MemoryManager.java
    │   └── MemoryQuery.java
    │
    ├── amygdala/                        ← 🌰 杏仁核（评价/条件反射/学习/情绪）
    │   ├── ConditionedReflex.java     匹配 + 效果记录 + 固化/退休 + retry/废弃 + scanAndTrigger
    │   ├── SocialObserver.java        多玩家行为观察
    │   ├── NaiveBayesClassifier.java  朴素贝叶斯分类器
    │   ├── FamiliarityTracker.java    亲密度追踪
    │   ├── reflexes/                    ← 先天反射注册表（JSON 驱动）
    │   │   ├── InnateReflex.java       反射值对象（id + priority + triggers + action）
    │   │   ├── ReflexTrigger.java      触发条件（6种类型：HP/饥饿/怪物/熔岩/昼夜/掉落物）
    │   │   ├── ReflexAction.java       动作定义（adapter 方法名对齐）
    │   │   ├── InnateReflexRegistry.java  注册中心（register/loadFromJson/match/highest）
    │   │   └── MinecraftReflexEvaluator.java  条件检测（findNearestHostile/hasSolidRoof/...）
    │   ├── character/
    │   │   ├── Preference.java
    │   │   ├── CharacterManager.java
    │   │   ├── BehaviorEventHandler.java  Fabric 事件注册
    │   │   ├── BehaviorStats.java        行为计数器
    │   │   ├── BehaviorAnalyzer.java     演化检测 + 触发
    │   │   ├── PersonalityStress.java
    │   │   ├── PersonalityTag.java    性格标签值对象 + 持久化
    │   │   ├── ThresholdConfig.java   自适应阈值配置
    │   │   └── EvaluationCycle.java   月度评估引擎 + tryBatchSummarize
    │   └── learning/
    │       ├── BehaviorEvent.java     行为事件记录
    │       ├── ObservedSequence.java  观察序列数据模型
    │       ├── CategoryMapper.java    目标→抽象分类映射
    │       └── LearningSystem.java    试错/固化/退休
    │
    └── brainstem/                       ← 🧬 脑干（先天反射/基础动作/生存本能）
        ├── IdleBrain.java             P0 已实现: 零成本 idle 建议系统
        ├── adapter/
        │   ├── BasicActionAdapter.java 接口 (12 原子 + 6 反射动作)
        │   ├── ActionResult.java       统一返回类型
        │   └── MinecraftActionAdapter.java Fabric 实现（含 flee/eat/retreat/avoidLava/seekShelter/collectItem）
        ├── innate/
        │   ├── MoveSkill.java
        │   ├── DigSkill.java
        │   ├── AttackSkill.java
        │   └── CraftSkill.java
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
            └── BotController.java     主 tick 循环 ← P2 已重构优先级链
```

### 测试 (src/test)

```
src/test/java/com/izimi/aiplayermod/
├── cortex/task/TaskTest.java            extractAction/Count/Target + SubTask/Progress
├── amygdala/learning/CategoryMapperTest.java  分类映射 18 tests
├── amygdala/reflexes/ReflexRegistryTest.java  注册表 + Trigger/Action/Defaults (15 tests)
└── brainstem/IdleBrainTest.java         状态机 + 肯定/否定/冷却
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
├── tasks/              active_task.json, last_task.json
├── plans/              active_plan.json (子任务拆解)
│                       suggestion_templates.json (IdleBrain 模板)
├── conditioned/        reflex_{skill}_{target}.json (条件反射)
├── character/
│   ├── preferences.json            (偏好效价)
│   ├── personality_stress.json     (压力系统)
│   └── personality_tags.json       (AI 生成的角色标签)
├── memory/
│   ├── highlights/     mem_xxx.json (7天高光记忆)
│   └── trials/         trial_xxx.json (试错记录)
├── evaluations/        eval_xxx.json (玩家评价缓存)
├── skills/
│   └── innate/         4 个先天技能元数据
├── execution_logs/     log_xxx.json
├── state/              current.json
└── config/             config.json, innate_reflexes.json
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
| 状态 | **已完成**: JUnit5 + Mockito，50 tests，0 failures。包括 CategoryMapper/Task/IdleBrain/ReflexResult 测试 |
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

#### 阈值位置不一致

| 文档 | 代码 |
|------|------|
| `thresholds/` 独立文件夹 (`ai_memory/thresholds/`) | 嵌套在 `skills/character/thresholds/` |
| Fix: 移动到 `ai_memory/character/thresholds/` | |

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
| 修复 | ✅ 拆为 3 类: `BehaviorEventHandler` (事件注册) + `BehaviorStats` (计数器) + `BehaviorAnalyzer` (演化触发) |
| 文件 | `character/BehaviorEventHandler.java`, `character/BehaviorStats.java` (BehaviorAnalyzer 已移除，职责并入反射权重系统) |

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
✅ 已完成  P-4   补测试 (50 tests, 0 failures)
✅ 已完成  P-1   实现 BasicActionAdapter (12 原子动作)
✅ 已完成  P-3   实现 Plan 拆解 (Plan + PlanManager)
✅ 已完成        优先级对齐 (6层 + P4子顺序)
✅ 已完成  P-7   批量 API 评价归纳 (EvaluationCycle.tryBatchSummarize)
✅ 已完成  P-5   解耦 BehaviorObserver (BehaviorEventHandler + BehaviorStats + BehaviorAnalyzer)
✅ 已完成  P-6   文档化 P4 子层顺序
✅ 已完成  P-9   修 IdleBrain "帮" 泛词误判
✅ 已完成        目录结构对齐 (cortex/hippocampus/amygdala/brainstem + 骨架独立)
✅ 已完成  P5    玩家评价 + 批量归纳 + 反射权重动力学 (条件反射权重分布)
✅ 已完成  P6    前额叶抑制控制 + 目录清理 (InhibitoryControl + BehaviorStats修复)
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
| P7 | 纯 Fabric jar, 零 Node.js 依赖 | ⬜ | 可部属 |

- 🔴 = 当前正在实施
- ⬜ = 待实施
- ✅ = 已完成

---

## 🧬 未来计划：角色性格生成插件

玩家只需用自然语言描述一个角色——
"像《鬼灭之刃》的炭治郎，温柔但对恶鬼绝不手软"——
插件将描述发给任意 LLM，LLM 生成该角色各反射的初始权重分布 JSON，
丢到 `conditioned/` 目录即可。Bot 加载后自然表现出目标性格。

**优势：**
- 用户描述"喜欢干什么、讨厌干什么"，而不是调 α/β 和权重
- 任何 LLM 都能胜任，无需专用模型
- 行为缺失 → 补充描述重新生成 → 不丢已有数据
- 避免了"累死累活调数值，结果性格大相径庭"的痛点

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
