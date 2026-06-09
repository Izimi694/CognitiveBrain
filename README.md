# CognitiveBrain — 让 AI 的长期运行成本无限趋近于零

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.19.3-blue)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Status](https://img.shields.io/badge/status-beta-yellow)](#测试版状态)

> **核心理念**：学习是为了不学习。思考是为了不思考。
> **调用一次，固化一次，永久免费。**

一个基于 Minecraft Fabric 的 AI 玩家模组（原名 `AI Player Mod`）。
LLM 仅用于"陌生场景"的第一次思考，之后靠本地反射（0 成本、0 API）自动执行。

---

## 它解决了什么核心问题？

| 问题 | 传统方案的表现 |
|------|-------------|
| **成本爆炸** | 每次对话都调用 LLM，长期运行账单失控。多智能体方案更恐怖——一个任务消耗 10 万+ token。 |
| **性格漂移** | 上下文窗口有限，AI 会忘记你教它的东西。今天是朋友，明天是陌生人。 |
| **经验孤岛** | 每个项目从零学习。Minecraft 服务器的经验，跨服学不到（复制 `conditioned/` JSON 即可复用）。 |

---

## 如何做到"成本趋近于零"？

```
传统方案：    每个请求 → LLM处理 → 花钱 → (下一个请求重复)
CognitiveBrain：请求 → L0-L5反射层(0成本) → 命中 → 完成
                    ↓ (完全不命中)
                  LLM(花一次钱) → 蒸馏成新反射 → 永久0成本
```

### 三大降低策略

**策略一：六层拦截器（L0-L6）**

每一层的存在，都是为了让下一层不需要被调用。

| 层 | 名称 | 成本 | 不学习的理由 |
|----|------|:--:|-------------|
| L0 | 生存本能 | 0 | 先天不学 |
| L1 | 先天预警 | 0 | 学一次 / 永远不学 |
| L2 | 条件反射 | 0 | 用进废退 |
| L3 | 模仿学习 | 0 | 观察→固化 |
| L4 | 自组织 | 0 | 乱试学会 |
| L5 | 本地规划 | 0 | 模板拆解 |
| L6 | LLM 兜底 | $ | 最后一次思考 |

**策略二：调用即蒸馏**

每次 LLM 调用后，输出被蒸馏为<情境→确定性动作>的反射 JSON。
下次相同情境直接走反射层，0 成本。上下文窗口清空，不漂移。

**策略三：用进废退 + 跨项目复用**

反射固化在条件反射库 JSON 中（`conditioned/`），永不丢失。
一个 bot 学会的技能，复制 `conditioned/` 文件夹即导出。
新 bot 加载后直接使用，边际成本随部署数增加无限摊薄。

---

## 🧠 核心架构

### 三大信息传递闭环

| 传递类型 | 时间尺度 | 工程实现 |
|---------|---------|---------|
| **基因层** | 代际 | `BotParams` + 三规则继承 |
| **激素层** | 秒~分钟 | `HormonalSystem`（stress/aggression/curiosity/intimacy） |
| **反射层** | 分钟~小时 | `ConditionedReflex` + 双权重(stw/ltb) 固化 |

执行反射 → 成功/失败 → 激素浓度变化 → 视角选择偏移 → 反射固化 → 死亡 → 三规则继承给后代。

### MetaScheduler — 动机驱动的动态路由器

```
MetaScheduler.tick():
  1. MotivationEngine.computeDrives()  ← 5通道并行竞争 (玻尔兹曼选择)
  2. labelProblem()                     ← 贴标签（现在是哪种问题）
  3. getFlowAdjustment(ctx)             ← 升降级（该升还是降）
  4. dispatch(label, flow)              ← 分派到对应执行层 (LLM门控)
```

---

## 🧩 功能特性

### 四模块目录

| 模块 | 职责 |
|------|------|
| **cortex/** | 规划、复杂决策、语义理解 |
| **hippocampus/** | 记忆存储、高光回忆 |
| **amygdala/** | 条件反射、学习、评价、激素 |
| **brainstem/** | 先天反射、基础动作、生存本能 |

### 社交学习

- **一次预警**：玩家说"creeper危险" → 永久记住
- **观察学习**：60s 窗口模式检测 → 3 次自动固化
- **社交镜像**：KNN + 朴素贝叶斯 → 选择性模仿群体
- **镜像抑制**：前额叶否决有害从众（跳崖、打村民）
- **失败记忆**：`failureTags` 记录失败场景，防止反复重试

### 可进化的反射链

- **双权重**：stw（快变）+ ltb（慢变），用进废退
- **休眠不删除**：低频反射标记 dormant，保留 JSON
- **因果链差分重建**：失败时只重建断裂点后的子链，不重建整条链
- **沙箱验证**：新反射先试跑 3 次，成功后才标记为 active
- **组块化**：连续成功 N 次后，多步焊接为一步

---

## 🔧 构建

```bash
# 环境要求: JDK 21, Gradle 8+
git clone <repo-url>
cd AIPlayerMod-1.21.1-Fabric
.\gradlew.bat build
# 输出: build/libs/ai-player-mod-1.21.1.jar
```

## 🚀 部署

1. 将 `build/libs/ai-player-mod-1.21.1.jar` 放入 `mods/` 目录
2. 启动 Fabric 服务器 (Loader 0.19.3+)
3. 配置 API 密钥：`/ai setkey <your-api-key>`
4. 说自然语言指令（如"帮我挖 10 个铁矿"）

### 常用指令

| 指令 | 功能 |
|------|------|
| `/ai spawn <name>` | 生成指定名字的假人 |
| `/ai despawn [name]` | 移除假人 |
| `/ai list` | 列出所有假人 |
| `/ai bot <name> <指令>` | 指定假人执行指令 |
| `/ai status` | 当前任务状态 |
| `/ai model [name]` | 查看/设置 AI 模型 |
| `/ai personality` | 查看 α/β 参数及反射统计 |
| `/ai reflexes` | 已学习条件反射 |
| `/ai setkey <key>` | 设置 API 密钥 |
| `/ai suggestions` | 触发主动建议 |
| `/ai help` | 全部指令 |

每个假人独立拥有自己的反射库、激素系统、任务、记忆。知识库（合成表等）、RecipeManager、API 客户端全局共享。

`@bot_name <消息>` — 精确路由到指定假人。无 @ 时路由到最近假人。`@` 不匹配时，由最近假人的 IdleBrain 判断是否执行。`/ai despawn` 不带名字时移除最近假人。

---

## ⚠️ 测试版状态

| 状态 | 说明 |
|:----:|------|
| ✅ | 四模块 + 六层拦截器完整实现 |
| ✅ | MetaScheduler + MotivationEngine (5通道玻尔兹曼 + 交叉抑制) |
| ✅ | LLM 门控 (6条件合取) + Curiosity 指数衰减 (30min半衰期) |
| ✅ | ConditionedReflex + 双权重 + failureTags + 差分重建 |
| ✅ | HormonalSystem + OneShotAlarmSystem |
| ✅ | SocialObserver + 社交镜像 + 抑制控制 |
| ✅ | 12 原子动作 + 原版 RecipeManager 合成 |
| ✅ | Phase 5 — 紧急程度 + 时间缩放 + 自组织 |
| ✅ | Phase 6 — Multi-bot 共存 |
| ⚠️ | 寻路未接入主循环 |
| ⬜ | Phase 7 — 繁衍模块 |

---

## 📁 运行时数据目录

```
minecraft/ai_memory/
├── config/               全局配置
├── thresholds/           自适应阈值
└── bots/{bot_uuid}/      每个假人独立命名空间
    ├── conditioned/      条件反射库 (反射包 = 此目录)
    ├── alarms/           L1 一次预警
    ├── memory/           记忆
    ├── plans/            任务计划
    ├── evaluations/      玩家评价缓存
    └── execution_logs/   执行日志
```

---

## 📚 详细文档

- [AGENTS.md](AGENTS.md) — 完整架构、设计权衡、11 个脑启发优化方向
- [THEORY.md](THEORY.md) — 工程理由说明
- [INTERNALIZATION.md](INTERNALIZATION.md) — 抽象概念的内化指南

---

## 📄 许可证

MIT License
