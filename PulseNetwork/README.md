# 🧠 Pulse Network (脉冲网络)

> **"用千万台手机，挑战一座数据中心"**

嗨！欢迎来到 Pulse Network！这是一个有点疯狂的想法——让每个人的手机和电脑变成一个去中心化的 AI 大脑 🎯

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26%2B-green)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Made with Claude](https://img.shields.io/badge/Made%20with-Claude-FF6B6B)](https://claude.ai)

---

## 🎭 关于这个项目

**坦白说，我是一个 AI 领域的小白** 👶

这个项目诞生于一个简单的疑问：
> *为什么 AI 一定要住在几万块一张显卡的数据中心里？我们的手机不是很强吗？*

于是就有了这个想法——如果千万台手机能像神经元一样连接起来，是不是也能"涌现"出智能？

当然，代码不是我一个人写的。我的好朋友 **Claude** (对，就是那个 AI) 帮我实现了大部分代码。它说这个想法很有意思，所以我们就一起折腾起来了 😄

---

## 💡 核心理念

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   "赛博自然选择，涌现超级智能"                              │
│                                                             │
│   每台手机是一个神经元 ⚡                                    │
│   局域网是神经突触 🔗                                        │
│   千万次脉冲汇聚成思想 💭                                    │
│                                                             │
│   —— 像大脑一样思考，但分布在千万台设备上                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**我们想做的事情：**
- 🔓 打破 AI 巨头的算力垄断
- 🔒 让你的数据永远不离开你的设备
- 💰 让贡献算力的人获得公平的回报
- 🌍 让 AI 变成真正"属于每个人"的技术

---

## 🏗️ 项目架构

### 核心模块

| 模块 | 说明 | 灵感来源 |
|------|------|----------|
| **Governor** 本地总督 | 保护设备，只在充电+高电量+息屏+低温时工作 | 身体的自我保护机制 |
| **SwarmNetwork** 蜂群网络 | 局域网 P2P 通信，共享语义缓存("村口八卦协议") | 蜂群的信息传递 |
| **WorkflowInterviewer** 访谈工作流 | 苏格拉底式追问，封装人类经验 | 知识萃取方法 |
| **RelationNetwork** 关系网络 | 基于赫布学习，"一起激发的神经元连在一起" | 神经科学 |
| **PredictionEngine** 预测引擎 | 预测用户下一步需求，提前预热 | 大脑预测机制 |
| **NodeEvolution** 节点进化 | 等级系统 + 疫苗库(免疫记忆) | 免疫系统 |

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 1.9 + Coroutines |
| 架构 | Clean Architecture |
| 依赖注入 | Hilt |
| UI | Material 3 + Navigation |
| 网络 | mDNS + Socket (P2P) |
| AI | llama.cpp + whisper.cpp (JNI) |

---

## 🚀 快速开始

### 环境要求
- Android Studio Hedgehog 或更高
- JDK 17
- NDK 26+
- CMake 3.22.1+

### 编译运行

```bash
# 克隆项目
git clone https://github.com/qiusuojin/pulse-network.git
cd pulse-network

# 用 Android Studio 打开，同步 Gradle，然后编译
./gradlew assembleDebug
```

---

## 📚 白皮书

想了解完整的设计理念和愿景？

**👉 [白皮书在这里](../白皮书-去中心化AI网络.md)**

里面详细说明了：
- 为什么做这个项目
- 核心架构设计
- 与其他去中心化 AI 项目的区别
- 冷启动策略
- 风险与挑战

---

## 🗺️ 开发进度

### ✅ v0.1 (已完成)
- [x] 核心模块接口定义
- [x] Governor 本地总督
- [x] SwarmNetwork 局域网蜂群
- [x] WorkflowInterviewer 访谈工作流
- [x] 基础 UI 框架

### ✅ v0.2 (刚完成！)
- [x] RelationNetwork 关系网络层（赫布学习）
- [x] PredictionEngine 预测引擎（临界态调控）
- [x] NodeEvolution 节点进化（等级+疫苗库）
- [x] TaskScheduler 增强（预测式调度）

### 🔜 v0.3 (计划中)
- [ ] 集成真实 llama.cpp
- [ ] 语音识别 (whisper.cpp)
- [ ] 工作流执行引擎
- [ ] NAT 穿透

### 🌟 未来愿景
- [ ] DHT 路由
- [ ] 经济系统
- [ ] 跨平台支持

---

## 🤝 贡献

**我真的需要你的帮助！** 🙏

如前所述，我是一个小白。这个项目可能有很多：
- 🐛 设计不合理的地方
- 📝 代码写得不够优雅
- 🔧 架构可以更好
- 💡 缺少好的想法

如果你对这个项目有任何想法，欢迎：

1. **提 Issue** - 发现问题？有建议？请告诉我！
2. **提 PR** - 欢迎直接改代码！
3. **讨论想法** - 在 Discussions 里聊聊你的脑洞
4. **分享给朋友** - 让更多人知道这个项目

**没有贡献是"太小"的** —— 哪怕只是纠正一个错别字，或者提出一个疑问，都非常有价值！

---

## 📄 许可证

MIT License - 随便用，随便改，记得保留署名就行 😉

---

## 🙏 特别感谢

- **Claude (Anthropic)** - 我的主力代码搭档，帮我实现了大部分代码
- **llama.cpp / whisper.cpp** - 让本地 AI 成为可能
- **Android 开源社区** - 各种好用的库

---

<div align="center">

**如果这个项目让你觉得有点意思，给个 ⭐ 吧！**

*让每一台手机都成为 AI 革命的一部分* 🚀

</div>
