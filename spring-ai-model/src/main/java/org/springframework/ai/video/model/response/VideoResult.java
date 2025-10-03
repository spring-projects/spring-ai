package org.springframework.ai.video.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResultMetadata;

/**
 * 视频处理结果封装类
 *
 * 该类用于封装视频处理操作的结果，实现Spring AI的ModelResult接口，
 * 专门用于处理String类型的输出结果。
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/29
 */
public class VideoResult implements ModelResult<String> {

    /**
     * 视频处理输出结果
     * 使用@JsonProperty注解将该字段映射为JSON中的"requestId"属性
     */
    @JsonProperty("requestId")
    private String output;

    /**
     * 结果元数据信息
     * 使用@JsonIgnore注解在序列化时忽略该字段
     */
    @JsonIgnore
    private ResultMetadata resultMetadata;

    /**
     * 获取视频处理的输出结果
     *
     * @return String类型的处理结果
     */
    @Override
    public String getOutput() {
        return this.output;
    }

    /**
     * 获取结果的元数据信息
     *
     * @return ResultMetadata元数据对象
     */
    @Override
    public ResultMetadata getMetadata() {
        return this.resultMetadata;
    }
}
