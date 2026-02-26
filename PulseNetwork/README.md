# Pulse Network (脉冲网络)

> **用千万台手机，挑战一座数据中心**

纯原生 Android 应用，实现去中心化 AI 网络。

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26%2B-green)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

## 核心理念

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   "赛博自然选择，涌现超级智能"                              │
│                                                             │
│   每台手机是一个神经元，                                     │
│   局域网是神经突触，                                         │
│   千万次脉冲汇聚成思想。                                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 核心模块

### 1. 本地总督 (Governor)
物理级算力阀门，保护用户设备：

```
┌──────────────────┐
│   四条件检查     │
├──────────────────┤
│ ✓ 充电中         │
│ ✓ 电量 ≥ 80%     │
│ ✓ 屏幕关闭       │
│ ✓ 温度 < 40°C    │
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  释放算力接单    │
└──────────────────┘
```

### 2. 访谈工作流 (Socratic Interview)
苏格拉底式追问，封装人类经验：

```
问题发现 → 明确输入 → 明确输出 → 深入流程 → 边界情况 → 确认总结
    │                                                    │
    └──────────────► 生成加密工作流 ◄─────────────────────┘
```

### 3. 局域网蜂群 (Swarm Network)
去中心化点对点通信：

```
    ┌─────┐          ┌─────┐
    │节点A│◄────────►│节点B│
    └──┬──┘          └──┬──┘
       │    mDNS       │
       │   发现+P2P    │
    ┌──▼──┐          ┌──▼──┐
    │节点C│◄────────►│节点D│
    └─────┘          └─────┘
         │
         ▼
    ┌─────────────┐
    │ 语义缓存共享 │
    │ ("村口八卦") │
    └─────────────┘
```

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        App Layer                            │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │  Chat   │ │Workflow │ │ Network │ │Settings │           │
│  │   UI    │ │   UI    │ │   UI    │ │   UI    │           │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘           │
│       │           │           │           │                 │
│  ┌────▼───────────▼───────────▼───────────▼────┐           │
│  │              ViewModels + Hilt               │           │
│  └────────────────────┬────────────────────────┘           │
└───────────────────────┼─────────────────────────────────────┘
                        │
┌───────────────────────┼─────────────────────────────────────┐
│                       ▼            Data Layer               │
│  ┌─────────────────────────────────────────────────┐        │
│  │              Repository Implementations          │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐    │        │
│  │  │ Governor │ │ Workflow │ │    Swarm     │    │        │
│  │  │  Service │ │Interviewer│ │   Network    │    │        │
│  │  └──────────┘ └──────────┘ └──────────────┘    │        │
│  └─────────────────────────────────────────────────┘        │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────┼─────────────────────────────────────┐
│                       ▼           Domain Layer              │
│  ┌─────────────────────────────────────────────────┐        │
│  │              Pure Kotlin Interfaces              │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐    │        │
│  │  │ Governor │ │Workflow  │ │    Swarm     │    │        │
│  │  │ Interface│ │Interviewer│ │   Interface  │    │        │
│  │  └──────────┘ └──────────┘ └──────────────┘    │        │
│  └─────────────────────────────────────────────────┘        │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────┼─────────────────────────────────────┐
│                       ▼           Native Layer              │
│  ┌─────────────────────────────────────────────────┐        │
│  │              JNI + C++ Native Libs               │        │
│  │  ┌──────────┐ ┌──────────┐                      │        │
│  │  │llama.cpp │ │whisper.cpp│                      │        │
│  │  │ (LLM)    │ │ (Speech) │                      │        │
│  │  └──────────┘ └──────────┘                      │        │
│  └─────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| **语言** | Kotlin 1.9 + Coroutines |
| **架构** | Clean Architecture |
| **DI** | Hilt |
| **UI** | Material 3 + Navigation |
| **存储** | Room + EncryptedSharedPreferences |
| **网络** | NsdManager (mDNS) + Socket |
| **AI** | llama.cpp + whisper.cpp (JNI) |

## 项目结构

```
PulseNetwork/
├── app/                        # 应用层
│   ├── src/main/kotlin/
│   │   └── com/pulsenetwork/app/
│   │       ├── ui/             # UI Fragment/ViewModel
│   │       │   ├── chat/       # 聊天界面
│   │       │   ├── workflow/   # 工作流界面
│   │       │   ├── network/    # 网络状态
│   │       │   └── settings/   # 设置页面
│   │       └── service/        # Android Service
│   └── src/main/res/           # 资源文件
│
├── domain/                     # 领域层（纯 Kotlin）
│   └── src/main/kotlin/com/pulsenetwork/domain/
│       ├── governor/           # 本地总督接口
│       ├── workflow/           # 访谈工作流接口
│       └── swarm/              # 蜂群网络接口
│
├── data/                       # 数据层
│   └── src/main/kotlin/com/pulsenetwork/data/
│       ├── governor/           # Governor 实现
│       ├── workflow/           # Workflow 实现
│       ├── swarm/              # Swarm 实现
│       └── di/                 # Hilt 模块
│
└── core/native/                # 原生库
    └── src/main/
        ├── cpp/jni/            # JNI 桥接代码
        └── kotlin/             # Kotlin 接口
```

## 编译运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- NDK 26+
- CMake 3.22.1+

### 编译步骤

```bash
# 1. 克隆项目
git clone https://github.com/qiusuojin/pulse-network.git
cd pulse-network

# 2. 打开 Android Studio，导入项目

# 3. 同步 Gradle

# 4. 编译运行
./gradlew assembleDebug
```

### 集成 llama.cpp（可选）

```bash
# 1. 下载 llama.cpp 源码
cd core/native/src/main/cpp
git clone https://github.com/ggerganov/llama.cpp

# 2. 下载 whisper.cpp 源码
git clone https://github.com/ggerganov/whisper.cpp

# 3. 重新编译 Native 库
./gradlew :core:native:assembleRelease
```

## 开发路线

### v0.1 (当前)
- [x] 核心模块接口定义
- [x] Governor 本地总督实现
- [x] SwarmNetwork 局域网蜂群
- [x] WorkflowInterviewer 访谈工作流
- [x] JNI 桥接层
- [x] UI 框架

### v0.2 (计划中)
- [ ] 集成真实 llama.cpp
- [ ] 语音识别 (whisper.cpp)
- [ ] 工作流执行引擎
- [ ] 语义缓存优化

### v0.3 (未来)
- [ ] NAT 穿透
- [ ] DHT 路由
- [ ] 经济系统

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License
