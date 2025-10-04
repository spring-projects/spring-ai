package org.springframework.ai.video.trimer.config;

import com.springai.springaivideoextension.enhanced.api.VideoApi;
import com.springai.springaivideoextension.enhanced.storage.VideoStorage;
import com.springai.springaivideoextension.enhanced.trimer.VideoTimer;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 视频定时器配置类
 *
 * 该配置类用于管理视频处理的定时任务相关配置，包括是否启用定时器、超时时间及TTL设置
 * 通过读取 application.yml 中 ai.video.trimer 前缀的配置项进行参数设置
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.video.trimer")
public class VideoTimerConfig {

    /**
     * 是否启用视频定时器功能，默认为true
     */
    private boolean enabled = true;

    /**
     * 视频处理超时时间（毫秒），默认为300秒
     */
    private Long timeout = 300000L;

    /**
     * 视频存储的生存时间（毫秒），默认为24小时
     */
    private Long ttl = 86400000L;

    /**
     * 创建并配置视频定时器Bean
     *
     * @param videoStorage 视频存储服务
     * @param videoApi 视频API服务
     * @return 配置好的视频定时器实例，如果未启用则返回null
     */
    @Bean
    public VideoTimer videoTimer(VideoStorage videoStorage, VideoApi videoApi) {
        // 如果未启用定时器功能，则不创建Bean实例
        if (!enabled) {
            return null;
        }

        // 创建视频定时器实例并设置相关参数
        VideoTimer videoTimer = new VideoTimer(videoStorage, videoApi);
        videoTimer.setVideoTimeout(timeout);
        videoTimer.setTtl(ttl);
        return videoTimer;
    }
}
