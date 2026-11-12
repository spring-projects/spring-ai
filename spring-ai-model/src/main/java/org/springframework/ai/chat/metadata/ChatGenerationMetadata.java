/*
 * Copyright 2023-present the original author or authors.
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
	 * Get the reason this generation finished, as the provider names it. The names differ
	 * between providers: {@code stop}, {@code end_turn} and {@code STOP} all say that the
	 * model finished its answer. For a meaning that is the same for every provider, see
	 * {@link #getFinishReasonCategory()}.
	 * @return the raw finish reason of the provider, or {@code null} when it gave none
	 */
	@Nullable String getFinishReason();

	/**
	 * Return the normalized category of the finish reason, which is the same for every
	 * provider, while {@link #getFinishReason()} keeps the name the provider uses.
	 * <p>
	 * A chat model classifies its own finish reasons and sets the category with
	 * {@link Builder#finishReasonCategory(FinishReasonCategory)}. It sees the whole
	 * provider response, so it can also classify a stop that the finish reason alone does
	 * not tell, such as a refusal reported in another field.
	 * <p>
	 * This default implementation is the fallback for metadata that carries no category.
	 * It classifies the raw string only: a {@code null} reason is
	 * {@link FinishReasonCategory#UNSPECIFIED} and an unrecognized non-blank reason is
	 * {@link FinishReasonCategory#OTHER}.
	 * @return the categorized finish reason, never {@code null}
	 * @since 2.1.0
	 * @see FinishReasonCategory#categorize(String)
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
		 * Set the reason this generation finished, as the provider names it. See
		 * {@link #finishReasonCategory(FinishReasonCategory)} for its normalized
		 * category.
		 */
		Builder finishReason(@Nullable String finishReason);

		/**
		 * Set the normalized category of the finish reason. A chat model sets it next to
		 * {@link #finishReason(String)}, as it knows what the names of its provider mean.
		 * When it is not set, the category is derived from the raw finish reason with
		 * {@link FinishReasonCategory#categorize(String)}.
		 * @param finishReasonCategory the category, or {@code null} to derive it
		 * @return this builder
		 * @since 2.1.0
		 */
		default Builder finishReasonCategory(@Nullable FinishReasonCategory finishReasonCategory) {
			return this;
		}

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
