# CognitiveBrain — 让 AI 的长期运行成本无限趋近于零

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

### 四个边界推论

**边界 1：层 = 成本防火墙**

L0-L5 存在的意义不是"像脑"，是让 L6（LLM）不被调用。每一层用自己最便宜的方式拦截刺激。

**边界 2：记忆 vs 技能**

MemoryManager 管"是什么"（0 成本查询），ConditionedReflex 管"怎么做"（0 成本执行）。两者分离 = LLM 不需要理解上下文。

**边界 3：预测 vs 执行**

BayesianModule 预测成功率（0 成本计算）。预测准 → 反射 0 成本执行。预测错 → 错误蒸馏 → 更新先验 → 下次更准。

**边界 4：伺候层 vs LLM**

TemplateManager 承担所有协议、格式、重试、路由。LLM 只看 JSON 模板填空。老爷不做苦力。

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
| `TASK_PLAN` | `steps[{action, target, params}]` | TaskManager |
| `EVALUATION_BATCH` | `[{reflexId, delta}]` | EvaluationCycle |
| `FAILURE_CLASSIFY` | `{featureKey, outcome}` | BayesianModule |
| `CHAT_DIRECTION` | `{perspective, priority}` | ChatSessionManager |

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

### MetaScheduler — 动机驱动的动态路由器

```
MetaScheduler.tick():
  1. MotivationEngine.computeDrives()  ← 5通道并行竞争 (玻尔兹曼选择 + 交叉抑制)
  2. labelProblem(label)               ← 贴标签 (现在是哪种问题)
  3. getFlowAdjustment(ctx)            ← 升降级 (AUTOPILOT/NORMAL/OVERRIDE)
  4. dispatch(label, flow)             ← 分派到对应执行层 (LLM门控)
```

---

## 🧩 功能特性

### 四模块目录

| 模块 | 职责 |
|------|------|
| **cortex/** | 规划、复杂决策、语义理解 |
| **hippocampus/** | 记忆存储、高光回忆 |
| **amygdala/** | 条件反射、学习、评价 |
| **brainstem/** | 先天反射、基础动作、生存本能 |

**骨架** (`bayesian/`, `commend/`, `config/`, `util/`): 跨领域支撑层。

### 社交学习

- **一次预警**：玩家说"creeper危险" → 永久记住
- **观察学习**：60s 窗口模式检测 → 3 次自动固化
- **社交镜像**：KNN + 贝叶斯 → 选择性模仿群体
- **镜像抑制**：前额叶否决有害从众（跳崖、打村民）
- **失败记忆**：`failureTags` 记录失败场景 → 贝叶斯后验更新

### 可进化的反射链

- **双权重**：stw（快变）+ ltb（慢变），用进废退
- **休眠不删除**：低频反射标记 dormant，保留 JSON
- **因果链差分重建**：失败时只重建断裂点后的子链
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
# 输出: build/libs/ai-player-mod-1.21.1.jar
```

### 部署

1. 将 jar 放入 `mods/` 目录
2. 启动 Fabric 服务端 (Loader 0.19.3+)
3. 配置 API 密钥: `/ai setkey <your-api-key>`
4. 说自然语言指令（如"帮我挖 10 个铁矿"）

### 常用指令

| 指令 | 功能 |
|------|------|
| `/ai spawn <name>` | 生成指定名字的假人 |
| `/ai despawn [name]` | 移除假人 |
| `/ai list` | 列出所有假人 |
| `/ai status` | 当前任务状态 |
| `/ai reflexes` | 已学习反射 |
| `/ai setkey <key>` | 设置 API 密钥 |
| `/ai reflexpack export <bot> <name> [noprior]` | 导出反射包 (默认含先验) |
| `/ai reflexpack import <bot> <name> [reset]` | 导入反射包 (合并/冷启动) |
| `/ai reflexpack list` | 列出已导入反射包 |
| `/ai reflexpack delete <name>` | 删除反射包 |
| `/ai help` | 全部指令 |

每个假人独立拥有反射库、激素系统、任务、记忆。知识库全局共享。

---

## 📁 运行时数据目录

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
    ├── memory/            高光记忆
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
