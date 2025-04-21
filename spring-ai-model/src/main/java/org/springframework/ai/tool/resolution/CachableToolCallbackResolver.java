package org.springframework.ai.tool.resolution;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An abstract class that caches the resolved {@link ToolCallback} and provides a method to remove the cached tcbs.
 *	Implementation of {@link DestructionAwareBeanPostProcessor} DestructionAwareBeanPostProcessor in order to remove the cached tcb when bean is going to be destroyed by spring.
 *
 * @author JieBin Lin
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

	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
		removeCachedToolCallback(beanName);
	}

	/**
	 * remove the cached tcb manually
	 */
	public void removeCachedToolCallback(String toolName) {
		toolCallbackCacheMap.remove(toolName);
	}

	/**
	 * remove all the cached tcbs
	 */
	public void clearCachedToolCallbacks() {
		toolCallbackCacheMap.clear();
	}

	/**
	 * define the final implementation to cache the resolved tool
	 * @param toolName the tool name
	 * @return the cached tool
	 */
	@Override
	public final ToolCallback resolve(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");

		return toolCallbackCacheMap.computeIfAbsent(toolName, this::uncachedResolve);
	}

	/**
	 * the actual implementation to resolve the tool that will not cache the tcb
	 * @param toolName the tool name
	 * @return the resolved tool
	 */
	public abstract ToolCallback uncachedResolve(String toolName);

}
