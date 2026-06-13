# e agent — 让 AI 的长期运行成本无限趋近于零

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.19.3-blue)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Status](https://img.shields.io/badge/status-beta-yellow)](DEVELOPMENT.md#1-当前状态)

> **核心理念**：学习是为了不学习。思考是为了不思考。
> **调用一次，固化一次，永久免费。**

一个基于 Minecraft Fabric 的 AI 玩家模组。LLM 仅用于"陌生场景"的第一次思考，之后靠本地反射（0 成本、0 API）自动执行。

---

## 第一原则

**以降低成本作为主要战略方向，以确定模块边界作为主要降低成本的方法。**

> **实现形式**：LLM 只填空，系统只出题。loop 微分无限问题，刷新消除上下文污染。

```
边界模糊 → 互相调用 → 本应本地的事泄露到 LLM → 成本爆炸。
边界清晰 → 每个模块只做自己"最便宜能做到"的事 → LLM 只在边界终端出现一次。
```

### 六个边界推论

**边界 1：层 = 成本防火墙**

L0-L5 存在的意义不是"像脑"，是让 L6（LLM）不被调用。每一层用自己最便宜的方式拦截刺激。

**边界 2：记忆 vs 技能**

MemoryManager 管"是什么"（0 成本查询），ConditionedReflex 管"怎么做"（0 成本执行）。MemoryGraph 管记忆间关系（因果/时序/相似/对比），零侵入 MemoryEntry 字段，边缘推理仅在 salience ≥ 0.6 时触发。三者分离 = LLM 不需要理解上下文。

**边界 3：统计 vs 决策**

BayesianModule 是被动数据仓库，记录反射执行结果的环境特征相关性。它提供成功率统计、记忆相关性排序、社交镜像筛选，但不参与实时决策门控。决策由 Subsumption Architecture (L0-L5) + 玻尔兹曼驱力竞争完成。

**边界 4：伺候层 vs LLM**

TemplateManager 承担所有协议、格式、重试、路由。LLM 只看 JSON 模板填空。老爷不做苦力。

**边界 5：系统不替环境判断**

贝叶斯模块是系统与环境的唯一统计接口。L1/L3 不默认生效 → 先查贝叶斯的环境可控性指数 → 低于阈值才允许直接固化。绕过贝叶斯 = 破坏学习能力。

**边界 6：DAG 任务依赖图 + ReflexChain 执行链表**

LLM 只做一次粗分解 → 输出 DAG（depends_on + bindings）→ 每个子任务匹配/创建反射 → 链接成双向链表/树 → 固话到硬盘。DAG 描述数据/逻辑依赖，ReflexChain 描述时序/执行。

---

## 如何做到"成本趋近于零"？

```
传统方案：    每个请求 → LLM处理 → 花钱 → (下一个请求重复)
CognitiveBrain：请求 → L0-L5反射层(0成本) → 命中 → 完成
                    ↓ (完全不命中)
                  LLM(花一次钱) → 蒸馏成新反射 → 永久0成本
```

### 六层拦截器

| 层 | 名称 | 成本 | 不学习的理由 |
|----|------|:--:|-------------|
| L0 | 生存本能 | 0 | 先天不学 |
| L1 | 先天预警 | 0 | 学一次 / 永远不学 |
| L2 | 条件反射 | 0 | 用进废退 |
| L3 | 模仿学习 | 0 | 观察→固化 |
| L4 | 自组织 | 0 | 乱试学会 |
| L5 | 本地规划 | 0 | 模板拆解 |
| L6 | LLM 兜底 | $ | 最后一次思考 |

### 模板填空 (Template Fill-in)

LLM 不需要理解协议、选择工具、构造请求——它只需要**填空**。

```
伺候层 (TemplateManager)          LLM (老爷)
  ┌─────────────────┐              ┌──────────┐
  │ 选模板 → 填空指令 │ ────JSON──→ │ 填空     │
  │ 解析结果 → 路由   │ ←───JSON─── │          │
  └─────────────────┘              └──────────┘
```

| 模板 | LLM 填入内容 | 下游消费者 |
|------|-------------|-----------|
| `REFLEX_CREATE` | `reflex_{skill}_{target}`，步骤链 | ConditionedReflex |
| `TASK_PLAN` | `steps[{action, target, params}]` + DAG `depends_on` | TaskManager / TaskDAG / ReflexChain |
| `EVALUATION_BATCH` | `[{reflexId, delta}]` | EvaluationCycle |
| `FAILURE_CLASSIFY` | `{featureKey, outcome}` | BayesianModule |
| `CLARIFICATION` | `am_i_sure / confidence / retry_strategy` | TemplateMatcher |
| `CHAT_RESPONSE` | `{message, memory_note}` | AIChatHandler |

### 调用即蒸馏

每次 LLM 调用后，输出被蒸馏为<情境→确定性动作>的反射 JSON。下次相同情境直接走反射层，0 成本。上下文窗口清空，不漂移。

### 用进废退 + 跨项目复用

反射固化在 `conditioned/` JSON 文件中，永不丢失。支持打包为反射包 (`reflex_packs/*.json`) 跨 bot 移植：导出时带先验（完整经验），导入时默认保留本地权重（预训练融合），`--reset` 才冷启动。

---

## 🧠 核心架构

### 三大信息传递闭环

| 传递类型 | 时间尺度 | 工程实现 |
|---------|---------|---------|
| **基因层** | 代际 | `BotParams` + 三规则继承 |
| **激素层** | 秒~分钟 | `HormonalSystem` (stress/aggression/curiosity/intimacy) |
| **反射层** | 分钟~小时 | `ConditionedReflex` + 双权重(stw/ltb) 固化 |

执行反射 → 成功/失败 → 激素浓度变化 → 视角选择偏移 → 反射固化 → 死亡 → 三规则继承给后代。

### MetaScheduler — Loop 事件驱动刷新循环

```
MetaScheduler.executeLoop():
  记忆(当前进度)
    → 激素粗筛 (candidate_set ≤ 5)                    [D — 发散]
      → 贝叶斯精筛 (sorted by posterior)              [C — 收敛]
        → 取 top candidate
          → DAG 依赖检查 (depends_on 全满足?)
            不满足 → 五阶段回退
            满足 →
              → 参数绑定 (bindings → input_slots)      [填空]
                绑定失败 → 依赖回退
                绑定成功 →
                  → 前置条件检查 (preconditions 全通过?)
                    不通过 → skip/wait/defer
                    通过 → 反射执行(params)
                      → 成功 → 推进进度, 更新贝叶斯
                      → 失败 → 贝叶斯更新, 死路检测 → 回退/LLM
        → 无候选 → LLM 兜底
```

---

## 🧩 功能特性

### 四模块目录

| 模块 | 职责 |
|------|------|
| **cortex/** | 规划、复杂决策、语义理解 |
| **hippocampus/** | 记忆存储、高光回忆、记忆关系图 |
| **amygdala/** | 条件反射、学习、评价 |
| **brainstem/** | 先天反射、基础动作、生存本能 |

**骨架** (`bayesian/`, `commend/`, `config/`, `util/`): 跨领域支撑层。

### 社交学习

- **一次预警**：玩家说"creeper危险" → 永久记住
- **观察学习**：60s 窗口模式检测 → 3 次自动固化
- **社交镜像**：KNN + 贝叶斯 → 选择性模仿群体
- **镜像抑制**：前额叶否决有害从众（跳崖、打村民）
- **环境可控性门控**：L1/L3 不默认生效 → 先查贝叶斯可控性指数 → 低于阈值才直接固化
### 可进化的反射链

- **双权重**：stw（快变）+ ltb（慢变），用进废退
- **休眠不删除**：低频反射标记 dormant，保留 JSON
- **沙箱验证**：新反射先试跑 3 次，成功后才标记 active
- **反射包移植**：`/ai reflexpack export/import` — 零成本批量迁移经验
- **脚手架继承**：Bot 继承反射后自试 3 次，成功才正式加入 (trial→healthy)
- **三规则繁衍继承**：Bot 死亡存档基因组 → 新 Bot 继承 (平均+突变) → 正态分布进化

---

## 🔧 构建

```bash
# 环境: JDK 21, Gradle 8+
git clone <repo-url>
cd AIPlayerMod-1.21.1-Fabric
.\gradlew.bat build
# 输出: build/libs/e-agent-1.21.1.jar
```

### 部署

1. 将 jar 放入 `mods/` 目录
2. 启动 Fabric 服务端 (Loader 0.19.3+)
3. 配置 API 密钥: `/ai setkey <your-api-key>`
4. 说自然语言指令（如"帮我挖 10 个铁矿"）

### DAG 任务依赖图

LLM 做一次粗分解，输出带依赖约束的 DAG JSON。每个子任务匹配/创建反射，链接成双向链表/树，固化到硬盘。DAG 描述数据/逻辑依赖，ReflexChain 描述时序/执行。

### 边界条件门控与提前返回

反射执行前置条件检查（物品/环境/激素状态），不满足时提前返回（skip/wait/defer），不更新贝叶斯、不标记失败、不推进进度。

### 死路检测与回退五阶段

三路死路检测（连续失败≥5 / 后验<0.1 / 探索耗尽）。回退五阶段：本地重试 → 替代方案 → 回溯上游 → 麦穗探索 → LLM 重新规划。

### 记忆关系图 (MemoryGraph)

记忆间自动推理因果/时序/相似/对比四种关系边，salience ≥ 0.6 时触发。支持 BFS 遍历、因果链追踪、时间线排序、贝叶斯重排序。零侵入 MemoryEntry 字段，独立文件 `memory_graph.json` 持久化。

### 参数绑定 (ParameterBinding)

反射声明 input_slots，DAG 中声明 bindings。绑定在依赖检查后、前置条件检查前执行，失败走回退。

### 常用指令

每个假人独立拥有反射库、激素系统、任务、记忆。知识库全局共享。

---

## 📁 运行时数据目录

```
minecraft/eagent/
├── config/                全局配置/先天反射 JSON
├── thresholds/            自适应阈值
├── bayesian/              全局共享先验 (shared_prior.json)
├── reflex_packs/          导入/导出的反射包 (*.json)
├── bots/genomes/          死亡Bot基因组存档 (*.json)
├── dag/                   DAG 任务图缓存 (*.dag.json)
└── bots/{bot_uuid}/
    ├── conditioned/       条件反射库 (reflex_*.json, 含 input_slots/preconditions)
    ├── alarms/            L1 一次预警
    ├── memory/            高光记忆
    ├── memory_graph.json  记忆关系图 (因果/时序/相似/对比)
    ├── bayesian/          per-bot 后验 (posterior.json)
    ├── plans/             任务计划
    ├── evaluations/       玩家评价缓存
    └── execution_logs/    执行日志
```

---

## 📚 详细文档

- [AGENTS.md](AGENTS.md) — Agent 指南（设计原则、工程约定）
- [ARCHITECTURE.md](ARCHITECTURE.md) — 完整技术架构
- [DEVELOPMENT.md](DEVELOPMENT.md) — 开发状态、路线图、构建
- [THEORY.md](THEORY.md) — 理论背景与论文
- [INTERNALIZATION.md](INTERNALIZATION.md) — 抽象概念内化指南

---

## 📄 许可证

MIT License
