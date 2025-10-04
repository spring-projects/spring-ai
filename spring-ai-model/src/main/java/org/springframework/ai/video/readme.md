# Spring AI Video Extension

本模块是基于 Spring AI 框架构建的视频生成扩展快速入门模块。它严格遵循 Spring AI 的核心设计哲学与架构规范，为开发者提供了一套完整的视频处理解决方案，涵盖视频生成、数据存储以及任务状态管理等核心功能。

## 📁 项目结构

```
enhanced/
├── api/                      # 视频 API 客户端
│   └── VideoApi.java         # 与视频服务提供商 API 交互的客户端
├── client/                   # 视频客户端
│   └── VideoClient.java      # 提供给用户的视频操作客户端
├── model/                    # 视频模型相关类
│   ├── VideoModel.java       # 视频模型接口
│   ├── impl/                 
│   │   └── VideoModelImpl.java # 视频模型实现
│   ├── request/              
│   │   └── VideoPrompt.java  # 视频生成请求封装
│   └── response/             
│       ├── VideoResponse.java # 视频生成响应封装
│       └── VideoResult.java  # 视频生成结果数据
├── option/                   # 视频选项配置
│   ├── VideoOptions.java     # 视频选项接口
│   └── impl/                 
│       └── VideoOptionsImpl.java # 视频选项实现
├── storage/                  # 视频存储管理
│   ├── VideoStorage.java     # 视频存储接口
│   ├── VideoStorageStatus.java # 视频存储状态枚举
│   └── impl/                 
│       └── InMemoryVideoStorage.java # 内存存储实现
└── trimer/                   # 视频定时任务处理
    ├── VideoTimer.java       # 视频任务定时扫描器
    ├── config/               
    │   └── VideoTimerConfig.java # 定时任务配置
    ├── enums/                
    │   └── VideoStorageStatus.java # 存储状态枚举
    └── response/             
        └── VideoScanResponse.java # 视频扫描响应
```

## ⚠️ 常见问题与踩坑提示

### 模型使用注意事项
1. **模型选择**：系统支持两种模型：
   - 文生视频模型：`Wan-AI/Wan2.2-T2V-A14B`
   - 图生视频模型：`Wan-AI/Wan2.2-I2V-A14B`
   - 不同模型有不同用途，不能混用

2. **参数配置**：
   - `prompt`：视频生成提示词，不能为空
   - `image`：图生视频时必须提供，文生视频时应为空
   - `model`：必须指定正确的模型名称

### 定时任务注意事项
1. **定时任务依赖配置**：确保在 `application.yaml` 中正确配置定时任务参数
2. **任务状态更新**：任务状态更新时会同时更新状态和扫描结果数据
3. **内存存储限制**：默认使用内存存储，重启服务会丢失数据，生产环境建议替换为持久化存储

### API调用注意事项
1. **API Key配置**：确保在配置文件中正确设置API Key
2. **错误处理**：API调用包含重试机制，但仍需处理网络异常等边界情况
3. **响应处理**：注意检查API响应状态码，非2xx状态码表示调用失败

## 🚀 核心功能

