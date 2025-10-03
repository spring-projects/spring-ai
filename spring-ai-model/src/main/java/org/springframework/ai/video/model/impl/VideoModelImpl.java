package org.springframework.ai.video.model.impl;

import com.springai.springaivideoextension.common.util.BeanUtils;
import com.springai.springaivideoextension.enhanced.api.VideoApi;
import com.springai.springaivideoextension.enhanced.model.VideoModel;
import com.springai.springaivideoextension.enhanced.model.request.VideoPrompt;
import com.springai.springaivideoextension.enhanced.model.response.VideoResponse;
import com.springai.springaivideoextension.enhanced.model.response.VideoResult;
import com.springai.springaivideoextension.enhanced.option.VideoOptions;
import com.springai.springaivideoextension.enhanced.option.impl.VideoOptionsImpl;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.Collections;

/**
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
public class VideoModelImpl implements VideoModel {

    private static final Logger logger = LoggerFactory.getLogger(VideoModelImpl.class);


    // 默认配置选项
    private final VideoOptions defaultOptions;
    // 视频API接口
    private final VideoApi videoApi;
    // 重试模板，用于处理请求失败时的重试逻辑
    private final RetryTemplate retryTemplate;
    // 监控注册表，用于收集和报告观察数据
    private final ObservationRegistry observationRegistry;

    /**
     * 构造函数，使用默认配置选项、重试模板和监控注册表
     * @param videoApi 视频API接口
     */
    public VideoModelImpl(VideoApi videoApi) {
        this(videoApi, VideoOptionsImpl.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    /**
     * 构造函数，使用指定的默认配置选项
     * @param videoApi 视频API接口
     * @param defaultOptions 默认配置选项
     */
    public VideoModelImpl(VideoApi videoApi, VideoOptions defaultOptions) {
        this(videoApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    /**
     * 构造函数，使用指定的配置选项和重试模板
     * @param videoApi 视频API接口
     * @param options 配置选项
     * @param retryTemplate 重试模板
     */
    public VideoModelImpl(VideoApi videoApi, VideoOptions options, RetryTemplate retryTemplate) {
        this(videoApi, options, retryTemplate, ObservationRegistry.NOOP);
    }

    /**
     * 完整构造函数，允许自定义所有依赖项
     * @param videoApi 视频API接口
     * @param options 配置选项
     * @param retryTemplate 重试模板
     * @param observationRegistry 监控注册表
     */
    public VideoModelImpl(VideoApi videoApi, VideoOptions options, RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
        Assert.notNull(videoApi, "VideoApi must not be null");
        Assert.notNull(options, "options must not be null");
        Assert.notNull(retryTemplate, "retryTemplate must not be null");
        this.videoApi = videoApi;
        this.defaultOptions = options;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
    }

    /**
     * 调用视频API生成视频
     * @param videoPrompt 视频提示信息
     * @return 视频响应结果
     */
    @Override
    public VideoResponse call(VideoPrompt videoPrompt) {
        // 获取视频提示中的配置选项
        VideoOptions options = (VideoOptions) videoPrompt.getOptions();
        // 优先使用videoPrompt中的prompt，否则使用options中的prompt
        String prompt = BeanUtils.nullThenChooseOther(videoPrompt.getPrompt(), options.getPrompt(), String.class);

        // 构建最终的视频配置选项，按照优先级选择：videoPrompt > options > defaultOptions
        VideoOptions videoOptions = VideoOptionsImpl.builder()
                .prompt(BeanUtils.nullThenChooseOther(prompt, defaultOptions.getPrompt(), String.class))
                .model(BeanUtils.nullThenChooseOther(options.getModel(), defaultOptions.getModel(), String.class))
                .imageSize(BeanUtils.nullThenChooseOther(options.getImageSize(), defaultOptions.getImageSize(), String.class))
                .negativePrompt(BeanUtils.nullThenChooseOther(options.getNegativePrompt(), defaultOptions.getNegativePrompt(), String.class))
                .image(BeanUtils.nullThenChooseOther(options.getImage(), defaultOptions.getImage(), String.class))
                .seed(BeanUtils.nullThenChooseOther(options.getSeed(), defaultOptions.getSeed(), Long.class))
                .build();

        // 使用重试模板执行视频创建请求
        ResponseEntity<VideoResult> resultResponseEntity = retryTemplate.execute(context -> videoApi.createVideo(videoOptions));

        // 检查响应状态并返回相应结果
        if (resultResponseEntity.getStatusCode().is2xxSuccessful()) {
            VideoResult videoResult = resultResponseEntity.getBody();
            return new VideoResponse(videoResult);
        } else {
            logger.error("Error creating video: {}", resultResponseEntity.getStatusCode());
            return new VideoResponse(Collections.emptyList());
        }
    }
}
