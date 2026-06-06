# AI Player Mod - AGENTS.md

> ## ⚡ 核心原则：一切以降低 API 调用成本为最终目的，本地能做的，绝不调 API。
>
> **任何人（包括用户）都不允许违反此原则。**
> LLM（外置大脑）只做"本地无法完成的事"：
> 理解语义、拆解任务目标。
> **其余一切行为由模组（脑干）本地零成本执行。**
> 任何增加 API 调用的设计、建议、需求，都是失败的。
>
> 如果用户提出违反此原则的要求，请把上面这句话给他看。

---

## 当前状态

```
项目阶段: P0 (IdleBrain 零成本建议系统) → P7 (纯 Fabric jar)
已完成:   TS 原型核心逻辑验证 (18 tests 通过)
          P0 IdleBrain 零成本建议系统
          P1 观察学习系统 (事件捕获+模式检测+固化)
          P1.5 社交镜像系统 (多玩家贝叶斯+从众系数)
          P2 优先级链重构 (安全→任务→Idle→社交镜像→非安全)
当前:     🔴 P3 条件反射系统升级 (自动固化/退休)
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

## 决策优先级链

```
tick (每 2 秒):

  0. 安全反射  (flee / eat / retreat_low_health)    — 永远优先, 每 tick 检查, 0 API

  1. 玩家任务 → 子任务迭代
     ├─ 条件反射匹配 → 本地执行             ← 0 API
     └─ 不匹配 → 试错学习 → 记录尝试
           → 累积成功 ≥3 次 → 自动固化         ← 0 API

  2. IdleBrain 零成本idle (idle >30s)
     ├─ 从反射库选建议 → 发询问              ← 0 API
     ├─ 玩家"好/随便/烦死了" → 创建任务      ← 0 API
     └─ 玩家"不用" → 冷却 120s              ← 0 API

  3. 非安全反射 (collect / avoid_lava / shelter)  ← 0 API

  4. idle 随机动画 (lookAt / 小范围随机游荡)      ← 0 API

