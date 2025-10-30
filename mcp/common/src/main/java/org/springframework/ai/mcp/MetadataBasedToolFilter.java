/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.StringUtils;

/**
 * A metadata-based implementation of {@link McpToolFilter} that filters MCP tools based
 * on their metadata properties. This filter supports filtering by type, category,
 * priority, tags, and custom metadata fields.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre class="code">
 * // Filter tools by type
 * var filter = MetadataBasedToolFilter.builder().types(Set.of("RealTimeAnalysis")).build();
 *
 * // Filter tools by category and minimum priority
 * var filter = MetadataBasedToolFilter.builder()
 *     .categories(Set.of("market", "analytics"))
 *     .minPriority(7)
 *     .build();
 * </pre>
 *
 */
public class MetadataBasedToolFilter implements McpToolFilter {

	private static final Logger logger = LoggerFactory.getLogger(MetadataBasedToolFilter.class);

	private final Set<String> types;

	private final Set<String> categories;

	private final Set<String> tags;

	private final Integer minPriority;

	private final Integer maxPriority;

	private final Map<String, Predicate<Object>> customFilters;

	private MetadataBasedToolFilter(Builder builder) {
		this.types = builder.types;
		this.categories = builder.categories;
		this.tags = builder.tags;
		this.minPriority = builder.minPriority;
		this.maxPriority = builder.maxPriority;
		this.customFilters = builder.customFilters;
	}

	@Override
	public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
		// Extract metadata from tool
		// Note: MCP Java SDK's Tool might have a meta() method or we need to extract
		// metadata from description or other fields
		Map<String, Object> metadata = extractMetadataFromTool(tool);

		if (metadata.isEmpty()) {
			// If no metadata is present, check if we're filtering anything
			// If filters are set, exclude tools without metadata
			return types.isEmpty() && categories.isEmpty() && tags.isEmpty() && minPriority == null
					&& maxPriority == null && customFilters.isEmpty();
		}

		// Filter by type
		if (!types.isEmpty()) {
			Object typeValue = metadata.get("type");
			if (typeValue == null || !types.contains(typeValue.toString())) {
				return false;
			}
		}

		// Filter by category
		if (!categories.isEmpty()) {
			Object categoryValue = metadata.get("category");
			if (categoryValue == null || !categories.contains(categoryValue.toString())) {
				return false;
			}
		}

		// Filter by tags
		if (!tags.isEmpty()) {
			Object tagsValue = metadata.get("tags");
			if (tagsValue == null) {
				return false;
			}
			if (tagsValue instanceof List) {
				List<?> toolTags = (List<?>) tagsValue;
				boolean hasMatchingTag = toolTags.stream().anyMatch(tag -> tags.contains(tag.toString()));
				if (!hasMatchingTag) {
					return false;
				}
			}
			else if (!tags.contains(tagsValue.toString())) {
				return false;
			}
		}

		// Filter by priority
		if (minPriority != null || maxPriority != null) {
			Object priorityValue = metadata.get("priority");
			if (priorityValue != null) {
				try {
					int priority = priorityValue instanceof Number ? ((Number) priorityValue).intValue()
							: Integer.parseInt(priorityValue.toString());

					if (minPriority != null && priority < minPriority) {
						return false;
					}
					if (maxPriority != null && priority > maxPriority) {
						return false;
					}
				}
				catch (NumberFormatException e) {
					logger.warn("Invalid priority value in metadata: {}", priorityValue);
					return false;
				}
			}
			else {
				// No priority metadata, but we require it for filtering
				return false;
			}
		}

		// Apply custom filters
		for (Map.Entry<String, Predicate<Object>> entry : customFilters.entrySet()) {
			Object value = metadata.get(entry.getKey());
			if (!entry.getValue().test(value)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Extracts metadata from an MCP tool. This implementation attempts to extract
	 * metadata from the tool's description if it follows a specific format: [key1=value1,
	 * key2=value2] Description text
	 *
	 * Subclasses can override this method to implement different metadata extraction
	 * strategies.
	 * @param tool the MCP tool
	 * @return a map of metadata key-value pairs
	 */
	protected Map<String, Object> extractMetadataFromTool(McpSchema.Tool tool) {
		// Try to extract from description if it has metadata prefix
		String description = tool.description();
		if (StringUtils.hasText(description) && description.startsWith("[")) {
			int endIdx = description.indexOf("]");
			if (endIdx > 0) {
				String metadataStr = description.substring(1, endIdx);
				return parseMetadataString(metadataStr);
			}
		}

		// If MCP Tool has a meta() method, try to use it
		// This would require reflection or waiting for the MCP SDK to expose it
		// For now, return empty map
		return Map.of();
	}

	private Map<String, Object> parseMetadataString(String metadataStr) {
		Map<String, Object> metadata = new java.util.HashMap<>();
		String[] entries = metadataStr.split(",");
		for (String entry : entries) {
			String[] parts = entry.split("=", 2);
			if (parts.length == 2) {
				String key = parts[0].trim();
				String value = parts[1].trim();
				if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
					metadata.put(key, value);
				}
			}
		}
		return metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Set<String> types = Set.of();

		private Set<String> categories = Set.of();

		private Set<String> tags = Set.of();

		private Integer minPriority;

		private Integer maxPriority;

		private Map<String, Predicate<Object>> customFilters = Map.of();

		private Builder() {
		}

		/**
		 * Set the allowed tool types.
		 * @param types the types to filter by
		 * @return this builder
		 */
		public Builder types(Set<String> types) {
			this.types = types != null ? Set.copyOf(types) : Set.of();
			return this;
		}

		/**
		 * Set the allowed tool categories.
		 * @param categories the categories to filter by
		 * @return this builder
		 */
		public Builder categories(Set<String> categories) {
			this.categories = categories != null ? Set.copyOf(categories) : Set.of();
			return this;
		}

		/**
		 * Set the required tags (tool must have at least one of these tags).
		 * @param tags the tags to filter by
		 * @return this builder
		 */
		public Builder tags(Set<String> tags) {
			this.tags = tags != null ? Set.copyOf(tags) : Set.of();
			return this;
		}

		/**
		 * Set the minimum priority threshold.
		 * @param minPriority the minimum priority
		 * @return this builder
		 */
		public Builder minPriority(Integer minPriority) {
			this.minPriority = minPriority;
			return this;
		}

		/**
		 * Set the maximum priority threshold.
		 * @param maxPriority the maximum priority
		 * @return this builder
		 */
		public Builder maxPriority(Integer maxPriority) {
			this.maxPriority = maxPriority;
			return this;
		}

		/**
		 * Add a custom filter for a specific metadata field.
		 * @param key the metadata key
		 * @param predicate the predicate to test the value
		 * @return this builder
		 */
		public Builder addCustomFilter(String key, Predicate<Object> predicate) {
			if (this.customFilters.isEmpty()) {
				this.customFilters = new java.util.HashMap<>();
			}
			else if (!(this.customFilters instanceof java.util.HashMap)) {
				this.customFilters = new java.util.HashMap<>(this.customFilters);
			}
			this.customFilters.put(key, predicate);
			return this;
		}

		public MetadataBasedToolFilter build() {
			return new MetadataBasedToolFilter(this);
		}

	}

}
