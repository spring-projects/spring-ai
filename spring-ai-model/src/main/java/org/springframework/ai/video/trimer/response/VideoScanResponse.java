package org.springframework.ai.video.trimer.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
@Data
public class VideoScanResponse {

    /**
     * 创建一个空的响应对象, 用于初始化任务状态
     */
    public VideoScanResponse() {
        this.startTime = System.currentTimeMillis();
        this.status = Status.IN_QUEUE;
    }

    /**
     * 创建一个空的响应对象, 用于初始化任务状态
     */
    public VideoScanResponse(String requestId) {
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
        this.status = Status.IN_QUEUE;
    }

    /**
     * 获取任务处理失败的原因
     *
     * @return 任务处理失败的原因
     */
    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    /**
     * 任务当前状态
     */
    public enum Status {
        @JsonProperty("InQueue") IN_QUEUE("InQueue"),
        @JsonProperty("InProgress") IN_PROGRESS("InProgress"),
        @JsonProperty("Succeed") SUCCEED("Succeed"),
        @JsonProperty("Failed") FAILED("Failed");

        private final String apiValue;
        Status(String apiValue) { this.apiValue = apiValue; }
    }

    //----- 所有状态均返回的字段 -----//
    @JsonProperty("status")
    private Status status;
    @JsonIgnore
    private String requestId;
    @JsonIgnore
    private Long startTime;

    //----- 仅失败时返回的字段 -----//
    @JsonProperty("reason")
    private String reason;

    //----- 仅成功时返回的字段 -----//
    @JsonProperty("results")
    private Results results;

    @Data
    public static class Results {
        @JsonProperty("videos")
        private List<Video> videos;     // 生成的视频列表
        @JsonProperty("timings")
        private Timings timings;        // 耗时详情
        @JsonProperty("seed")
        private Integer seed;           // 实际使用的随机数种子

        @Data
        public static class Video {
            @JsonProperty("url")
            private String url;         // 视频访问URL
        }

        @Data
        public static class Timings {
            @JsonProperty("inference")
            private Long inference;
        }
    }

    /**
     * 判断任务是否处理成功
     *
     * @return 任务处理成功返回true，否则返回false
     */
    public boolean isSuccess() {
        if (Objects.isNull(status)) {
            return false;
        } else {
            return status == Status.SUCCEED;
        }
    }
}
