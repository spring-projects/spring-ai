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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link ChatGenerationMetadata}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultChatGenerationMetadata implements ChatGenerationMetadata {

	private final Map<String, Object> metadata;

	private final @Nullable String finishReason;

	private final Set<String> contentFilters;

	/**
	 * Create a new {@link DefaultChatGenerationMetadata} instance.
	 * @param metadata the metadata map, must not be null
	 * @param finishReason the finish reason, may be null
	 * @param contentFilters the content filters, must not be null
	 * @throws IllegalArgumentException if metadata or contentFilters is null
	 */
	DefaultChatGenerationMetadata(Map<String, Object> metadata, @Nullable String finishReason,
			Set<String> contentFilters) {
		Assert.notNull(metadata, "Metadata must not be null");
		Assert.notNull(contentFilters, "Content filters must not be null");
		this.metadata = metadata;
		this.finishReason = finishReason;
		this.contentFilters = new HashSet<>(contentFilters);
	}

	@Override
	public <T> @Nullable T get(String key) {
		return (T) this.metadata.get(key);
	}

	@Override
	public boolean containsKey(String key) {
		return this.metadata.containsKey(key);
	}

	@Override
	public <T> T getOrDefault(String key, T defaultObject) {
		T value = get(key);
		return value != null ? value : defaultObject;
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return Collections.unmodifiableSet(this.metadata.entrySet());
	}

	@Override
	public Set<String> keySet() {
		return Collections.unmodifiableSet(this.metadata.keySet());
	}

	@Override
	public boolean isEmpty() {
		return this.metadata.isEmpty();
	}

	@Override
	public @Nullable String getFinishReason() {
		return this.finishReason;
	}

	@Override
	public Set<String> getContentFilters() {
		return Collections.unmodifiableSet(this.contentFilters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.metadata, this.finishReason, this.contentFilters);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		DefaultChatGenerationMetadata other = (DefaultChatGenerationMetadata) obj;
		return Objects.equals(this.metadata, other.metadata) && Objects.equals(this.finishReason, other.finishReason)
				&& Objects.equals(this.contentFilters, other.contentFilters);
	}

	@Override
	public String toString() {
		return String.format("DefaultChatGenerationMetadata[finishReason='%s', filters=%d, metadata=%d]",
				this.finishReason, this.contentFilters.size(), this.metadata.size());
	}

}
