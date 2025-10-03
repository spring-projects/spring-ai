package org.springframework.ai.video.trimer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
@Getter
@RequiredArgsConstructor
public enum VideoStorageStatus {
    /**
     * 任务已创建，等待处理
     */
    PENDING("pending", "任务排队中"),

    /**
     * 任务正在处理中
     */
    PROCESSING("processing", "视频生成中"),

    /**
     * 任务成功完成
     */
    SUCCESS("success", "处理成功"),

    /**
     * 任务处理失败（可重试）
     */
    FAILED("failed", "处理失败"),

    /**
     * 任务超时（需人工干预）
     */
    TIME_OUT("time_out", "处理超时"),

    /**
     * 任务被取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

}