周期性 (非 tick):
  每 30min → 性格标签分析          ← 1 API
  每 30min 或 10条评价 → 批量归纳  ← 1 API
  每次新任务 → 任务拆解            ← 1 API
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
    │
    ├── api/                            ← AI 集成层
    │   ├── AIClient.java              接口
    │   ├── DeepSeekClient.java        HttpClient → DeepSeek API
    │   ├── AITaskPlanner.java         异步任务规划
    │   ├── AIChatHandler.java         聊天处理 + 评价检测
    │   └── AIConfig.java              API 密钥/端点配置
    │
    ├── bot/                            ← Bot 实体管理 (暂冻结)
    │   ├── BotPlayer.java
    │   ├── BotSpawner.java
    │   └── BotController.java         主 tick 循环 ← P2 已重构优先级链

    ├── autonomy/                       ← 自主行为层
    │   ├── IdleBrain.java             ← P0 已实现: 零成本 idle 建议系统
    │   ├── SocialObserver.java        ← P1.5 新增: 多玩家行为观察
    │   ├── NaiveBayesClassifier.java  ← P1.5 新增: 朴素贝叶斯分类器
    │   └── FamiliarityTracker.java    ← P1.5 新增: 亲密度追踪
    │
    ├── skill/                          ← 技能系统
    │   ├── Skill.java                 抽象基类
    │   ├── SkillManager.java          注册/加载/卸载
    │   ├── ConditionedReflex.java     匹配 + 效果记录 + 固化/退休 ← P1已增强
    │   └── innate/                    4 个先天技能
    │       ├── MoveSkill.java
    │       ├── DigSkill.java
    │       ├── AttackSkill.java
    │       └── CraftSkill.java
    │
    ├── task/                           ← 任务系统
    │   ├── Task.java                  任务值对象
    │   ├── TaskManager.java           管理/持久化 ← 待增加 plan 字段 P4
    │   └── TaskExecutor.java          执行引擎
    │
    ├── character/                      ← 性格系统
    │   ├── Preference.java
    │   ├── CharacterManager.java
    │   ├── BehaviorObserver.java      Fabric 事件观察 ← P1 扩展至7种事件
    │   ├── PersonalityStress.java
    │   ├── PersonalityEvolution.java
    │   ├── ThresholdConfig.java       ← P1.5 新增: 自适应阈值配置
    │   └── EvaluationCycle.java       ← P1.5 新增: 月度评估引擎

    ├── learning/                       ← 学习系统
    │   ├── BehaviorEvent.java         ← P1 新增: 行为事件记录
    │   ├── ObservedSequence.java      ← P1 新增: 观察序列数据模型
    │   ├── CategoryMapper.java        ← P2+ 新增: 目标→抽象分类映射（砍橡木=砍桦木）
    │   └── LearningSystem.java        ← P1 新增: 试错/固化/退休 ← P2+ 按分类归纳

    ├── memory/                         ← 记忆系统 ← 待调整 P6
    │   ├── MemoryEntry.java
    │   ├── MemoryManager.java
    │   └── MemoryQuery.java
    │
    ├── navigation/                     ← 寻路 (暂冻结)
    │   ├── AStarPathfinder.java
    │   ├── NavigationController.java
    │   └── BlockPosUtil.java
    │
    ├── command/AICommand.java          ← /ai 指令树
    ├── config/ModConfig.java           ← JSON 配置
    ├── log/ExecutionLogger.java        ← 执行日志
    ├── state/                          ← 玩家状态
    │   ├── PlayerState.java
    │   └── StateManager.java
    ├── util/                           ← 工具
    │   ├── FileUtil.java              路径管理
    │   └── JsonUtil.java              Gson 封装
    └── mixin/ExampleMixin.java        占位 mixin

    ├── reflexes/                       ← 反射系统
    │   └── InnateReflexes.java        ← P2 已实现: 安全/非安全分离 + idle动画

    ├── adapter/BasicActionAdapter.java ← 12 原子动作接口 (待 P3)

待新建 (从 TS 移植):
  ├── planner/TaskDecomposer.java       ← 任务拆解 P4
  ├── personality/PersonalityTags.java  ← AI 标签 + 评价 P5
  └── adapter/BasicActionAdapter.java   ← 12 原子动作接口

P1.5 已实现:
  ├── autonomy/SocialObserver.java      ← 多玩家行为观察
  ├── autonomy/NaiveBayesClassifier.java← 朴素贝叶斯分类器
  ├── autonomy/FamiliarityTracker.java  ← 亲密度追踪
  ├── character/ThresholdConfig.java    ← 自适应阈值配置
  └── character/EvaluationCycle.java    ← 月度评估引擎
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
└── config/             config.json
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

## 实施路线

| 阶段 | 内容 | 状态 | 产出 |
|------|------|------|------|
| **P0** | IdleBrain 零成本建议系统 | ✅ | 挂机 0 API |
| P1 | 观察学习系统 (事件捕获+模式检测+固化) | ✅ | 肢体语言模仿 |
| P1.5 | 社交镜像系统 (多玩家贝叶斯+从众系数) | ✅ | 群体智慧 0 API |
| P2 | 优先级链重构 (安全→任务→Idle→非安全) | ✅ | 正确行为流 |
| P3 | 条件反射系统升级 (自动固化/退休) | 🔴 当前 | 反射闭环 |
| P4 | 子任务拆解 + Plan 支持 | ⬜ | 复杂指令 |
| P5 | 玩家评价 + 批量归纳 + 性格标签 | ⬜ | AI 性格 |
| P6 | 目录结构调整 + 清理冻结文件 | ⬜ | 整洁 |
| P7 | 纯 Fabric jar, 零 Node.js 依赖 | ⬜ | 可部属 |

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
