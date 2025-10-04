package org.springframework.ai.video.trimer;

import com.springai.springaivideoextension.enhanced.api.VideoApi;
import com.springai.springaivideoextension.enhanced.storage.VideoStorage;
import com.springai.springaivideoextension.enhanced.storage.VideoStorageStatus;
import com.springai.springaivideoextension.enhanced.trimer.response.VideoScanResponse;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;

/**
 * 视频定时任务处理器
 *
 * 该类负责定期扫描未处理成功的视频任务，并根据处理结果更新任务状态。
 * 主要功能包括：
 * 1. 定时扫描未成功的视频任务
 * 2. 调用视频API处理任务
 * 3. 根据处理结果更新任务状态（成功/超时）
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
@Data
@Slf4j
@RequiredArgsConstructor
public class VideoTimer {

    private Long videoTimeout;
    private Long ttl;

    @PostConstruct
    public void init() {
        log.info("初始化视频定时任务");
    }

    /**
     * 视频存储实现类, 暂时使用内存存储直接实现，后续可以自行修改
     */
    private final VideoStorage videoStorage;

    /**
     * 视频API实现类
     */
    private final VideoApi videoApi;

    /**
     * 视频扫描定时任务
     *
     * 该方法会定期执行，扫描所有未处理成功的视频任务并尝试处理。
     * 执行间隔可以通过配置项 ai.video.trimer.interval 进行设置，默认为30秒。
     *
     * 处理逻辑：
     * 1. 获取所有未成功的任务键值
     * 2. 移除默认前缀以获取真实键值
     * 3. 遍历每个任务键值，调用视频API进行处理
     * 4. 根据API返回结果更新任务状态：
     *    - 处理成功：更新状态为SUCCESS
     *    - 处理失败且超时：更新状态为TIME_OUT
     */
    @Scheduled(fixedDelayString = "${ai.video.trimer.interval:10000}")
    public void videoTask() {
        log.info("视频扫描任务开始执行");

        // 获取所有未处理成功的视频任务键值集合
        Collection<String> keys = videoStorage.keysForInProgress();

        // 移除键值中的默认前缀，获取真实的任务标识
        keys = videoStorage.removeDefaultPrefix(keys);

        // 遍历所有未成功的任务
        for (String key : keys) {
            log.info("开始扫描未成功数据: {}", key);

            // 调用视频API处理当前任务
            ResponseEntity<VideoScanResponse> responseEntity = videoApi.createVideo(key);
            log.info("扫描结果: {}", responseEntity);

            // 解析API响应结果
            VideoScanResponse scanResponse = responseEntity.getBody();

            if (scanResponse == null) {
                log.error("API返回结果为空");
                continue;
            }

            // 根据处理结果更新任务状态
            if (scanResponse.isSuccess()) {
                log.info("开始处理成功数据: {}", key);
                // 任务处理成功，更新状态为SUCCESS
                videoStorage.changeStatus(key, VideoStorageStatus.SUCCESS, scanResponse);
            } else if (scanResponse.isFailed() && isTimeOut(scanResponse.getStartTime())) {
                log.info("数据处理超时: {}", key);
                // 任务处理超时，更新状态为TIME_OUT
                videoStorage.changeStatus(key, VideoStorageStatus.TIME_OUT, scanResponse);
            } else if (isTtl(scanResponse.getStartTime())) {
                log.info("数据处理已过期: {}", key);
                videoStorage.delete(key);
            }
        }

        log.info("视频扫描任务结束");
    }

    /**
     * 判断任务是否已过期
     *
     * @param startTime 任务开始时间
     * @return 任务是否已超时
     */
    private boolean isTtl(Long startTime) {
        return System.currentTimeMillis() - startTime > ttl;
    }

    /**
     * 判断任务是否超时
     *
     * @param startTime 任务开始时间
     * @return 任务是否超时
     */
    private boolean isTimeOut(Long startTime) {
        return System.currentTimeMillis() - startTime > videoTimeout;
    }


}
