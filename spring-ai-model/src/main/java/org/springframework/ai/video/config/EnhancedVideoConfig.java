package org.springframework.ai.video.config;

import com.springai.springaivideoextension.enhanced.api.VideoApi;
import com.springai.springaivideoextension.enhanced.client.VideoClient;
import com.springai.springaivideoextension.enhanced.model.VideoModel;
import com.springai.springaivideoextension.enhanced.model.enums.VideoGenerationModel;
import com.springai.springaivideoextension.enhanced.model.impl.VideoModelImpl;
import com.springai.springaivideoextension.enhanced.option.VideoOptions;
import com.springai.springaivideoextension.enhanced.option.impl.VideoOptionsImpl;
import com.springai.springaivideoextension.enhanced.storage.VideoStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 增强版视频服务配置类
 * 
 * 该配置类负责初始化视频相关的Bean组件，包括视频API客户端和视频存储服务。
 * 通过读取应用配置属性来构建视频API客户端，并提供内存存储的默认实现。
 * 
 * @author 王玉涛
 * @version 1.0
 * @since 2025/10/2
 */
@Slf4j
@Configuration
public class EnhancedVideoConfig {
    
    /**
     * OpenAI API密钥，从配置文件中读取
     * 用于视频处理服务的身份验证
     */
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    
    /**
     * OpenAI API基础URL，从配置文件中读取
     * 用于指定视频处理服务的访问地址
     */
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    
    /**
     * 创建视频API客户端Bean
     * 
     * 使用Builder模式构建VideoApi实例，配置了API密钥、基础URL以及
     * 视频提交和状态查询的路径端点。
     * 
     * @return 配置完成的VideoApi实例
     */
    @Bean
    public VideoApi videoApi() {
        return VideoApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .videoPath("/v1/video/submit")
                .videoStatusPath("/v1/video/status")
                .build();
    }


    /**
     * 创建默认视频选项Bean
     * 
     * 提供视频生成的默认配置，包括提示词和模型选择。
     * 默认模型设置为"Wan-AI/Wan2.2-T2V-A14B"。
     * 
     * @return 默认视频选项配置
     */
    @Bean
    public VideoOptions defaultVideoOptions() {
        return VideoOptionsImpl.builder()
                .prompt("")
                .model(VideoGenerationModel.QWEN_TEXT_TO_VIDEO.getModel())
                .build();
    }

    /**
     * 创建视频模型Bean
     * 
     * 结合视频API客户端和默认选项创建视频模型实现。
     * 作为视频处理的核心业务逻辑层。
     * 
     * @return 配置的视频模型实例
     */
    @Bean
    public VideoModel videoModel() {
        return new VideoModelImpl(videoApi(), defaultVideoOptions());
    }

    /**
     * 创建视频客户端Bean
     * 
     * 通过组合视频模型和存储服务构建视频操作的主要入口点。
     * 提供视频处理的高层接口。
     * 
     * @return 配置的视频客户端实例
     */
    @Bean
    public VideoClient videoClient(VideoStorage videoStorage) {
        return new VideoClient(videoModel(), videoStorage);
    }
}
