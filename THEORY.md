# CognitiveBrain — 理论背景与从零推导

---

## 第一原则

**以降低成本作为主要战略方向，以确定模块边界作为主要降低成本的方法。**

```
边界模糊 → 互相调用 → 本应本地做的事泄露到 LLM → 成本爆炸。
边界清晰 → 每个模块只做自己"最便宜能做到"的事 → LLM 只在边界终端出现一次。
```

---

## 一、核心矛盾

**运行时间 ∞ vs 预算有限。**

在 Minecraft 中控制一个可以长期在线的 AI 玩家，要求：
- **不无限烧钱** → 少调大模型 API
- **能长期在线** → 本地自动学习、自动固化
- **个体差异** → 不同 bot 性格不同
- **行为可解释** → 什么情况做什么，能追溯

这不是研究"意识本质"，而是在有限、可控、可重复的环境里，建一个经济且可演化的行为控制系统。

---

## 二、从零推导：五个递进洞察

### 洞察 1：记忆 ≠ 技能

| 维度 | 记忆（陈述性） | 反射（程序性） |
|------|-------------|-------------|
| 存储内容 | 事实、观察、偏好 | 参数化的步骤链 |
| 精确性要求 | 低（模糊更有价值） | 高（步骤错全错） |
| 执行成本 | 仍需 LLM 理解 | 命中即零成本 |
| 泛化能力 | 低 | 高（参数自动适配） |

**核心论断**：记忆告诉 AI"上次做了什么"，反射告诉 AI"下次怎么做"。

**工程边界**：MemoryManager（陈述性）vs ConditionedReflex（程序性）— 彻底分离。

### 洞察 2：反射链 = 参数化步骤序列

反射不是单一动作，是一组可参数化的原子步骤。泛化的方式不是复制整条链，而是替换链中的参数（目标位置、物品类型等）。

**工程边界**：ConditionedReflex 的 atoms 数组 + stw/ltb 双权重，每条链独立演化。

### 洞察 3：贝叶斯 = 错误预测与拦截

#### 三层存储

| 存储区 | 内容 | 更新频率 | 对应公式项 |
|--------|------|---------|---------|
| 先验条件表 | P(成功) | 极慢 | 先验概率 |
| 后验观测链 | P(环境特征 \| 成功/失败) | 快（每次错误） | 似然度 |
| 主题锚定区 | 当前任务锚点 | 中 | 条件过滤器 |

#### 计算公式

```
P(成功 | 环境) ∝ P(环境 | 成功) × P(成功)
```

#### 错误蒸馏

预测错了不是终点。失败信息被拆解为环境特征（underwater、has_workbench 等），更新条件概率表，下次预测更准。

**工程边界**：BayesianModule（预测）vs ConditionedReflex（执行）— 预测准 = 0 成本执行。

### 洞察 4：激素调度 = 相对优先级

激素只输出相对值（stress vs curiosity vs aggression），不输出绝对值。玻尔兹曼选择只看相对大小，temperature 控制随机性，交叉抑制防止视角振荡。

**工程边界**：HormonalSystem（激素浓度）vs MotivationEngine（视角选择）— 激素不直接决定动作。

### 洞察 5：e = 自然切割边界

| 切割对象 | e 的角色 | 具体数值 |
|---------|---------|---------|
| 探索/利用 | 最优停止边界 | 37% 探索，63% 利用 |
| 任务切换 | 执行/切换比例 | 63.2% 执行，36.8% 缓冲 |
| 记忆衰减 | 遗忘曲线 | e^(-λt) |
| 概率收敛 | KL 散度边界 | 1/e 阈值 |

**工程落点**：探索阈值（MotivationEngine）、切换缓冲（MetaScheduler）、收敛判断（BayesianModule）。

---

## 三、统一表述

> 并发不是并行，是极速串行。
> 无限不是无界，是有限段的递推。
> 复杂不是高维，是压扁后用 e 切割。

反射链线性化任务，激素线性化优先级，贝叶斯线性化不确定性——三个线性化叠加，并发的复杂度被压成一根线，e 告诉你线该切在哪里。

