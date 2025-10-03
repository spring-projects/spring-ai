package org.springframework.ai.video.model.request;

import com.springai.springaivideoextension.enhanced.option.VideoOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 视频生成请求提示类
 * 用于封装视频生成所需的提示信息和配置选项
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
public class VideoPrompt implements ModelRequest<List<String>> {

    /**
     * 提示词列表，用于指导视频生成内容
     */
    private List<String> prompts;

    /**
     * 视频生成选项配置
     */
    private VideoOptions videoOptions;

    /**
     * 构造函数，使用提示词列表和选项配置创建视频提示
     *
     * @param prompts 提示词列表
     * @param videoOptions 视频生成选项配置
     */
    public VideoPrompt(List<String> prompts, VideoOptions videoOptions) {
        this.prompts = prompts;
        this.videoOptions = videoOptions;
    }

    /**
     * 构造函数，使用单个提示词和选项配置创建视频提示
     *
     * @param prompt 单个提示词
     * @param videoOptions 视频生成选项配置
     */
    public VideoPrompt(String prompt, VideoOptions videoOptions) {
        this.prompts = List.of(prompt);
        this.videoOptions = videoOptions;
    }

    /**
     * 获取提示词指令列表
     *
     * @return 提示词列表
     */
    @Override
    public List<String> getInstructions() {
        return this.prompts;
    }

    /**
     * 获取视频生成选项配置
     *
     * @return 视频选项配置
     */
    @Override
    public ModelOptions getOptions() {
        return this.videoOptions;
    }

    /**
     * 获取第一个提示词
     *
     * @return 第一个提示词字符串
     * @throws IllegalArgumentException 当提示词为空或null时抛出异常
     */
    public String getPrompt() {
        Assert.notNull(this.prompts, "Prompt cannot be null.");
        Assert.isTrue(!this.prompts.isEmpty(), "Prompt cannot be Empty.");
        return this.prompts.get(0);
    }
}
