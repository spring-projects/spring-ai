package org.springframework.ai.video.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 视频生成模型枚举类
 * 
 * 该枚举定义了系统支持的视频生成模型类型，包括文本到视频和图像到视频两种模式。
 * 每个枚举值包含模型标识符和描述信息。
 * 
 * @author 王玉涛
 * @version 1.0
 * @since 2025/10/2
 */
@Getter
@RequiredArgsConstructor
public enum VideoGenerationModel {
    
    /**
     * 阿里千问文本生成视频模型
     * 该模型可以根据文本描述生成相应的视频内容
     */
    QWEN_TEXT_TO_VIDEO("Wan-AI/Wan2.2-T2V-A14B", "Qwen文生视频模型"),
    
    /**
     * 阿里千问视频生成视频模型
     * 该模型可以根据输入图像生成视频内容
     */
    QWEN_IMAGE_TO_VIDEO("Wan-AI/Wan2.2-I2V-A14B", "Qwen图生视频模型");
    
    /**
     * 模型标识符
     */
    private final String model;
    
    /**
     * 模型描述信息
     */
    private final String description;
}
