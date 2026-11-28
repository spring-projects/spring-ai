/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for filtering {@link ToolCallback} instances based on metadata and other
 * criteria. This class provides various predicate-based filters that can be used to
 * select tools dynamically based on their metadata properties.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre class="code">
 * // Filter tools by type
 * List&lt;ToolCallback&gt; filteredTools = ToolCallbackFilters.filterByType(allTools, "RealTimeAnalysis");
 *
 * // Filter tools by multiple criteria
 * Predicate&lt;ToolCallback&gt; filter = ToolCallbackFilters.byType("RealTimeAnalysis")
 *     .and(ToolCallbackFilters.byMinPriority(7));
 * List&lt;ToolCallback&gt; filtered = allTools.stream().filter(filter).collect(Collectors.toList());
 * </pre>
 *
 */
public final class ToolCallbackFilters {

	private ToolCallbackFilters() {
	}

	/**
	 * Creates a predicate that filters tools by their type metadata.
	 * @param type the required type
	 * @return a predicate that tests if a tool has the specified type
	 */
	public static Predicate<ToolCallback> byType(String type) {
		return toolCallback -> {
			Map<String, Object> metadata = toolCallback.getToolDefinition().metadata();
			Object typeValue = metadata.get("type");
			return typeValue != null && type.equals(typeValue.toString());
		};
	}

	/**
	 * Creates a predicate that filters tools by their category metadata.
	 * @param category the required category
	 * @return a predicate that tests if a tool has the specified category
	 */
	public static Predicate<ToolCallback> byCategory(String category) {
		return toolCallback -> {
			Map<String, Object> metadata = toolCallback.getToolDefinition().metadata();
			Object categoryValue = metadata.get("category");
			return categoryValue != null && category.equals(categoryValue.toString());
		};
	}

	/**
	 * Creates a predicate that filters tools by their priority metadata. Only tools with
	 * priority greater than or equal to the specified minimum are included.
	 * @param minPriority the minimum priority
	 * @return a predicate that tests if a tool's priority meets the threshold
	 */
	public static Predicate<ToolCallback> byMinPriority(int minPriority) {
		return toolCallback -> {
			Map<String, Object> metadata = toolCallback.getToolDefinition().metadata();
			Object priorityValue = metadata.get("priority");
			if (priorityValue != null) {
				try {
					int priority = priorityValue instanceof Number ? ((Number) priorityValue).intValue()
							: Integer.parseInt(priorityValue.toString());
					return priority >= minPriority;
				}
				catch (NumberFormatException e) {
					return false;
				}
			}
			return false;
		};
	}

	/**
	 * Creates a predicate that filters tools by their tags metadata. Tools must have at
	 * least one of the specified tags.
	 * @param tags the required tags
	 * @return a predicate that tests if a tool has any of the specified tags
	 */
	public static Predicate<ToolCallback> byTags(String... tags) {
		Set<String> tagSet = Set.of(tags);
		return toolCallback -> {
			Map<String, Object> metadata = toolCallback.getToolDefinition().metadata();
			Object tagsValue = metadata.get("tags");
			if (tagsValue instanceof List) {
				List<?> toolTags = (List<?>) tagsValue;
				return toolTags.stream().anyMatch(tag -> tagSet.contains(tag.toString()));
			}
			return false;
		};
	}

	/**
	 * Creates a predicate that filters tools by a custom metadata field.
	 * @param key the metadata key
	 * @param expectedValue the expected value
	 * @return a predicate that tests if a tool has the specified metadata value
	 */
	public static Predicate<ToolCallback> byMetadata(String key, Object expectedValue) {
		return toolCallback -> {
			Map<String, Object> metadata = toolCallback.getToolDefinition().metadata();
			Object value = metadata.get(key);
			return expectedValue.equals(value);
		};
	}

	/**
	 * Filters a list of tool callbacks by type.
	 * @param toolCallbacks the list of tool callbacks to filter
	 * @param type the required type
	 * @return a filtered list containing only tools with the specified type
	 */
	public static List<ToolCallback> filterByType(List<ToolCallback> toolCallbacks, String type) {
		return toolCallbacks.stream().filter(byType(type)).collect(Collectors.toList());
	}

	/**
	 * Filters an array of tool callbacks by type.
	 * @param toolCallbacks the array of tool callbacks to filter
	 * @param type the required type
	 * @return a filtered array containing only tools with the specified type
	 */
	public static ToolCallback[] filterByType(ToolCallback[] toolCallbacks, String type) {
		return Arrays.stream(toolCallbacks).filter(byType(type)).toArray(ToolCallback[]::new);
	}

	/**
	 * Filters a list of tool callbacks by category.
	 * @param toolCallbacks the list of tool callbacks to filter
	 * @param category the required category
	 * @return a filtered list containing only tools with the specified category
	 */
	public static List<ToolCallback> filterByCategory(List<ToolCallback> toolCallbacks, String category) {
		return toolCallbacks.stream().filter(byCategory(category)).collect(Collectors.toList());
	}

	/**
	 * Filters a list of tool callbacks by minimum priority.
	 * @param toolCallbacks the list of tool callbacks to filter
	 * @param minPriority the minimum priority
	 * @return a filtered list containing only tools with priority >= minPriority
	 */
	public static List<ToolCallback> filterByMinPriority(List<ToolCallback> toolCallbacks, int minPriority) {
		return toolCallbacks.stream().filter(byMinPriority(minPriority)).collect(Collectors.toList());
	}

	/**
	 * Filters a list of tool callbacks by tags.
	 * @param toolCallbacks the list of tool callbacks to filter
	 * @param tags the required tags (tool must have at least one)
	 * @return a filtered list containing only tools with at least one of the specified
	 * tags
	 */
	public static List<ToolCallback> filterByTags(List<ToolCallback> toolCallbacks, String... tags) {
		return toolCallbacks.stream().filter(byTags(tags)).collect(Collectors.toList());
	}

}
