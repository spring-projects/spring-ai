package org.springframework.ai.video.storage;


import com.springai.springaivideoextension.enhanced.trimer.response.VideoScanResponse;

import java.util.Collection;

/**
 * 视频存储接口，提供视频的保存和检索功能
 *
 * @author 王玉涛
 * @version 1.0
 * @since 2025/9/30
 */
public interface VideoStorage {

    /**
     * 保存视频数据，使用默认ID
     *
     * @param videoValue 视频数据内容
     * @return 保存成功返回true，否则返回false
     */
    boolean save(String videoValue);

    /**
     * 根据指定ID保存视频数据
     *
     * @param saveId 视频存储ID
     * @param videoValue 视频数据内容
     * @return 保存成功返回true，否则返回false
     */
    boolean save(String saveId, Object videoValue);

    /**
     * 根据ID查找视频数据
     *
     * @param saveId 视频存储ID
     * @return 返回找到的视频数据对象，未找到返回null
     */
    Object findVideoById(String saveId);

    /**
     * 获取所有保存的ID
     *
     * @return 返回所有保存的ID列表
     */
    Collection<String> keys();

    /**
     * 获取所有未处理的视频任务键值
     *
     * @return 键值列表
     */
    Collection<String> keysForInProgress();

    /**
     * 更新视频任务状态
     *
     * @param keyEnd             视频任务键值
     * @param videoStorageStatus 新的视频任务状态
     * @param scanResponse
     */
    void changeStatus(String keyEnd, VideoStorageStatus videoStorageStatus, VideoScanResponse scanResponse);

    /**
     * 移除默认前缀
     *
     * @param keys 键值列表
     * @return 移除默认前缀后的键值列表
     */
    Collection<String> removeDefaultPrefix(Collection<String> keys);

    /**
     * 删除视频任务
     *
     * @param key 视频任务键值
     */
    void delete(String key);
}
