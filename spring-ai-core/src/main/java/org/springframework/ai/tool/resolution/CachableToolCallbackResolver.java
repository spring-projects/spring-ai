package org.springframework.ai.tool.resolution;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An abstract class that caches the resolved {@link ToolCallback} and provides a method to remove the cached tcb
 *
 * @author Lin JieBin
 * @since 1.0.0
 */
public abstract class CachableToolCallbackResolver implements ToolCallbackResolver, DestructionAwareBeanPostProcessor {
	private final Map<String, ToolCallback> toolCallbackCacheMap;

	public CachableToolCallbackResolver(Map<String, ToolCallback> toolCallbackCacheMap) {
		this.toolCallbackCacheMap = toolCallbackCacheMap;
	}

	public CachableToolCallbackResolver() {
		this.toolCallbackCacheMap = new ConcurrentHashMap<>();
	}

	/**
	 * the implementation for {@link DestructionAwareBeanPostProcessor#postProcessBeforeDestruction(Object, String)}
	 * 	in order to remove the cached tcb when it is going to be destroyed
	 */
	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
		removeCache(beanName);
	}

	public void removeCache(String toolName) {
		toolCallbackCacheMap.remove(toolName);
	}

	/**
	 * define the final implementation to cache the resolved tool
	 * @param toolName the tool name
	 * @return the cached tool
	 */
	@Override
	public final ToolCallback resolve(String toolName) {
		if (! StringUtils.hasLength(toolName)) {
			return null;
		}
		return toolCallbackCacheMap.computeIfAbsent(toolName, this::resolveInternal);
	}

	/**
	 * the actual implementation to resolve the tool
	 * @param toolName the tool name
	 * @return the resolved tool
	 */
	protected abstract ToolCallback resolveInternal(String toolName);

}
