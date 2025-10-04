package org.springframework.ai.video.model.response;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ResponseMetadata;

import java.util.List;

/**
 * 视频响应类，用于封装视频生成API的响应结果
 * 实现了Spring AI的ModelResponse接口，提供对视频结果的统一访问
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
public class VideoResponse implements ModelResponse<VideoResult> {

    /**
     * 视频结果列表，包含一个或多个视频生成结果
     */
    private List<VideoResult> videoResults;

    /**
     * 响应元数据，包含请求相关的元信息
     */
    private ResponseMetadata responseMetadata;

    /**
     * 构造函数，使用视频结果列表和响应元数据创建VideoResponse实例
     *
     * @param videoResults 视频结果列表
     * @param responseMetadata 响应元数据
     */
    public VideoResponse(List<VideoResult> videoResults, ResponseMetadata responseMetadata) {
        this.videoResults = videoResults;
        this.responseMetadata = responseMetadata;
    }

    /**
     * 构造函数，仅使用视频结果列表创建VideoResponse实例
     * 元数据将被设置为null
     *
     * @param videoResults 视频结果列表
     */
    public VideoResponse(List<VideoResult> videoResults) {
        this.videoResults = videoResults;
        this.responseMetadata = null;
    }

    /**
     * 构造函数，使用单个视频结果创建VideoResponse实例
     * 会将单个结果包装为列表形式
     * 元数据将被设置为null
     *
     * @param videoResult 单个视频结果
     */
    public VideoResponse(VideoResult videoResult) {
        this.videoResults = List.of(videoResult);
        this.responseMetadata = null;
    }

    /**
     * 获取第一个视频结果
     *
     * @return 第一个视频结果，如果列表为空可能会抛出异常
     */
    @Override
    public VideoResult getResult() {
        return this.videoResults.get(0);
    }

    /**
     * 获取所有视频结果列表
     *
     * @return 视频结果列表
     */
    @Override
    public List<VideoResult> getResults() {
        return this.videoResults;
    }

    /**
     * 获取响应元数据
     *
     * @return 响应元数据，可能为null
     */
    @Override
    public ResponseMetadata getMetadata() {
        return this.responseMetadata;
    }
}
