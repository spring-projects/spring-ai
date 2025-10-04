package org.springframework.ai.video.storage.impl;

import com.springai.springaivideoextension.enhanced.storage.VideoStorage;
import com.springai.springaivideoextension.enhanced.storage.VideoStorageStatus;
import com.springai.springaivideoextension.enhanced.trimer.response.VideoScanResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于内存的视频存储实现类
 * 提供视频数据的保存和查询功能，使用内存Map作为存储介质
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
@Data
@Slf4j
@Component
public class InMemoryVideoStorage implements VideoStorage {

    /**
     * 默认键前缀，用于构建存储键值，这里配置到配置文件中，在这里我们进行默认值的配置
     */
    @Value("${ai.video.trimer.key-prefix:in:memory:key:}")
    private String defaultKey;

    /**
     * 视频数据存储映射表
     * key: 完整存储键值 (defaultKey + saveId)
     * value: 视频数据内容
     */
    private final Map<String, VideoScanResponse> videoStorage = new HashMap<>();

    /**
     * 保存视频数据，使用默认ID
     * 该方法会自动构建包含默认前缀和视频内容的完整ID
     *
     * @param videoValue 视频数据内容
     * @return 保存成功返回true，否则返回false
     */
    @Override
    public boolean save(String videoValue) {
        log.info("未指定前缀，将使用默认前缀: {}", defaultKey);
        String saveId = this.defaultKey + videoValue;
        return this.save(saveId, new VideoScanResponse(videoValue));
    }

    /**
     * 根据指定ID保存视频数据
     * 将视频数据存储到内存映射表中，键为defaultKey与saveId的组合
     *
     * @param saveId     视频存储ID，将与默认前缀组合成完整键值
     * @param videoValue 视频数据内容，将转换为字符串存储
     * @return 保存成功返回true，否则返回false
     */
    @Override
    public boolean save(String saveId, Object videoValue) {
        log.info("开始保存视频数据: {}", saveId);
        try {
            this.videoStorage.put(saveId, (VideoScanResponse) videoValue);
            log.info("保存视频数据成功: {}", saveId);
            return true;
        } catch (Exception e) {
            log.error("保存视频数据失败: {}", saveId, e);
            return false;
        }
    }

    /**
     * 根据ID查找视频数据
     * 从内存映射表中检索指定ID的视频数据
     *
     * @param saveId 视频存储ID，将与默认前缀组合成完整键值
     * @return 返回找到的视频数据对象，未找到返回null
     */
    @Override
    public Object findVideoById(String saveId) {
        String key = this.defaultKey + saveId;
        log.info("开始查找视频数据: {}", key);
        VideoScanResponse videoData = this.videoStorage.get(key);
        if (videoData != null) {
            log.info("找到视频数据: {}", key);
            return videoData;
        } else {
            log.info("未找到视频数据: {}", key);
            return null;
        }
    }

    /**
     * 获取所有保存的ID
     *
     * @return 返回所有保存的ID列表
     */
    @Override
    public Collection<String> keys() {
        log.info("开始获取所有保存的ID");
        Set<String> strings = this.videoStorage.keySet();
        log.info("获取所有保存的ID成功: {}", strings);
        return strings;
    }



    /**
     * 获取所有正在进行中的视频任务键值
     * 遍历视频存储映射表，筛选出状态为IN_QUEUE或IN_PROGRESS的视频任务
     * 这些任务表示正在处理队列中或处理中
     *
     * @return 处理中和队列中状态的视频任务键值列表
     */
    @Override
    public Collection<String> keysForInProgress() {
        log.info("开始获取所有正在进行中的视频任务键值");
        return videoStorage.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() == VideoScanResponse.Status.IN_QUEUE ||
                        entry.getValue().getStatus() == VideoScanResponse.Status.IN_PROGRESS)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 更新视频任务状态
     *
     * @param keyEnd             视频任务键值
     * @param videoStorageStatus 新的视频任务状态
     * @param scanResponse       视频扫描结果
     */
    @Override
    public void changeStatus(String keyEnd, VideoStorageStatus videoStorageStatus, VideoScanResponse scanResponse) {
        scanResponse.setRequestId(keyEnd);

        String key = this.defaultKey + keyEnd;

        log.info("开始更新视频任务状态: {}, 新状态: {}", key, videoStorageStatus);
        VideoScanResponse videoData = this.videoStorage.get(key);

        if (videoData != null) {
            // 根据传入的状态更新视频任务状态
            switch (videoStorageStatus) {
                case TIME_OUT, FAILED:
                    videoData.setStatus(VideoScanResponse.Status.FAILED);
                    break;
                case SUCCESS:
                    videoData.setStatus(VideoScanResponse.Status.SUCCEED);
                    this.videoStorage.put(key, scanResponse);
                    break;
                default:
                    log.warn("未知的状态: {}", videoStorageStatus);
                    break;
            }
            log.info("更新视频任务状态成功: {}, 新状态: {}", key, videoData.getStatus());
        } else {
            log.warn("未找到对应的视频任务: {}", key);
        }
    }

    /**
     * 移除默认前缀
     *
     * @param keys 键值列表
     * @return 移除默认前缀后的键值列表
     */
    @Override
    public Collection<String> removeDefaultPrefix(Collection<String> keys) {
        log.info("开始移除默认前缀: {}", keys);
        List<String> strings = keys.stream().map(key -> key.replace(this.defaultKey, "")).toList();
        log.debug("移除默认前缀成功: {}", strings);
        return strings;
    }

    /**
     * 删除视频任务
     *
     * @param key 视频任务键值
     */
    @Override
    public void delete(String key) {
        try {
            log.info("开始删除视频任务: {}", key);
            this.videoStorage.remove(key);
        } catch (Exception e) {
            log.error("删除视频任务失败: {}", key, e);
        }
    }

}
