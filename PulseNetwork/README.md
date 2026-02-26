# Pulse Network (脉冲网络)

> **用千万台手机，挑战一座数据中心**

纯原生 Android 应用，实现去中心化 AI 网络。

## 核心模块

### 1. 本地总督 (Governor)
物理级算力阀门，保护用户设备：
- 实时监控电池、温度、CPU、内存
- 监听系统广播（充电状态、屏幕开关）
- 智能决策是否释放算力

### 2. 访谈工作流 (Socratic Interview)
人不一定知道自己想要什么，通过苏格拉底式追问封装经验：
- 零代码创建工作流
- 加密封装，隐私保护
- 支持条件分支、循环、并行

### 3. 局域网蜂群 (Swarm Network)
去中心化点对点通信：
- mDNS 零配置设备发现
- 语义缓存共享（"村口八卦"协议）
- 分布式任务调度

## 技术栈

- **语言**: Kotlin + Coroutines
- **架构**: Clean Architecture (Domain/Data/App)
- **DI**: Hilt
- **本地存储**: Room + EncryptedSharedPreferences
- **网络**: 原生 Socket + NsdManager
- **AI 推理**: llama.cpp + whisper.cpp (JNI)

## 项目结构

```
PulseNetwork/
├── app/                    # 应用层
│   ├── src/main/kotlin/
│   │   └── com/pulsenetwork/app/
│   │       ├── ui/         # UI 层
│   │       ├── viewmodel/  # ViewModel
│   │       └── service/    # Android Service
│   └── src/main/cpp/       # JNI 桥接
│
├── domain/                 # 领域层（纯 Kotlin）
│   └── src/main/kotlin/com/pulsenetwork/domain/
│       ├── governor/       # 本地总督
│       ├── workflow/       # 访谈工作流
│       └── swarm/          # 蜂群网络
│
├── data/                   # 数据层
│   └── src/main/kotlin/com/pulsenetwork/data/
│       ├── repository/     # Repository 实现
│       ├── local/          # 本地数据源
│       └── remote/         # 网络数据源
│
└── core/native/            # 原生库
    └── src/main/cpp/
        ├── llama.cpp/      # LLM 推理
        ├── whisper.cpp/    # 语音识别
        └── jni/            # JNI 桥接
```

## 编译要求

- Android Studio Hedgehog+
- JDK 17
- NDK 26+
- CMake 3.22.1+

## 许可证

MIT License
