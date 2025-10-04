# Enhanced Details 文档

## 1. 文档介绍

1. 本技术文档旨在全面记录项目的完整演进历程，涵盖详细设计方案、架构决策考量以及关键实现要点等核心内容。
2. 每个重要的演进节点将独立成章，采用结构化叙述方式，确保内容清晰且易于理解。
3. 本文档基于[飞书源文档：SpringAI 深入探索](https://dcn7850oahi9.feishu.cn/docx/DDehdPBMSoGTycxmFTLcER4In0F?from=from_copylink)进行迭代更新，专注于视频模型的深度细化与实现。

## 2. 已完成功能模块

1. 基于 SpringAI 框架规范，已成功实现视频模型的核心功能体系，包括**任务创建、定时轮询、任务查询、任务存储**等完整解决方案。
2. 当前框架基于**硅基流动**厂商的 API 接口规范进行开发构建。
3. 得益于遵循 OpenAI 标准规范的设计理念，该方案天然兼容硅基流动、OpenAI 等主流厂商的 API 接口。

## 3. 后续演进规划

1. 持续优化架构设计，提升框架的厂商适配能力，实现对多元化 API 文档的兼容支持。
2. 完整记录架构演进过程，形成标准化参考模板，并推广应用于图像模型等其他 AI 模型领域。
3. 当前只实现了基于 Map 的存储方案，后续将会继续扩展 [VideoStorage.java](file:///D:/program-test2/programming/spring-ai-video-extension/src/main/java/com/springai/springaivideoextension/enhanced/storage/VideoStorage.java) 存储方案，引入 Redis 等多样化存储机制，并基于此推动代码设计的进一步优化与演进。

## 4. 架构演进——兼容多厂商API

### 4.1 前言

1. 本次的架构演进，我们将会使用 **火山方舟官方** 提供的API文档作为演示案例。
2. 我们将会提供完整的架构演进方案，这个 **演进的过程、决策过程** 将会作为其他模态模型的演进参考方案。
3. 本次方案将会通过进一步的抽象、架构改造，实现从 **单规范多厂商适配** 到 **多规范多厂商适配** 的跃进。

### 4.2 前置分析
1. 在架构演进开始之前，我们通常需要重新捋一遍当前的架构逻辑，并根据当前的架构逻辑，进行前置分析+方案初步设计
2. 首先我们通过描述当前的架构逻辑，随后给出Mermaid流程图
   - 第一层(请求层): [VideoApi.java](api/VideoApi.java), 该类负责接收[VideoOptions.java](option/VideoOptions.java)参数，并将其作为请求体进行发送
   - 第二层(参数层): [VideoOptions.java](option/VideoOptions.java), 该类负责封装参数逻辑，一方面是作为请求时候的自定义参数，另一方面则是作为发送参数
   - 第三层(模型层): [VideoModel.java](model/VideoModel.java), 该类负责封装模型逻辑，并作为模型参数的接收者, 将参数封装为 [VideoApi.java](api/VideoApi.java), 并调用[VideoApi.java](api/VideoApi.java)进行请求发送
   - 第四层(客户端层): [VideoClient.java](client/VideoClient.java), 该类是核心类，负责统筹复杂的视频模型调用逻辑，具体可以进行如下拆分
     1. 封装请求、发送请求: 通过 param().xx().getOutput()，将请求参数封装为 [VideoOptions.java](option/VideoOptions.java), 并调用 [VideoModel.java](model/VideoModel.java) 进行第一轮的请求发送
     2. 接收响应、持久化响应: 当调用[VideoModel.java](model/VideoModel.java)，且返回值返回到客户端层后，[VideoClient.java](client/VideoClient.java) 通过 [VideoStorage.java](storage/VideoStorage.java) 进行持久化，并将结果返回给调用方
   - 应用层额外(定时任务层): [VideoTimer.java](trimer/VideoTimer.java), 该类负责从[VideoStorage.java](storage/VideoStorage.java)中获取已经存储的 requestId，并调用[VideoApi.java](api/VideoApi.java)进行查询，并根据结果，选择性调用[VideoStorage.java](storage/VideoStorage.java), 更新对应视频状态
   
3. 我们通过Mermaid，画出当前的架构流程图
    ```mermaid
    graph TD
    %% 用户发起请求
        A[Application User] -->|1. 初始化请求| B[VideoClient]
    
    %% 参数构建阶段
        B -->|2. 创建参数构造器| C[ParamBuilder]
        C -->|3. 链式调用设置参数| C
        C -->|4. 构建完成参数| B
    
    %% API调用阶段
        B -->|5. 构建VideoPrompt| D[VideoPrompt]
        D -->|包含配置| E[VideoOptions]
        B -->|6. 调用视频模型| F[VideoModel]
        F -->|7. 委托API调用| G[VideoApi]
        G -->|8. HTTP请求| H[External Video Service]
        H -->|9. 返回原始结果| G
        G -->|10. 封装响应| F
        F -->|11. 返回VideoResponse| B
    
    %% 存储阶段
        B -->|12. 检查存储配置| I{存储已配置?}
        I -->|是| J[videoStorage.save]
        I -->|否| K[log.warn 无存储配置]
        J -->|13. 保存到存储| L{存储成功?}
        L -->|是| M[记录requestId等信息]
        L -->|否| N[log.error 存储失败]
    
    %% 异步状态轮询（独立流程）
        O[VideoTimer Scheduler] -->|14. 定时触发| P[获取待处理请求]
        P -->|15. 查询存储| Q[videoStorage.getPendingRequests]
        Q -->|16. 获取requestIds列表| O
        O -->|17. 逐个查询状态| R[VideoApi.queryStatus]
        R -->|18. 调用外部服务| H
        H -->|19. 返回最新状态| R
        R -->|20. 更新存储状态| S[videoStorage.updateStatus]
    
    %% 最终返回用户
        M -->|21. 返回最终结果| T[VideoResponse to User]
        K -->|21. 返回最终结果| T
        N -->|21. 返回最终结果| T
    
    %% 样式定义
        classDef client fill:#e1f5fe,stroke:#01579b,stroke-width:2px
        classDef api fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px
        classDef storage fill:#fff3e0,stroke:#e65100,stroke-width:2px
        classDef timer fill:#fce4ec,stroke:#c2185b,stroke-width:2px
        classDef external fill:#ffebee,stroke:#b71c1c,stroke-width:2px
        classDef builder fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
        classDef user fill:#e0f2f1,stroke:#004d40,stroke-width:2px
    
        class B,F client
        class G,R api
        class J,Q,S storage
        class O,P timer
        class H external
        class C builder
        class A,T user
    ```
4. 而是否可以实现多厂商、多规范的关键，就在于 **请求发送时候的请求体格式是否兼容** 。
5. 不管是Options作为直传参数、还是Api作为请求发送类，关键在于 **请求的uri 和 请求的参数格式**。
6. 这意味着，我们需要从 [VideoOptions.java](option/VideoOptions.java)接口入手，我们之前预留了该接口，并通过[VideoOptionsImpl.java](option/impl/VideoOptionsImpl.java)验证过，以接口+多态接收作为参数传递这一方法是可行的
7. 我们可以运用这一层抽象，使用策略模式，将不同厂商的参数进行适配，并实现多厂商多规范的逻辑
8. 在上述几点中，我们已经解决了参数传递层次的问题，而我们后续需要解决的，是多厂商、多规范下的兼容问题
9. 我们已经知道，调用大模型的API，需要在请求头中放入api-key，同时需要提供base-url作为请求基地址，也需要uri后缀适配
10. 我们针对VideoApi[VideoApi.java](api/VideoApi.java)目前的请求逻辑进行分析
    ```java
    public class VideoApi {
        // 这里的RestClient，会作为核心请求客户端，这里会进行请求发送，并返回结果
        private final RestClient restClient;
        // 这里的videoPath，会作为视频上传的uri，这里会进行上传请求发送，并返回结果
        private final String videoPath;
        // 这里的videoStatusPath，会作为视频查询的uri，这里会进行查询请求发送，并返回结果
        private final String videoStatusPath;
        
        /**
         * 构造函数，用于创建VideoApi实例
         * 
         * @param baseUrl 基础URL地址，作为所有API请求的根路径
         * @param apiKey API密钥，用于身份验证，如果提供了有效的ApiKey则会添加到请求头中
         * @param headers 额外的请求头信息，允许自定义请求头参数
         * @param videoPath 视频上传接口的路径后缀，将与baseUrl组合成完整的上传接口URL
         * @param videoStatusPath 视频状态查询接口的路径后缀，将与baseUrl组合成完整的查询接口URL
         * @param restClientBuilder RestClient构建器，用于构建和配置HTTP客户端
         * @param responseErrorHandler 响应错误处理器，用于处理HTTP响应中的错误情况
         */
        public VideoApi(String baseUrl, ApiKey apiKey, MultiValueMap<String, String> headers, String videoPath, String videoStatusPath, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {
            // 使用RestClient.Builder构建RestClient实例
            this.restClient = restClientBuilder
                    // 设置基础URL
                    .baseUrl(baseUrl)
                    // 配置默认请求头
                    .defaultHeaders((h) -> {
                        // 如果提供了有效的ApiKey（不是NoopApiKey），则设置Bearer认证头
                        if (!(apiKey instanceof NoopApiKey)) {
                            h.setBearerAuth(apiKey.getValue());
                        }
                        // 设置Content-Type为JSON格式
                        h.setContentType(MediaType.APPLICATION_JSON);
                        // 添加所有自定义请求头
                        h.addAll(headers);
                    })
                    // 设置默认的错误响应处理器
                    .defaultStatusHandler(responseErrorHandler)
                    // 构建RestClient实例
                    .build();
            // 保存视频上传路径
            this.videoPath = videoPath;
            // 保存视频状态查询路径
            this.videoStatusPath = videoStatusPath;
        }
        // ...
    }
    ```

11. 我们发现：apiKey、videoPath、videoStatusPath、restClient 的关系如下：
    - apiKey 与 restClient 高度绑定，正常情况下，是不会直接更换 apiKey 的
    - videoPath、videoStatusPath 也与 restClient 有绑定，但是它们是可以随时更换的，可以通过 Options 参数自定义直传等方式解决
    - 而三者关系如何统筹，又是一道难题，单厂商，是单 uri，但是可以是多 apiKey

12. 而如果为了多厂商、多规范适配，我们是否需要沿用 [飞书源文档](https://dcn7850oahi9.feishu.cn/docx/DDehdPBMSoGTycxmFTLcER4In0F?from=from_copylink) 的自定义集群逻辑，即：
    - 通过配置文件配置多 VideoProperties
    - 再通过多 VideoProperties 实例，配置多 VideoApi，通过 modelId 进行区分，这是**"面向模型"**的集群方案，即为单个模型，配置多 apiKey、videoPath、videoStatusPath、restClient
    - 通过这样配置，我们可以实现的是：apiKey、videoPath、videoStatusPath、restClient 一体化且多配置隔离
    - 但是问题在于：大模型调用，在厂商提供方，会有很严格的并发限制，我们目前的 restClient 完全是同一类型线程池多实例，显然，这里的性能是严重溢出的

13. 此时我们需要考虑，是否要更换一下这样的侧重方式，也就是把 "面向模型" 的集群方案，更换为其他的方案：
    - 因为 **"面向模型"** 的集群方案，本质上是单种模型，配置多厂商、多账户
    - 这种情况下，会带来最大程度的模型控制精细度，不过多 VideoApi 的创建确实会带来一定程度的性能冗余
    - 但是这种情况下也是最方便维护的，关键配置全部显性化，而不是隐藏到更深的层次
    - 且当前的方案，是比较贴合 SpringAI 原生的架构设计的，如果为了灵活度，而大幅度改动底层字段，势必会造成未来的兼容性问题

14. 因此我们依旧沿用原来的自定义集群方案，即 "面向模型" 的集群方案来管理多 apiKey、videoPath、videoStatusPath、restClient。

### 4.3 总结
因此我们发现，架构设计的更新迭代需要从多个维度进行综合权衡，我们总结如下：
- 业务适配度：这是最重要的考量因素，需要评估新旧方案在业务场景中的适配程度是否在可接受范围内
- 短期成本控制：需要对比旧方案当前的维护成本与新方案短期改造成本的大小关系，同时评估新方案带来的收益是否显著高于旧方案
- 长期成本控制：评估新旧方案在长期维护方面的成本差异，以及新方案是否能带来比旧方案更显著的长期收益
- 兼容性：新旧方案之间的兼容性是否满足要求。以本例而言，旧方案在SpringAI兼容性方面表现更优，能够更快速地跟进SpringAI的更新
- 灵活性：对比新旧方案的灵活性差异是否在可接受范围内，主要体现在修改关键配置参数所需的成本
- 需要注意的是：面向接口编程的抽象设计虽然能带来更高的灵活性，但同时也增加了维护成本。过度抽象往往会导致开发和维护成本的双重提升，因此我们需要把握好抽象的度，做到适度抽象
