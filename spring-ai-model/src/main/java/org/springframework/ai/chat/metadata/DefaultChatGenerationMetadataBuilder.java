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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata.Builder;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultChatGenerationMetadataBuilder implements Builder {

	private @Nullable String finishReason;

	private final Map<String, Object> metadata = new HashMap<>();

	private final Set<String> contentFilters = new HashSet<>();

	DefaultChatGenerationMetadataBuilder() {
	}

	@Override
	public Builder finishReason(String finishReason) {
		this.finishReason = finishReason;
		return this;
	}

	@Override
	public <T> Builder metadata(String key, T value) {
		this.metadata.put(key, value);
		return this;
	}

	@Override
	public Builder metadata(Map<String, Object> metadata) {
		this.metadata.putAll(metadata);
		return this;
	}

	@Override
	public Builder contentFilter(String contentFilter) {
		this.contentFilters.add(contentFilter);
		return this;
	}

	@Override
	public Builder contentFilters(Set<String> contentFilters) {
		this.contentFilters.addAll(contentFilters);
		return this;
	}

	@Override
	public ChatGenerationMetadata build() {
		return new DefaultChatGenerationMetadata(this.metadata, this.finishReason, this.contentFilters);
	}

}