---

## 四、论文引用

### 第一组：贝叶斯大脑与自由能原理

1. **Knill, D. C., & Pouget, A. (2004).** The Bayesian brain. *Trends in Neurosciences*.
   → 支撑"大脑通过概率分布编码不确定性"，对应先验/后验分离存储。

2. **Friston, K. (2010).** The free-energy principle. *Nature Reviews Neuroscience*.
   → 贝叶斯更新 = 变分自由能最小化，对应后验更新机制。

### 第二组：模型误设与收敛边界

3. **Fudenberg, D. et al. (2022).** Bayesian learning with misspecified models. *arXiv*.
   → 后验收敛到 KL 散度最小的近似解，对应不追求"绝对准确"。

### 第三组：最优停止与 37% 法则

4. **Ferguson, T. S. (1989).** Who solved the secretary problem? *Statistical Science*.
   → 37% 法则的数学史依据，对应探索/利用切割。

5. **Gilbert, J. P. & Mosteller, F. (1966).** Recognizing the maximum of a sequence. *JASA*.
   → 最优停止策略的经典证明，对应任务切换窗口。

### 第四组：梯度下降与认知架构

6. **Rosenbloom, P. S. et al. (2013).** Learning via Gradient Descent in Sigma. *CogSci*.
   → 梯度下降可作为认知架构核心学习机制，对应贝叶斯更新工程实现。

7. **Baltieri, M. & Buckley, C. L. (2021).** Kalman filters as gradient descent on variational free energy. *arXiv*.
   → 贝叶斯滤波 = 梯度下降的稳态解。

### 第五组：人脑的无限处理机制

8. **Tozzi, A. & Peters, J. F. (2019).** The brain deals with infinity by means of the finite. *Cognitive Neurodynamics*.
   → 建立观测边界替代处理"无限"，对应探索窗口切割。

### 论文章节建议

| 章节 | 引用论文 |
|------|---------|
| 绪论/理论背景 | Knill & Pouget (2004), Friston (2010) |
| 方法：贝叶斯模块 | Fudenberg et al. (2022), Baltieri & Buckley (2021) |
| 方法：并发与调度 | Ferguson (1989), Gilbert & Mosteller (1966) |
| 方法：认知架构对比 | Rosenbloom et al. (2013) |
| 讨论：认知根源 | Tozzi & Peters (2019) |

---

## 五、Related Work

| 维度 | 已有工作 | 本系统做法 |
|------|---------|-----------|
| 行为分层 | Brooks Subsumption Architecture | 6 层拦截器 + 按成本分层 |
| 双权重学习 | TD(λ) / Dual-process RL | 简化工程实现 + 休眠机制 |
| 情绪/激素调节 | EM-BDI / Homeostatic regulation | 绑定 Perspective 选择 |
| LLM + 技能复用 | Voyager / Generative Agents | 反射固化，成本收敛到 0 |

### 这是组合，不是发明

- 双权重（stw/ltb）≈ Q-learning 的简化变体
- 繁衍 ≈ 遗传算法的特例
- MetaScheduler ≈ Behavior Tree 的变种
- 六层分层 ≈ Subsumption Architecture 的分层继承

### 独特性的可能方向

**"本地优先 + 反射固化 + 休眠不删除"的组合机制**——不同于现有 LLM 缓存方案。

### 生物学启发的声明

本系统引用生物学概念（条件反射、激素、突触可塑性）作为**设计启发**，不是声称模拟神经系统。以下对应关系是**工程简化**：

- 双权重 (stw/ltb) ⇢ 类似突触可塑性的快/慢成分，但不模拟生物机制
- 激素系统 ⇢ 类似神经调节的行为倾向调节，不是内分泌系统模型
- 休眠/复活 ⇢ 类似记忆再巩固，但纯工程决策

这些不是学术贡献，是工程上的简化。

---

> 工程架构详细说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)
> 开发状态与路线图见 [DEVELOPMENT.md](./DEVELOPMENT.md)
> Agent 设计指南见 [AGENTS.md](./AGENTS.md)
