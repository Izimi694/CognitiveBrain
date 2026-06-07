# AI Player Mod — 基于神经科学的 Minecraft AI 助手

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.19.3-blue)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Status](https://img.shields.io/badge/status-beta-yellow)](#测试版状态)

> **核心理念**：脑干（模组本地执行）+ 外置大脑（LLM 语义理解）

一个能在 Minecraft 中自主生存、向玩家学习、形成习惯性格的 AI 玩家。
以神经科学为蓝图，将人脑功能映射为四个解耦模块，
实现从"本能反射"到"性格标签"的完整认知链路。

---

## 🧠 设计思路

### 人脑 = 五脑区，模组 = 四模块

```
人的思想 = 前额叶（理性规划+抑制） + 杏仁核（情绪驱动）
          + 基底节（习惯） + 海马体（记忆） + 脑干（执行）
          ↓
      基底节工程上合并于杏仁核
          ↓
模组 = cortex/ + amygdala/ + hippocampus/ + brainstem/
```

### 设计原则

| 原则 | 内容 |
|------|------|
| **成本优先** | LLM 只做"本地无法完成的事"——理解语义、拆解任务。其余全部本地零成本。 |
| **脑区解耦** | 每个新功能归属于一个脑区模块，层次分明，互不越界。 |
| **连续内层，离散外层** | 学习层使用连续数值（权重、概率），交互层使用离散符号（指令、评价）。数字负责"怎么变"，符号负责"是什么"。 |

### 哲学根基

以物理主义和唯物主义为底层框架：思维不是神秘的"意识"，
而是反射权重的物理分布。LLM 做"具体情况具体分析"，
反射做"泛化"——这正是马克思主义辩证法的工程化实现。性格的
稳定与变化不过是同一系统的两个时间尺度。

### 决策优先级链（P0 → P5）

```
tick (每 2 秒):
  P0   安全反射 (逃跑/进食)          ← 0 API, 毫秒
  P1   玩家任务 + 固化反射匹配       ← 0 API
  P2   玩家复杂指令 (无匹配反射)     ← 1 API (异步)
  P3   条件反射自动触发 (无任务时)   ← 0 API
  P4   AI 自主 (IdleBrain/SocialMirror) ← 0 API
  P5   Idle 动画                    ← 0 API
```

**成本模式**：挂机1小时 = 0次API。活跃1小时 ≈ 8次（任务拆解 + 性格分析 + 评价归纳）。

---

## 🧩 功能特性

### 四模块能力

| 模块 | 脑区 | 能力 |
|------|------|------|
| **cortex/** | 前额叶 | LLM 任务拆解、Plan 管理、抑制控制（否决不当行为） |
| **hippocampus/** | 海马体 | 高光记忆存储、记忆检索 |
| **amygdala/** | 杏仁核+基底节 | 安全反射、条件反射固化、反射权重动力学、社交镜像、评价强化 |
| **brainstem/** | 脑干 | 12 原子动作、寻路、bot 实体管理、Idle 动画 |

### 12 原子动作

`moveTo` / `lookAt` / `dig` / `attack` / `placeBlock` / `useItem` / `equipItem` / `openBlock` / `closeWindow` / `clickSlot` / `chat` / `jump`

### 6 先天反射

`flee` / `eat` / `retreat` / `avoidLava` / `seekShelter` / `collectItem` — 全部 JSON 可配，0 API

### 社交学习

- **观察学习**：监听玩家行为 → 60s 窗口模式检测 → 3次成功自动固化
- **社交镜像**：KNN + 朴素贝叶斯 → 选择性模仿群体
- **模仿抑制**：前额叶否决有害从众（跳崖、打村民）

### AI 性格（反射权重模型）

- **性格 = 反射权重的统计分布**：无独立标签模块，所有行为倾向编码在反射的 `shortTermWeight` + `longTermBaseline` 中
- **负反馈稳定**：短期权重向长期基线回归，短时不变，长期互动中缓慢演进
- **个体差异**：每个 AI 的学习率(α)和遗忘率(β)随机初始化，自然形成"固执"或"善变"
- **直接强化**：玩家夸奖 → LLM 判"好" → 强化刚执行的反射权重，无需翻译为抽象标签
- **反射归档**：成功率过低的反射标记为休眠，但不删除，可复活

---

## 🔧 构建

```bash
# 环境要求: JDK 21, Gradle 8+

git clone <repo-url>
cd AIPlayerMod-1.21.1-Fabric

# 编译
.\gradlew.bat build

# 运行测试 (58 tests)
.\gradlew.bat runTests

# 输出: build/libs/ai-player-mod-1.21.1.jar
```

> 由于项目路径含中文，标准 `test` 任务需使用 `runTests` 自定义任务规避 ClassNotFoundException。

---

## 🚀 部署

1. 将 `build/libs/ai-player-mod-1.21.1.jar` 放入 `mods/` 目录
2. 启动 Fabric 服务器 (Loader 0.19.3+)
3. 首次启动后，配置 API 密钥：

```
/ai setkey <your-deepseek-api-key>
```

4. 对 AI 玩家说自然语言指令（如"帮我挖10个铁矿"）
5. 查看状态：

```
/ai status          # 当前任务
/ai personality     # AI 学习参数 (α/β) 及反射统计
/ai reflexes        # 已学习的条件反射
/ai suggestions     # 触发主动建议
/ai help            # 全部指令
```

---

## ⚠️ 测试版状态

**当前版本为测试版 (beta)**，以下功能仍在开发中：

| 状态 | 说明 |
|------|------|
| ✅ | 四个脑区模块全部实现，58 测试 0 失败 |
| ✅ | 安全反射、条件反射、社交镜像均已可用 |
| ✅ | LLM API 异步集成（DeepSeek） |
| ✅ | 反射权重动力学 + 评价强化系统 |
| ✅ | 前额叶抑制控制 |
| ⬜ | P7 — 纯 Fabric jar 最终部署验证 |
| ⚠️ | 寻路暂未集成到主循环（AStarPathfinder 存在但未接入） |
| ⚠️ | 合成、附魔等高阶技能未完整实现 |
| ⚠️ | 多 bot 同时运行未经测试 |

---

## 📁 运行时数据目录

```
minecraft/ai_memory/
├── conditioned/         条件反射库 (JSON，含 shortTermWeight + longTermBaseline)
│   └── archived/         休眠反射归档
├── memory/               记忆 (7天窗口)
│   ├── highlights/
│   └── trials/
├── evaluations/          玩家评价缓存
├── plans/                任务计划
├── config/
│   ├── config.json       模组全局配置
│   ├── innate_reflexes.json  先天反射配置
│   └── bot_params.json    AI 实体参数 (α/β 学习率)
├── skills/
│   └── character/
│       └── thresholds/
│           └── thresholds.json  社交镜像阈值
└── execution_logs/       执行日志
```

---

## 📚 详细文档

完整架构、设计原则、已知问题及修复历史见 [AGENTS.md](AGENTS.md)。

---

## 🧬 未来计划

**角色性格生成插件** — 自然语言描述角色性格（如"像炭治郎一样温柔但对恶鬼绝不手软"），LLM 生成反射权重分布 JSON，丢入 `conditioned/` 即可。任何 LLM 都胜任，迭代只需重新描述。

---

## 📄 许可证

MIT License