### 1. 视频生成模型 (VideoModel)
- 实现了 Spring AI 的 [Model](file:///D:/program-test2/programming/spring-ai-video-extension/src/main/java/org/springframework/ai/model/Model.java#L27-L51) 接口
- 提供 [VideoModelImpl](file:///D:/program-test2/programming/spring-ai-video-extension/src/main/java/com/springai/springaivideoextension/enhanced/model/impl/VideoModelImpl.java#L32-L117) 实现类，用于调用视频 API 生成视频
- 支持重试机制和监控注册

### 2. 视频客户端 (VideoClient)
- 提供用户友好的 API 接口
- 支持链式调用和参数构建器模式
- 集成视频存储功能

### 3. 视频存储 (VideoStorage)
- 提供 [VideoStorage](file:///D:/program-test2/programming/spring-ai-video-extension/src/main/java/com/springai/springaivideoextension/enhanced/storage/VideoStorage.java#L12-L76) 接口和 [InMemoryVideoStorage](file:///D:/program-test2/programming/spring-ai-video-extension/src/main/java/com/springai/springaivideoextension/enhanced/storage/impl/InMemoryVideoStorage.java#L15-L123) 内存实现
- 支持视频任务的状态管理
- 支持任务的持久化和检索

### 4. 定时任务处理 (VideoTimer)
- 自动扫描未完成的视频生成任务
- 定期查询任务状态并更新存储中的状态
- 支持超时处理和任务清理

## ⚙️ 配置选项

视频生成支持以下配置选项：

- `prompt`: 视频生成提示词
- `model`: 使用的模型名称
- `imageSize`: 生成视频的尺寸
- `negativePrompt`: 负面提示词，排除不希望出现的内容
- `image`: 参考图像路径
- `seed`: 随机种子，用于控制生成的一致性

## 🧪 使用示例

```java
// 1. 构建视频选项
VideoOptions options = VideoOptionsImpl.builder()
        .prompt("一只柯基在沙滩奔跑")
        .model("Wan-AI/Wan2.2-T2V-A14B")
        .negativePrompt("模糊,低质量")
        .build();

// 2. 构建视频 API 客户端
VideoApi videoApi = VideoApi.builder()
        .apiKey("your-api-key")
        .baseUrl("https://api.video-service.com")
        .videoPath("v1/video/submit")
        .build();

// 3. 构建视频模型和客户端
VideoModel videoModel = new VideoModelImpl(videoApi);
VideoStorage videoStorage = new InMemoryVideoStorage();
VideoClient videoClient = new VideoClient(videoModel, videoStorage);

// 4. 调用视频生成
String requestId = videoClient.param()
        .prompt("一只柯基在沙滩奔跑")
        .model("Wan-AI/Wan2.2-T2V-A14B")
        .negativePrompt("模糊,低质量")
        .getOutput();

System.out.println("视频生成请求ID: " + requestId);
```

## ⚙️ 定时任务配置

| 配置项                         | 说明           | 默认值               |
|-----------------------------|--------------|-------------------|
| `ai.video.timer.enabled`    | 是否启用轮询定时任务   | `true`            |
| `ai.video.timer.timeout`    | 任务超时时间（毫秒）   | `300000` (5分钟)    |
| `ai.video.timer.ttl`        | 任务存储 TTL（毫秒） | `86400000` (24小时) |
| `ai.video.timer.interval`   | 轮询间隔（毫秒）     | `30000` (30秒)     |
| `ai.video.timer.key-prefix` | 存储 key 前缀    | `in:memory:key:`   |

> ⚠️ 注意：`ai.video.timer.key-prefix` 默认值已修正为 `in:memory:key:`（以冒号结尾），旧版本缺少末尾冒号可能导致键值处理异常

## 🔄 工作流程

```
sequenceDiagram
    participant Client
    participant API_Server
    participant Cache
    participant Worker

    Client->>API_Server: 1. 提交视频生成请求
    API_Server->>Client: 2. 返回RequestId
    API_Server->>Cache: 3. 存储RequestId和初始状态
    Client->>API_Server: 4. 查询状态（可选手动轮询）
    
    loop 自动轮询流程
        Worker->>Cache: 5. 定时扫描待处理RequestId
        Cache->>Worker: 6. 返回未完成的任务列表
        Worker->>API_Server: 7. 内部查询生成状态
        API_Server->>Worker: 8. 返回最新状态
        alt 状态=Succeed/Failed
            Worker->>Cache: 9. 更新最终状态和结果
        else 状态=InProgress
            Worker->>Cache: 11. 更新进度
        end
    end
```

## 📦 依赖

- Spring Boot 3.5.6
- Spring AI 1.0.2
- Spring Web
- Spring AI OpenAI Starter

## 📄 许可证

本项目基于 Spring AI 框架开发，遵循相应的许可证协议。