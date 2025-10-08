package org.springframework.ai.video.option.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.springai.springaivideoextension.enhanced.option.VideoOptions;
import lombok.Builder;
import lombok.Data;

/**
 * 视频生成选项实现类
 *
 * 该类用于封装视频生成所需的各种配置参数，通过Builder模式构建，
 * 实现了VideoOptions接口，提供视频生成的完整配置选项。
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoOptionsImpl implements VideoOptions {

    /**
     * 视频生成的主要提示词
     * 描述期望生成的视频内容
     */
    @JsonProperty("prompt")
    private String prompt;

    /**
     * 使用的AI模型名称
     * 指定用于视频生成的具体模型
     */
    @JsonProperty("model")
    private String model;

    /**
     * 生成视频的尺寸规格
     * 格式如: "1024x1024", "1280x720" 等
     */
    @JsonProperty("image_size")
    private String imageSize;

    /**
     * 负面提示词
     * 指定不希望在生成视频中出现的内容
     */
    @JsonProperty("negative_prompt")
    private String negativePrompt;

    /**
     * 参考图像路径或URL
     * 用于图像到视频的生成任务
     */
    @JsonProperty("image")
    private String image;

    /**
     * 随机种子
     * 用于控制生成过程的随机性，相同种子可产生一致结果
     */
    @JsonProperty("seed")
    private Long seed;
}
