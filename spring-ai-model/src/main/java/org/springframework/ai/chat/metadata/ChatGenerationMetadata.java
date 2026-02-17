/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.metadata;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ResultMetadata;

/**
 *
 * Represents the metadata associated with the generation of a chat response.
 *
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
public interface ChatGenerationMetadata extends ResultMetadata {

	ChatGenerationMetadata NULL = builder().build();

	/**
	 * Get the {@link String reason} this choice completed for the generation.
	 * @return the {@link String reason} this choice completed for the generation.
	 */
	@Nullable String getFinishReason();

	/**
	 * Get the normalized category for this finish reason.
	 *
	 * <p>
	 * This method provides a provider-agnostic categorization of the raw finish reason,
	 * making it easier to build consistent audits, metrics, and alerts across multiple AI
	 * providers.
	 * </p>
	 * @return the categorized finish reason, never null
	 * @see FinishReasonCategory#categorize(String)
	 * @since 1.0.0
	 */
	default FinishReasonCategory getFinishReasonCategory() {
		return FinishReasonCategory.categorize(getFinishReason());
	}

	Set<String> getContentFilters();

	<T> @Nullable T get(String key);

	boolean containsKey(String key);

	<T> T getOrDefault(String key, T defaultObject);

	Set<Entry<String, Object>> entrySet();

	Set<String> keySet();

	boolean isEmpty();

	static Builder builder() {
		return new DefaultChatGenerationMetadataBuilder();
	}

	/**
	 * @author Christian Tzolov
	 * @since 1.0.0
	 */
	public interface Builder {

		/**
		 * Set the reason this choice completed for the generation.
		 */
		Builder finishReason(@Nullable String finishReason);

		/**
		 * Add metadata to the Generation result.
		 */
		<T> Builder metadata(String key, T value);

		/**
		 * Add metadata to the Generation result.
		 */
		Builder metadata(Map<String, Object> metadata);

		/**
		 * Add content filter to the Generation result.
		 */
		Builder contentFilter(String contentFilter);

		/**
		 * Add content filters to the Generation result.
		 */
		Builder contentFilters(Set<String> contentFilters);

		/**
		 * Build the Generation metadata.
		 */
		ChatGenerationMetadata build();

	}

}
