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

	// /**
	// * Factory method used to construct a new {@link ChatGenerationMetadata} from the
	// * given {@link String finish reason} and content filter metadata.
	// * @param finishReason {@link String} contain the reason for the choice completion.
	// * @param contentFilterMetadata underlying AI provider metadata for filtering
	// applied
	// * to generation content.
	// * @return a new {@link ChatGenerationMetadata} from the given {@link String finish
	// * reason} and content filter metadata.
	// */
	// static ChatGenerationMetadata from(String finishReason, Object
	// contentFilterMetadata) {
	// return new ChatGenerationMetadata() {

	// @Override
	// @SuppressWarnings("unchecked")
	// public <T> T getContentFilterMetadata() {
	// return (T) contentFilterMetadata;
	// }

	// @Override
	// public String getFinishReason() {
	// return finishReason;
	// }

	// @Override
	// public String toString() {
	// return "ChatGenerationMetadata{finishReason=" + finishReason + "," +
	// "contentFilterMetadata="
	// + contentFilterMetadata + "}";
	// }
	// };
	// }

	// /**
	// * Returns the underlying AI provider metadata for filtering applied to generation
	// * content.
	// * @param <T> {@link Class Type} used to cast the filtered content metadata into the
	// * AI provider-specific type.
	// * @return the underlying AI provider metadata for filtering applied to generation
	// * content.
	// */
	// @Nullable
	// <T> T getContentFilterMetadata();

	/**
	 * Get the {@link String reason} this choice completed for the generation.
	 * @return the {@link String reason} this choice completed for the generation.
	 */
	String getFinishReason();

	Set<String> getContentFilters();

	<T> T get(String key);

	boolean containsKey(String key);

	<T> T getOrDefault(String key, T defaultObject);

	Set<Entry<String, Object>> entrySet();

	Set<String> keySet();

	boolean isEmpty();

	public static Builder builder() {
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
		Builder finishReason(String id);

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
