package org.springframework.ai.video.option;

import org.springframework.ai.model.ModelOptions;

/**
 * 视频生成选项接口
 * 该接口定义了视频生成所需的各种配置选项，继承自Spring AI的ModelOptions接口，
 * 为视频生成模型提供统一的参数配置标准。
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
public interface VideoOptions extends ModelOptions {

    /**
     * 获取视频生成的主要提示词
     * 用于描述期望生成的视频内容
     *
     * @return 提示词字符串
     */
    String getPrompt();

    /**
     * 获取使用的视频生成模型名称
     *
     * @return 模型名称
     */
    String getModel();

    /**
     * 获取生成视频的尺寸规格
     * 格式通常为 "宽度x高度"，如 "1920x1080"
     *
     * @return 视频尺寸字符串
     */
    String getImageSize();

    /**
     * 获取反向提示词
     * 用于指定不希望在生成视频中出现的内容
     *
     * @return 反向提示词字符串
     */
    String getNegativePrompt();

    /**
     * 获取参考图像路径或URL
     * 用于基于图像生成视频内容
     *
     * @return 图像路径或URL字符串
     */
    String getImage();

    /**
     * 获取随机种子值
     * 用于控制视频生成的随机性，相同种子值可产生相似结果
     *
     * @return 种子值字符串
     */
    Long getSeed();
}
